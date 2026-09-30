package org.ekatra.alfred.data

import org.ekatra.alfred.persona.ActivePersona

/**
 * Configuration for different LLM models
 */
data class ModelConfig(
    val id: String,
    val name: String,
    val description: String,
    val url: String,
    val filename: String,
    val expectedSha256: String,
    val sizeInMB: Long,
    val minRamGB: Int
)

object AvailableModels {
    private val sharedModels = listOf(
        // Best answers in both Arabic and English in our 2026-09 eval; needs a 6 GB+ phone.
        ModelConfig(
            id = "gemma-4-e2b-it",
            name = "Gemma 4 E2B Instruct",
            description = "Google Gemma 4 (effective 2B). Most accurate tutor answers in Arabic and English; needs 6GB+ RAM.",
            url = "https://huggingface.co/unsloth/gemma-4-E2B-it-GGUF/resolve/0314792d7f1f7e229411f620751375812bb9faf2/gemma-4-E2B-it-Q4_K_M.gguf",
            filename = "gemma-4-e2b-it-q4_k_m.gguf",
            expectedSha256 = "740185b21d22ceb83a11c3aa62ad5842ef32c70f6096d756bbee85a1e4ec34b8",
            sizeInMB = 3106,
            minRamGB = 6
        ),
        // Fast hybrid model, runs in ~1 GB. LFM Open License v1.0: free under $10M annual revenue.
        ModelConfig(
            id = "lfm2.5-1.2b-instruct",
            name = "LFM2.5 1.2B Instruct",
            description = "Liquid AI LFM2.5. Fast and light, solid Arabic and English; good for 3-4GB phones.",
            url = "https://huggingface.co/LiquidAI/LFM2.5-1.2B-Instruct-GGUF/resolve/8ed288026e23958ad9dfa92d53ed773a8eee7125/LFM2.5-1.2B-Instruct-Q4_K_M.gguf",
            filename = "lfm2.5-1.2b-instruct-q4_k_m.gguf",
            expectedSha256 = "b1b3de114215d9507409a662a501a631095a479a419584e8a2ded6304b19b4f5",
            sizeInMB = 731,
            minRamGB = 2
        ),
        ModelConfig(
            id = "qwen2.5-0.5b-instruct",
            name = "Qwen 2.5 0.5B Instruct",
            description = "Instruction-tuned, lightweight; good for on-device tutoring with concise answers.",
            url = "https://huggingface.co/Qwen/Qwen2.5-0.5B-Instruct-GGUF/resolve/main/qwen2.5-0.5b-instruct-q4_k_m.gguf",
            filename = "qwen2.5-0.5b-instruct-q4_k_m.gguf",
            expectedSha256 = "74a4da8c9fdbcd15bd1f6d01d621410d31c6fc00986f5eb687824e7b93d7a9db",
            sizeInMB = 469,
            minRamGB = 2
        ),
        ModelConfig(
            id = "qwen-1.5b",
            name = "Qwen 2.5 1.5B",
            description = "Better quality responses, needs 3GB+ RAM. More detailed explanations.",
            url = "https://huggingface.co/Qwen/Qwen2.5-1.5B-Instruct-GGUF/resolve/main/qwen2.5-1.5b-instruct-q4_k_m.gguf",
            filename = "qwen-1.5b-q4.gguf",
            expectedSha256 = "6a1a2eb6d15622bf3c96857206351ba97e1af16c30d7a74ee38970e434e9407e",
            sizeInMB = 934,
            minRamGB = 3
        ),
        ModelConfig(
            id = "phi-3-mini",
            name = "Phi-3 Mini 3.8B",
            description = "High quality, needs 4GB+ RAM. Best for detailed learning and complex topics.",
            url = "https://huggingface.co/microsoft/Phi-3-mini-4k-instruct-gguf/resolve/main/Phi-3-mini-4k-instruct-q4.gguf",
            filename = "phi-3-mini-q4.gguf",
            expectedSha256 = "8a83c7fb9049a9b2e92266fa7ad04933bb53aa1e85136b7b30f1b8000ff2edef",
            sizeInMB = 2300,
            minRamGB = 4
        ),
        ModelConfig(
            id = "tinyllama",
            name = "TinyLlama 1.1B (Ultra Light)",
            description = "Ultra-compact, works on 1.5GB+ RAM. Basic responses, very fast.",
            url = "https://huggingface.co/TheBloke/TinyLlama-1.1B-Chat-v1.0-GGUF/resolve/main/tinyllama-1.1b-chat-v1.0.Q4_K_M.gguf",
            filename = "tinyllama-1.1b-q4.gguf",
            expectedSha256 = "9fecc3b3cd76bba89d504f29b616eedf7da85b96540e490ca5824d3f7d2776a0",
            sizeInMB = 669,
            minRamGB = 2
        )
    )
    
    val models: List<ModelConfig> = ActivePersona.models + sharedModels

    fun getModelById(id: String): ModelConfig? {
        return models.find { it.id == id }
    }
    
    /** Best model in the persona's preference order that fits in [deviceRamGb]. */
    fun getDefaultModel(deviceRamGb: Int): ModelConfig {
        val ranked = ActivePersona.modelPreference.mapNotNull { getModelById(it) }
        return ranked.firstOrNull { it.minRamGB <= deviceRamGb } ?: ranked.last()
    }
    
    fun getModelByFilename(filename: String): ModelConfig? {
        return models.find { it.filename == filename }
    }
}
