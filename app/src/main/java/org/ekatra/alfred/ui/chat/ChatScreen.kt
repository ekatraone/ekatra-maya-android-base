package org.ekatra.alfred.ui.chat


import org.ekatra.alfred.persona.ActivePersona
import androidx.compose.ui.res.stringResource
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import android.speech.tts.TextToSpeech
import android.util.Log
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.*
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import java.io.File
import androidx.hilt.navigation.compose.hiltViewModel
import org.ekatra.alfred.viewmodel.ChatViewModel
import org.ekatra.alfred.NativeChatActivity
import org.ekatra.alfred.R
import org.ekatra.alfred.getWifiHotspotIpAddress

private const val CHAT_TAG = "NativeChat"

@Composable
fun EkatraChatTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = Color(0xFF2563EB),
            onPrimary = Color.White,
            surface = Color(0xFFF0F4F8),
            background = Color(0xFFF0F4F8),
        ),
        content = content
    )
}

// ==================== Data Classes ====================


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    modelFile: File,
    onSettingsClick: () -> Unit,
    onHotspotToggle: (Boolean) -> Unit,
    viewModel: ChatViewModel = hiltViewModel()
) {
    val scope = rememberCoroutineScope()
    val uiState by viewModel.uiState.collectAsState()
    val listState = rememberLazyListState()
    val focusManager = LocalFocusManager.current
    val context = LocalContext.current
    val clipboardManager = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    var showLibrary by remember { mutableStateOf(false) }
    var libraryQuery by remember { mutableStateOf("") }
    var librarySubject by remember { mutableStateOf<String?>(null) }
    var feedbackPromptForMessageId by remember { mutableStateOf<Long?>(null) }
    var pendingSuggestedQuestion by remember { mutableStateOf<String?>(null) }

    // Sessions and messages backed by Room flows via ViewModel
    val recentSessions by viewModel.recentSessions.collectAsState(initial = emptyList())
    val sessionMessagesEntities by viewModel.currentMessages.collectAsState(initial = emptyList())

    val persistedMessages = remember(sessionMessagesEntities) {
        var lastUserQuestion: String? = null
        sessionMessagesEntities.map { entity ->
            if (entity.isUser) {
                lastUserQuestion = entity.text
            }
            ChatMessage(
                id = entity.id,
                text = entity.text,
                isUser = entity.isUser,
                isStreaming = false,
                relatedQuestion = if (!entity.isUser) lastUserQuestion else null,
                subject = uiState.selectedSubject,
                rating = entity.rating
            )
        }
    }

    val messages = remember(persistedMessages, uiState.streamingText) {
        if (uiState.streamingText != null) {
            persistedMessages + ChatMessage(
                id = System.currentTimeMillis(),
                text = uiState.streamingText!!,
                isUser = false,
                isStreaming = true
            )
        } else persistedMessages
    }

    val saveAnswer: (ChatMessage) -> Unit = lambda@ { message ->
        if (message.isUser || message.isStreaming || message.text.isBlank()) return@lambda
        val question = message.relatedQuestion
        if (question.isNullOrBlank()) {
            Toast.makeText(context, context.getString(R.string.chat_save_needs_question), Toast.LENGTH_SHORT).show()
            return@lambda
        }
        viewModel.saveAnswer(question, message.text, message.subject)
        Toast.makeText(context, context.getString(R.string.chat_saved), Toast.LENGTH_SHORT).show()
    }

    val copyText: (String) -> Unit = lambda@ { text ->
        if (text.isBlank()) return@lambda
        clipboardManager.setPrimaryClip(ClipData.newPlainText("${ActivePersona.key}_answer", text))
        Toast.makeText(context, context.getString(R.string.chat_copied), Toast.LENGTH_SHORT).show()
    }

    val shareAnswer: (ChatMessage) -> Unit = lambda@ { message ->
        if (message.text.isBlank() || message.isStreaming) return@lambda
        viewModel.shareAnswer(message.subject)
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, message.text)
            putExtra(Intent.EXTRA_SUBJECT, message.relatedQuestion ?: context.getString(R.string.chat_share_subject_default, context.getString(R.string.app_name)))
        }
        context.startActivity(Intent.createChooser(shareIntent, context.getString(R.string.chat_share_answer)))
    }

    val rateMessage: (Long, Int) -> Unit = { messageId, rating ->
        viewModel.rateMessage(messageId, rating)
        if (rating == -1 && viewModel.hasFeedbackConsent()) {
            feedbackPromptForMessageId = messageId
        }
    }

    val speakText: (String) -> Unit = lambda@ { text ->
        if (text.isBlank()) return@lambda
        val activity = context as? NativeChatActivity
        if (activity == null) {
            Toast.makeText(context, context.getString(R.string.chat_tts_unavailable), Toast.LENGTH_SHORT).show()
            return@lambda
        }
        val engine = activity.tts
        if (engine == null) {
            Toast.makeText(context, context.getString(R.string.chat_tts_not_ready), Toast.LENGTH_SHORT).show()
            return@lambda
        }
        if (engine.isSpeaking) {
            engine.stop()
        } else {
            engine.speak(text, TextToSpeech.QUEUE_FLUSH, null, "${ActivePersona.key}_${System.currentTimeMillis()}")
        }
    }

    val askSuggestedQuestion: (String) -> Unit = { question ->
        if (uiState.isGenerating) {
            Toast.makeText(context, context.getString(R.string.chat_wait_for_answer), Toast.LENGTH_SHORT).show()
        } else if (!uiState.modelReady) {
            pendingSuggestedQuestion = question
            viewModel.updateInput(question)
            Toast.makeText(context, context.getString(R.string.chat_will_send_when_ready), Toast.LENGTH_SHORT).show()
        } else {
            if (viewModel.sendMessage(question)) {
                viewModel.updateInput("")
                focusManager.clearFocus()
            }
        }
    }

    LaunchedEffect(uiState.modelReady, uiState.isGenerating, pendingSuggestedQuestion) {
        val question = pendingSuggestedQuestion
        if (!question.isNullOrBlank() && uiState.modelReady && !uiState.isGenerating) {
            if (viewModel.sendMessage(question)) {
                pendingSuggestedQuestion = null
                viewModel.updateInput("")
                focusManager.clearFocus()
            }
        }
    }
    
    // Combined effects for status updates
    LaunchedEffect(uiState.serverRunning, uiState.modelReady, uiState.isLoading) {
        if (uiState.serverRunning && uiState.modelReady) {
            val ip = getWifiHotspotIpAddress(context)
            viewModel.updateStatusForServer(ip)
        }
    }
    
    // Load model only; session is created on first real message to avoid empty chats.
    LaunchedEffect(Unit) {
        viewModel.loadModel(modelFile)
    }
    
    // Auto-scroll when new messages arrive
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }
    
    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = stringResource(R.string.chat_conversations),
                        modifier = Modifier.padding(16.dp),
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp
                    )

                    // New Chat item
                    NavigationDrawerItem(
                        label = { Text(stringResource(R.string.chat_new_chat)) },
                        selected = false,
                        onClick = {
                            scope.launch {
                                viewModel.createNewSession()
                                drawerState.close()
                                Toast.makeText(context, context.getString(R.string.chat_new_chat_created), Toast.LENGTH_SHORT).show()
                            }
                        },
                        colors = NavigationDrawerItemDefaults.colors()
                    )

                    NavigationDrawerItem(
                        label = { Text(stringResource(R.string.chat_library)) },
                        selected = false,
                        onClick = {
                            viewModel.refreshSavedAnswers()
                            showLibrary = true
                            scope.launch { drawerState.close() }
                        },
                        colors = NavigationDrawerItemDefaults.colors()
                    )

                    HorizontalDivider()

                    if (recentSessions.isEmpty()) {
                        Text(
                            text = stringResource(R.string.chat_no_conversations),
                            modifier = Modifier.padding(16.dp),
                            color = Color(0xFF6B7280)
                        )
                    } else {
                        recentSessions.forEach { session ->
                            NavigationDrawerItem(
                                label = {
                                    Column {
                                        Text(session.title.ifBlank { stringResource(R.string.chat_untitled) })
                                        session.subject?.let {
                                            Text(
                                                it,
                                                fontSize = 12.sp,
                                                color = Color(0xFF6B7280)
                                            )
                                        }
                                    }
                                },
                                selected = session.id == uiState.currentSessionId,
                                onClick = {
                                    viewModel.switchSession(session.id, session.subject)
                                    scope.launch { drawerState.close() }
                                }
                            )
                        }
                    }
                }
            }
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFFF0F4F8))
                .navigationBarsPadding()
        ) {

        // Thumbs-down feedback confirmation dialog (opt-in only)
        feedbackPromptForMessageId?.let { msgId ->
            AlertDialog(
                onDismissRequest = { feedbackPromptForMessageId = null },
                title = { Text(stringResource(R.string.chat_feedback_title)) },
                text = {
                    Text(stringResource(R.string.chat_feedback_body))
                },
                confirmButton = {
                    TextButton(onClick = {
                        viewModel.submitFeedback(msgId)
                        Toast.makeText(context, context.getString(R.string.chat_feedback_sent), Toast.LENGTH_SHORT).show()
                        feedbackPromptForMessageId = null
                    }) { Text(stringResource(R.string.chat_send)) }
                },
                dismissButton = {
                    TextButton(onClick = { feedbackPromptForMessageId = null }) {
                        Text(stringResource(R.string.chat_no_thanks))
                    }
                }
            )
        }

        // Header
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.horizontalGradient(
                        colors = listOf(Color(0xFF2563EB), Color(0xFF1E40AF))
                    )
                )
                .statusBarsPadding()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { scope.launch { drawerState.open() } }) {
                        Text("☰", fontSize = 20.sp, color = Color.White)
                    }

                    Spacer(modifier = Modifier.width(4.dp))

                    // Avatar
                    Image(
                        painter = painterResource(id = R.drawable.maya_ai),
                        contentDescription = stringResource(R.string.chat_avatar_description, stringResource(R.string.app_name)),
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                    )
                    
                    Spacer(modifier = Modifier.width(12.dp))
                    
                    Column {
                        Text(
                            text = stringResource(R.string.app_name),
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp
                        )
                        val statusLabel = chatStatusLabel(uiState)
                        val subtitle = when {
                            uiState.isGenerating -> stringResource(R.string.status_typing)
                            uiState.streakDays > 0 -> stringResource(R.string.chat_streak_suffix, statusLabel, uiState.streakDays)
                            else -> statusLabel
                        }
                        Text(
                            text = subtitle,
                            color = Color.White.copy(alpha = 0.8f),
                            fontSize = 12.sp
                        )
                    }
                }
                
                // Action Buttons Row
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Hotspot Info/Toggle
                    IconButton(
                        onClick = { 
                            if (!uiState.serverRunning) {
                                viewModel.toggleServer(true)
                                onHotspotToggle(true)
                                val ip = getWifiHotspotIpAddress(context)
                                Toast.makeText(
                                    context,
                                    context.getString(R.string.chat_server_started, ip),
                                    Toast.LENGTH_LONG
                                ).show()
                            } else {
                                viewModel.toggleServer(false)
                                onHotspotToggle(false)
                                Toast.makeText(context, context.getString(R.string.chat_server_stopped), Toast.LENGTH_SHORT).show()
                            }
                        }
                    ) {
                        Text(
                            if (uiState.serverRunning) "📡" else "📴",
                            fontSize = 20.sp
                        )
                    }
                    
                    // Settings Button
                    IconButton(onClick = onSettingsClick) {
                        Text("⚙️", fontSize = 20.sp)
                    }
                }
            }
        }
        
        // Update available banner
        val updateInfo = uiState.updateInfo
        if (updateInfo != null && updateInfo.isUpdateAvailable && !uiState.updateDismissed) {
            UpdateBanner(
                updateInfo = updateInfo,
                onDismiss = { viewModel.dismissUpdateBanner() },
                onUpdate = {
                    // Open Play Store or download page
                    try {
                        val intent = Intent(Intent.ACTION_VIEW,
                            android.net.Uri.parse("market://details?id=${context.packageName}"))
                        context.startActivity(intent)
                    } catch (e: Exception) {
                        val intent = Intent(Intent.ACTION_VIEW,
                            android.net.Uri.parse("https://play.google.com/store/apps/details?id=${context.packageName}"))
                        context.startActivity(intent)
                    }
                }
            )
        }

        // Maintenance message banner
        // (RemoteConfigManager.maintenanceMessage can be set from Firebase Console)

        // Loading overlay
        if (uiState.isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xFFF0F4F8)),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Simple loading dots instead of CircularProgressIndicator
                    Text(
                        "⏳",
                        fontSize = 48.sp,
                        modifier = Modifier.padding(bottom = 16.dp)
                    )
                    Text(chatStatusLabel(uiState), color = Color(0xFF64748B))
                }
            }
        } else {
            if (uiState.dailyQuestion.isNotBlank()) {
                DailyQuestionCard(
                    question = uiState.dailyQuestion,
                    onAsk = askSuggestedQuestion
                )
            }

            SubjectChipsRow(
                selectedSubject = uiState.selectedSubject,
                onSubjectSelected = { subject ->
                    Log.d(CHAT_TAG, "Subject chip tapped: $subject")
                    viewModel.selectSubject(subject)
                    Log.d(CHAT_TAG, "Subject now ${uiState.selectedSubject ?: "none"}")
                }
            )

            // Chat messages
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(vertical = 16.dp)
            ) {
                // Welcome message if empty
                if (messages.isEmpty()) {
                    item {
                        WelcomeCard(
                            onQuestionClick = askSuggestedQuestion
                        )
                    }
                }
                
                items(messages, key = { it.id }) { message ->
                    MessageBubble(
                        message = message,
                        onSave = saveAnswer,
                        onCopy = copyText,
                        onShare = shareAnswer,
                        onRate = rateMessage,
                        onSpeak = speakText
                    )
                }
            }
            
            // Input area; imePadding keeps it above the soft keyboard
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .imePadding(),
                shadowElevation = 8.dp,
                color = Color.White
            ) {
                Row(
                    modifier = Modifier
                        .padding(12.dp)
                        .fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = uiState.inputText,
                        onValueChange = { viewModel.updateInput(it) },
                        modifier = Modifier.weight(1f),
                        placeholder = {
                            Text(
                                uiState.selectedSubject?.let { stringResource(R.string.chat_input_hint_subject, it) } ?: stringResource(R.string.chat_input_hint)
                            )
                        },
                        enabled = uiState.modelReady && !uiState.isGenerating,
                        shape = RoundedCornerShape(24.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color(0xFF2563EB),
                            unfocusedBorderColor = Color(0xFFE2E8F0)
                        ),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                        keyboardActions = KeyboardActions(
                            onSend = {
                                if (uiState.inputText.isNotBlank() && uiState.modelReady && !uiState.isGenerating) {
                                    val userMsg = uiState.inputText.trim()
                                    if (viewModel.sendMessage(userMsg)) {
                                        viewModel.updateInput("")
                                        focusManager.clearFocus()
                                    }
                                }
                            }
                        ),
                        singleLine = true
                    )
                    
                    Spacer(modifier = Modifier.width(8.dp))

                    if (uiState.isGenerating) {
                        OutlinedButton(
                            onClick = { viewModel.stopGeneration() },
                            shape = RoundedCornerShape(24.dp),
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp)
                        ) {
                            Text(stringResource(R.string.chat_stop))
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                    
                    Button(
                        onClick = {
                            if (uiState.inputText.isNotBlank() && uiState.modelReady && !uiState.isGenerating) {
                                val userMsg = uiState.inputText.trim()
                                if (viewModel.sendMessage(userMsg)) {
                                    viewModel.updateInput("")
                                    focusManager.clearFocus()
                                }
                            }
                        },
                        enabled = uiState.modelReady && !uiState.isGenerating && uiState.inputText.isNotBlank(),
                        shape = CircleShape,
                        contentPadding = PaddingValues(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF2563EB)
                        )
                    ) {
                        Text("→", fontSize = 20.sp)
                    }
                }
            }
        }

        if (showLibrary) {
            SavedLibraryDialog(
                allAnswers = uiState.savedAnswers,
                query = libraryQuery,
                subject = librarySubject,
                onQueryChange = { libraryQuery = it },
                onSubjectChange = { librarySubject = it },
                onFavoriteToggle = { answerId -> viewModel.toggleFavoriteSavedAnswer(answerId) },
                onShare = { text -> shareText(context, text) },
                onDismiss = { showLibrary = false }
            )
        }
    }
}

}
