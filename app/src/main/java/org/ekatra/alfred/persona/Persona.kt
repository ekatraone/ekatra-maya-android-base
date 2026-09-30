package org.ekatra.alfred.persona

import org.ekatra.alfred.data.ModelConfig

/**
 * Everything that differs between the tutor personas (Asmaa, Maya) and is either sent to the
 * model or authored as teaching content. App chrome (buttons, titles, toasts) lives in string
 * resources instead: English in main/res, per-flavor overrides in <flavor>/res.
 *
 * Each product flavor supplies exactly one implementation as [ActivePersona].
 */
interface Persona {
    /** Stable lowercase id, used in file names and TTS utterance ids. */
    val key: String

    /** Name the tutor uses for itself in transcripts and prompts, e.g. "Maya". */
    val assistantName: String

    /** Label for the student's turns in transcripts fed to the model. */
    val studentLabel: String

    /** Whether the persona's UI reads right-to-left. Applied once at the theme root. */
    val isRtl: Boolean

    /** How long to wait for the model's first token before showing [emptyResponseFallback]. */
    val firstTokenTimeoutMs: Long

    val defaultSystemPrompt: String

    /** Turns the Remote Config prompt (possibly blank or written for another persona) into the prompt to use. */
    fun resolveSystemPrompt(remotePrompt: String): String

    /** Prepended to every user turn, before any subject instruction. Empty when unused. */
    val userTurnPreamble: String

    fun subjectInstruction(subject: String?): String

    /** A vetted answer for common questions, served instantly instead of running the model. */
    fun curatedAnswer(question: String, subject: String?): String?

    fun emptyResponseFallback(userMessage: String): String

    fun mathExplanationPrompt(question: String, result: String): String

    val conversationHistoryHeader: String
    val conversationRecapHeader: String
    fun recapStudentAsked(topics: String): String
    fun recapAssistantExplained(hints: String): String

    val dailyQuestions: List<String>
    val dailyQuestionFallback: String
    val exampleQuestions: List<String>
    val subjectSuggestions: List<String>

    /** Models this persona adds to the shared catalog. */
    val models: List<ModelConfig>

    /**
     * Model ids best-first. On first run the app picks the first one whose
     * [ModelConfig.minRamGB] fits the device; the last entry is the floor for any device.
     */
    val modelPreference: List<String>

    /**
     * When the selected model fails to load on this runtime, the model id to fall back to
     * if its file is already on disk. Empty when there is no fallback.
     */
    val modelLoadFallbacks: Map<String, String>
}
