package org.ekatra.alfred

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.security.MessageDigest

object ModelVerifier {
    private const val TAG = "ModelVerifier"

    /**
     * Verifies that the given [modelFile] matches the [expectedSha256].
     * Runs on the IO dispatcher because hashing a large file takes time.
     */
    suspend fun verifyModel(modelFile: File, expectedSha256: String): Boolean = withContext(Dispatchers.IO) {
        if (!modelFile.exists() || !modelFile.isFile || expectedSha256.isBlank()) {
            return@withContext false
        }
        
        try {
            val digest = MessageDigest.getInstance("SHA-256")
            modelFile.inputStream().use { stream ->
                val buffer = ByteArray(1024 * 1024) // 1MB buffer for speed
                var bytesRead: Int
                while (stream.read(buffer).also { bytesRead = it } != -1) {
                    digest.update(buffer, 0, bytesRead)
                }
            }
            
            // Format digest to hex string manually
            val actualHash = digest.digest().joinToString("") { "%02x".format(it) }
            
            val matches = actualHash.equals(expectedSha256, ignoreCase = true)
            if (!matches) {
                Log.e(TAG, "SHA-256 mismatch. expected=$expectedSha256 actual=$actualHash")
            } else {
                Log.d(TAG, "SHA-256 match successful for ${modelFile.name}")
            }
            matches
        } catch (e: Exception) {
            Log.e(TAG, "Error verifying model hash", e)
            false
        }
    }
}
