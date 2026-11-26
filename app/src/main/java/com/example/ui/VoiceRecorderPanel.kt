package com.example.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.MicOff
import androidx.compose.material.icons.rounded.RecordVoiceOver
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.speech.SpeechRecognitionState
import com.example.ui.theme.CrimsonBrightLight
import com.example.ui.theme.CrimsonDeepRecording
import com.example.ui.theme.CrimsonPrimaryLight

@Composable
fun VoiceRecorderPanel(
    draftText: String,
    speechState: SpeechRecognitionState,
    hasMicPermission: Boolean,
    isSaving: Boolean,
    isExtraLargeText: Boolean,
    onRequestMicPermission: () -> Unit,
    onOpenAppSettings: () -> Unit,
    onToggleRecording: () -> Unit,
    onLaunchSystemSpeechDialog: () -> Unit,
    onDraftTextChange: (String) -> Unit,
    onSaveMemo: () -> Unit,
    onClearDraft: () -> Unit,
    modifier: Modifier = Modifier
) {
    var isEditingManually by remember { mutableStateOf(false) }

    val hasDisplayText = draftText.isNotBlank() || speechState.partialText.isNotBlank()
    val transcriptFontSize = if (isExtraLargeText) 30.sp else 24.sp
    val transcriptLineHeight = if (isExtraLargeText) 42.sp else 35.sp

    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Real-time Large-Text Speech Display Card
        val cardBorderColor by animateColorAsState(
            targetValue = if (speechState.isListening) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.outlineVariant
            },
            label = "recorder_card_border"
        )

        Surface(
            shape = RoundedCornerShape(26.dp),
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            border = BorderStroke(
                width = if (speechState.isListening) 2.5.dp else 1.5.dp,
                color = cardBorderColor
            ),
            shadowElevation = if (speechState.isListening) 6.dp else 2.dp,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 148.dp)
                .testTag("live_transcript_card")
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 18.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (speechState.isListening) {
                        Surface(
                            color = MaterialTheme.colorScheme.primaryContainer,
                            shape = RoundedCornerShape(50),
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.GraphicEq,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(22.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = stringResource(R.string.listening_badge),
                                    style = MaterialTheme.typography.labelLarge.copy(fontSize = 15.sp),
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                        }
                    } else {
                        Spacer(modifier = Modifier.width(1.dp))
                    }

                    TextButton(
                        onClick = { isEditingManually = !isEditingManually },
                        modifier = Modifier
                            .heightIn(min = 48.dp)
                            .testTag("toggle_manual_edit_button")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Edit,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (isEditingManually) {
                                stringResource(R.string.edit_draft_done)
                            } else {
                                stringResource(R.string.edit_draft_toggle)
                            },
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold
                            )
                        )
                    }
                }

                if (isEditingManually) {
                    OutlinedTextField(
                        value = draftText,
                        onValueChange = onDraftTextChange,
                        placeholder = {
                            Text(
                                text = stringResource(R.string.edit_draft_placeholder),
                                style = MaterialTheme.typography.bodyLarge
                            )
                        },
                        textStyle = MaterialTheme.typography.headlineSmall.copy(
                            fontSize = transcriptFontSize,
                            lineHeight = transcriptLineHeight,
                            color = MaterialTheme.colorScheme.onSurface
                        ),
                        shape = RoundedCornerShape(18.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outline
                        ),
                        minLines = 2,
                        maxLines = 5,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("manual_draft_input")
                    )
                } else if (hasDisplayText) {
                    val primaryColor = MaterialTheme.colorScheme.primary
                    val onSurfaceColor = MaterialTheme.colorScheme.onSurface
                    val annotatedTranscript = buildAnnotatedString {
                        if (draftText.isNotBlank()) {
                            withStyle(
                                SpanStyle(
                                    color = onSurfaceColor,
                                    fontWeight = FontWeight.SemiBold
                                )
                            ) {
                                append(draftText.trim())
                            }
                        }
                        if (speechState.partialText.isNotBlank()) {
                            if (draftText.isNotBlank()) append(" ")
                            withStyle(
                                SpanStyle(
                                    color = primaryColor,
                                    fontWeight = FontWeight.Bold
                                )
                            ) {
                                append(speechState.partialText.trim())
                            }
                        }
                    }
                    Text(
                        text = annotatedTranscript,
                        style = MaterialTheme.typography.headlineSmall.copy(
                            fontSize = transcriptFontSize,
                            lineHeight = transcriptLineHeight
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("live_transcript_text")
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 92.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = stringResource(R.string.draft_placeholder),
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontSize = if (isExtraLargeText) 25.sp else 21.sp,
                                lineHeight = if (isExtraLargeText) 36.sp else 31.sp,
                                fontWeight = FontWeight.Medium
                            ),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }

        // Permission or Speech Error Banner with Actionable Recovery Buttons
        AnimatedVisibility(
            visible = !hasMicPermission || speechState.errorMessage != null,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically()
        ) {
            val isPermissionBanner = !hasMicPermission
            Surface(
                color = MaterialTheme.colorScheme.errorContainer,
                shape = RoundedCornerShape(18.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = if (isPermissionBanner) Icons.Rounded.MicOff else Icons.Rounded.Info,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = if (isPermissionBanner) {
                                stringResource(R.string.mic_permission_rationale)
                            } else {
                                speechState.errorMessage.orEmpty()
                            },
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontSize = 17.sp,
                                fontWeight = FontWeight.Medium
                            ),
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                    if (isPermissionBanner) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Button(
                                onClick = onRequestMicPermission,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.primary,
                                    contentColor = MaterialTheme.colorScheme.onPrimary
                                ),
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(52.dp)
                                    .testTag("grant_mic_permission_button")
                            ) {
                                Text(
                                    text = stringResource(R.string.mic_permission_grant_button),
                                    style = MaterialTheme.typography.labelLarge.copy(fontSize = 16.sp)
                                )
                            }
                            OutlinedButton(
                                onClick = onOpenAppSettings,
                                shape = RoundedCornerShape(14.dp),
                                border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.onErrorContainer),
                                modifier = Modifier
                                    .height(52.dp)
                                    .testTag("open_app_settings_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Settings,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onErrorContainer,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    } else if (speechState.suggestSystemDialogFallback) {
                        Button(
                            onClick = onLaunchSystemSpeechDialog,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.tertiary,
                                contentColor = MaterialTheme.colorScheme.onTertiary
                            ),
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(54.dp)
                                .testTag("system_speech_dialog_button")
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.RecordVoiceOver,
                                contentDescription = null,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = stringResource(R.string.speech_system_dialog_button),
                                style = MaterialTheme.typography.labelLarge.copy(fontSize = 17.sp)
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Giant Crimson Center Microphone Button with Live Acoustic Pulse Rings
        GiantRedMicButton(
            isListening = speechState.isListening,
            voiceLevel = speechState.voiceLevel,
            onClick = {
                isEditingManually = false
                onToggleRecording()
            }
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Primary Save & Secondary Clear Action Buttons
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (hasDisplayText) {
                OutlinedButton(
                    onClick = {
                        isEditingManually = false
                        onClearDraft()
                    },
                    enabled = !isSaving,
                    shape = RoundedCornerShape(20.dp),
                    border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.outline),
                    modifier = Modifier
                        .height(64.dp)
                        .testTag("clear_draft_button")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Refresh,
                        contentDescription = stringResource(R.string.clear_draft_cd),
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = stringResource(R.string.clear_draft_button),
                        style = MaterialTheme.typography.labelLarge.copy(fontSize = 17.sp)
                    )
                }
            }

            Button(
                onClick = {
                    isEditingManually = false
                    onSaveMemo()
                },
                enabled = hasDisplayText && !isSaving,
                shape = RoundedCornerShape(20.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.secondary,
                    contentColor = MaterialTheme.colorScheme.onSecondary,
                    disabledContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant
                ),
                elevation = ButtonDefaults.buttonElevation(
                    defaultElevation = 4.dp,
                    disabledElevation = 0.dp
                ),
                modifier = Modifier
                    .weight(1f)
                    .height(64.dp)
                    .testTag("save_memo_button")
            ) {
                if (isSaving) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        color = MaterialTheme.colorScheme.onSecondary,
                        strokeWidth = 3.dp
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = stringResource(R.string.saving_memo_button),
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold
                        )
                    )
                } else {
                    Icon(
                        imageVector = Icons.Rounded.CheckCircle,
                        contentDescription = null,
                        modifier = Modifier.size(28.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = stringResource(R.string.save_memo_button),
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold
                        )
                    )
                }
            }
        }
    }
}

