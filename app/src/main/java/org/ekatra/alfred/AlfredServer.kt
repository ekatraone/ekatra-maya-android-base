package org.ekatra.alfred

import org.ekatra.alfred.persona.ActivePersona

import android.util.Log
import com.google.gson.Gson
import fi.iki.elonen.NanoHTTPD
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.ekatra.alfred.data.repository.ChatRepository
import java.net.InetAddress
import java.net.NetworkInterface

/**
 * AlfredServer - HTTP server for multi-device mode
 * 
 * When running, other devices on the same WiFi can connect
 * and use this device's LLM for inference.
 */
class AlfredServer(
    private val engine: InferenceEngine,
    private val chatRepository: ChatRepository,
    private val context: android.content.Context,
    private val inferenceMutex: kotlinx.coroutines.sync.Mutex = kotlinx.coroutines.sync.Mutex(),
    port: Int = 8080
) : NanoHTTPD(port) {
    
    companion object {
        private const val TAG = "AlfredServer"
        private const val MAX_SESSION_HISTORY = 6   // per-session message pairs to keep
    }
    
    private val gson = Gson()
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val sessionMutex = Mutex()

    // ========== Session Memory (Room-backed) ==========
    // Maps client session_id (short string) -> Room sessionId (Long)
    private val sessionMap = linkedMapOf<String, Long>()

    /** Get or create a Room-backed session, return client session ID and Room session ID. */
    private suspend fun getOrCreateSession(requestedId: String?): Pair<String, Long> = sessionMutex.withLock {
        val clientId = if (!requestedId.isNullOrBlank() && sessionMap.containsKey(requestedId)) {
            requestedId
        } else {
            java.util.UUID.randomUUID().toString().take(8)
        }

        val roomId = sessionMap[clientId] ?: chatRepository.createSession("server").also {
            sessionMap[clientId] = it
        }

        // Evict oldest if over 20 sessions (memory only — Room data stays for history)
        while (sessionMap.size > 20) {
            val oldest = sessionMap.keys.first()
            sessionMap.remove(oldest)
        }

        clientId to roomId
    }

    /** Fetch last N turns from Room as formatted history text. */
    private suspend fun getSessionHistory(roomSessionId: Long): String =
        chatRepository.getConversationContext(roomSessionId, maxMessages = MAX_SESSION_HISTORY)

    /** Persist a turn to Room. */
    private suspend fun persistTurn(roomSessionId: Long, studentMsg: String, mayaReply: String) {
        chatRepository.addMessage(roomSessionId, studentMsg, isUser = true)
        chatRepository.addMessage(roomSessionId, mayaReply, isUser = false)
    }
    
    // Cache the HTML content
    private val htmlContent: String by lazy {
        try {
            context.assets.open("index.html").bufferedReader().use { it.readText() }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load index.html", e)
            """<!DOCTYPE html>
<html><head><title>${context.getString(R.string.app_name)}</title></head>
<body style="font-family: sans-serif; max-width: 600px; margin: 50px auto; padding: 20px;">
    <h1>👩‍🏫 ${context.getString(R.string.app_name)}</h1>
    <p>Server is running. API endpoints:</p>
    <ul>
        <li><strong>GET /status</strong> - Server status</li>
        <li><strong>POST /api/chat</strong> - Send messages (JSON: {"message": "your text"})</li>
    </ul>
    <p>Web UI not available. Please install UI files in assets/</p>
</body></html>""".trimIndent()
        }
    }
    
    // Request/Response data classes
    data class ChatRequest(val message: String)
    data class ChatResponse(val response: String, val success: Boolean, val error: String? = null)
    data class StatusResponse(val status: String, val modelLoaded: Boolean, val serverAddress: String)
    
    override fun serve(session: IHTTPSession): Response {
        val uri = session.uri
        val method = session.method
        
        Log.d(TAG, "Request: $method $uri")
        
        // CORS headers for browser clients
        val corsHeaders = mapOf(
            "Access-Control-Allow-Origin" to "*",
            "Access-Control-Allow-Methods" to "GET, POST, OPTIONS",
            "Access-Control-Allow-Headers" to "Content-Type"
        )
        
        // Handle preflight
        if (method == Method.OPTIONS) {
            return newFixedLengthResponse(Response.Status.OK, "text/plain", "").apply {
                corsHeaders.forEach { (k, v) -> addHeader(k, v) }
            }
        }
        
        val response = when {
            // Serve web UI at root
            uri == "/" -> newFixedLengthResponse(
                Response.Status.OK,
                "text/html",
                htmlContent
            )
            
            // Health check / status
            uri == "/status" -> handleStatus()
            
            // Chat endpoint (Ollama-compatible)
            uri == "/api/chat" && method == Method.POST -> handleChat(session)
            
            // Simple generate endpoint
            uri == "/api/generate" && method == Method.POST -> handleGenerate(session)
            
            // 404
            else -> newFixedLengthResponse(
                Response.Status.NOT_FOUND,
                "application/json",
                """{"error": "Not found"}"""
            )
        }
        
        // Add CORS headers to response
        corsHeaders.forEach { (k, v) -> response.addHeader(k, v) }
        return response
    }
    
    private fun handleStatus(): Response {
        val ready = engine.isReady()
        Log.d(TAG, "Status check - modelReady=$ready")
        val status = StatusResponse(
            status = if (ready) "ready" else "loading",
            modelLoaded = ready,
            serverAddress = getLocalIpAddress() ?: "unknown"
        )
        return newFixedLengthResponse(
            Response.Status.OK,
            "application/json",
            gson.toJson(status)
        )
    }
    
    private fun handleChat(session: IHTTPSession): Response {
        return try {
            // Read body
            val bodyMap = mutableMapOf<String, String>()
            session.parseBody(bodyMap)
            val body = bodyMap["postData"] ?: ""
            
            // Parse JSON - handle both simple and Ollama format
            val message = extractMessage(body)
            val studentName = extractStudentName(body)
            val requestedSessionId = extractSessionId(body)
            
            if (message.isBlank()) {
                Log.w(TAG, "Empty message received")
                return errorResponse("Empty message")
            }
            
            val nameTag = if (studentName.isNotBlank()) " [student=$studentName]" else ""
            Log.d(TAG, "Chat request$nameTag: '${message.take(50)}...' modelReady=${engine.isReady()}")
            
            if (!engine.isReady()) {
                Log.e(TAG, "Model not ready!")
                return errorResponse("Model not loaded")
            }

            val startTime = System.currentTimeMillis()
            val (clientSessionId, roomSessionId, responseText) = runBlocking {
                withContext(Dispatchers.IO) {
                    // Session management: get/create Room-backed session
                    val (clientId, roomId) = getOrCreateSession(requestedSessionId)

                    // Fetch persisted conversation history from Room
                    val history = getSessionHistory(roomId)

                    // Build prompt with conversation history for multi-turn memory
                    val promptWithHistory = buildString {
                        appendLine(ActivePersona.defaultSystemPrompt)
                        appendLine()
                        if (history.isNotBlank()) {
                            appendLine(ActivePersona.conversationHistoryHeader)
                            appendLine(history)
                            appendLine()
                        }
                        appendLine("${ActivePersona.studentLabel}: $message")
                        append("${ActivePersona.assistantName}: ")
                    }

                    // Clear KV cache before each generation (history is in the prompt text)
                    val generated = inferenceMutex.withLock {
                        engine.clearContext()

                        Log.d(TAG, "Starting generation for session=$clientId (room=$roomId)...")
                        val builder = StringBuilder()
                        engine.generateRaw(promptWithHistory).collect { token ->
                            builder.append(token)
                        }
                        builder.toString()
                    }
                    persistTurn(roomId, message, generated)
                    Triple(clientId, roomId, generated)
                }
            }
            val elapsed = System.currentTimeMillis() - startTime
            Log.d(TAG, "Generation complete: ${responseText.length} chars in ${elapsed}ms session=$clientSessionId")
            
            // Return response with session_id so the client can maintain the conversation
            val chatResponse = mapOf(
                "response" to responseText,
                "success" to true,
                "session_id" to clientSessionId,
                "error" to null
            )
            
            newFixedLengthResponse(
                Response.Status.OK,
                "application/json",
                gson.toJson(chatResponse)
            )
            
        } catch (e: Exception) {
            Log.e(TAG, "Chat error", e)
            errorResponse(e.message ?: "Unknown error")
        }
    }
    
    private fun handleGenerate(session: IHTTPSession): Response {
        // Alias to handleChat for Ollama compatibility
        return handleChat(session)
    }
    
    private fun extractMessage(body: String): String {
        return try {
            val json = gson.fromJson(body, Map::class.java)
            when {
                json.containsKey("message") -> json["message"] as String
                json.containsKey("messages") -> {
                    val messages = json["messages"] as List<*>
                    val lastMessage = messages.lastOrNull() as? Map<*, *>
                    lastMessage?.get("content") as? String ?: ""
                }
                json.containsKey("prompt") -> json["prompt"] as String
                else -> ""
            }
        } catch (e: Exception) {
            Log.e(TAG, "Parse error: $body", e)
            ""
        }
    }

    private fun extractStudentName(body: String): String {
        return try {
            val json = gson.fromJson(body, Map::class.java)
            (json["studentName"] as? String)?.trim() ?: ""
        } catch (e: Exception) { "" }
    }

    private fun extractSessionId(body: String): String? {
        return try {
            val json = gson.fromJson(body, Map::class.java)
            (json["session_id"] as? String)?.trim()?.ifBlank { null }
        } catch (e: Exception) { null }
    }
    
    private fun errorResponse(message: String): Response {
        val response = ChatResponse(
            response = "",
            success = false,
            error = message
        )
        return newFixedLengthResponse(
            Response.Status.INTERNAL_ERROR,
            "application/json",
            gson.toJson(response)
        )
    }
    
    fun getServerUrl(): String {
        val ip = getLocalIpAddress() ?: "localhost"
        return "http://$ip:$listeningPort"
    }
    
    private fun getLocalIpAddress(): String? {
        try {
            val interfaces = NetworkInterface.getNetworkInterfaces()
            while (interfaces.hasMoreElements()) {
                val intf = interfaces.nextElement()
                val addresses = intf.inetAddresses
                while (addresses.hasMoreElements()) {
                    val addr = addresses.nextElement()
                    if (!addr.isLoopbackAddress && addr is InetAddress) {
                        val ip = addr.hostAddress
                        // Return IPv4 address
                        if (ip?.contains(".") == true && !ip.startsWith("127.")) {
                            return ip
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting IP", e)
        }
        return null
    }
    
    override fun stop() {
        super.stop()
        scope.cancel()
        Log.d(TAG, "Server stopped")
    }
}
