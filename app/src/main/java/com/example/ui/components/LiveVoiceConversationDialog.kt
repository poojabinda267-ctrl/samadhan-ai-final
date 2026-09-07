package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.i18n.appStrings
import com.example.voice.VoiceAssistantState
import com.example.voice.VoiceProviderType

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LiveVoiceConversationDialog(
    voiceState: VoiceAssistantState,
    liveSpokenText: String,
    lastAiResponse: String,
    rmsDb: Float,
    isSpeaking: Boolean,
    isTtsEnabled: Boolean,
    errorMessage: String?,
    activeVoiceProvider: VoiceProviderType = VoiceProviderType.NONE,
    activeProviderLabel: String = "",
    lastFallbackReason: String? = null,
    onToggleTts: () -> Unit,
    onEndConversation: () -> Unit,
    modifier: Modifier = Modifier
) {
    val strings = appStrings()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onEndConversation,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        dragHandle = null,
        modifier = modifier.testTag("live_voice_conversation_dialog")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Header: Title and Close button
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.GraphicEq,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    Column {
                        Text(
                            text = strings.liveVoiceTitle,
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp
                            ),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = strings.liveVoiceSubtitle,
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontSize = 11.5.sp
                            ),
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                        )
                    }
                }

                IconButton(
                    onClick = onEndConversation,
                    modifier = Modifier
                        .size(36.dp)
                        .testTag("close_live_voice_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = strings.cancel,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Visual Debug Indicator for Voice Provider
            AnimatedVisibility(
                visible = activeVoiceProvider != VoiceProviderType.NONE,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                Surface(
                    color = if (activeVoiceProvider == VoiceProviderType.ELEVENLABS)
                        Color(0xFF10B981).copy(alpha = 0.15f)
                    else
                        Color(0xFFF59E0B).copy(alpha = 0.15f),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(
                        1.dp,
                        if (activeVoiceProvider == VoiceProviderType.ELEVENLABS)
                            Color(0xFF10B981)
                        else
                            Color(0xFFF59E0B)
                    ),
                    modifier = Modifier
                        .padding(top = 10.dp)
                        .testTag("live_dialog_voice_provider_chip")
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp)
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
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = if (activeProviderLabel.isNotBlank()) activeProviderLabel
                            else if (activeVoiceProvider == VoiceProviderType.ELEVENLABS) "Voice Provider: ElevenLabs"
                            else "Voice Provider: Android Fallback",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.5.sp
                            ),
                            color = if (activeVoiceProvider == VoiceProviderType.ELEVENLABS)
                                Color(0xFF047857)
                            else
                                Color(0xFFB45309)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Central Animated Voice Orb Visualizer
            LiveVoiceVisualizerOrb(
                voiceState = voiceState,
                isSpeaking = isSpeaking,
                rmsDb = rmsDb
            )

            Spacer(modifier = Modifier.height(20.dp))

            // Current State Label
            val stateText = when (voiceState) {
                VoiceAssistantState.LISTENING -> strings.voiceStateListening
                VoiceAssistantState.THINKING -> strings.voiceStateThinking
                VoiceAssistantState.SPEAKING -> strings.voiceStateSpeaking
                VoiceAssistantState.ERROR -> errorMessage ?: strings.voiceStateNotice
                VoiceAssistantState.IDLE -> strings.voiceStateListening
            }

            val stateColor = when (voiceState) {
                VoiceAssistantState.LISTENING -> MaterialTheme.colorScheme.primary
                VoiceAssistantState.THINKING -> MaterialTheme.colorScheme.tertiary
                VoiceAssistantState.SPEAKING -> MaterialTheme.colorScheme.secondary
                VoiceAssistantState.ERROR -> MaterialTheme.colorScheme.error
                VoiceAssistantState.IDLE -> MaterialTheme.colorScheme.onSurfaceVariant
            }

            Text(
                text = stateText,
                style = MaterialTheme.typography.titleSmall.copy(
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 15.sp,
                    letterSpacing = 0.3.sp
                ),
                color = stateColor,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(18.dp))

            // Live Transcript Card
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(110.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .border(
                        1.dp,
                        MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f),
                        RoundedCornerShape(16.dp)
                    ),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    contentAlignment = Alignment.Center
                ) {
                    if (liveSpokenText.isNotBlank()) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Text(
                                text = "🗣️ \"$liveSpokenText\"",
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontSize = 13.5.sp,
                                    fontWeight = FontWeight.Medium
                                ),
                                color = MaterialTheme.colorScheme.onSurface,
                                textAlign = TextAlign.Center,
                                maxLines = 3,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    } else if (lastAiResponse.isNotBlank()) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Text(
                                text = "🤖 $lastAiResponse",
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Normal
                                ),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center,
                                maxLines = 3,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    } else {
                        Text(
                            text = if (voiceState == VoiceAssistantState.LISTENING) {
                                "Start speaking naturally..."
                            } else {
                                "Samadhan AI is ready..."
                            },
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                            ),
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(28.dp))

            // Bottom Action Controls: End Conversation Button & TTS Mute Toggle
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Audio TTS Mute Toggle
                IconButton(
                    onClick = onToggleTts,
                    modifier = Modifier
                        .size(46.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .testTag("live_voice_tts_toggle")
                ) {
                    Icon(
                        imageVector = if (isTtsEnabled) Icons.AutoMirrored.Filled.VolumeUp else Icons.AutoMirrored.Filled.VolumeOff,
                        contentDescription = if (isTtsEnabled) strings.muteVoice else strings.enableVoice,
                        tint = if (isTtsEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Spacer(modifier = Modifier.width(16.dp))

                // End Conversation Primary Action
                Box(
                    modifier = Modifier
                        .height(48.dp)
                        .clip(RoundedCornerShape(24.dp))
                        .background(MaterialTheme.colorScheme.error)
                        .clickable { onEndConversation() }
                        .padding(horizontal = 24.dp)
                        .testTag("end_voice_conversation_button"),
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.CallEnd,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onError,
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            text = strings.endVoiceConversation,
                            style = MaterialTheme.typography.labelLarge.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            ),
                            color = MaterialTheme.colorScheme.onError
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
        }
    }
}

@Composable
private fun LiveVoiceVisualizerOrb(
    voiceState: VoiceAssistantState,
    isSpeaking: Boolean,
    rmsDb: Float
) {
    val infiniteTransition = rememberInfiniteTransition(label = "orb_pulse")

    val basePulse by infiniteTransition.animateFloat(
        initialValue = 1.0f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "base_pulse"
    )

    val rmsMultiplier = 1f + (rmsDb.coerceIn(0f, 10f) / 10f) * 0.35f
    val dynamicScale = when (voiceState) {
        VoiceAssistantState.LISTENING -> basePulse * rmsMultiplier
        VoiceAssistantState.SPEAKING -> basePulse * 1.12f
        VoiceAssistantState.THINKING -> basePulse * 1.05f
        else -> 1.0f
    }

    val primaryColor = MaterialTheme.colorScheme.primary
    val secondaryColor = MaterialTheme.colorScheme.secondary
    val tertiaryColor = MaterialTheme.colorScheme.tertiary
    val errorColor = MaterialTheme.colorScheme.error

    val orbGradient = when (voiceState) {
        VoiceAssistantState.LISTENING -> Brush.radialGradient(
            colors = listOf(
                primaryColor.copy(alpha = 0.9f),
                primaryColor.copy(alpha = 0.4f),
                Color.Transparent
            )
        )
        VoiceAssistantState.SPEAKING -> Brush.radialGradient(
            colors = listOf(
                secondaryColor.copy(alpha = 0.9f),
                secondaryColor.copy(alpha = 0.4f),
                Color.Transparent
            )
        )
        VoiceAssistantState.THINKING -> Brush.radialGradient(
            colors = listOf(
                tertiaryColor.copy(alpha = 0.9f),
                tertiaryColor.copy(alpha = 0.4f),
                Color.Transparent
            )
        )
        VoiceAssistantState.ERROR -> Brush.radialGradient(
            colors = listOf(
                errorColor.copy(alpha = 0.9f),
                errorColor.copy(alpha = 0.4f),
                Color.Transparent
            )
        )
        VoiceAssistantState.IDLE -> Brush.radialGradient(
            colors = listOf(
                primaryColor.copy(alpha = 0.5f),
                Color.Transparent
            )
        )
    }

    Box(
        modifier = Modifier.size(140.dp),
        contentAlignment = Alignment.Center
    ) {
        // Outer pulsing glow
        Box(
            modifier = Modifier
                .size(130.dp)
                .scale(dynamicScale)
                .clip(CircleShape)
                .background(orbGradient)
        )

        // Core Solid Orb
        Box(
            modifier = Modifier
                .size(80.dp)
                .clip(CircleShape)
                .background(
                    when (voiceState) {
                        VoiceAssistantState.LISTENING -> primaryColor
                        VoiceAssistantState.SPEAKING -> secondaryColor
                        VoiceAssistantState.THINKING -> tertiaryColor
                        VoiceAssistantState.ERROR -> errorColor
                        VoiceAssistantState.IDLE -> primaryColor.copy(alpha = 0.8f)
                    }
                ),
            contentAlignment = Alignment.Center
        ) {
            when (voiceState) {
                VoiceAssistantState.THINKING -> {
                    CircularProgressIndicator(
                        modifier = Modifier.size(36.dp),
                        color = Color.White,
                        strokeWidth = 3.dp
                    )
                }
                VoiceAssistantState.SPEAKING -> {
                    Icon(
                        imageVector = Icons.Default.GraphicEq,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(36.dp)
                    )
                }
                else -> {
                    Icon(
                        imageVector = Icons.Default.Mic,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(36.dp)
                    )
                }
            }
        }
    }
}
