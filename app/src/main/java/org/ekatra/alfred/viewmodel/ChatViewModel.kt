package org.ekatra.alfred.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.ekatra.alfred.InferenceEngine
import org.ekatra.alfred.data.AppSettings
import org.ekatra.alfred.data.FeedbackUploader
import org.ekatra.alfred.data.RemoteConfigManager
import org.ekatra.alfred.data.model.ChatMessageEntity
import org.ekatra.alfred.data.model.ChatSession
import org.ekatra.alfred.data.model.SavedAnswer
import org.ekatra.alfred.data.repository.ChatRepository
import org.ekatra.alfred.data.repository.SavedContentRepository
import org.ekatra.alfred.notification.AppUpdateChecker
import org.ekatra.alfred.tools.MathInterceptor
import org.ekatra.alfred.persona.Persona
import org.ekatra.alfred.tools.PromptFormatter
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Named

data class ChatUiState(
    val isLoading: Boolean = true,
    val isGenerating: Boolean = false,
    val firstTokenReceived: Boolean = false,
    val modelReady: Boolean = false,
    val status: ChatStatus = ChatStatus.LOADING_MODEL,
    /** Hotspot server address while [status] is [ChatStatus.SERVING]. */
    val serverAddress: String? = null,
    val contextUsagePercent: Int = 0,
    val currentSessionId: Long? = null,
    val selectedSubject: String? = null,
    val inputText: String = "",
    val streamingText: String? = null, // null = not streaming
    val serverRunning: Boolean = false,
    val savedAnswers: List<SavedAnswer> = emptyList(),
    val streakDays: Int = 0,
    val dailyQuestion: String = "",
    val updateInfo: AppUpdateChecker.UpdateInfo? = null,
    val updateDismissed: Boolean = false
)

