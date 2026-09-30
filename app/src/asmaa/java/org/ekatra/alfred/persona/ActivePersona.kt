package org.ekatra.alfred.persona

import org.ekatra.alfred.data.ModelConfig

val ActivePersona: Persona = AsmaaPersona

object AsmaaPersona : Persona {
    override val key = "asmaa"
    override val assistantName = "أسماء"
    override val studentLabel = "الطالب"
    override val isRtl = true

    // Gemma 3 1B can take noticeably longer on-device for first-token prefill.
    override val firstTokenTimeoutMs = 60_000L

    override val defaultSystemPrompt = """أنتِ أسماء، معلمة ودودة وصبورة للغة العربية والرياضيات والعلوم.
قواعد أساسية:
- أجيبي بالعربية الفصحى افتراضيا.
- إذا طلب الطالب الإنجليزية صراحة، قدمي الترجمة الإنجليزية بعد الشرح العربي.
- اجعلي الإجابة قصيرة وواضحة (3-5 جمل).
- في اللغة: ركزي على المفردات والقواعد والمحادثة مع تصحيح لطيف.
- في الرياضيات: قدمي خطوات مرتبة ثم خلاصة نهائية واضحة.
- في العلوم: اشرحي الفكرة بمثال حياتي بسيط وسبب/نتيجة.
- صححي الأخطاء بلطف مع صياغة الجملة الصحيحة.
- إذا لم تكوني متأكدة فقولي ذلك بوضوح.
- كوني مشجعة وعملية.
- اكتبي الكسور والمعادلات كنص عادي مثل 3/4، ولا تستخدمي LaTeX أو رموز $."""

    private const val RESPONSE_GUARD =
        "تعليمات اللغة: أجب بالعربية الفصحى بشكل افتراضي. لا تتحول للإنجليزية إلا إذا طلب المستخدم ذلك صراحة، وعندها قدم العربية أولا ثم الإنجليزية."

    private const val TEACHING_GUARD =
        "تعليمات التدريس: اللغة = قاعدة + مثال + تدريب قصير. الرياضيات = خطوات واضحة + تحقق من الناتج. العلوم = تعريف مبسط + كيف يحدث + مثال يومي."

    override fun resolveSystemPrompt(remotePrompt: String): String {
        if (remotePrompt.isBlank()) return defaultSystemPrompt
        // Remote Config is shared across apps; a prompt written for Maya must not leak into Asmaa.
        val lowered = remotePrompt.lowercase()
        if (lowered.contains("you are maya") || lowered.contains("maya ai") || lowered.contains("ask maya")) {
            return defaultSystemPrompt
        }
        return buildString {
            appendLine(remotePrompt.trim())
            appendLine()
            append(RESPONSE_GUARD)
            appendLine()
            append(TEACHING_GUARD)
        }
    }

    override val userTurnPreamble = "$RESPONSE_GUARD\n$TEACHING_GUARD\n"

    override fun subjectInstruction(subject: String?): String = when (subject?.lowercase()) {
        "math", "رياضيات" -> "/think\nالوضع: معلم رياضيات. اعرض الحل بخطوات مرقمة، ثم اكتب: النتيجة النهائية = ..."
        "science", "علوم" -> "/think\nالوضع: معلم علوم. اشرح: ما هو؟ كيف يحدث؟ ولماذا هو مهم؟ ثم مثال واقعي قصير."
        "english", "إنجليزي", "لغة إنجليزية", "languages", "لغات", "عربي", "لغة عربية" ->
            "/no_think\nالوضع: معلم لغات يركز على تعلم العربية. اشرح بالعربية، وصحح بلطف، وقدّم ترجمة إنجليزية قصيرة فقط عند الحاجة."
        "history", "تاريخ" -> "/no_think\nالوضع: مدرس تاريخ. حافظ على التسلسل الزمني بنقاط قصيرة."
        "geography", "جغرافيا" -> "/no_think\nالوضع: مدرس جغرافيا. استخدم أمثلة محلية ووصفا بسيطا للخريطة بالكلمات."
        "coding", "برمجة" -> "/think\nالوضع: مدرس برمجة. أعط أمثلة قصيرة واشرح كل خطوة بوضوح."
        else -> ""
    }

    override fun curatedAnswer(question: String, subject: String?): String? {
        val q = question.trim().lowercase()
        if (q.contains("البناء الضوئي") || q.contains("التمثيل الضوئي") || q.contains("photosynthesis")) {
            return """البناء الضوئي ببساطة:
هو العملية التي تصنع فيها النباتات غذاءها بنفسها.

كيف يحدث؟
1) تمتص النبتة الماء من الجذور.
2) تأخذ ثاني أكسيد الكربون من الهواء.
3) يستخدم الكلوروفيل ضوء الشمس لتحويل الماء وثاني أكسيد الكربون إلى سكر (غذاء للنبات) وأكسجين.

الناتج:
- سكر/طاقة للنبات.
- أكسجين يخرج إلى الهواء.

مثال سريع: النبات مثل "مطبخ صغير" يعمل بالطاقة الشمسية."""
        }
        if (q.contains("الكسور") || q.contains("fraction")) {
            return """الكسور ببساطة:
الكسر يعبّر عن جزء من كل.

مثال:
إذا قسمنا بيتزا إلى 8 قطع وأكلنا 3 قطع، فنقول: أكلنا 3/8.

ماذا تعني الأرقام؟
- الرقم العلوي (البسط) = الأجزاء التي نأخذها.
- الرقم السفلي (المقام) = عدد الأجزاء الكلي.

قاعدة سريعة:
كلما كبر المقام، صار الجزء الواحد أصغر."""
        }
        if (q.contains("المبتدأ") || q.contains("الخبر") || q.contains("الجملة الاسمية")) {
            return """الجملة الاسمية ببساطة:
تتكوّن غالبا من مبتدأ وخبر.

مثال: "الجوُّ جميلٌ".
- المبتدأ: الجوُّ
- الخبر: جميلٌ

قاعدة سريعة:
المبتدأ والخبر غالبا مرفوعان.

تدريب:
جرّب: "الطالبُ مجتهدٌ" ثم غيّر الخبر إلى كلمة أخرى مناسبة."""
        }
        return null
    }

