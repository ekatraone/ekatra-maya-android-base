package org.ekatra.alfred.notification

import android.util.Log
import org.ekatra.alfred.BuildConfig
import org.ekatra.alfred.data.RemoteConfigManager
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Checks if a newer version of the app is available using Firebase Remote Config.
 *
 * How it works:
 * 1. Developer sets `min_app_version` in Firebase Console → Remote Config
 *    (e.g., "1.2.0" when a new release is published)
 * 2. On app launch, Remote Config is fetched (already done in EkatraApp.onCreate)
 * 3. This class compares the running version against the remote minimum
 * 4. If remote > local, an update is available
 *
 * To trigger an update notification:
 *   Firebase Console → Remote Config → min_app_version → set to new version → Publish
 *
 * Optionally, also add a `latest_app_version` and `update_message` key for richer UX.
 */
@Singleton
class AppUpdateChecker @Inject constructor(
    private val remoteConfigManager: RemoteConfigManager
) {
    companion object {
        private const val TAG = "AppUpdateChecker"
    }

    data class UpdateInfo(
        val isUpdateAvailable: Boolean,
        val currentVersion: String,
        val requiredVersion: String,
        val isForceUpdate: Boolean = false
    )

    /**
     * Check if an update is available.
     * Call after Remote Config has been fetched.
     */
    fun checkForUpdate(): UpdateInfo {
        val currentVersion = BuildConfig.VERSION_NAME  // e.g., "1.1.0"
        val minVersion = remoteConfigManager.minAppVersion  // from Remote Config

        val updateAvailable = compareVersions(minVersion, currentVersion) > 0
        // Force update if the min version is a major bump ahead
        val isForce = updateAvailable && isMajorBump(currentVersion, minVersion)

        Log.d(TAG, "Update check: current=$currentVersion, required=$minVersion, " +
                "available=$updateAvailable, force=$isForce")

        return UpdateInfo(
            isUpdateAvailable = updateAvailable,
            currentVersion = currentVersion,
            requiredVersion = minVersion,
            isForceUpdate = isForce
        )
    }

    /**
     * Compare two semantic version strings (e.g., "1.2.0" vs "1.1.0").
     * Returns positive if v1 > v2, negative if v1 < v2, 0 if equal.
     */
    private fun compareVersions(v1: String, v2: String): Int {
        val parts1 = v1.split(".").map { it.toIntOrNull() ?: 0 }
        val parts2 = v2.split(".").map { it.toIntOrNull() ?: 0 }

        val maxLen = maxOf(parts1.size, parts2.size)
        for (i in 0 until maxLen) {
            val p1 = parts1.getOrElse(i) { 0 }
            val p2 = parts2.getOrElse(i) { 0 }
            if (p1 != p2) return p1 - p2
        }
        return 0
    }

    /**
     * A "major bump" means the major version number increased (e.g., 1.x.x → 2.x.x).
     * Force updates are shown as blocking dialogs.
     */
    private fun isMajorBump(current: String, required: String): Boolean {
        val currentMajor = current.split(".").firstOrNull()?.toIntOrNull() ?: 0
        val requiredMajor = required.split(".").firstOrNull()?.toIntOrNull() ?: 0
        return requiredMajor > currentMajor
    }
}
