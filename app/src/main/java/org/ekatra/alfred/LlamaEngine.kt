package org.ekatra.alfred

import org.ekatra.alfred.persona.ActivePersona

import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import java.io.File

/**
 * LlamaEngine - Native LLM inference engine using llama.cpp
 *
 * Implements [InferenceEngine] so callers depend on the interface, not this class.
 * Consumers should prefer [generateRaw] with a fully-formatted prompt.
 */
class LlamaEngine : InferenceEngine {
    
    companion object {
        private const val TAG = "LlamaEngine"
        @Volatile private var backendInitialized = false
        private fun logPrefix(): String = "pid=${android.os.Process.myPid()} tid=${Thread.currentThread().id}"
        
        init {
            try {
                System.loadLibrary("llama_jni")
                Log.d(TAG, "${logPrefix()} Native library loaded successfully")
            } catch (e: UnsatisfiedLinkError) {
                Log.e(TAG, "${logPrefix()} Failed to load native library: ${e.message}")
            }
        }
    }
    
    // Engine state — MutableStateFlow for atomic, observable transitions
    enum class State {
        UNINITIALIZED,
        LOADING,
        READY,
        GENERATING,
        ERROR
    }

    private val _state = MutableStateFlow(State.UNINITIALIZED)
    val stateFlow: StateFlow<State> = _state.asStateFlow()
    val state: State get() = _state.value

    var modelPath: String? = null
        private set

    var lastError: String? = null
        private set
    
    // Configuration - optimized for 3GB RAM phones
    private val contextSize = 2048   // Supports conversation history
    private val maxTokensPerResponse = 512  // Allow complete responses without mid-sentence cut-off

    private val defaultSystemPrompt = ActivePersona.defaultSystemPrompt

    private var systemPrompt: String = defaultSystemPrompt

    // Accept role prefixes from some models and strip them instead of aborting generation.
    private fun stripLeadingRolePrefix(token: String, firstToken: Boolean): String {
        if (!firstToken) return token
        val prefixes = listOf(
            "Assistant:",
            "assistant:",
            "Model:",
            "model:",
            "Maya:",
            "Asmaa:",
            "المساعد:",
            "أسماء:"
        )
        var cleaned = token
        for (prefix in prefixes) {
            if (cleaned.startsWith(prefix)) {
                cleaned = cleaned.removePrefix(prefix).trimStart()
            }
        }
        return cleaned
    }

    @Synchronized
    private fun ensureBackendInitialized() {
        if (!backendInitialized) {
            nativeInitBackend()
            backendInitialized = true
            Log.d(TAG, "${logPrefix()} Backend initialized lazily")
        }
    }

    /**
     * Override the system prompt (e.g. from Firebase Remote Config).
     * Pass blank/empty to revert to the built-in default.
     */
    override fun setSystemPrompt(prompt: String) {
        systemPrompt = prompt.ifBlank { defaultSystemPrompt }
        Log.d(TAG, "System prompt updated (${systemPrompt.length} chars)")
    }

    // Load model from file
    override suspend fun loadModel(modelFile: File): Boolean = withContext(Dispatchers.IO) {
        if (!modelFile.exists()) {
            lastError = "Model file not found: ${modelFile.absolutePath}"
            _state.value = State.ERROR
            return@withContext false
        }
        
        _state.value = State.LOADING
        Log.d(TAG, "${logPrefix()} Loading model: ${modelFile.absolutePath}")
        Log.d(TAG, "${logPrefix()} Model file size: ${modelFile.length() / 1_000_000} MB")
        Log.d(TAG, "${logPrefix()} Model file exists: ${modelFile.exists()}")
        Log.d(TAG, "${logPrefix()} Model file readable: ${modelFile.canRead()}")
        
        try {
            ensureBackendInitialized()

            // CPU-only inference (Vulkan removed — marginal benefit for small models,
            // causes driver crashes on budget phones)
            Log.d(TAG, "${logPrefix()} Calling nativeLoadModel with threads=4, context=$contextSize")
            val code = nativeLoadModel(modelFile.absolutePath, 4, contextSize, false, 0)
            Log.d(TAG, "${logPrefix()} nativeLoadModel returned code=$code")

            if (code == 0) {
                modelPath = modelFile.absolutePath
                _state.value = State.READY
                Log.d(TAG, "${logPrefix()} Model loaded successfully")
            } else {
                lastError = when (code) {
                    1 -> "Model file error"
                    2 -> "Load error"
                    3 -> "Context/OOM error"
                    4 -> "Backend not initialized"
                    else -> "Native model loading failed"
                }
                _state.value = State.ERROR
                Log.e(TAG, "${logPrefix()} Native model loading failed code=$code : $lastError")
            }
            code == 0
        } catch (e: Exception) {
            lastError = e.message
            _state.value = State.ERROR
            Log.e(TAG, "${logPrefix()} Error loading model", e)
            false
        }
    }
    
    /**
     * Generate from a raw, fully-formatted prompt.
     * Callers are responsible for chat-ML wrapping — this method does NOT
     * call [buildPrompt].
     */
    override fun generateRaw(prompt: String): Flow<String> = generateInternal(prompt)

