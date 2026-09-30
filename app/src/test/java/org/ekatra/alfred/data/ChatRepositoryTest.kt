package org.ekatra.alfred.data

import org.ekatra.alfred.persona.ActivePersona
import io.mockk.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.ekatra.alfred.data.local.ChatMessageDao
import org.ekatra.alfred.data.local.ChatSessionDao
import org.ekatra.alfred.data.model.ChatMessageEntity
import org.ekatra.alfred.data.model.ChatSession
import org.ekatra.alfred.data.repository.ChatRepository
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ChatRepositoryTest {

    private lateinit var sessionDao: ChatSessionDao
    private lateinit var messageDao: ChatMessageDao
    private lateinit var repo: ChatRepository

    @Before
    fun setup() {
        sessionDao = mockk(relaxed = true)
        messageDao = mockk(relaxed = true)
        repo = ChatRepository(sessionDao, messageDao)
    }

    @Test
    fun `createSession inserts and returns id`() = runTest {
        coEvery { sessionDao.insert(any()) } returns 42L

        val id = repo.createSession("Math")

        assertEquals(42L, id)
        coVerify { sessionDao.insert(match { it.subject == "Math" }) }
    }

    @Test
    fun `addMessage inserts message entity`() = runTest {
        repo.addMessage(1L, "Hello", true)

        coVerify {
            messageDao.insert(match {
                it.sessionId == 1L && it.text == "Hello" && it.isUser
            })
        }
    }

    @Test
    fun `addMessage also updates session timestamp`() = runTest {
        repo.addMessage(1L, "Hi", false)

        coVerify { sessionDao.updateSessionTimestamp(1L, any()) }
    }

    @Test
    fun `rateMessage delegates to dao`() = runTest {
        repo.rateMessage(123L, 1)

        coVerify { messageDao.updateRating(123L, 1) }
    }

    @Test
    fun `getRecentSessions returns flow from dao`() = runTest {
        val sessions = listOf(
            ChatSession(id = 1, title = "Chat 1"),
            ChatSession(id = 2, title = "Chat 2")
        )
        every { sessionDao.getRecentSessions() } returns flowOf(sessions)

        val result = repo.getRecentSessions().first()

        assertEquals(2, result.size)
        assertEquals("Chat 1", result[0].title)
    }

    @Test
    fun `getConversationContext builds formatted string`() = runTest {
        val messages = listOf(
            ChatMessageEntity(id = 1, sessionId = 1, text = "What is 2+2?", isUser = true),
            ChatMessageEntity(id = 2, sessionId = 1, text = "2+2 equals 4.", isUser = false)
        )
        coEvery { messageDao.getMessagesForSessionSync(1L) } returns messages

        val context = repo.getConversationContext(1L, 10)

        assertTrue(context.contains("${ActivePersona.studentLabel}: What is 2+2?"))
        assertTrue(context.contains("${ActivePersona.assistantName}: 2+2 equals 4."))
    }

    @Test
    fun `exportAllAsJson returns valid JSON`() = runTest {
        val sessions = listOf(ChatSession(id = 1, title = "Test"))
        val messages = listOf(
            ChatMessageEntity(id = 1, sessionId = 1, text = "Hello", isUser = true)
        )
        coEvery { sessionDao.getAllSessionsSync() } returns sessions
        coEvery { messageDao.getMessagesForSessionSync(1L) } returns messages

        val json = repo.exportAllAsJson()

        assertNotNull(json)
        assertTrue(json.contains("session"))
        assertTrue(json.contains("Hello"))
    }
}
