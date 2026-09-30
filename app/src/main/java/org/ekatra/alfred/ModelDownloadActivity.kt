package org.ekatra.alfred

import org.ekatra.alfred.ui.PersonaDirection
import androidx.compose.ui.res.stringResource
import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.lifecycleScope
import androidx.work.WorkInfo
import androidx.work.WorkManager
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.ekatra.alfred.data.AppSettings
import org.ekatra.alfred.data.ModelConfig
import org.ekatra.alfred.worker.ModelDownloadWorker
import java.io.File

@AndroidEntryPoint
class ModelDownloadActivity : ComponentActivity() {
    
    private val settings by lazy { AppSettings(this) }
    private val selectedModel by lazy { settings.getSelectedModel() }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        var modelToUse = selectedModel
        var modelFile = File(filesDir, "models/${modelToUse.filename}")

        if (!modelFile.exists()) {
            val recovered = settings.recoverDownloadedModel(this)
            if (recovered != null && File(filesDir, "models/${recovered.filename}").exists()) {
                Log.d("ModelDownload", "Recovered existing model: ${recovered.filename}")
                modelToUse = recovered
                modelFile = File(filesDir, "models/${modelToUse.filename}")
            }
        }

        setContent {
            PersonaDirection {
                var isVerifying by remember { mutableStateOf(false) }
                val verificationFailed = remember { mutableStateOf(false) }

                LaunchedEffect(Unit) {
                    if (modelFile.exists() && modelFile.length() > 0) {
                        val lastMod = modelFile.lastModified()
                        val size = modelFile.length()
                    
                        if (settings.isModelVerified(modelToUse.id, lastMod, size)) {
                            Log.d("ModelDownload", "Model already verified, skipping to chat")
                            startActivity(Intent(this@ModelDownloadActivity, NativeChatActivity::class.java))
                            finish()
                            return@LaunchedEffect
                        }

                        // Needs verification
                        isVerifying = true
                        Log.d("ModelDownload", "Starting model verification...")
                        val isValid = ModelVerifier.verifyModel(modelFile, modelToUse.expectedSha256)
                    
                        if (isValid) {
                            Log.d("ModelDownload", "Model verification passed")
                            settings.setModelVerified(modelToUse.id, modelFile.lastModified(), modelFile.length())
                            startActivity(Intent(this@ModelDownloadActivity, NativeChatActivity::class.java))
                            finish()
                        } else {
                            Log.e("ModelDownload", "Model verification failed! Deleting model.")
                            modelFile.delete()
                            settings.clearModelVerification(modelToUse.id)
                            isVerifying = false
                            verificationFailed.value = true
                            ModelDownloadWorker.enqueue(this@ModelDownloadActivity, modelToUse.url, modelToUse.filename, modelToUse.id)
                        }
                    } else {
                        // No model file, start download
                        ModelDownloadWorker.enqueue(this@ModelDownloadActivity, modelToUse.url, modelToUse.filename, modelToUse.id)
                    }
                }

                EkatraDownloadTheme {
                    // Observe WorkManager progress
                    val workInfos = WorkManager.getInstance(applicationContext)
                        .getWorkInfosForUniqueWorkLiveData(ModelDownloadWorker.WORK_NAME)
                        .observeAsState()

                    val localModelExists = modelFile.exists() && modelFile.length() > 0
                    val infos = workInfos.value.orEmpty()
                    val workInfo = infos.firstOrNull { it.state == WorkInfo.State.RUNNING || it.state == WorkInfo.State.ENQUEUED }
                        ?: infos.firstOrNull { it.state == WorkInfo.State.SUCCEEDED }
                        ?: infos.firstOrNull()
                    val progressData = workInfo?.progress
                    val baseProgressPercent = progressData?.getInt(ModelDownloadWorker.KEY_PROGRESS, 0) ?: 0
                    val statusFromWorker = progressData?.getString(ModelDownloadWorker.KEY_STATUS) ?: stringResource(R.string.download_starting)
                    val downloadedBytes = progressData?.getLong(ModelDownloadWorker.KEY_DOWNLOADED_BYTES, 0L) ?: 0L
                    val totalBytes = progressData?.getLong(ModelDownloadWorker.KEY_TOTAL_BYTES, 0L) ?: 0L

                    val isRunning = workInfo?.state == WorkInfo.State.RUNNING || workInfo?.state == WorkInfo.State.ENQUEUED
                    val isFailed = workInfo?.state == WorkInfo.State.FAILED
                    val hasWorkerIntegrityFailure = isFailed &&
                        workInfo?.outputData?.getString(ModelDownloadWorker.KEY_ERROR_TYPE) == ModelDownloadWorker.ERROR_INTEGRITY
                    val isIntegrityError = verificationFailed.value ||
                        (!isVerifying && !localModelExists && hasWorkerIntegrityFailure)
                    val isSucceeded = workInfo?.state == WorkInfo.State.SUCCEEDED || baseProgressPercent >= 100

                    // Navigate when complete
                    LaunchedEffect(isSucceeded) {
                        if (isSucceeded) {
                            // After download succeeds, worker has already verifyFileHash!
                            // So we can assume it's good, mark it verified and launch.
                            val freshFile = File(filesDir, "models/${modelToUse.filename}")
                            if (freshFile.exists()) {
                                settings.setModelVerified(modelToUse.id, freshFile.lastModified(), freshFile.length())
                            }
                            delay(500)
                            startActivity(Intent(this@ModelDownloadActivity, NativeChatActivity::class.java))
                            finish()
                        }
                    }

                    // If we are currently showing the verifying step before any work kicks in
                    val displayProgress = if (isVerifying) 1f else (baseProgressPercent / 100f)
                    val displayStatus = if (isVerifying) stringResource(R.string.download_verifying) else statusFromWorker
                
                    val downloadedMB = downloadedBytes / 1_000_000
                    val totalMB = totalBytes / 1_000_000
                    val sizeInfo = if (isVerifying) "" else if (totalMB > 0) stringResource(R.string.download_size_progress, downloadedMB.toInt(), totalMB.toInt()) else if (downloadedMB > 0) stringResource(R.string.download_size_done, downloadedMB.toInt()) else ""

                    DownloadScreen(
                        progress = displayProgress,
                        statusText = displayStatus,
                        sizeInfo = sizeInfo,
                        showRetry = isFailed && !isIntegrityError,
                        showIntegrityError = isIntegrityError,
                        isVerifying = isVerifying,
                        onRetry = {
                            ModelDownloadWorker.enqueue(
                                this@ModelDownloadActivity,
                                modelToUse.url,
                                modelToUse.filename,
                                modelToUse.id
                            )
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun EkatraDownloadTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = Color(0xFF2762EC),
            background = Color(0xFFF6F6F8),
            surface = Color.White
        ),
        content = content
    )
}

@Composable
fun DownloadScreen(
    progress: Float,
    statusText: String,
    sizeInfo: String = "",
    showRetry: Boolean = false,
    showIntegrityError: Boolean = false,
    isVerifying: Boolean = false,
    onRetry: () -> Unit = {}
) {
    val screenWidth = LocalConfiguration.current.screenWidthDp
    val screenHeight = LocalConfiguration.current.screenHeightDp
    val horizontalPadding = if (screenWidth < 360) 16.dp else 24.dp
    val illustrationHeight = (screenHeight * 0.3f).coerceIn(160f, 340f).dp
    val avatarSize = (screenWidth * 0.4f).coerceIn(100f, 200f).dp
    val titleSize = if (screenWidth < 360) 20.sp else 24.sp

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF6F6F8))
            .systemBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = horizontalPadding, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFF2762EC).copy(alpha = 0.1f)),
                    contentAlignment = Alignment.Center
                ) {
                    Text("📘", fontSize = 18.sp)
                }
                Text(
                    stringResource(R.string.download_brand),
                    fontSize = if (screenWidth < 360) 14.sp else 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF111318)
                )
            }
        }
        
        Spacer(modifier = Modifier.height(if (screenHeight < 700) 16.dp else 32.dp))
        
        // Asmaa AI illustration — responsive height
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(illustrationHeight)
                .clip(RoundedCornerShape(24.dp))
                .background(
                    Brush.verticalGradient(
                        listOf(
                            Color(0xFF2762EC).copy(alpha = 0.05f),
                            Color(0xFF2762EC).copy(alpha = 0.02f)
                        )
                    )
                ),
            contentAlignment = Alignment.Center
        ) {
            Image(
                painter = painterResource(id = R.drawable.maya_ai),
                contentDescription = stringResource(R.string.app_name),
                modifier = Modifier
                    .size(avatarSize)
                    .clip(RoundedCornerShape(avatarSize / 2)),
                contentScale = ContentScale.Crop
            )
        }
        
        Spacer(modifier = Modifier.height(if (screenHeight < 700) 16.dp else 32.dp))
        
        // Title Section
        Text(
            stringResource(R.string.download_setting_up),
            fontSize = titleSize,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF111318)
        )
        
        Spacer(modifier = Modifier.height(8.dp))
        
        Text(
            stringResource(R.string.download_tagline),
            fontSize = 13.sp,
            color = Color(0xFF6B7280),
            modifier = Modifier.padding(horizontal = if (screenWidth < 360) 16.dp else 32.dp),
            lineHeight = 18.sp,
            textAlign = TextAlign.Center
        )
        
        Spacer(modifier = Modifier.height(if (screenHeight < 700) 16.dp else 32.dp))
        
        // Progress Card
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            color = Color.White,
            shadowElevation = 2.dp
        ) {
            Column(
                modifier = Modifier.padding(20.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Bottom
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            when {
                                showIntegrityError -> stringResource(R.string.download_state_integrity)
                                showRetry -> stringResource(R.string.download_state_paused)
                                else -> stringResource(R.string.download_state_downloading)
                            },
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = if (showIntegrityError) Color(0xFFEF4444) else Color(0xFF111318)
                        )
                        if (showIntegrityError) {
                            Text(
                                stringResource(R.string.download_integrity_body),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                                color = Color(0xFFEF4444)
                            )
                        } else if (!showRetry) {
                            AnimatedStatusText(statusText)
                        } else {
                            Text(
                                statusText,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                                color = Color(0xFFEF4444)
                            )
                        }
                        if (sizeInfo.isNotBlank()) {
                            Text(
                                sizeInfo,
                                fontSize = 11.sp,
                                color = Color(0xFF9CA3AF),
                                modifier = Modifier.padding(top = 2.dp)
                            )
                        }
                    }
                    
                    if (!isVerifying) {
                        Text(
                            "${(progress * 100).toInt()}%",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF111318)
                        )
                    } else {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            color = Color(0xFF2762EC),
                            strokeWidth = 2.dp
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // Progress Bar
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(12.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color(0xFFE5E7EB))
                ) {
                    if (isVerifying) {
                        val infiniteTransition = rememberInfiniteTransition()
                        val offsetX by infiniteTransition.animateFloat(
                            initialValue = -1f,
                            targetValue = 2f,
                            animationSpec = infiniteRepeatable(
                                animation = tween(1500, easing = LinearEasing),
                                repeatMode = RepeatMode.Restart
                            )
                        )
                        Box(
                            modifier = Modifier
                                .fillMaxHeight()
                                .fillMaxWidth(0.3f)
                                .offset(x = 100.dp * offsetX)
                                .background(Color(0xFF2762EC))
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .fillMaxHeight()
                                .fillMaxWidth(progress.coerceIn(0f, 1f))
                                .background(if (showRetry) Color(0xFFFBBF24) else Color(0xFF2762EC))
                        )
                    }
                    
                    // Shine effect
                    if (!showRetry && progress < 1f) {
                        ShineEffect()
                    }
                }
                
                // Retry button when download fails
                if (showRetry) {
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        stringResource(R.string.download_paused_body),
                        fontSize = 12.sp,
                        color = Color(0xFF6B7280),
                        lineHeight = 16.sp
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Button(
                        onClick = onRetry,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF2762EC)
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(stringResource(R.string.download_retry), fontSize = 15.sp)
                    }
                }
            }
        }
        
        Spacer(modifier = Modifier.height(24.dp))
        
        Text(
            stringResource(R.string.download_background_note),
            fontSize = 12.sp,
            color = Color(0xFF059669),
            lineHeight = 18.sp,
            textAlign = TextAlign.Center
        )
        
        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
fun AnimatedStatusText(text: String) {
    var dots by remember { mutableStateOf("") }
    
    LaunchedEffect(Unit) {
        while (true) {
            dots = when (dots.length) {
                0 -> "."
                1 -> ".."
                2 -> "..."
                else -> ""
            }
            delay(500)
        }
    }
    
    Text(
        "$text$dots",
        fontSize = 12.sp,
        fontWeight = FontWeight.Medium,
        color = Color(0xFF2762EC)
    )
}

@Composable
fun ShineEffect() {
    val infiniteTransition = rememberInfiniteTransition(label = "shine")
    val offsetX by infiniteTransition.animateFloat(
        initialValue = -1f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "shineOffset"
    )
    
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.horizontalGradient(
                    colors = listOf(
                        Color.Transparent,
                        Color.White.copy(alpha = 0.3f),
                        Color.Transparent
                    ),
                    startX = offsetX * 1000f,
                    endX = (offsetX + 0.5f) * 1000f
                )
            )
    )
}
