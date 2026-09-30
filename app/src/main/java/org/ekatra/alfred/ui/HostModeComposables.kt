package org.ekatra.alfred.ui

import org.ekatra.alfred.R
import androidx.compose.ui.res.stringResource
import android.graphics.Bitmap
import android.os.Build
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.MultiFormatWriter

// ─── QR Code Generator ────────────────────────────────────────────────────────

fun generateQrBitmap(content: String, sizePx: Int): Bitmap {
    val hints = mapOf(EncodeHintType.MARGIN to 1)
    val matrix = MultiFormatWriter().encode(content, BarcodeFormat.QR_CODE, sizePx, sizePx, hints)
    val bmp = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.RGB_565)
    for (x in 0 until sizePx) {
        for (y in 0 until sizePx) {
            bmp.setPixel(x, y, if (matrix[x, y]) android.graphics.Color.BLACK else android.graphics.Color.WHITE)
        }
    }
    return bmp
}

// ─── Host Mode Section (3-step wizard) ─────────────────────────────────────────

@Composable
fun HostModeSection(
    isServerRunning: Boolean,
    isServerLoading: Boolean,
    serverUrl: String,
    onStartServer: () -> Unit,
    onStopServer: () -> Unit,
    onOpenHotspot: () -> Unit
) {
    // Description card
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = Color(0xFFF0F9FF),
        shadowElevation = 0.dp
    ) {
        Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.Top) {
            Text("📡", fontSize = 22.sp)
            Spacer(Modifier.width(10.dp))
            Text(
                stringResource(R.string.host_intro),
                fontSize = 13.sp,
                color = Color(0xFF374151),
                lineHeight = 19.sp
            )
        }
    }

    Spacer(Modifier.height(14.dp))

    // ── Step 1: Enable Hotspot
    StepCard(
        stepNumber = "1",
        title = stringResource(R.string.host_step1_title),
        subtitle = stringResource(R.string.host_step1_subtitle),
        isComplete = false,
        accentColor = Color(0xFF2563EB)
    ) {
        Spacer(Modifier.height(10.dp))
        Button(
            onClick = onOpenHotspot,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(10.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = Color(0xFF2563EB),
                contentColor = Color.White
            )
        ) {
            Text(stringResource(R.string.host_open_hotspot), fontSize = 14.sp)
        }
    }

    Spacer(Modifier.height(12.dp))

    // ── Step 2: Share Wi-Fi
    StepCard(
        stepNumber = "2",
        title = stringResource(R.string.host_step2_title),
        subtitle = stringResource(R.string.host_step2_subtitle),
        isComplete = false,
        accentColor = Color(0xFF7C3AED)
    ) {
        val deviceName = remember { Build.MODEL }
        Spacer(Modifier.height(10.dp))
        Text(
            stringResource(R.string.host_hotspot_name_hint, deviceName),
            fontSize = 12.sp,
            color = Color(0xFF6B7280),
            lineHeight = 17.sp
        )
        Spacer(Modifier.height(8.dp))
        OutlinedButton(
            onClick = onOpenHotspot,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(10.dp),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF7C3AED))
        ) {
            Text(stringResource(R.string.host_change_hotspot), fontSize = 13.sp)
        }
    }

    Spacer(Modifier.height(12.dp))

    // ── Step 3: Start Server
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = Color.White,
        shadowElevation = 2.dp
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(
                            if (isServerRunning) Color(0xFF16A34A)
                            else Color(0xFF608A9A)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        if (isServerRunning) "✓" else "3",
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                Spacer(Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        stringResource(R.string.host_start_server),
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color(0xFF111318)
                    )
                    Text(
                        when {
                            isServerLoading -> stringResource(R.string.host_server_loading)
                            isServerRunning -> stringResource(R.string.host_server_running)
                            else -> stringResource(R.string.host_server_idle)
                        },
                        fontSize = 13.sp,
                        color = when {
                            isServerLoading -> Color(0xFFD97706)
                            isServerRunning -> Color(0xFF16A34A)
                            else -> Color(0xFF6B7280)
                        }
                    )
                }
                if (isServerLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(28.dp),
                        strokeWidth = 3.dp,
                        color = Color(0xFFD97706)
                    )
                } else {
                    Switch(
                        checked = isServerRunning,
                        onCheckedChange = { on -> if (on) onStartServer() else onStopServer() },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = Color(0xFF16A34A)
                        )
                    )
                }
            }

            // Server running: show QR + URL
            if (isServerRunning && serverUrl.isNotBlank()) {
                Spacer(Modifier.height(16.dp))
                HorizontalDivider(color = Color(0xFFF0F1F4))
                Spacer(Modifier.height(16.dp))

                val qrBitmap = remember(serverUrl) {
                    try { generateQrBitmap(serverUrl, 480) } catch (e: Exception) { null }
                }
                if (qrBitmap != null) {
                    Text(
                        stringResource(R.string.host_scan_qr),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color(0xFF374151),
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.Center
                    )
                    Spacer(Modifier.height(10.dp))
                    Image(
                        bitmap = qrBitmap.asImageBitmap(),
                        contentDescription = stringResource(R.string.host_qr_description),
                        modifier = Modifier
                            .size(200.dp)
                            .align(Alignment.CenterHorizontally)
                            .clip(RoundedCornerShape(8.dp))
                    )
                    Spacer(Modifier.height(10.dp))
                }

                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp),
                    color = Color(0xFFDBF2E4)
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Text(stringResource(R.string.host_server_address), fontSize = 11.sp, color = Color(0xFF166534))
                        Text(
                            serverUrl,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFF166534)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StepCard(
    stepNumber: String,
    title: String,
    subtitle: String,
    isComplete: Boolean,
    accentColor: Color,
    content: @Composable ColumnScope.() -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = Color.White,
        shadowElevation = 2.dp
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(if (isComplete) Color(0xFF16A34A) else accentColor),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        if (isComplete) "✓" else stepNumber,
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                Spacer(Modifier.width(10.dp))
                Column {
                    Text(title, fontSize = 15.sp, fontWeight = FontWeight.Medium, color = Color(0xFF111318))
                    Text(subtitle, fontSize = 12.sp, color = Color(0xFF6B7280))
                }
            }
            content()
        }
    }
}