/** What the chat header shows; the UI maps each value to localized text. */
enum class ChatStatus { LOADING_MODEL, READY, LOAD_FAILED, THINKING, TYPING, NO_RESPONSE, SERVING }

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class ChatViewModel @Inject constructor(
    private val chatRepository: ChatRepository,
    private val savedContentRepository: SavedContentRepository,
    private val engine: InferenceEngine,
    private val settings: AppSettings,
    private val analyticsHelper: AnalyticsHelper,
    private val remoteConfig: RemoteConfigManager,
    private val appUpdateChecker: AppUpdateChecker,
    private val feedbackUploader: FeedbackUploader,
    @Named("inference") private val inferenceMutex: Mutex,
    private val persona: Persona
) : ViewModel() {

    companion object {
        private const val TAG = "ChatViewModel"
        private const val MODEL_CONTEXT_SIZE = 2048
    }

    private val _uiState = MutableStateFlow(ChatUiState())
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()
    private var generationJob: Job? = null

    init {
        hydrateDailyState()
        refreshSavedAnswers()
        checkForAppUpdate()
    }

    private fun hydrateDailyState() {
        val dayEpoch = System.currentTimeMillis() / 86_400_000L
        val streak = settings.getStreakDays()
        val daily = settings.getDailyQuestionForDay(dayEpoch, persona.dailyQuestions, persona.dailyQuestionFallback)
        _uiState.update { it.copy(streakDays = streak, dailyQuestion = daily) }
    }

    private fun checkForAppUpdate() {
        viewModelScope.launch {
            try {
                // R.10: cap update check at 5s so a slow/dead server can't block startup
                val updateInfo = withTimeoutOrNull(5_000L) { appUpdateChecker.checkForUpdate() }
                if (updateInfo?.isUpdateAvailable == true) {
                    _uiState.update { it.copy(updateInfo = updateInfo) }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Update check failed", e)
            }
        }
    }

    fun dismissUpdateBanner() {
        _uiState.update { it.copy(updateDismissed = true) }
    }

    val recentSessions: Flow<List<ChatSession>> = chatRepository.getRecentSessions()

    // Messages for the current session — switches when currentSessionId changes
    val currentMessages: Flow<List<ChatMessageEntity>> = _uiState
        .map { it.currentSessionId }
        .distinctUntilChanged()
        .flatMapLatest { sessionId ->
            if (sessionId != null) chatRepository.getMessagesForSession(sessionId)
            else flowOf(emptyList())
        }

    fun loadModel(modelFile: File) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, status = ChatStatus.LOADING_MODEL) }
            val success = engine.loadModel(modelFile)
            _uiState.update {
                it.copy(
                    isLoading = false,
                    modelReady = success,
                    status = if (success) ChatStatus.READY else ChatStatus.LOAD_FAILED
                )
            }
            if (success) {
                analyticsHelper.setModelProperty(settings.getSelectedModel().id)
            }
        }
    }

    fun createInitialSession() {
        if (_uiState.value.currentSessionId != null) return
        viewModelScope.launch {
            val id = chatRepository.createSession(_uiState.value.selectedSubject)
            _uiState.update { it.copy(currentSessionId = id) }
            refreshContextUsage(id)
        }
    }

    fun createNewSession() {
        viewModelScope.launch {
            engine.clearContext() // S.2: flush KV cache for the new session
            val id = chatRepository.createSession(_uiState.value.selectedSubject)
            _uiState.update { it.copy(currentSessionId = id, streamingText = null, inputText = "") }
            refreshContextUsage(id)
        }
    }

    fun switchSession(sessionId: Long, subject: String?) {
        engine.clearContext() // S.2: flush KV cache when switching sessions
        _uiState.update {
            it.copy(currentSessionId = sessionId, selectedSubject = subject, streamingText = null, inputText = "")
        }
        refreshContextUsage(sessionId)
    }

    fun updateInput(text: String) {
        _uiState.update { it.copy(inputText = text) }
    }

    fun selectSubject(subject: String) {
        val current = _uiState.value.selectedSubject
        _uiState.update { it.copy(selectedSubject = if (current == subject) null else subject) }
    }

    fun refreshSavedAnswers() {
        viewModelScope.launch {
            val answers = savedContentRepository.getAnswers()
            _uiState.update { it.copy(savedAnswers = answers) }
        }
    }

    fun toggleFavoriteSavedAnswer(answerId: Long) {
        viewModelScope.launch {
            savedContentRepository.toggleFavorite(answerId)
            refreshSavedAnswers()
        }
    }

    fun stopGeneration() {
        generationJob?.cancel()
        generationJob = null
        _uiState.update {
            it.copy(
                isGenerating = false,
                firstTokenReceived = false,
                streamingText = null,
                status = if (it.serverRunning && it.modelReady) it.status else ChatStatus.READY
            )
        }
    }

    private val mathInterceptor = MathInterceptor(persona)
    private val promptFormatter = PromptFormatter()

    fun sendMessage(userMsg: String): Boolean {
        var accepted = false
        _uiState.update { state ->
            if (userMsg.isBlank() || !state.modelReady || state.isGenerating) {
                state
            } else {
                accepted = true
                state.copy(
                    inputText = "",
                    isGenerating = true,
                    firstTokenReceived = false,
                    status = ChatStatus.THINKING
                )
            }
        }

        if (!accepted) return false
        val state = _uiState.value
        val firstTokenTimedOut = AtomicBoolean(false)

        generationJob = viewModelScope.launch {
            val dayEpoch = System.currentTimeMillis() / 86_400_000L
            val streak = settings.markActiveToday(dayEpoch)
            _uiState.update { it.copy(streakDays = streak) }

            val sessionId = state.currentSessionId ?: chatRepository.createSession(state.selectedSubject).also { id ->
                _uiState.update { it.copy(currentSessionId = id) }
            }

            // Build context using remote config for context window size
            val maxMessages = remoteConfig.maxContextMessages
            // S.1: fetch turns as a list so PromptFormatter can emit proper chat-ML blocks
            val historyTurns = chatRepository.getConversationTurns(sessionId, maxMessages = maxMessages)
            val systemPrompt = persona.resolveSystemPrompt(remoteConfig.systemPrompt)

            // Calculator intercept: if the message is a math expression,
            // compute the correct answer and ask the model to explain it
            val mathResult = mathInterceptor.intercept(userMsg)
            val subjectInstruction = subjectInstruction(state.selectedSubject)
            val effectiveUserMsg = buildString {
                if (persona.userTurnPreamble.isNotBlank()) {
                    appendLine(persona.userTurnPreamble)
                }
                if (subjectInstruction.isNotBlank()) {
                    appendLine(subjectInstruction)
                    appendLine()
                }
                append(mathResult?.augmentedPrompt ?: userMsg)
            }

            // Format prompt with correct model-specific chat-ML template (S.1: multi-turn)
            val modelId = settings.getSelectedModel().id
            val promptText = promptFormatter.format(modelId, systemPrompt, historyTurns, effectiveUserMsg)
            updateContextUsageFromPrompt(promptText)

            // Persist user message
            chatRepository.addMessage(sessionId, userMsg, true)

            // Log analytics: aggregate non-PII signals only
            val isFollowup = historyTurns.isNotEmpty()
            val turnIndex = historyTurns.size / 2 // pairs of user/assistant
            analyticsHelper.logMessageSent(
                subject = state.selectedSubject,
                charCount = userMsg.length,
                isFollowup = isFollowup,
                turnIndex = turnIndex
            )

            val curatedAnswer = persona.curatedAnswer(userMsg, state.selectedSubject)
            if (curatedAnswer != null) {
                val startTime = System.currentTimeMillis()
                _uiState.update { it.copy(firstTokenReceived = true, streamingText = curatedAnswer, status = ChatStatus.TYPING) }
                _uiState.update { it.copy(streamingText = null) }
                chatRepository.addMessage(sessionId, curatedAnswer, false)
                refreshContextUsage(sessionId)
                val elapsed = System.currentTimeMillis() - startTime
                analyticsHelper.logResponseReceived(
                    responseTimeMs = elapsed,
                    tokenCount = curatedAnswer.length,
                    modelId = settings.getSelectedModel().id,
                    wordCount = curatedAnswer.trim().split(Regex("\\s+")).size,
                    hasCodeBlock = false,
                    wasCancelled = false
                )
                _uiState.update { it.copy(isGenerating = false, firstTokenReceived = false, status = ChatStatus.READY) }
                return@launch
            }

            // Stream AI response
            val startTime = System.currentTimeMillis()
            val response = StringBuilder()
            _uiState.update { it.copy(streamingText = "") }

            try {
                inferenceMutex.withLock {
                    // R.2: first-token watchdog — cancel generation if model hangs for too long.
                    // Gemma 3 1B can take noticeably longer on-device for first-token prefill,
                    // so keep this guard generous to avoid false cancellations.
                    val genJob = currentCoroutineContext()[Job]
                    val watchdog = viewModelScope.launch {
                        delay(persona.firstTokenTimeoutMs)
                        if (response.isEmpty()) {
                            Log.w(TAG, "First-token timeout — cancelling generation")
                            firstTokenTimedOut.set(true)
                            genJob?.cancel(CancellationException("First token timeout"))
                        }
                    }
                    try {
                        engine.generateRaw(promptText).collect { token ->
                            if (response.isEmpty()) watchdog.cancel() // disarm on first token
                            response.append(token)
                            _uiState.update {
                                it.copy(
                                    firstTokenReceived = true,
                                    streamingText = response.toString(),
                                    status = ChatStatus.TYPING
                                )
                            }
                        }
                    } finally {
                        watchdog.cancel()
                    }
                }
            } catch (e: CancellationException) {
                Log.d(TAG, "Generation cancelled: ${e.message}")
                if (firstTokenTimedOut.get() && response.isEmpty()) {
                    val fallback = persona.emptyResponseFallback(userMsg)
                    chatRepository.addMessage(sessionId, fallback, false)
                    refreshContextUsage(sessionId)
                    _uiState.update {
                        it.copy(
                            isGenerating = false,
                            firstTokenReceived = false,
                            streamingText = null,
                            status = ChatStatus.READY
                        )
                    }
                    return@launch
                }
                throw e
            }

            _uiState.update { it.copy(streamingText = null) }

            // Persist AI answer (never leave user without a visible reply)
            val finalResponse = if (response.isNotBlank()) response.toString() else persona.emptyResponseFallback(userMsg)
            if (response.isBlank()) {
                Log.w(TAG, "Blank generation output; using fallback response")
            }
            chatRepository.addMessage(sessionId, finalResponse, false)
            refreshContextUsage(sessionId)

            val elapsed = System.currentTimeMillis() - startTime
            val responseText = finalResponse
            analyticsHelper.logResponseReceived(
                responseTimeMs = elapsed,
                tokenCount = response.length,
                modelId = settings.getSelectedModel().id,
                wordCount = if (responseText.isBlank()) 0 else responseText.trim().split(Regex("\\s+")).size,
                hasCodeBlock = responseText.contains("```") || responseText.contains("`"),
                wasCancelled = false
            )

            Log.d(TAG, "AI response complete session=$sessionId chars=${response.length} time=${elapsed}ms")
            _uiState.update { it.copy(isGenerating = false, firstTokenReceived = false, status = ChatStatus.READY) }
        }.also { job ->
            job.invokeOnCompletion { throwable ->
                generationJob = null
                if (throwable is CancellationException) {
                    _uiState.update {
                        it.copy(
                            isGenerating = false,
                            firstTokenReceived = false,
                            streamingText = null,
                            status = if (firstTokenTimedOut.get()) ChatStatus.NO_RESPONSE else ChatStatus.READY
                        )
                    }
                }
            }
        }

        return true
    }

    fun rateMessage(messageId: Long, rating: Int) {
        viewModelScope.launch {
            chatRepository.rateMessage(messageId, rating)
            val ratingStr = if (rating > 0) "up" else "down"
            val state = _uiState.value
            val sessionId = state.currentSessionId?.toString() ?: "unknown"
            analyticsHelper.logResponseRated(
                rating = ratingStr,
                sessionId = sessionId,
                subject = state.selectedSubject,
                modelId = settings.getSelectedModel().id
            )
        }
    }

    /** True if user granted opt-in feedback uploads in PrivacyConsentActivity. */
    fun hasFeedbackConsent(): Boolean = settings.hasFeedbackConsent()

    /**
     * Opt-in feedback upload — caller (UI) is responsible for showing a confirmation
     * dialog. Only sends if [AppSettings.hasFeedbackConsent] is true.
     * Uploads {question, response, model_id, subject, ratings} to Firestore.
     */
    fun submitFeedback(messageId: Long) {
        viewModelScope.launch {
            try {
                if (!settings.hasFeedbackConsent()) return@launch
                val state = _uiState.value
                val sessionId = state.currentSessionId ?: return@launch
                val recent = chatRepository.getMessagesForSessionSync(sessionId)
                val aiMsg = recent.firstOrNull { it.id == messageId && !it.isUser } ?: return@launch
                val priorUserMsg = recent
                    .filter { it.isUser && it.timestamp <= aiMsg.timestamp }
                    .maxByOrNull { it.timestamp }
                val ok = feedbackUploader.upload(
                    question = priorUserMsg?.text.orEmpty(),
                    response = aiMsg.text,
                    modelId = settings.getSelectedModel().id,
                    subject = state.selectedSubject,
                    sessionId = sessionId,
                    messageId = messageId
                )
                analyticsHelper.logFeedbackUpload(ok, state.selectedSubject)
            } catch (e: Exception) {
                Log.w(TAG, "submitFeedback failed", e)
            }
        }
    }

    fun saveAnswer(question: String, answer: String, subject: String?) {
        viewModelScope.launch {
            savedContentRepository.saveAnswer(question, answer, subject)
            analyticsHelper.logAnswerSaved(subject)
            refreshSavedAnswers()
        }
    }

    fun shareAnswer(subject: String?) {
        analyticsHelper.logAnswerShared(subject, null)
    }

    fun toggleServer(enable: Boolean) {
        _uiState.update { it.copy(serverRunning = enable) }
    }

    fun updateStatusForServer(ip: String) {
        val state = _uiState.value
        if (state.serverRunning && state.modelReady) {
            _uiState.update { it.copy(status = ChatStatus.SERVING, serverAddress = "http://$ip:8080") }
        }
    }

    private fun refreshContextUsage(sessionId: Long) {
        viewModelScope.launch {
            val history = chatRepository.getConversationContext(sessionId, remoteConfig.maxContextMessages)
            val estimatedTokens = (history.length / 4f).roundToInt()
            val percent = ((estimatedTokens.toFloat() / MODEL_CONTEXT_SIZE) * 100f).roundToInt().coerceIn(0, 100)
            _uiState.update {
                it.copy(contextUsagePercent = percent)
            }
        }
    }

    private fun updateContextUsageFromPrompt(promptText: String) {
        val estimatedTokens = (promptText.length / 4f).roundToInt()
        val percent = ((estimatedTokens.toFloat() / MODEL_CONTEXT_SIZE) * 100f).roundToInt().coerceIn(0, 100)
        _uiState.update {
            it.copy(contextUsagePercent = percent)
        }
    }

    // S.13: personas emit /think for reasoning-heavy subjects and /no_think for language/casual ones.
    private fun subjectInstruction(subject: String?): String = persona.subjectInstruction(subject)
}
