package org.ekatra.alfred

import kotlinx.coroutines.flow.Flow
import java.io.File

/**
 * InferenceEngine — abstract boundary for LLM inference.
 *
 * All consumers (ChatViewModel, AlfredServer, tests) depend on this
 * interface, never on [LlamaEngine] directly.  This allows:
 *   • mocking in unit tests (no JNI required)
 *   • swapping to ExecuTorch / ONNX / remote backend without touching callers
 *   • clear documentation of the contract
 */
interface InferenceEngine {

    /** Whether the engine has a model loaded and is ready to generate. */
    fun isReady(): Boolean

    /** Load a GGUF (or other format) model file. Returns true on success. */
    suspend fun loadModel(modelFile: File): Boolean

    /**
     * Generate a streaming response for [prompt].
     *
     * The prompt should be **fully formatted** (system + history + user turn)
     * — the engine does NOT re-wrap it in chat-ML tags.
     *
     * Each emitted [String] is a small batch of new tokens.
     */
    fun generateRaw(prompt: String): Flow<String>

    /** Wipe the KV-cache so the next generation starts fresh. */
    fun clearContext()

    /** Override the system prompt used by the engine's internal formatter (if any). */
    fun setSystemPrompt(prompt: String)
}
