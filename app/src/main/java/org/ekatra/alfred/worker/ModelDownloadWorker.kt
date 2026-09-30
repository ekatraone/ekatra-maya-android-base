package org.ekatra.alfred.worker

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.hilt.work.HiltWorker
import androidx.work.*
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import org.ekatra.alfred.R
import org.ekatra.alfred.data.AvailableModels
import org.ekatra.alfred.data.AppSettings
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

/**
 * WorkManager-based background model download.
 * Survives app backgrounding, process death, and device restarts.
 * Shows a foreground notification with progress.
 */
@HiltWorker
class ModelDownloadWorker @AssistedInject constructor(
    @Assisted private val context: Context,
    @Assisted workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    companion object {
        private const val TAG = "ModelDownloadWorker"
        const val WORK_NAME = "model_download"
        private const val CHANNEL_ID = "model_download_channel"
        private const val NOTIFICATION_ID = 42

        // Input data keys
        const val KEY_MODEL_URL = "model_url"
        const val KEY_MODEL_FILENAME = "model_filename"
        const val KEY_MODEL_ID = "model_id"

        // Progress data keys
        const val KEY_PROGRESS = "download_progress"
        const val KEY_STATUS = "download_status"
        const val KEY_DOWNLOADED_BYTES = "downloaded_bytes"
        const val KEY_TOTAL_BYTES = "total_bytes"
        const val KEY_ERROR_TYPE = "error_type"

        // Error type values
        const val ERROR_NETWORK = "network"
        const val ERROR_INTEGRITY = "integrity"

        /**
         * Enqueue a model download that continues in the background.
         */
        fun enqueue(context: Context, modelUrl: String, modelFilename: String, modelId: String) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val inputData = workDataOf(
                KEY_MODEL_URL to modelUrl,
                KEY_MODEL_FILENAME to modelFilename,
                KEY_MODEL_ID to modelId
            )

            val request = OneTimeWorkRequestBuilder<ModelDownloadWorker>()
                .setConstraints(constraints)
                .setInputData(inputData)
                .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                .build()

            WorkManager.getInstance(context).enqueueUniqueWork(
                WORK_NAME,
                ExistingWorkPolicy.KEEP,
                request
            )
            Log.d(TAG, "Model download enqueued: $modelFilename")
        }

        /**
         * Cancel any running download.
         */
        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
        }
    }

    // Required for expedited workers — called by WorkManager before doWork() to promote
    // the worker to a foreground service even when the app is in the background.
    override suspend fun getForegroundInfo(): ForegroundInfo {
        createNotificationChannel()
        return createForegroundInfo(0, "Downloading AI model...")
    }

    override suspend fun doWork(): Result {
        val modelUrl = inputData.getString(KEY_MODEL_URL) ?: return Result.failure()
        val modelFilename = inputData.getString(KEY_MODEL_FILENAME) ?: return Result.failure()
        val modelId = inputData.getString(KEY_MODEL_ID) ?: return Result.failure()

        val settings = AppSettings(context)
        val modelDir = File(context.filesDir, "models")
        if (!modelDir.exists()) modelDir.mkdirs()

        val modelFile = File(modelDir, modelFilename)
        val tempFile = File(modelDir, "$modelFilename.tmp")
        val modelConfig = AvailableModels.getModelById(modelId)
        val expectedSha = modelConfig?.expectedSha256

        if (expectedSha.isNullOrBlank()) {
            Log.e(TAG, "Missing pinned SHA-256 for modelId=$modelId")
            return Result.failure(workDataOf(KEY_STATUS to "Model integrity policy missing"))
        }

        // Check if already downloaded
        val remoteSize = fetchRemoteSize(modelUrl)
        val minValidSize = remoteSize?.let { (it * 0.98).toLong() } ?: 0L

        if (modelFile.exists()) {
            val localSize = modelFile.length()
            val goodWithRemote = remoteSize != null && localSize >= minValidSize
            val goodWithoutRemote = remoteSize == null && localSize > 50_000_000L
            if ((goodWithRemote || goodWithoutRemote) && verifyFileHash(modelFile, expectedSha)) {
                setProgress(workDataOf(KEY_PROGRESS to 100, KEY_STATUS to context.getString(R.string.worker_model_ready)))
                return Result.success()
            }
            modelFile.delete()
        }

        // Clean up broken files
        if (modelFile.exists() && remoteSize != null && modelFile.length() < minValidSize) {
            modelFile.delete()
        }

        // Resume support
        var resumeBytes = tempFile.takeIf { it.exists() }?.length() ?: 0L
        if (resumeBytes == 0L) {
            resumeBytes = settings.getPartialDownload(modelId)
        }

        // Create notification channel
        createNotificationChannel()

        // Set as foreground with notification
        setForeground(createForegroundInfo(0, "Starting download..."))

        return try {
            downloadWithProgress(modelUrl, tempFile, modelFile, modelId, settings, resumeBytes, remoteSize)
            Result.success()
        } catch (e: IntegrityException) {
            // SHA-256 mismatch or incomplete file — no point retrying with same bytes
            Log.e(TAG, "Integrity failure — not retrying: ${e.message}")
            setProgress(workDataOf(
                KEY_PROGRESS to -1,
                KEY_STATUS to "integrity_failed",
                KEY_ERROR_TYPE to ERROR_INTEGRITY
            ))
            Result.failure(workDataOf(
                KEY_STATUS to "integrity_failed",
                KEY_ERROR_TYPE to ERROR_INTEGRITY
            ))
        } catch (e: Exception) {
            Log.e(TAG, "Download failed (will retry)", e)
            // Don't delete temp file — allow resume on retry
            setProgress(workDataOf(
                KEY_PROGRESS to -1,
                KEY_STATUS to context.getString(R.string.worker_download_failed, e.message?.take(50) ?: context.getString(R.string.worker_network_error)),
                KEY_ERROR_TYPE to ERROR_NETWORK
            ))
            Result.retry()
        }
    }

    private suspend fun downloadWithProgress(
        modelUrl: String,
        tempFile: File,
        modelFile: File,
        modelId: String,
        settings: AppSettings,
        resumeBytes: Long,
        remoteSize: Long?
    ) {
        val url = URL(modelUrl)
        val connection = url.openConnection() as HttpURLConnection
        connection.connectTimeout = 60000
        connection.readTimeout = 120000
        connection.instanceFollowRedirects = true
        connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Android)")

        if (resumeBytes > 0) {
            connection.setRequestProperty("Range", "bytes=$resumeBytes-")
            Log.d(TAG, "Resuming download from byte $resumeBytes")
        }

        val responseCode = connection.responseCode
        if (responseCode == 416) {
            // Range Not Satisfiable — resume offset is past end of file (corrupted partial).
            // Delete and restart from zero.
            Log.w(TAG, "HTTP 416: stale resume offset ($resumeBytes bytes), restarting download")
            tempFile.delete()
            settings.clearPartialDownload(modelId)
            connection.disconnect()
            return downloadWithProgress(modelUrl, tempFile, modelFile, modelId, settings, 0L, remoteSize)
        }
        if (responseCode != HttpURLConnection.HTTP_OK && responseCode != HttpURLConnection.HTTP_PARTIAL) {
            throw Exception("Server error: $responseCode")
        }

        // If we requested a range but server returned 200 (redirect dropped the Range header),
        // we must start fresh — otherwise we'd append the full file onto the partial file.
        val effectiveResumeBytes = if (resumeBytes > 0 && responseCode == HttpURLConnection.HTTP_OK) {
            Log.w(TAG, "Range request ignored by server (redirect?), restarting download")
            tempFile.delete()
            settings.clearPartialDownload(modelId)
            0L
        } else {
            resumeBytes
        }

        val fileSize = connection.contentLengthLong.takeIf { it > 0 } ?: remoteSize ?: 0L
        var downloaded = effectiveResumeBytes

        connection.inputStream.use { input ->
            FileOutputStream(tempFile, effectiveResumeBytes > 0).use { output ->
                val buffer = ByteArray(32768)
                var bytesRead: Int
                var lastProgressUpdate = 0L

                while (input.read(buffer).also { bytesRead = it } != -1) {
                    output.write(buffer, 0, bytesRead)
                    downloaded += bytesRead
                    settings.savePartialDownload(modelId, downloaded)

                    // Update progress every 500KB
                    if (downloaded - lastProgressUpdate > 500_000) {
                        lastProgressUpdate = downloaded
                        val progressPercent = if (fileSize > 0) {
                            ((downloaded.toFloat() / fileSize.toFloat()) * 100).toInt().coerceIn(0, 99)
                        } else {
                            (downloaded / 1_000_000).toInt().coerceAtMost(99)
                        }

                        val downloadedMB = downloaded / 1_000_000
                        val totalMB = if (fileSize > 0) fileSize / 1_000_000 else 0
                        val statusText = if (totalMB > 0) "${downloadedMB}MB / ${totalMB}MB" else "${downloadedMB}MB downloaded"

                        setProgress(workDataOf(
                            KEY_PROGRESS to progressPercent,
                            KEY_STATUS to context.getString(R.string.worker_downloading),
                            KEY_DOWNLOADED_BYTES to downloaded,
                            KEY_TOTAL_BYTES to fileSize
                        ))

                        setForeground(createForegroundInfo(progressPercent, statusText))
                    }
                }
            }
        }

        // Verify download
        val expectedSize = when {
            fileSize > 0 -> fileSize
            remoteSize != null -> remoteSize
            else -> tempFile.length()
        }

        if (expectedSize > 0 && tempFile.length() < expectedSize * 0.98) {
            throw IntegrityException("Download incomplete (${tempFile.length() / 1_000_000}/${expectedSize / 1_000_000} MB)")
        }

        val expectedSha = AvailableModels.getModelById(modelId)?.expectedSha256
            ?: throw IntegrityException("Missing pinned SHA-256 for modelId=$modelId")
        if (!verifyFileHash(tempFile, expectedSha)) {
            tempFile.delete()
            throw IntegrityException("Model integrity check failed")
        }

        // Move temp to final
        if (!tempFile.renameTo(modelFile)) {
            throw Exception("Failed to finalize downloaded model")
        }
        settings.clearPartialDownload(modelId)

        setProgress(workDataOf(KEY_PROGRESS to 100, KEY_STATUS to context.getString(R.string.worker_download_complete)))
        setForeground(createForegroundInfo(100, "Download complete!"))
    }

    private class IntegrityException(message: String) : Exception(message)

    private fun fetchRemoteSize(urlStr: String): Long? {
        return try {
            val url = URL(urlStr)
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "HEAD"
            connection.connectTimeout = 15_000
            connection.readTimeout = 15_000
            connection.instanceFollowRedirects = true
            connection.connect()
            val size = connection.contentLengthLong.takeIf { it > 0 }
            connection.disconnect()
            size
        } catch (e: Exception) {
            Log.w(TAG, "HEAD size probe failed: ${e.message}")
            null
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Model Download",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows progress while downloading the AI model"
            }
            val manager = context.getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun createForegroundInfo(progressPercent: Int, statusText: String): ForegroundInfo {
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(context.getString(R.string.app_name))
            .setContentText(statusText)
            .setProgress(100, progressPercent, progressPercent == 0)
            .setOngoing(true)
            .setSilent(true)
            .build()

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            ForegroundInfo(NOTIFICATION_ID, notification)
        }
    }

    private fun verifyFileHash(file: File, expectedSha256: String): Boolean {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().buffered().use { input ->
            val buffer = ByteArray(1024 * 1024)
            var read: Int
            while (input.read(buffer).also { read = it } != -1) {
                digest.update(buffer, 0, read)
            }
        }
        val actual = digest.digest().joinToString("") { "%02x".format(it) }
        val matches = actual.equals(expectedSha256, ignoreCase = true)
        if (!matches) {
            Log.e(TAG, "SHA-256 mismatch. expected=$expectedSha256 actual=$actual")
        }
        return matches
    }
}
