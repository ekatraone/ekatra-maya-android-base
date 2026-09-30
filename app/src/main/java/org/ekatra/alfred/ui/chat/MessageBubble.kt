package org.ekatra.alfred.ui.chat


import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.ekatra.alfred.R

@Composable
fun MessageBubble(
    message: ChatMessage,
    onSave: (ChatMessage) -> Unit,
    onCopy: (String) -> Unit,
    onShare: (ChatMessage) -> Unit,
    onRate: (Long, Int) -> Unit = { _, _ -> },
    onSpeak: (String) -> Unit = {}
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = if (message.isUser) Arrangement.End else Arrangement.Start
        ) {
            if (!message.isUser) {
                Image(
                    painter = painterResource(id = R.drawable.maya_ai),
                    contentDescription = stringResource(R.string.app_name),
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                )
                Spacer(modifier = Modifier.width(8.dp))
            }

            Surface(
                shape = RoundedCornerShape(
                    topStart = 16.dp,
                    topEnd = 16.dp,
                    bottomStart = if (message.isUser) 16.dp else 4.dp,
                    bottomEnd = if (message.isUser) 4.dp else 16.dp
                ),
                color = if (message.isUser) Color(0xFF2563EB) else Color.White,
                shadowElevation = if (message.isUser) 0.dp else 2.dp,
                modifier = Modifier.fillMaxWidth(0.75f)
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (message.isUser || message.text.isEmpty()) {
                        val bubbleText = message.text.ifEmpty { if (message.isStreaming) "..." else "" }
                        Text(
                            text = bubbleText,
                            color = if (message.isUser) Color.White else Color(0xFF1E293B),
                            fontSize = 15.sp,
                            lineHeight = 22.sp,
                            textAlign = TextAlign.Start,
                            style = LocalTextStyle.current.copy(
                                textDirection = if (containsArabicScript(bubbleText)) TextDirection.ContentOrRtl else TextDirection.ContentOrLtr
                            )
                        )
                    } else {
                        SimpleMarkdown(
                            text = if (message.isStreaming) "${message.text}▌" else message.text,
                            color = Color(0xFF1E293B),
                            fontSize = 15.sp,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                    }
                }
            }

            if (message.isUser) {
                Spacer(modifier = Modifier.width(8.dp))
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF22C55E)),
                    contentAlignment = Alignment.Center
                ) {
                }
            }
        }

        if (!message.isUser && !message.isStreaming && message.text.isNotBlank()) {
            // Thumbs up/down rating
            Row(
                modifier = Modifier.padding(start = 40.dp, top = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                IconButton(
                    onClick = { onRate(message.id, if (message.rating == 1) 0 else 1) },
                    modifier = Modifier.size(28.dp)
                ) {
                    Text(
                        "👍",
                        fontSize = 14.sp,
                        color = if (message.rating == 1) Color(0xFF2563EB) else Color(0xFF9CA3AF)
                    )
                }
                IconButton(
                    onClick = { onRate(message.id, if (message.rating == -1) 0 else -1) },
                    modifier = Modifier.size(28.dp)
                ) {
                    Text(
                        "👎",
                        fontSize = 14.sp,
                        color = if (message.rating == -1) Color(0xFFEF4444) else Color(0xFF9CA3AF)
                    )
                }
                // Read aloud button
                IconButton(
                    onClick = { onSpeak(message.text) },
                    modifier = Modifier.size(28.dp)
                ) {
                    Text("🔊", fontSize = 14.sp)
                }
            }

            MessageActions(
                alignStart = true,
                onSave = { onSave(message) },
                onCopy = { onCopy(message.text) },
                onShare = { onShare(message) }
            )
        }
    }
}

// ==================== Helper Functions ====================

/**
 * Get WiFi IP address for hotspot sharing
 */
