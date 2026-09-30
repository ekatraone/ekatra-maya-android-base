package org.ekatra.alfred.data

import android.app.ActivityManager
import android.content.Context
import android.content.SharedPreferences
import java.io.File

/**
 * App settings manager for model selection and preferences
 */
class AppSettings(context: Context) {

    private val appContext = context.applicationContext
    
    private val prefs: SharedPreferences = context.getSharedPreferences(
        "ekatra_settings", Context.MODE_PRIVATE
    )
    
    companion object {
        private const val KEY_SELECTED_MODEL_ID = "selected_model_id"
        private const val KEY_PARTIAL_PREFIX = "partial_"
        private const val KEY_ANALYTICS_CONSENT = "analytics_consent"
        private const val KEY_FEEDBACK_CONSENT = "feedback_consent"
        private const val KEY_CONSENT_SHOWN = "consent_shown"
        private const val KEY_ONBOARDING_COMPLETE = "onboarding_complete"
        private const val KEY_STREAK_DAYS = "streak_days"
        private const val KEY_LAST_STREAK_DAY = "last_streak_day"
        private const val KEY_LAST_DAILY_QUESTION_DAY = "last_daily_question_day"
        private const val KEY_LAST_DAILY_QUESTION = "last_daily_question"
    }
    
    /**
     * Get the currently selected model configuration
     */
    fun getSelectedModel(): ModelConfig {
        val savedModelId = prefs.getString(KEY_SELECTED_MODEL_ID, null)
        return savedModelId?.let { AvailableModels.getModelById(it) }
            ?: AvailableModels.getDefaultModel(deviceRamGb)
    }

    // Rounded up: a "6 GB" phone reports ~5.5 GB total after kernel/firmware reservations.
    private val deviceRamGb: Int by lazy {
        val info = ActivityManager.MemoryInfo()
        appContext.getSystemService(ActivityManager::class.java).getMemoryInfo(info)
        Math.ceil(info.totalMem / 1_000_000_000.0).toInt()
    }
    
    /**
     * Save the selected model ID
     */
    fun setSelectedModel(modelId: String) {
        prefs.edit().putString(KEY_SELECTED_MODEL_ID, modelId).apply()
    }

    /**
     * If the selected model file is missing but another known model file exists on disk,
     * automatically switch the selection to that file to avoid false "model not found" states.
     */
    fun recoverDownloadedModel(context: Context): ModelConfig? {
        val modelsDir = File(context.filesDir, "models")
        if (!modelsDir.exists()) return null

        val ggufFiles = modelsDir.listFiles { _, name -> name.endsWith(".gguf") } ?: return null
        // Prefer a known filename from AvailableModels
        val known = ggufFiles.firstNotNullOfOrNull { file ->
            AvailableModels.getModelByFilename(file.name)
        }
        return when {
            known != null -> {
                setSelectedModel(known.id)
                known
            }
            ggufFiles.isNotEmpty() -> {
                // Unknown file: keep selection but still signal presence
                null
            }
            else -> null
        }
    }
    
    /**
     * Check if a model has been downloaded
     */
    fun isModelDownloaded(context: Context, model: ModelConfig): Boolean {
        val modelFile = java.io.File(context.filesDir, "models/${model.filename}")
        return modelFile.exists() && modelFile.length() > 0
    }

    fun isModelVerified(modelId: String, fileLastModified: Long, fileSize: Long): Boolean {
        val verifiedModified = prefs.getLong("verified_mod_$modelId", -1L)
        val verifiedSize = prefs.getLong("verified_size_$modelId", -1L)
        return verifiedModified == fileLastModified && verifiedSize == fileSize && fileLastModified != -1L
    }

    fun setModelVerified(modelId: String, fileLastModified: Long, fileSize: Long) {
        prefs.edit()
            .putLong("verified_mod_$modelId", fileLastModified)
            .putLong("verified_size_$modelId", fileSize)
            .apply()
    }

    fun clearModelVerification(modelId: String) {
        prefs.edit()
            .remove("verified_mod_$modelId")
            .remove("verified_size_$modelId")
            .apply()
    }

    fun savePartialDownload(modelId: String, bytes: Long) {
        prefs.edit().putLong("$KEY_PARTIAL_PREFIX$modelId", bytes).apply()
    }

    fun clearPartialDownload(modelId: String) {
        prefs.edit().remove("$KEY_PARTIAL_PREFIX$modelId").apply()
    }

    fun getPartialDownload(modelId: String): Long = prefs.getLong("$KEY_PARTIAL_PREFIX$modelId", 0L)

    // Analytics consent (GDPR / DPDP Act)
    fun setAnalyticsConsent(consent: Boolean) {
        prefs.edit()
            .putBoolean(KEY_ANALYTICS_CONSENT, consent)
            .putBoolean(KEY_CONSENT_SHOWN, true)
            .apply()
    }

    fun hasAnalyticsConsent(): Boolean = prefs.getBoolean(KEY_ANALYTICS_CONSENT, false)
    fun isConsentShown(): Boolean = prefs.getBoolean(KEY_CONSENT_SHOWN, false)

    // Feedback consent — separate from analytics. Required to upload thumbs-down content.
    fun setFeedbackConsent(consent: Boolean) {
        prefs.edit().putBoolean(KEY_FEEDBACK_CONSENT, consent).apply()
    }
    fun hasFeedbackConsent(): Boolean = prefs.getBoolean(KEY_FEEDBACK_CONSENT, false)

    // Anonymous ID — random UUID generated on first call, used for grouping feedback uploads
    // without identifying the user. Stored locally only.
    fun getAnonId(): String {
        val existing = prefs.getString("anon_id", null)
        if (existing != null) return existing
        val fresh = java.util.UUID.randomUUID().toString()
        prefs.edit().putString("anon_id", fresh).apply()
        return fresh
    }

    // Onboarding
    fun setOnboardingComplete() {
        prefs.edit().putBoolean(KEY_ONBOARDING_COMPLETE, true).apply()
    }

    fun isOnboardingComplete(): Boolean = prefs.getBoolean(KEY_ONBOARDING_COMPLETE, false)

    fun getStreakDays(): Int = prefs.getInt(KEY_STREAK_DAYS, 0)

    fun markActiveToday(dayEpoch: Long): Int {
        val lastDay = prefs.getLong(KEY_LAST_STREAK_DAY, -1L)
        val currentStreak = prefs.getInt(KEY_STREAK_DAYS, 0)
        val nextStreak = when {
            lastDay == dayEpoch -> currentStreak
            lastDay == dayEpoch - 1L -> currentStreak + 1
            else -> 1
        }
        prefs.edit()
            .putLong(KEY_LAST_STREAK_DAY, dayEpoch)
            .putInt(KEY_STREAK_DAYS, nextStreak)
            .apply()
        return nextStreak
    }

    fun getDailyQuestionForDay(dayEpoch: Long, candidates: List<String>, fallback: String): String {
        if (candidates.isEmpty()) return fallback
        val lastDay = prefs.getLong(KEY_LAST_DAILY_QUESTION_DAY, -1L)
        val cached = prefs.getString(KEY_LAST_DAILY_QUESTION, null)
        // A cached question from an older question set (e.g. before a language switch) is discarded.
        if (lastDay == dayEpoch && cached != null && cached in candidates) {
            return cached
        }
        val index = (dayEpoch % candidates.size).toInt()
        val question = candidates[index]
        prefs.edit()
            .putLong(KEY_LAST_DAILY_QUESTION_DAY, dayEpoch)
            .putString(KEY_LAST_DAILY_QUESTION, question)
            .apply()
        return question
    }
}
