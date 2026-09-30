package org.ekatra.alfred.data

import android.util.Log
import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import com.google.firebase.remoteconfig.FirebaseRemoteConfigSettings
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages Firebase Remote Config values for feature flags,
 * model URL updates, and system prompt tweaks — without requiring
 * an app update.
 */
@Singleton
class RemoteConfigManager @Inject constructor(
    private val remoteConfig: FirebaseRemoteConfig
) {

    companion object {
        private const val TAG = "RemoteConfigManager"

        // Config keys
        const val KEY_MODEL_DOWNLOAD_URL = "model_download_url"
        const val KEY_SYSTEM_PROMPT = "system_prompt"
        const val KEY_MAX_CONTEXT_MESSAGES = "max_context_messages"
        const val KEY_HOTSPOT_ENABLED = "hotspot_feature_enabled"
        const val KEY_ANALYTICS_SAMPLE_RATE = "analytics_sample_rate"
        const val KEY_MIN_APP_VERSION = "min_app_version"
        const val KEY_MAINTENANCE_MESSAGE = "maintenance_message"

        // Defaults
        private val DEFAULTS = mapOf(
            KEY_MODEL_DOWNLOAD_URL to "https://huggingface.co/Qwen/Qwen2.5-0.5B-Instruct-GGUF/resolve/main/qwen2.5-0.5b-instruct-q4_k_m.gguf",
            KEY_SYSTEM_PROMPT to "",
            KEY_MAX_CONTEXT_MESSAGES to 10L,
            KEY_HOTSPOT_ENABLED to true,
            KEY_ANALYTICS_SAMPLE_RATE to 100L,
            KEY_MIN_APP_VERSION to "1.0.0",
            KEY_MAINTENANCE_MESSAGE to ""
        )
    }

    init {
        try {
            val configSettings = FirebaseRemoteConfigSettings.Builder()
                .setMinimumFetchIntervalInSeconds(3600) // 1 hour in production
                .build()
            remoteConfig.setConfigSettingsAsync(configSettings)
            remoteConfig.setDefaultsAsync(DEFAULTS)
            Log.d(TAG, "Remote Config initialized with defaults")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize Remote Config, using hardcoded defaults", e)
        }
    }

    /**
     * Fetch and activate latest config values from Firebase.
     * Safe to call from any coroutine — internally uses Tasks API.
     */
    fun fetchAndActivate(onComplete: (Boolean) -> Unit = {}) {
        remoteConfig.fetchAndActivate()
            .addOnSuccessListener { activated ->
                Log.d(TAG, "Remote config fetched. Activated new values: $activated")
                onComplete(activated)
            }
            .addOnFailureListener { e ->
                Log.w(TAG, "Remote config fetch failed: ${e.message}")
                onComplete(false)
            }
    }

    /** Model download URL — can be updated server-side to point to a new model. */
    val modelDownloadUrl: String
        get() = try {
            remoteConfig.getString(KEY_MODEL_DOWNLOAD_URL).takeIf { it.isNotBlank() }
                ?: DEFAULTS[KEY_MODEL_DOWNLOAD_URL] as String
        } catch (e: Exception) {
            Log.w(TAG, "Failed to get model URL from Remote Config, using default", e)
            DEFAULTS[KEY_MODEL_DOWNLOAD_URL] as String
        }

    /** Optional system prompt prefix injected before user messages. */
    val systemPrompt: String
        get() = try {
            remoteConfig.getString(KEY_SYSTEM_PROMPT)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to get system prompt, using default", e)
            ""
        }

    /** Max messages to include in conversation context window. */
    val maxContextMessages: Int
        get() = try {
            remoteConfig.getLong(KEY_MAX_CONTEXT_MESSAGES).toInt()
        } catch (e: Exception) {
            Log.w(TAG, "Failed to get max context messages, using default", e)
            10
        }

    /** Feature flag: whether hotspot/multi-device server is available. */
    val isHotspotEnabled: Boolean
        get() = try {
            remoteConfig.getBoolean(KEY_HOTSPOT_ENABLED)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to get hotspot flag, using default", e)
            true
        }

    /** Sampling rate for analytics (0-100). */
    val analyticsSampleRate: Int
        get() = try {
            remoteConfig.getLong(KEY_ANALYTICS_SAMPLE_RATE).toInt()
        } catch (e: Exception) {
            Log.w(TAG, "Failed to get analytics sample rate, using default", e)
            100
        }

    /** Minimum app version required — for forced update prompts. */
    val minAppVersion: String
        get() = try {
            remoteConfig.getString(KEY_MIN_APP_VERSION).takeIf { it.isNotBlank() } ?: "1.0.0"
        } catch (e: Exception) {
            Log.w(TAG, "Failed to get min app version, using default", e)
            "1.0.0"
        }

    /** Optional maintenance message — non-empty means show a banner. */
    val maintenanceMessage: String
        get() = try {
            remoteConfig.getString(KEY_MAINTENANCE_MESSAGE)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to get maintenance message, using default", e)
            ""
        }
}
