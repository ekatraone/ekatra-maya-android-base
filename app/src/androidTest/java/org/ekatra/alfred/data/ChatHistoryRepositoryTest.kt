package org.ekatra.alfred.data

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.ekatra.alfred.data.local.EkatraDatabase
import org.ekatra.alfred.data.repository.ChatRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ChatHistoryRepositoryTest {

    @Test
    fun conversationContext_persistsAndFormatsInOrder() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val db = Room.inMemoryDatabaseBuilder(context, EkatraDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        val repo = ChatRepository(db.chatSessionDao(), db.chatMessageDao())

        val sessionId = repo.createSession(subject = "Science", userId = "test-user")
        repo.addMessage(sessionId, "Explain photosynthesis simply", isUser = true, userId = "test-user")
        repo.addMessage(sessionId, "Photosynthesis is how plants make food using sunlight.", isUser = false, userId = "test-user")
        repo.addMessage(sessionId, "Give a daily life example", isUser = true, userId = "test-user")

        val contextText = repo.getConversationContext(sessionId, maxMessages = 6)
        assertTrue(contextText.contains("Student: Explain photosynthesis simply"))
        assertTrue(contextText.contains("Maya: Photosynthesis is how plants make food using sunlight."))
        assertTrue(contextText.contains("Student: Give a daily life example"))

        val messages = repo.getMessagesForSessionSync(sessionId)
        assertEquals(3, messages.size)
        assertEquals("Explain photosynthesis simply", messages[0].text)
        assertEquals("Give a daily life example", messages[2].text)
        db.close()
    }

    @Test
    fun conversationContext_respectsMaxMessageWindow() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val db = Room.inMemoryDatabaseBuilder(context, EkatraDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        val repo = ChatRepository(db.chatSessionDao(), db.chatMessageDao())

        val sessionId = repo.createSession(subject = "Math", userId = "window-user")
        for (i in 1..10) {
            repo.addMessage(sessionId, "Q$i", isUser = true, userId = "window-user")
        }

        val limited = repo.getConversationContext(sessionId, maxMessages = 4)
        val lines = limited.lines().map { it.trim() }
        assertTrue(lines.contains("Student: Q7"))
        assertTrue(lines.contains("Student: Q10"))
        assertTrue(!lines.contains("Student: Q1"))
        assertTrue(!lines.contains("Student: Q3"))
        db.close()
    }
}