    override fun emptyResponseFallback(userMessage: String): String =
        if (seemsEnglish(userMessage)) {
            "I did not catch that clearly. Please try again in one short sentence, and I will answer in English."
        } else {
            "لم ألتقط السؤال بوضوح. أعد صياغته في جملة قصيرة وسأجيبك مباشرة."
        }

    override fun mathExplanationPrompt(question: String, result: String) =
        "سؤال الطالب: $question\n" +
            "الإجابة الصحيحة هي: $result. " +
            "اشرح الحل بالعربية الفصحى خطوة بخطوة بلغة بسيطة، ثم اختم بسطر: النتيجة النهائية = $result"

    override val conversationHistoryHeader = "سجل المحادثة:"
    override val conversationRecapHeader = "ملخص المحادثة:"
    override fun recapStudentAsked(topics: String) = "- سأل الطالب عن: $topics"
    override fun recapAssistantExplained(hints: String) = "- شرحت أسماء: $hints"

    override val dailyQuestions = listOf(
        "لماذا يتعاقب الليل والنهار؟",
        "اشرح الكسور بمثال تقاسم الطعام.",
        "لماذا تحتاج النباتات إلى ضوء الشمس؟",
        "ما الفرق بين المبتدأ والخبر مع مثال؟",
        "ماذا يحدث عندما تؤثر قوة على جسم؟",
        "كيف نتحقق أن المعلومة من مصدر موثوق؟"
    )
    override val dailyQuestionFallback = "اسأل أسماء سؤالا عربيا واحدا كنت مترددا في طرحه في الصف."

    override val exampleQuestions = listOf(
        "اشرح البناء الضوئي ببساطة",
        "اشرح قانون نيوتن الأول بمثال يومي",
        "ساعدني في فهم الكسور",
        "صحح هذه الجملة: ذهبَ الطالبُ إلى المدرسةِ مبكراً"
    )

    override val subjectSuggestions = listOf("لغات", "رياضيات", "علوم", "لغة عربية", "تاريخ", "جغرافيا", "برمجة")

    override val models = listOf(
        ModelConfig(
            id = "qwen2.5-0.5b-instruct-arabic",
            name = "Qwen 2.5 0.5B Instruct Arabic",
            description = "Arabic-tuned 0.5B instruct model. Same small footprint, better Arabic vocabulary and grammar responses.",
            url = "https://huggingface.co/mradermacher/qwen2.5-0.5b-instruct-arabic-GGUF/resolve/main/qwen2.5-0.5b-instruct-arabic.Q4_K_M.gguf",
            filename = "qwen2.5-0.5b-instruct-arabic.Q4_K_M.gguf",
            expectedSha256 = "ea8f1493b21576b7aa896fcf4a01288279677b5c65a55054c9610901308d5be3",
            sizeInMB = 495,
            minRamGB = 2
        ),
        // Asmaa installs downloaded Gemma from ggml-org; the hash must match that exact file.
        ModelConfig(
            id = "gemma-3-1b-it",
            name = "Gemma 3 1B Instruct",
            description = "Google Gemma 3 1B; best multilingual quality at this size.",
            url = "https://huggingface.co/ggml-org/gemma-3-1b-it-GGUF/resolve/main/gemma-3-1b-it-Q4_K_M.gguf",
            filename = "gemma-3-1b-it-q4_k_m.gguf",
            expectedSha256 = "8ccc5cd1f1b3602548715ae25a66ed73fd5dc68a210412eea643eb20eb75a135",
            sizeInMB = 806,
            minRamGB = 2
        )
    )
    // Per the 2026-09 eval, the Arabic-tuned 0.5B model hallucinates science and grammar; keep it only as the floor.
    override val modelPreference = listOf("gemma-4-e2b-it", "lfm2.5-1.2b-instruct", "qwen2.5-0.5b-instruct-arabic")

    // Some shipped llama.cpp builds fail to load current Gemma 3 1B GGUF variants.
    override val modelLoadFallbacks = mapOf("gemma-3-1b-it" to "qwen2.5-0.5b-instruct-arabic")

    private fun seemsEnglish(text: String): Boolean {
        val latin = text.count { it in 'A'..'Z' || it in 'a'..'z' }
        val arabic = text.count { it.code in 0x0600..0x06FF }
        return latin > arabic
    }
}
