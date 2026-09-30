package org.ekatra.alfred.viewmodel

import io.mockk.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.test.*
import org.ekatra.alfred.InferenceEngine
import org.ekatra.alfred.data.AppSettings
import org.ekatra.alfred.data.FeedbackUploader
import org.ekatra.alfred.data.RemoteConfigManager
import org.ekatra.alfred.data.repository.ChatRepository
import org.ekatra.alfred.data.repository.SavedContentRepository
import org.ekatra.alfred.notification.AppUpdateChecker
import org.ekatra.alfred.persona.ActivePersona
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
class ChatViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()

    private lateinit var chatRepository: ChatRepository
    private lateinit var savedContentRepository: SavedContentRepository
    private lateinit var engine: InferenceEngine
    private lateinit var settings: AppSettings
    private lateinit var analyticsHelper: AnalyticsHelper
    private lateinit var remoteConfig: RemoteConfigManager
    private lateinit var appUpdateChecker: AppUpdateChecker
    private lateinit var feedbackUploader: FeedbackUploader
    private val inferenceMutex = Mutex()
    private lateinit var viewModel: ChatViewModel

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)

        chatRepository = mockk(relaxed = true)
        savedContentRepository = mockk(relaxed = true)
        engine = mockk(relaxed = true)
        settings = mockk(relaxed = true)
        analyticsHelper = mockk(relaxed = true)
        remoteConfig = mockk(relaxed = true)
        appUpdateChecker = mockk(relaxed = true)
        feedbackUploader = mockk(relaxed = true)

        every { chatRepository.getRecentSessions() } returns flowOf(emptyList())
        every { remoteConfig.maxContextMessages } returns 10
        every { remoteConfig.systemPrompt } returns ""

        viewModel = ChatViewModel(
            chatRepository = chatRepository,
            savedContentRepository = savedContentRepository,
            engine = engine,
            settings = settings,
            analyticsHelper = analyticsHelper,
            remoteConfig = remoteConfig,
            appUpdateChecker = appUpdateChecker,
            feedbackUploader = feedbackUploader,
            inferenceMutex = inferenceMutex,
            persona = ActivePersona
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `initial state is loading`() {
        val state = viewModel.uiState.value
        assertTrue(state.isLoading)
        assertFalse(state.modelReady)
        assertFalse(state.isGenerating)
        assertNull(state.currentSessionId)
    }

    @Test
    fun `loadModel success updates state`() = runTest {
        val file = mockk<File> {
            every { exists() } returns true
        }
        coEvery { engine.loadModel(any()) } returns true

        viewModel.loadModel(file)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.isLoading)
        assertTrue(state.modelReady)
        assertEquals(ChatStatus.READY, state.status)
    }

    @Test
    fun `loadModel failure updates state`() = runTest {
        val file = mockk<File> {
            every { exists() } returns true
        }
        coEvery { engine.loadModel(any()) } returns false

        viewModel.loadModel(file)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.isLoading)
        assertFalse(state.modelReady)
        assertEquals(ChatStatus.LOAD_FAILED, state.status)
    }

    @Test
    fun `createInitialSession creates session when none exists`() = runTest {
        coEvery { chatRepository.createSession(any()) } returns 42L

        viewModel.createInitialSession()
        advanceUntilIdle()

        assertEquals(42L, viewModel.uiState.value.currentSessionId)
    }

    @Test
    fun `createInitialSession does nothing if session exists`() = runTest {
        // First create a session
        coEvery { chatRepository.createSession(any()) } returns 1L
        viewModel.createInitialSession()
        advanceUntilIdle()

        // Try again — should not create another
        viewModel.createInitialSession()
        advanceUntilIdle()

        coVerify(exactly = 1) { chatRepository.createSession(any()) }
    }

    @Test
    fun `updateInput updates inputText`() {
        viewModel.updateInput("Hello Maya")
        assertEquals("Hello Maya", viewModel.uiState.value.inputText)
    }

    @Test
    fun `selectSubject toggles subject`() {
        viewModel.selectSubject("Math")
        assertEquals("Math", viewModel.uiState.value.selectedSubject)

        viewModel.selectSubject("Math")
        assertNull(viewModel.uiState.value.selectedSubject)

        viewModel.selectSubject("Science")
        assertEquals("Science", viewModel.uiState.value.selectedSubject)
    }

    @Test
    fun `sendMessage does nothing when model not ready`() = runTest {
        val accepted = viewModel.sendMessage("Hello")
        advanceUntilIdle()

        assertFalse(accepted)
        coVerify(exactly = 0) { chatRepository.addMessage(any(), any(), any(), any()) }
    }

    @Test
    fun `sendMessage does nothing with blank text`() = runTest {
        // Make model ready
        coEvery { engine.loadModel(any()) } returns true
        viewModel.loadModel(mockk { every { exists() } returns true })
        advanceUntilIdle()

        val accepted = viewModel.sendMessage("   ")
        advanceUntilIdle()

        assertFalse(accepted)
        coVerify(exactly = 0) { chatRepository.addMessage(any(), any(), any(), any()) }
    }

    @Test
    fun `sendMessage generates response and persists`() = runTest {
        // Setup
        coEvery { engine.loadModel(any()) } returns true
        viewModel.loadModel(mockk { every { exists() } returns true })
        advanceUntilIdle()

        coEvery { chatRepository.createSession(any()) } returns 1L
        viewModel.createInitialSession()
        advanceUntilIdle()

        coEvery { chatRepository.getConversationContext(any(), any()) } returns ""
        coEvery { engine.generateRaw(any()) } returns flowOf("Hello", " student", "!")

        val accepted = viewModel.sendMessage("Hi")
        advanceUntilIdle()

        assertTrue(accepted)

        // Verify user message was persisted
        coVerify { chatRepository.addMessage(1L, "Hi", true, any()) }
        // Verify AI response was persisted
        coVerify { chatRepository.addMessage(1L, "Hello student!", false, any()) }
        // Verify analytics
        verify { analyticsHelper.logMessageSent(any(), any(), any(), any()) }
        verify { analyticsHelper.logResponseReceived(any(), any(), any(), any(), any(), any()) }
    }

    @Test
    fun `rateMessage delegates to repository and logs analytics`() = runTest {
        coEvery { chatRepository.createSession(any()) } returns 5L
        viewModel.createInitialSession()
        advanceUntilIdle()

        viewModel.rateMessage(123L, 1)
        advanceUntilIdle()

        coVerify { chatRepository.rateMessage(123L, 1) }
        verify { analyticsHelper.logResponseRated("up", "5", any(), any()) }
    }

    @Test
    fun `saveAnswer delegates to repository and logs analytics`() = runTest {
        viewModel.saveAnswer("What is 2+2?", "4", "Math")
        advanceUntilIdle()

        coVerify { savedContentRepository.saveAnswer("What is 2+2?", "4", "Math") }
        verify { analyticsHelper.logAnswerSaved("Math") }
    }

    @Test
    fun `switchSession updates state correctly`() {
        viewModel.switchSession(10L, "History")

        val state = viewModel.uiState.value
        assertEquals(10L, state.currentSessionId)
        assertEquals("History", state.selectedSubject)
        assertNull(state.streamingText)
        assertEquals("", state.inputText)
    }

    @Test
    fun `toggleServer updates state`() {
        viewModel.toggleServer(true)
        assertTrue(viewModel.uiState.value.serverRunning)

        viewModel.toggleServer(false)
        assertFalse(viewModel.uiState.value.serverRunning)
    }

    @Test
    fun `sendMessage uses remote config for context window`() = runTest {
        every { remoteConfig.maxContextMessages } returns 5
        every { remoteConfig.systemPrompt } returns "You are Maya, an AI teacher."

        coEvery { engine.loadModel(any()) } returns true
        viewModel.loadModel(mockk { every { exists() } returns true })
        advanceUntilIdle()

        coEvery { chatRepository.createSession(any()) } returns 1L
        viewModel.createInitialSession()
        advanceUntilIdle()

        coEvery { chatRepository.getConversationContext(any(), any()) } returns ""
        coEvery { engine.generateRaw(any()) } returns flowOf("OK")

        val accepted = viewModel.sendMessage("test")
        advanceUntilIdle()

        assertTrue(accepted)
        coVerify { chatRepository.getConversationContext(1L, maxMessages = 5) }
    }

    @Test
    fun `sendMessage back-to-back accepts only first while generating`() = runTest {
        coEvery { engine.loadModel(any()) } returns true
        viewModel.loadModel(mockk { every { exists() } returns true })
        advanceUntilIdle()

        coEvery { chatRepository.createSession(any()) } returns 1L
        viewModel.createInitialSession()
        advanceUntilIdle()

        coEvery { chatRepository.getConversationTurns(any(), any()) } returns emptyList()
        coEvery { chatRepository.getConversationContext(any(), any()) } returns ""
        coEvery { engine.generateRaw(any()) } returns flow {
            emit("Hello")
            kotlinx.coroutines.delay(1_000)
            emit(" there")
        }

        val firstAccepted = viewModel.sendMessage("first")
        val secondAccepted = viewModel.sendMessage("second")

        assertTrue(firstAccepted)
        assertFalse(secondAccepted)

        advanceUntilIdle()

        coVerify(exactly = 1) { chatRepository.addMessage(1L, "first", true, any()) }
        coVerify(exactly = 0) { chatRepository.addMessage(1L, "second", true, any()) }
    }
}
