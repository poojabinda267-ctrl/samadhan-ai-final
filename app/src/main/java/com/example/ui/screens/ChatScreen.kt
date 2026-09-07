package com.example.ui.screens

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.example.data.model.ChatMessageEntity
import com.example.ui.components.ChatInputField
import com.example.ui.components.LiveVoiceConversationDialog
import com.example.ui.components.MarkdownText
import com.example.ui.components.SettingsDialog
import com.example.ui.components.VoiceAssistantBar
import com.example.ui.i18n.appStrings
import com.example.ui.theme.AccentUserBubbleLight
import com.example.voice.VoiceAssistantState
import com.example.voice.VoiceProviderType
import kotlinx.coroutines.launch
import androidx.compose.material.icons.filled.GraphicEq

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    viewModel: ChatViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val strings = appStrings()
    val coroutineScope = rememberCoroutineScope()
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)

    val currentUser by viewModel.currentUser.collectAsStateWithLifecycle()
    val conversations by viewModel.conversations.collectAsStateWithLifecycle()
    val activeConversationId by viewModel.activeConversationId.collectAsStateWithLifecycle()
    val messages by viewModel.messages.collectAsStateWithLifecycle()
    val inputText by viewModel.inputText.collectAsStateWithLifecycle()
    val selectedImageUri by viewModel.selectedImageUri.collectAsStateWithLifecycle()
    val isImageMode by viewModel.isImageMode.collectAsStateWithLifecycle()
    val isGenerating by viewModel.isGenerating.collectAsStateWithLifecycle()
    val selectedModel by viewModel.selectedModel.collectAsStateWithLifecycle()
    val customApiKey by viewModel.customApiKey.collectAsStateWithLifecycle()
    val appLanguage by viewModel.appLanguage.collectAsStateWithLifecycle()

    // Voice conversation & dictation states
    val isDictating by viewModel.isDictating.collectAsStateWithLifecycle()
    val isLiveVoiceDialogOpen by viewModel.isLiveVoiceDialogOpen.collectAsStateWithLifecycle()
    val lastAiResponse by viewModel.lastAiResponse.collectAsStateWithLifecycle()
    val voiceState by viewModel.voiceState.collectAsStateWithLifecycle()
    val isSpeaking by viewModel.isSpeaking.collectAsStateWithLifecycle()
    val rmsDb by viewModel.rmsDb.collectAsStateWithLifecycle()
    val liveSpokenText by viewModel.liveSpokenText.collectAsStateWithLifecycle()
    val voiceErrorMessage by viewModel.voiceErrorMessage.collectAsStateWithLifecycle()
    val isContinuousMode by viewModel.isContinuousVoiceMode.collectAsStateWithLifecycle()
    val isTtsEnabled by viewModel.isTtsEnabled.collectAsStateWithLifecycle()
    val activeVoiceProvider by viewModel.activeVoiceProvider.collectAsStateWithLifecycle()
    val activeProviderLabel by viewModel.activeProviderLabel.collectAsStateWithLifecycle()
    val lastFallbackReason by viewModel.lastFallbackReason.collectAsStateWithLifecycle()

    var showSettingsDialog by remember { mutableStateOf(false) }

    // Media Picker for image attachments (Google Play compliant photo picker)
    val mediaPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        viewModel.setSelectedImageUri(uri)
    }

    // Permission launcher for dictation microphone
    val dictationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            viewModel.startDictation()
        } else {
            Toast.makeText(
                context,
                strings.micPermissionRequired,
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    // Permission launcher for Live Voice Conversation
    val liveVoicePermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            viewModel.openLiveVoiceConversation()
        } else {
            Toast.makeText(
                context,
                strings.micPermissionRequired,
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    val listState = rememberLazyListState()

    // Auto-scroll on new message
    LaunchedEffect(messages.size, messages.lastOrNull()?.content?.length) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    // Live Voice Conversation Dialog (Feature 2)
    if (isLiveVoiceDialogOpen) {
        LiveVoiceConversationDialog(
            voiceState = voiceState,
            liveSpokenText = liveSpokenText,
            lastAiResponse = lastAiResponse,
            rmsDb = rmsDb,
            isSpeaking = isSpeaking,
            isTtsEnabled = isTtsEnabled,
            errorMessage = voiceErrorMessage,
            activeVoiceProvider = activeVoiceProvider,
            activeProviderLabel = activeProviderLabel,
            lastFallbackReason = lastFallbackReason,
            onToggleTts = { viewModel.toggleTtsEnabled() },
            onEndConversation = { viewModel.closeLiveVoiceConversation() }
        )
    }

    if (showSettingsDialog) {
        SettingsDialog(
            currentLanguage = appLanguage,
            onLanguageSelected = { viewModel.setAppLanguage(it) },
            selectedModel = selectedModel,
            onModelSelected = { viewModel.setSelectedModel(it) },
            customApiKey = customApiKey,
            onApiKeyChanged = { viewModel.setCustomApiKey(it) },
            onClearAllHistory = { viewModel.clearAllHistory() },
            onDismiss = { showSettingsDialog = false }
        )
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet(
                drawerContainerColor = MaterialTheme.colorScheme.surface
            ) {
                ChatDrawerContent(
                    conversations = conversations,
                    activeConversationId = activeConversationId,
                    currentUser = currentUser,
                    onSelectConversation = { convId ->
                        viewModel.selectConversation(convId)
                        coroutineScope.launch { drawerState.close() }
                    },
                    onNewChatClicked = {
                        viewModel.startNewChat()
                        coroutineScope.launch { drawerState.close() }
                    },
                    onDeleteConversation = { convId ->
                        viewModel.deleteConversation(convId)
                    },
                    onOpenSettings = {
                        showSettingsDialog = true
                        coroutineScope.launch { drawerState.close() }
                    },
                    onSignOut = {
                        viewModel.signOut()
                        coroutineScope.launch { drawerState.close() }
                    }
                )
            }
        }
    ) {
        Scaffold(
            modifier = modifier.fillMaxSize(),
            containerColor = MaterialTheme.colorScheme.background,
            topBar = {
                Column {
                    TopAppBar(
                        title = {
                            Text(
                                text = "SAMADHAN AI",
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 17.sp,
                                    letterSpacing = 0.5.sp
                                ),
                                color = MaterialTheme.colorScheme.onBackground
                            )
                        },
                        navigationIcon = {
                            IconButton(
                                onClick = { coroutineScope.launch { drawerState.open() } },
                                modifier = Modifier.testTag("drawer_toggle_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Menu,
                                    contentDescription = strings.openMenu,
                                    tint = MaterialTheme.colorScheme.onBackground
                                )
                            }
                        },
                        actions = {
                            // Speaker / Voice response toggle
                            IconButton(
                                onClick = {
                                    viewModel.toggleTtsEnabled()
                                    val msg = if (!isTtsEnabled) strings.voiceResponsesOnToast else strings.voiceResponsesMutedToast
                                    Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                                },
                                modifier = Modifier.testTag("top_speaker_toggle_button")
                            ) {
                                Icon(
                                    imageVector = if (isTtsEnabled) Icons.AutoMirrored.Filled.VolumeUp else Icons.AutoMirrored.Filled.VolumeOff,
                                    contentDescription = if (isTtsEnabled) strings.muteVoice else strings.enableVoice,
                                    tint = if (isTtsEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f)
                                )
                            }

                            // New Chat button
                            IconButton(
                                onClick = { viewModel.startNewChat() },
                                modifier = Modifier.testTag("top_new_chat_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Add,
                                    contentDescription = strings.newChatButton,
                                    tint = MaterialTheme.colorScheme.onBackground
                                )
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = MaterialTheme.colorScheme.background
                        )
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(1.dp)
                            .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    )

                    // Voice Provider Indicator (e.g. ElevenLabs playback)
                    AnimatedVisibility(
                        visible = activeVoiceProvider != VoiceProviderType.NONE,
                        enter = fadeIn() + expandVertically(),
                        exit = fadeOut() + shrinkVertically()
                    ) {
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 6.dp)
                                .testTag("voice_provider_indicator"),
                            color = if (activeVoiceProvider == VoiceProviderType.ELEVENLABS)
                                Color(0xFF10B981).copy(alpha = 0.12f)
                            else
                                Color(0xFFF59E0B).copy(alpha = 0.12f),
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(
                                1.dp,
                                if (activeVoiceProvider == VoiceProviderType.ELEVENLABS)
                                    Color(0xFF10B981).copy(alpha = 0.5f)
                                else
                                    Color(0xFFF59E0B).copy(alpha = 0.5f)
                            )
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 6.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(
                                        imageVector = if (activeVoiceProvider == VoiceProviderType.ELEVENLABS)
                                            Icons.AutoMirrored.Filled.VolumeUp
                                        else
                                            Icons.Default.Warning,
                                        contentDescription = null,
                                        tint = if (activeVoiceProvider == VoiceProviderType.ELEVENLABS)
                                            Color(0xFF059669)
                                        else
                                            Color(0xFFD97706),
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Text(
                                        text = if (activeProviderLabel.isNotBlank()) activeProviderLabel
                                        else if (activeVoiceProvider == VoiceProviderType.ELEVENLABS) "Voice Provider: ElevenLabs"
                                        else "Voice Provider: Android Fallback",
                                        style = MaterialTheme.typography.labelMedium.copy(
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 12.sp
                                        ),
                                        color = if (activeVoiceProvider == VoiceProviderType.ELEVENLABS)
                                            Color(0xFF047857)
                                        else
                                            Color(0xFFB45309)
                                    )
                                }

                                if (isSpeaking) {
                                    IconButton(
                                        onClick = { viewModel.stopSpeaking() },
                                        modifier = Modifier.size(24.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Stop,
                                            contentDescription = strings.stopSpeaking,
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            },
            bottomBar = {
                Column(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    VoiceAssistantBar(
                        voiceState = voiceState,
                        liveSpokenText = liveSpokenText,
                        rmsDb = rmsDb,
                        isSpeaking = isSpeaking,
                        isContinuousMode = isContinuousMode,
                        errorMessage = voiceErrorMessage,
                        activeVoiceProvider = activeVoiceProvider,
                        activeProviderLabel = activeProviderLabel,
                        onStopListening = { viewModel.stopVoiceListening() },
                        onStopSpeaking = { viewModel.stopSpeaking() },
                        onToggleContinuousMode = { viewModel.toggleContinuousVoiceMode() },
                        onCloseVoiceMode = { viewModel.stopVoiceConversation() }
                    )

                    ChatInputField(
                        text = inputText,
                        onTextChanged = { viewModel.onInputTextChanged(it) },
                        onSendClicked = { viewModel.sendMessage() },
                        onAttachClicked = {
                            mediaPicker.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        },
                        onMicClicked = {
                            val hasPermission = ContextCompat.checkSelfPermission(
                                context,
                                Manifest.permission.RECORD_AUDIO
                            ) == PackageManager.PERMISSION_GRANTED

                            if (hasPermission) {
                                if (isDictating) {
                                    viewModel.stopDictation()
                                } else {
                                    viewModel.startDictation()
                                }
                            } else {
                                dictationPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                            }
                        },
                        onLiveVoiceClicked = {
                            val hasPermission = ContextCompat.checkSelfPermission(
                                context,
                                Manifest.permission.RECORD_AUDIO
                            ) == PackageManager.PERMISSION_GRANTED

                            if (hasPermission) {
                                viewModel.openLiveVoiceConversation()
                            } else {
                                liveVoicePermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                            }
                        },
                        isGenerating = isGenerating,
                        onStopClicked = { viewModel.stopGeneration() },
                        selectedImageUri = selectedImageUri,
                        onRemoveImage = { viewModel.setSelectedImageUri(null) },
                        isImageMode = isImageMode,
                        onToggleImageMode = { viewModel.setImageMode(!isImageMode) },
                        isListening = isDictating
                    )
                }
            }
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .background(MaterialTheme.colorScheme.background)
            ) {
                if (messages.isEmpty()) {
                    // Empty state (Home Screen)
                    HomeScreenCenterView(
                        onSuggestionSelected = { suggestionText ->
                            viewModel.sendMessage(customPrompt = suggestionText)
                        },
                        onCreateImageSelected = { prompt ->
                            viewModel.setImageMode(true)
                            viewModel.sendMessage(customPrompt = prompt)
                        }
                    )
                } else {
                    // Conversation list
                    LazyColumn(
                        state = listState,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 16.dp),
                        contentPadding = PaddingValues(top = 12.dp, bottom = 24.dp),
                        verticalArrangement = Arrangement.spacedBy(20.dp)
                    ) {
                        items(messages, key = { it.id }) { message ->
                            ChatMessageRow(
                                message = message,
                                isSpeaking = isSpeaking,
                                onCopyText = { text ->
                                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    val clip = ClipData.newPlainText("Samadhan AI", text)
                                    clipboard.setPrimaryClip(clip)
                                    Toast.makeText(context, strings.copiedToast, Toast.LENGTH_SHORT).show()
                                },
                                onShareText = { text ->
                                    val sendIntent = Intent().apply {
                                        action = Intent.ACTION_SEND
                                        putExtra(Intent.EXTRA_TEXT, text)
                                        type = "text/plain"
                                    }
                                    val shareIntent = Intent.createChooser(sendIntent, strings.shareVia)
                                    context.startActivity(shareIntent)
                                },
                                onSpeakText = { text ->
                                    if (isSpeaking) {
                                        viewModel.stopSpeaking()
                                    } else {
                                        viewModel.speakMessage(text)
                                    }
                                },
                                onRegenerateResponse = { viewModel.regenerateResponse(message) },
                                onGenerateAgain = { viewModel.generateAgain(message) },
                                onEditImage = { viewModel.prepareImageEdit(message) },
                                onCreateAnother = { viewModel.createAnotherImage() }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun HomeScreenCenterView(
    onSuggestionSelected: (String) -> Unit,
    onCreateImageSelected: (String) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val strings = appStrings()
    val suggestions = listOf(
        strings.suggestion1Title to strings.suggestion1Prompt,
        strings.suggestion2Title to strings.suggestion2Prompt,
        strings.suggestion3Title to strings.suggestion3Prompt,
        strings.suggestion4Title to strings.suggestion4Prompt
    )

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Geometric Icon Emblem (Slate 900 rounded-2xl with 'S')
        Box(
            modifier = Modifier
                .size(64.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(MaterialTheme.colorScheme.primary),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "S",
                style = MaterialTheme.typography.headlineMedium.copy(
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimary,
                    fontSize = 28.sp
                )
            )
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Center headline
        Text(
            text = strings.welcomeTitle,
            style = MaterialTheme.typography.headlineSmall.copy(
                fontWeight = FontWeight.SemiBold,
                fontSize = 22.sp,
                letterSpacing = (-0.3).sp,
                lineHeight = 30.sp
            ),
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(6.dp))

        // Tagline
        Text(
            text = strings.taglineHindi,
            style = MaterialTheme.typography.bodyMedium.copy(
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium
            ),
            color = MaterialTheme.colorScheme.secondary,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(4.dp))

        // Subtitle
        Text(
            text = strings.welcomeSubtitle,
            style = MaterialTheme.typography.bodySmall.copy(
                fontSize = 12.5.sp
            ),
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(32.dp))

        // Geometric 2x2 Balanced Grid for Prompts
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Row 1
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                GeometricSuggestionCard(
                    title = suggestions[0].first,
                    modifier = Modifier.weight(1f),
                    testTag = "suggestion_card_0",
                    onClick = { onSuggestionSelected(suggestions[0].second) }
                )
                GeometricSuggestionCard(
                    title = suggestions[1].first,
                    modifier = Modifier.weight(1f),
                    testTag = "suggestion_card_1",
                    onClick = { onSuggestionSelected(suggestions[1].second) }
                )
            }
            // Row 2
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                GeometricSuggestionCard(
                    title = suggestions[2].first,
                    modifier = Modifier.weight(1f),
                    testTag = "suggestion_card_2",
                    onClick = { onSuggestionSelected(suggestions[2].second) }
                )
                GeometricSuggestionCard(
                    title = suggestions[3].first,
                    modifier = Modifier.weight(1f),
                    testTag = "suggestion_card_3",
                    onClick = { onSuggestionSelected(suggestions[3].second) }
                )
            }
        }
    }
}

@Composable
private fun GeometricSuggestionCard(
    title: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    testTag: String = ""
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.primaryContainer)
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outlineVariant,
                shape = RoundedCornerShape(16.dp)
            )
            .clickable { onClick() }
            .then(if (testTag.isNotEmpty()) Modifier.testTag(testTag) else Modifier)
            .padding(horizontal = 14.dp, vertical = 14.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.bodyMedium.copy(
                fontSize = 13.5.sp,
                fontWeight = FontWeight.Medium
            ),
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
fun ChatMessageRow(
    message: ChatMessageEntity,
    onCopyText: (String) -> Unit,
    onShareText: (String) -> Unit,
    onSpeakText: (String) -> Unit,
    isSpeaking: Boolean = false,
    onRegenerateResponse: () -> Unit = {},
    onGenerateAgain: () -> Unit = {},
    onEditImage: () -> Unit = {},
    onCreateAnother: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val isUser = message.role == "user"
    val strings = appStrings()

    if (isUser) {
        // User message bubble
        Row(
            modifier = modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End
        ) {
            val bubbleBg = AccentUserBubbleLight

            Column(
                modifier = Modifier
                    .clip(
                        RoundedCornerShape(
                            topStart = 20.dp,
                            topEnd = 20.dp,
                            bottomStart = 20.dp,
                            bottomEnd = 4.dp
                        )
                    )
                    .background(bubbleBg)
                    .padding(horizontal = 16.dp, vertical = 12.dp)
                    .fillMaxWidth(0.85f),
                horizontalAlignment = Alignment.End
            ) {
                if (message.imageUri != null) {
                    AsyncImage(
                        model = message.imageUri,
                        contentDescription = "User uploaded image",
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(180.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .padding(bottom = 8.dp),
                        contentScale = ContentScale.Crop
                    )
                }

                if (message.content.isNotEmpty()) {
                    Text(
                        text = message.content,
                        style = MaterialTheme.typography.bodyLarge.copy(
                            fontSize = 15.sp,
                            lineHeight = 22.sp
                        ),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
    } else {
        // Assistant Message
        Column(
            modifier = modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Role / Source label
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = "Samadhan AI",
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    ),
                    color = MaterialTheme.colorScheme.onBackground
                )

                if (message.isStreaming) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(12.dp),
                        strokeWidth = 1.5.dp,
                        color = MaterialTheme.colorScheme.secondary
                    )
                }
            }

            // If it is a Generated Image message
            if (message.isGeneratedImage || (message.imageUri != null && !isUser)) {
                if (message.isStreaming && message.imageUri == null) {
                    // Loading placeholder
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(220.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(MaterialTheme.colorScheme.primaryContainer)
                            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(16.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(28.dp),
                                color = MaterialTheme.colorScheme.primary,
                                strokeWidth = 2.5.dp
                            )
                            Text(
                                 text = strings.generatingImage,
                                 style = MaterialTheme.typography.bodySmall.copy(
                                     fontWeight = FontWeight.Medium
                                 ),
                                 color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                } else if (message.imageUri != null) {
                    // Generated Image Card
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(16.dp))
                                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(16.dp))
                        ) {
                            AsyncImage(
                                model = message.imageUri,
                                contentDescription = message.imagePrompt ?: "AI Generated Image",
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(260.dp),
                                contentScale = ContentScale.Crop
                            )
                        }

                        if (message.content.isNotBlank()) {
                            Text(
                                text = message.content,
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontSize = 12.5.sp
                                ),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        // Action buttons: [ Generate Again ], [ Edit Image ], [ Create Another ]
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Generate Again Button
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(MaterialTheme.colorScheme.primaryContainer)
                                    .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(10.dp))
                                    .clickable { onGenerateAgain() }
                                    .padding(horizontal = 10.dp, vertical = 7.dp)
                                    .testTag("generate_again_button")
                            ) {
                                Text(
                                    text = "🔄 ${strings.generateAgain}",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 11.5.sp
                                    ),
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }

                            // Edit Image Button
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(MaterialTheme.colorScheme.primaryContainer)
                                    .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(10.dp))
                                    .clickable { onEditImage() }
                                    .padding(horizontal = 10.dp, vertical = 7.dp)
                                    .testTag("edit_image_button")
                            ) {
                                Text(
                                    text = "✏️ ${strings.editImage}",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 11.5.sp
                                    ),
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }

                            // Create Another Button
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(MaterialTheme.colorScheme.primaryContainer)
                                    .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(10.dp))
                                    .clickable { onCreateAnother() }
                                    .padding(horizontal = 10.dp, vertical = 7.dp)
                                    .testTag("create_another_button")
                            ) {
                                Text(
                                    text = "✨ ${strings.createAnother}",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 11.5.sp
                                    ),
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                }
            } else {
                // Message text content rendered as clean Markdown
                if (message.content.isNotEmpty()) {
                    MarkdownText(
                        content = message.content,
                        textColor = if (message.isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onBackground
                    )
                } else if (message.isStreaming) {
                    Text(
                        text = strings.voiceStateThinking,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.secondary
                    )
                }
            }

            // Action toolbar (Copy, Share, Speak, Regenerate) for text messages
            if (!message.isStreaming && message.content.isNotEmpty() && !message.isGeneratedImage) {
                Row(
                    modifier = Modifier.padding(top = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = { onCopyText(message.content) },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.ContentCopy,
                            contentDescription = strings.copy,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                            modifier = Modifier.size(16.dp)
                        )
                    }

                    IconButton(
                        onClick = { onShareText(message.content) },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Share,
                            contentDescription = strings.share,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                            modifier = Modifier.size(16.dp)
                        )
                    }

                    IconButton(
                        onClick = { onSpeakText(message.content) },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = if (isSpeaking) Icons.Default.Stop else Icons.AutoMirrored.Filled.VolumeUp,
                            contentDescription = if (isSpeaking) strings.stopReading else strings.readAloud,
                            tint = if (isSpeaking) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                            modifier = Modifier.size(16.dp)
                        )
                    }

                    IconButton(
                        onClick = { onRegenerateResponse() },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = strings.regenerate,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }
    }
}