    /**
     * Legacy convenience: wraps [userMessage] in the Qwen chat-ML template
     * via [buildPrompt] and then generates.  Prefer [generateRaw] from new code.
     */
    fun generate(userMessage: String): Flow<String> {
        val fullPrompt = buildPrompt(userMessage)
        return generateInternal(fullPrompt)
    }

    // Shared generation implementation
    private fun generateInternal(fullPrompt: String): Flow<String> = flow {
        if (state != State.READY) {
            emit("[Error: Model not ready]")
            return@flow
        }
        
        _state.value = State.GENERATING
        
        Log.d(TAG, "${logPrefix()} Generating response for: ${fullPrompt.take(50)}...")
        
        try {
            // Prepare context
            val prepared = nativePrepare(fullPrompt)
            if (!prepared) {
                Log.e(TAG, "Prompt preparation failed")
                emit("[Error: Prompt preparation failed]")
                return@flow
            }
            
            // Generate tokens - emit immediately for real-time streaming
            var tokenCount = 0
            val maxTokens = maxTokensPerResponse  // 128 tokens for concise answers
        
            // Batch tokens for smoother UI updates
            val batch = StringBuilder()
            val fullText = StringBuilder() // Track full output for stop detection
            
            while (tokenCount < maxTokens) {
                currentCoroutineContext().ensureActive()
                val token = nativeGenerateToken()
                
                // Empty string means end of generation (EOG token or error)
                if (token.isNullOrEmpty()) {
                    break
                }
                
                // Check for hard stop sequences in the token itself
                if (token.contains("<|") || token.contains("|>") || 
                    token == "</s>" || token == "<|end|>" || token == "<|eot_id|>") {
                    Log.d(TAG, "Stop token detected: '$token'")
                    break
                }

                val cleanedToken = stripLeadingRolePrefix(token, tokenCount == 0)
                if (cleanedToken.isEmpty()) {
                    continue
                }

                fullText.append(cleanedToken)

                // Check if accumulated text contains stop sequences
                val accumulated = fullText.toString()
                if (accumulated.contains("<|im_start|>") ||
                    accumulated.contains("<|im_end|>") ||
                    accumulated.contains("<start_of_turn>") ||
                    accumulated.contains("<end_of_turn>") ||
                    accumulated.contains("<|")) {
                    Log.d(TAG, "Stop sequence detected in accumulated text")
                    // Flush the clean portion of the batch before stopping
                    if (batch.isNotEmpty()) {
                        emit(batch.toString())
                        batch.clear()
                    }
                    break
                }

                batch.append(cleanedToken)
                tokenCount++
                
                // Emit in batches of 3 tokens for smoother UI
                if (tokenCount % 3 == 0 && batch.isNotEmpty()) {
                    emit(batch.toString())
                    batch.clear()
                }
                
                // ensureActive is called at top of loop; no extra yield needed
            }
            
            // Emit remaining tokens
            if (batch.isNotEmpty()) {
                emit(batch.toString())
            }
            
            // Log the full output for debugging
            Log.d(TAG, "${logPrefix()} Full output text: ${fullText.toString().take(300)}")
            Log.d(TAG, "${logPrefix()} Generated $tokenCount tokens")
            
        } catch (e: Exception) {
            Log.e(TAG, "Generation error", e)
            emit("[Error: ${e.message}]")
        } finally {
            _state.value = State.READY
        }
    }.flowOn(Dispatchers.IO)  // Run entire flow on IO dispatcher
    
    // Build the full prompt with system context
    private fun buildPrompt(userMessage: String): String {
        // Qwen format (adjust for your model)
        return """<|im_start|>system
$systemPrompt<|im_end|>
<|im_start|>user
$userMessage<|im_end|>
<|im_start|>assistant
"""
    }
    
    // Unload model and free resources
    fun unload() {
        if (state != State.UNINITIALIZED) {
            Log.d(TAG, "${logPrefix()} Unloading model")
            nativeUnload()
            _state.value = State.UNINITIALIZED
            modelPath = null
        }
    }

    fun shutdownBackend() {
        if (backendInitialized) {
            nativeFreeBackend()
            backendInitialized = false
        }
    }
    
    // Check if model is ready
    override fun isReady(): Boolean = state == State.READY
    
    // Clear conversation context (for "New Chat" button)
    override fun clearContext() {
        if (state != State.UNINITIALIZED) {
            Log.d(TAG, "Clearing conversation context")
            nativeClearContext()
        }
    }
    
    // ==================== Native Methods ====================
    
    private external fun nativeInitBackend()
    private external fun nativeFreeBackend()
    private external fun nativeLoadModel(path: String, threads: Int, contextSize: Int, useGpu: Boolean, gpuLayers: Int): Int
    private external fun nativePrepare(prompt: String): Boolean
    private external fun nativeGenerateToken(): String?
    private external fun nativeClearContext(): Unit
    private external fun nativeUnload(): Unit
}
