package org.ekatra.alfred.tools

import org.ekatra.alfred.data.model.ChatMessageEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PromptFormatterTest {

    private val formatter = PromptFormatter()
    private val history = listOf(
        ChatMessageEntity(id = 1, sessionId = 1, text = "hi", isUser = true),
        ChatMessageEntity(id = 2, sessionId = 1, text = "hello", isUser = false)
    )

    @Test
    fun `gemma 4 uses turn markers with a native system turn`() {
        val p = formatter.format("gemma-4-e2b-it", "SYS", history, "Q")
        assertEquals(
            "<|turn>system\nSYS<turn|>\n<|turn>user\nhi<turn|>\n<|turn>model\nhello<turn|>\n<|turn>user\nQ<turn|>\n<|turn>model\n",
            p
        )
        assertFalse(p.contains("<start_of_turn>"))
    }

    @Test
    fun `gemma 3 keeps start_of_turn markers with system folded into first user turn`() {
        val p = formatter.format("gemma-3-1b-it", "SYS", emptyList(), "Q")
        assertEquals("<start_of_turn>user\nSYS\n\nQ<end_of_turn>\n<start_of_turn>model\n", p)
    }

    @Test
    fun `lfm falls through to chatml`() {
        val p = formatter.format("lfm2.5-1.2b-instruct", "SYS", emptyList(), "Q")
        assertTrue(p.startsWith("<|im_start|>system\nSYS"))
        assertTrue(p.endsWith("<|im_start|>assistant\n"))
    }
}
