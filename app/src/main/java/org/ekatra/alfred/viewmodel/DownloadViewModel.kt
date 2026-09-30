package org.ekatra.alfred.viewmodel

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.ekatra.alfred.data.AppSettings
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject

data class DownloadUiState(
    val progress: Float = 0f,
    val statusText: String = "Starting download...",
    val downloadComplete: Boolean = false,
    val downloadFailed: Boolean = false
)

@HiltViewModel
class DownloadViewModel @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val settings: AppSettings,
    private val analyticsHelper: AnalyticsHelper
) : ViewModel() {

    companion object {
        private const val TAG = "DownloadViewModel"
    }

    private val _uiState = MutableStateFlow(DownloadUiState())
    val uiState: StateFlow<DownloadUiState> = _uiState.asStateFlow()

    private val selectedModel by lazy { settings.getSelectedModel() }
    val modelPath: String by lazy {
        File(appContext.filesDir, "models/${selectedModel.filename}").absolutePath
    }

    fun startDownload() {
        viewModelScope.launch {
            _uiState.update { DownloadUiState() } // reset
            downloadModel()
        }
    }

    private suspend fun downloadModel() {
        withContext(Dispatchers.IO) {
            val modelDir = File(appContext.filesDir, "models")
            if (!modelDir.exists()) modelDir.mkdirs()

            val modelFile = File(modelPath)
            val tempFile = File("$modelPath.tmp")
            var resumeBytes = tempFile.takeIf { it.exists() }?.length() ?: 0L
            if (resumeBytes == 0L) resumeBytes = settings.getPartialDownload(selectedModel.id)

            val remoteSize = fetchRemoteSize(selectedModel.url)
            val minValidSize = remoteSize?.let { (it * 0.98).toLong() } ?: 0L

            if (modelFile.exists()) {
                val localSize = modelFile.length()
                val goodWithRemote = remoteSize != null && localSize >= minValidSize
                val goodWithoutRemote = remoteSize == null && localSize > 50_000_000L
                if (goodWithRemote || goodWithoutRemote) {
                    _uiState.update { it.copy(progress = 1.0f, statusText = "Model ready!", downloadComplete = true) }
                    return@withContext
                }
            }

            if (modelFile.exists() && remoteSize != null && modelFile.length() < minValidSize) {
                modelFile.delete()
            }

            try {
                val url = URL(selectedModel.url)
                val connection = url.openConnection() as HttpURLConnection
                connection.connectTimeout = 60000
                connection.readTimeout = 120000
                connection.instanceFollowRedirects = true
                connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Android)")

                if (resumeBytes > 0) {
                    connection.setRequestProperty("Range", "bytes=$resumeBytes-")
                }

                val responseCode = connection.responseCode
                if (responseCode != HttpURLConnection.HTTP_OK && responseCode != HttpURLConnection.HTTP_PARTIAL) {
                    _uiState.update { it.copy(downloadFailed = true, statusText = "Server error: $responseCode") }
                    return@withContext
                }

                val fileSize = connection.contentLengthLong.takeIf { it > 0 } ?: remoteSize ?: 0L
                var downloaded = resumeBytes

                connection.inputStream.use { input ->
                    FileOutputStream(tempFile, resumeBytes > 0).use { output ->
                        val buffer = ByteArray(32768)
                        var bytesRead: Int
                        var lastProgressUpdate = 0L

                        while (input.read(buffer).also { bytesRead = it } != -1) {
                            output.write(buffer, 0, bytesRead)
                            downloaded += bytesRead
                            settings.savePartialDownload(selectedModel.id, downloaded)

                            if (downloaded - lastProgressUpdate > 100_000) {
                                lastProgressUpdate = downloaded
                                val progress = if (fileSize > 0) {
                                    (downloaded.toFloat() / fileSize.toFloat()).coerceIn(0f, 0.99f)
                                } else {
                                    (downloaded / 1_000_000f).coerceAtMost(0.99f)
                                }
                                _uiState.update { it.copy(progress = progress, statusText = "Downloading AI brain...") }
                            }
                        }
                    }
                }

                val expectedSize = when {
                    fileSize > 0 -> fileSize
                    remoteSize != null -> remoteSize
                    else -> tempFile.length()
                }

                if (expectedSize > 0 && tempFile.length() < expectedSize * 0.98) {
                    tempFile.delete()
                    _uiState.update {
                        it.copy(downloadFailed = true, statusText = "Download incomplete, please retry")
                    }
                    return@withContext
                }

                tempFile.renameTo(modelFile)
                settings.clearPartialDownload(selectedModel.id)

                analyticsHelper.logModelSelected(selectedModel.id, selectedModel.sizeInMB)

                _uiState.update { it.copy(progress = 1.0f, statusText = "Download complete!", downloadComplete = true) }
            } catch (e: Exception) {
                Log.e(TAG, "Download error", e)
                tempFile.delete()
                _uiState.update {
                    it.copy(downloadFailed = true, statusText = "Download failed: ${e.message?.take(50) ?: "Network error"}")
                }
            }
        }
    }

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
            null
        }
    }
}
