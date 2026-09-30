package org.ekatra.alfred.ui.chat


import org.ekatra.alfred.viewmodel.ChatStatus
import org.ekatra.alfred.persona.ActivePersona
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.res.stringResource
import android.content.Context
import android.content.Intent
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.ekatra.alfred.data.model.SavedAnswer
import org.ekatra.alfred.notification.AppUpdateChecker
import org.ekatra.alfred.viewmodel.ChatUiState
import org.ekatra.alfred.R

@Composable
fun MessageActions(
    alignStart: Boolean,
    onSave: () -> Unit,
    onCopy: () -> Unit,
    onShare: () -> Unit
) {
    Row(
        modifier = Modifier.padding(start = if (alignStart) 40.dp else 0.dp, top = 6.dp, end = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        ActionChip(label = stringResource(R.string.chat_save), onClick = onSave)
        ActionChip(label = stringResource(R.string.chat_copy), onClick = onCopy)
        ActionChip(label = stringResource(R.string.chat_share), onClick = onShare)
    }
}

@Composable
fun ActionChip(label: String, onClick: () -> Unit) {
    Surface(
        modifier = Modifier.clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        color = Color(0xFFF1F5F9),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE2E8F0))
    ) {
        Text(
            text = label,
            fontSize = 12.sp,
            color = Color(0xFF475569),
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
        )
    }
}

// ==================== Update Banner ====================

@Composable
fun UpdateBanner(
    updateInfo: AppUpdateChecker.UpdateInfo,
    onDismiss: () -> Unit,
    onUpdate: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = if (updateInfo.isForceUpdate) Color(0xFFFEE2E2) else Color(0xFFEFF6FF),
        shadowElevation = 2.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = if (updateInfo.isForceUpdate) "⚠️" else "🆕",
                fontSize = 20.sp
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (updateInfo.isForceUpdate) stringResource(R.string.chat_update_required) else stringResource(R.string.chat_update_available),
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp,
                    color = if (updateInfo.isForceUpdate) Color(0xFFDC2626) else Color(0xFF1E40AF)
                )
                Text(
                    text = stringResource(R.string.chat_update_body, updateInfo.requiredVersion.toString(), updateInfo.currentVersion.toString()),
                    fontSize = 12.sp,
                    color = Color(0xFF6B7280)
                )
            }
            Spacer(Modifier.width(8.dp))
            Surface(
                modifier = Modifier.clickable(onClick = onUpdate),
                shape = RoundedCornerShape(8.dp),
                color = if (updateInfo.isForceUpdate) Color(0xFFDC2626) else Color(0xFF2563EB)
            ) {
                Text(
                    stringResource(R.string.chat_update_action),
                    color = Color.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                )
            }
            if (!updateInfo.isForceUpdate) {
                IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
                    Text("✕", fontSize = 16.sp, color = Color(0xFF9CA3AF))
                }
            }
        }
    }
}

// ==================== Theme ====================

@Composable
fun DailyQuestionCard(
    question: String,
    onAsk: (String) -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFFFFBEB)),
        shape = RoundedCornerShape(14.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    stringResource(R.string.chat_daily_question),
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFF92400E),
                    fontSize = 13.sp,
                    maxLines = 1
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    question,
                    color = Color(0xFF78350F),
                    fontSize = 12.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            TextButton(onClick = { onAsk(question) }) {
                Text(stringResource(R.string.chat_ask), maxLines = 1)
            }
        }
    }
}