@Composable
private fun GiantRedMicButton(
    isListening: Boolean,
    voiceLevel: Float,
    onClick: () -> Unit
) {
    val infiniteTransition = rememberInfiniteTransition(label = "mic_pulse_transition")
    val pulseProgress by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "mic_pulse_progress"
    )

    val reactiveScale by animateFloatAsState(
        targetValue = if (isListening) 1.04f + (voiceLevel * 0.06f) else 1f,
        animationSpec = spring(dampingRatio = 0.6f, stiffness = 400f),
        label = "mic_button_scale"
    )

    val micContentDescription = if (isListening) {
        stringResource(R.string.mic_button_stop_cd)
    } else {
        stringResource(R.string.mic_button_start_cd)
    }

    val buttonBrush = if (isListening) {
        Brush.radialGradient(
            colors = listOf(CrimsonPrimaryLight, CrimsonDeepRecording)
        )
    } else {
        Brush.radialGradient(
            colors = listOf(CrimsonBrightLight, CrimsonPrimaryLight)
        )
    }

    val pulseRingColor = MaterialTheme.colorScheme.primary

    Box(
        modifier = Modifier.size(204.dp),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val baseRadius = size.minDimension * 0.38f
            val maxExtra = size.minDimension * 0.12f

            if (isListening) {
                val ring1Radius = baseRadius + (maxExtra * (0.4f + pulseProgress * 0.6f)) + (voiceLevel * 14f)
                val ring1Alpha = (1f - pulseProgress) * 0.38f
                drawCircle(
                    color = pulseRingColor.copy(alpha = ring1Alpha.coerceIn(0f, 0.45f)),
                    radius = ring1Radius
                )

                val secondPhase = (pulseProgress + 0.5f) % 1f
                val ring2Radius = baseRadius + (maxExtra * (0.4f + secondPhase * 0.6f)) + (voiceLevel * 10f)
                val ring2Alpha = (1f - secondPhase) * 0.30f
                drawCircle(
                    color = pulseRingColor.copy(alpha = ring2Alpha.coerceIn(0f, 0.35f)),
                    radius = ring2Radius
                )
            } else {
                drawCircle(
                    color = pulseRingColor.copy(alpha = 0.12f),
                    radius = baseRadius + 14.dp.toPx()
                )
            }
        }

        val interactionSource = remember { MutableInteractionSource() }
        Box(
            modifier = Modifier
                .scale(reactiveScale)
                .size(160.dp)
                .shadow(
                    elevation = if (isListening) 14.dp else 10.dp,
                    shape = CircleShape,
                    ambientColor = CrimsonPrimaryLight,
                    spotColor = CrimsonPrimaryLight
                )
                .clip(CircleShape)
                .background(buttonBrush)
                .clickable(
                    interactionSource = interactionSource,
                    indication = ripple(bounded = true, color = Color.White),
                    role = Role.Button,
                    onClick = onClick
                )
                .semantics { contentDescription = micContentDescription }
                .testTag("mic_record_button"),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier.padding(12.dp)
            ) {
                Icon(
                    imageVector = if (isListening) Icons.Rounded.Stop else Icons.Rounded.Mic,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(58.dp)
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = if (isListening) {
                        stringResource(R.string.mic_stop_label)
                    } else {
                        stringResource(R.string.mic_start_label)
                    },
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontSize = 21.sp,
                        fontWeight = FontWeight.Bold
                    ),
                    color = Color.White,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}
