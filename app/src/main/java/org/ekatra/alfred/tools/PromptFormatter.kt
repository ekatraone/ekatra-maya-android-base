package org.ekatra.alfred.tools

import org.ekatra.alfred.persona.ActivePersona

import org.ekatra.alfred.data.model.ChatMessageEntity

/**
 * PromptFormatter — formats chat prompts for different model families.
 *
 * Currently supports:
 *   • Qwen (ChatML: `<|im_start|>` / `<|im_end|>`)
 *   • Phi-3  (`<|user|>` / `<|assistant|>`)
 *   • Llama / TinyLlama (`[INST]` / `[/INST]`)
 *   • Gemma 3 (`<start_of_turn>` / `<end_of_turn>`)
 *
 * The formatter is **stateless** — call [format] with the model id,
 * system prompt, history turns, and the new user message to get a
 * complete prompt string ready for [InferenceEngine.generateRaw].
 *
 * S.1: History is passed as a list of [ChatMessageEntity] so that each
 * turn gets its own correctly-labelled chat-ML block, rather than being
 * concatenated into a single giant user message.
 */
class PromptFormatter {

    /**
     * Build a fully-formatted prompt for the given model.
     *
     * @param modelId      one of the ids from [ModelConfig]
     * @param system       system prompt text (may be blank)
     * @param historyTurns prior turns from [ChatRepository.getConversationTurns] — may be empty
     * @param userMessage  the new user turn (plain text, no "Student:" prefix)
     */
    fun format(
        modelId: String,
        system: String,
        historyTurns: List<ChatMessageEntity>,
        userMessage: String
    ): String = when {
        modelId.startsWith("qwen")      -> formatQwen(system, historyTurns, userMessage)
        modelId.startsWith("phi")       -> formatPhi(system, historyTurns, userMessage)
        modelId.startsWith("tinyllama") -> formatLlama(system, historyTurns, userMessage)
        // Gemma 4 changed turn markers; it must be matched before the Gemma 3 branch.
        modelId.startsWith("gemma-4")   -> formatGemma4(system, historyTurns, userMessage)
        modelId.startsWith("gemma")     -> formatGemma(system, historyTurns, userMessage)
        else                            -> formatQwen(system, historyTurns, userMessage)
    }

    /**
     * Legacy overload — used by [AlfredServer] which still builds its own body string.
     * Kept for source compatibility; prefer the turns-based overload from new code.
     */
    fun format(modelId: String, system: String, body: String): String =
        format(modelId, system, emptyList(), body)

    // ---- Qwen ChatML ----
    private fun formatQwen(
        system: String,
        history: List<ChatMessageEntity>,
        userMessage: String
    ): String = buildString {
        append("<|im_start|>system\n")
        append(system)
        append("<|im_end|>\n")
        for (msg in history) {
            val role = if (msg.isUser) "user" else "assistant"
            append("<|im_start|>$role\n")
            append(msg.text)
            append("<|im_end|>\n")
        }
        append("<|im_start|>user\n")
        append(userMessage)
        append("<|im_end|>\n")
        append("<|im_start|>assistant\n")
    }

    // ---- Phi-3 ----
    private fun formatPhi(
        system: String,
        history: List<ChatMessageEntity>,
        userMessage: String
    ): String = buildString {
        append("<|system|>\n")
        append(system)
        append("<|end|>\n")
        for (msg in history) {
            val role = if (msg.isUser) "<|user|>" else "<|assistant|>"
            append("$role\n")
            append(msg.text)
            append("<|end|>\n")
        }
        append("<|user|>\n")
        append(userMessage)
        append("<|end|>\n")
        append("<|assistant|>\n")
    }

    // ---- Gemma 3 ----
    // Gemma has no native system role: the system prompt is prepended
    // to the first user turn, separated by a blank line. <bos> is added
    // automatically by llama.cpp when the model's add_bos_token is true,
    // so we do NOT emit it here.
    private fun formatGemma(
        system: String,
        history: List<ChatMessageEntity>,
        userMessage: String
    ): String = buildString {
        var systemInjected = false
        fun openUser(text: String) {
            append("<start_of_turn>user\n")
            if (!systemInjected && system.isNotBlank()) {
                append(system.trim())
                append("\n\n")
                systemInjected = true
            }
            append(text)
            append("<end_of_turn>\n")
        }
        for (msg in history) {
            if (msg.isUser) {
                openUser(msg.text)
            } else {
                append("<start_of_turn>model\n")
                append(msg.text)
                append("<end_of_turn>\n")
            }
        }
        openUser(userMessage)
        append("<start_of_turn>model\n")
    }

    // ---- Gemma 4 ----
    // Native system role; turns are <|turn>role ... <turn|>. Thinking stays off (no <|think|>).
    private fun formatGemma4(
        system: String,
        history: List<ChatMessageEntity>,
        userMessage: String
    ): String = buildString {
        if (system.isNotBlank()) {
            append("<|turn>system\n").append(system.trim()).append("<turn|>\n")
        }
        for (msg in history) {
            append(if (msg.isUser) "<|turn>user\n" else "<|turn>model\n")
            append(msg.text).append("<turn|>\n")
        }
        append("<|turn>user\n").append(userMessage).append("<turn|>\n")
        append("<|turn>model\n")
    }

    // ---- Llama / TinyLlama ----
    private fun formatLlama(
        system: String,
        history: List<ChatMessageEntity>,
        userMessage: String
    ): String = buildString {
        // Llama uses a single [INST] block; prior turns interleaved as plain text
        append("[INST] <<SYS>>\n")
        append(system)
        append("\n<</SYS>>\n\n")
        for (msg in history) {
            if (msg.isUser) append("${ActivePersona.studentLabel}: ${msg.text}\n")
            else append("${ActivePersona.assistantName}: ${msg.text}\n")
        }
        append(userMessage)
        append(" [/INST]\n")
    }
}