@Composable
fun SavedLibraryDialog(
    allAnswers: List<SavedAnswer>,
    query: String,
    subject: String?,
    onQueryChange: (String) -> Unit,
    onSubjectChange: (String?) -> Unit,
    onFavoriteToggle: (Long) -> Unit,
    onShare: (String) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.chat_close)) }
        },
        title = { Text(stringResource(R.string.chat_library_title)) },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = query,
                    onValueChange = onQueryChange,
                    label = { Text(stringResource(R.string.chat_search)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val allLabel = stringResource(R.string.chat_filter_all)
                    (listOf(allLabel) + ActivePersona.subjectSuggestions).forEach { chip ->
                        val selected = (chip == allLabel && subject == null) || chip.equals(subject, ignoreCase = true)
                        Surface(
                            modifier = Modifier.clickable {
                                onSubjectChange(if (chip == allLabel) null else chip)
                            },
                            shape = RoundedCornerShape(20.dp),
                            color = if (selected) Color(0xFFDBEAFE) else Color(0xFFF8FAFC),
                            border = androidx.compose.foundation.BorderStroke(1.dp, if (selected) Color(0xFF2563EB) else Color(0xFFE2E8F0))
                        ) {
                            Text(
                                chip,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                fontSize = 12.sp
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                val filtered = allAnswers.filter { answer ->
                    val subjectOk = subject == null || answer.subject.equals(subject, ignoreCase = true)
                    val queryOk = query.isBlank() ||
                        answer.answer.contains(query, ignoreCase = true) ||
                        answer.question.contains(query, ignoreCase = true)
                    subjectOk && queryOk
                }

                if (filtered.isEmpty()) {
                    Text(stringResource(R.string.chat_library_empty), color = Color(0xFF6B7280), fontSize = 12.sp)
                } else {
                    LazyColumn(modifier = Modifier.heightIn(max = 320.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(filtered, key = { it.id }) { answer ->
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = Color(0xFFF8FAFC),
                                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE2E8F0))
                            ) {
                                Column(modifier = Modifier.fillMaxWidth().padding(10.dp)) {
                                    Text(stringResource(R.string.chat_q_prefix, answer.question), fontWeight = FontWeight.SemiBold, fontSize = 12.sp, color = Color(0xFF334155))
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(answer.answer.take(220), fontSize = 12.sp, color = Color(0xFF0F172A))
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        TextButton(onClick = { onFavoriteToggle(answer.id) }) {
                                            Text(if (answer.isFavorite) "★" else "☆")
                                        }
                                        val shareBody = stringResource(R.string.chat_qa_share, answer.question, answer.answer)
                                        TextButton(onClick = { onShare(shareBody) }) { Text(stringResource(R.string.chat_share)) }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    )
}

internal fun shareText(context: Context, text: String) {
    val shareIntent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, text)
    }
    context.startActivity(Intent.createChooser(shareIntent, context.getString(R.string.chat_share)))
}

@Composable
internal fun chatStatusLabel(state: ChatUiState): String = when (state.status) {
    ChatStatus.LOADING_MODEL -> stringResource(R.string.status_loading_model)
    ChatStatus.READY -> stringResource(R.string.status_ready)
    ChatStatus.LOAD_FAILED -> stringResource(R.string.status_load_failed)
    ChatStatus.THINKING -> stringResource(R.string.status_thinking)
    ChatStatus.TYPING -> stringResource(R.string.status_typing)
    ChatStatus.NO_RESPONSE -> stringResource(R.string.status_no_response)
    ChatStatus.SERVING -> stringResource(R.string.status_serving, state.serverAddress.orEmpty())
}

// ==================== Components ====================

@Composable
fun WelcomeCard(onQuestionClick: (String) -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Image(
                painter = painterResource(id = R.drawable.maya_ai),
                contentDescription = stringResource(R.string.app_name),
                modifier = Modifier
                    .size(80.dp)
                    .clip(CircleShape)
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = stringResource(R.string.chat_welcome_title),
                fontWeight = FontWeight.Bold,
                fontSize = 20.sp,
                color = Color(0xFF1E293B)
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.chat_welcome_body),
                color = Color(0xFF64748B),
                fontSize = 14.sp,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
            
            Spacer(modifier = Modifier.height(20.dp))
            
            // Example Questions
            ActivePersona.exampleQuestions.forEach { question ->
                QuestionChip(
                    text = question,
                    onClick = { onQuestionClick(question) }
                )
                Spacer(modifier = Modifier.height(8.dp))
            }
        }
    }
}

@Composable
fun QuestionChip(text: String, onClick: () -> Unit) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        color = Color(0xFFF0F4F8),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE2E8F0))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "💡",
                fontSize = 16.sp
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = text,
                fontSize = 14.sp,
                color = Color(0xFF475569)
            )
        }
    }
}

@Composable
fun SubjectChipsRow(
    selectedSubject: String?,
    onSubjectSelected: (String) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        ActivePersona.subjectSuggestions.forEach { subject ->
            val isSelected = selectedSubject == subject
            Surface(
                modifier = Modifier.clickable { onSubjectSelected(subject) },
                shape = RoundedCornerShape(50),
                color = if (isSelected) Color(0xFFDBEAFE) else Color(0xFFF8FAFC),
                border = androidx.compose.foundation.BorderStroke(
                    1.dp,
                    if (isSelected) Color(0xFF2563EB) else Color(0xFFE2E8F0)
                )
            ) {
                Text(
                    text = subject,
                    color = if (isSelected) Color(0xFF1E3A8A) else Color(0xFF475569),
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}
