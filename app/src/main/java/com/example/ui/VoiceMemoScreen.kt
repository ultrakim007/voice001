package com.example.ui

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.FormatSize
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.credentials.CredentialManager
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.R
import com.example.auth.signOut
import com.example.data.VoiceMemo
import com.example.data.VoiceMemoRepository
import com.example.speech.MemoTtsManager
import com.example.speech.VoiceRecognizerManager
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VoiceMemoScreen(
    currentUserId: String
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val credentialManager = remember(context) { CredentialManager.create(context) }

    val viewModel: VoiceMemoViewModel = viewModel(
        key = currentUserId,
        factory = viewModelFactory {
            initializer {
                val app = checkNotNull(this[APPLICATION_KEY]) {
                    "APPLICATION_KEY missing from CreationExtras"
                }
                val databaseId = app.getString(R.string.firestore_database_id)
                val db = FirebaseFirestore.getInstance(databaseId)
                VoiceMemoViewModel(VoiceMemoRepository(db), currentUserId)
            }
        }
    )

    val memosState by viewModel.memosState.collectAsStateWithLifecycle()
    val draftText by viewModel.draftText.collectAsStateWithLifecycle()
    val isSaving by viewModel.isSaving.collectAsStateWithLifecycle()
    val isExtraLargeText by viewModel.isExtraLargeText.collectAsStateWithLifecycle()

    var hasMicPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED
        )
    }

    val voiceRecognizer = remember(context, viewModel) {
        VoiceRecognizerManager(
            context = context,
            onFinalResult = { segment -> viewModel.appendSpokenText(segment) }
        )
    }
    val speechState by voiceRecognizer.state.collectAsStateWithLifecycle()

    val ttsUnavailableMessage = stringResource(R.string.tts_unavailable)
    val ttsManager = remember(context) {
        MemoTtsManager(
            context = context,
            onError = {
                scope.launch {
                    snackbarHostState.showSnackbar(ttsUnavailableMessage)
                }
            }
        )
    }
    val speakingMemoId by ttsManager.speakingMemoId.collectAsStateWithLifecycle()

    DisposableEffect(voiceRecognizer, ttsManager) {
        onDispose {
            voiceRecognizer.release()
            ttsManager.release()
        }
    }

    val micPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasMicPermission = granted
        if (granted) {
            voiceRecognizer.clearError()
            ttsManager.stop()
            voiceRecognizer.startListening()
        }
    }

    val systemSpeechLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val matches = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
            val spoken = matches?.firstOrNull()?.trim().orEmpty()
            if (spoken.isNotEmpty()) {
                voiceRecognizer.clearError()
                viewModel.appendSpokenText(spoken)
            }
        }
    }

    val handleOpenAppSettings: () -> Unit = {
        val settingsIntent = Intent(
            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            Uri.fromParts("package", context.packageName, null)
        )
        context.startActivity(settingsIntent)
    }

    val handleLaunchSystemSpeechDialog: () -> Unit = {
        ttsManager.stop()
        voiceRecognizer.stopListening()
        val intent = voiceRecognizer.buildSystemSpeechIntent()
        if (intent != null) {
            systemSpeechLauncher.launch(intent)
        } else {
            voiceRecognizer.showSystemDialogUnavailableError()
        }
    }

    val savedMessage = stringResource(R.string.memo_saved_message)
    val saveFailedMessage = stringResource(R.string.memo_save_failed)
    val tooLongMessage = stringResource(R.string.memo_too_long_error)
    val deletedMessage = stringResource(R.string.memo_deleted_message)
    val deleteFailedMessage = stringResource(R.string.memo_delete_failed)
    val restoredMessage = stringResource(R.string.memo_restored_message)
    val undoActionLabel = stringResource(R.string.undo_action)

    LaunchedEffect(viewModel) {
        viewModel.feedbackEvents.collect { event ->
            snackbarHostState.currentSnackbarData?.dismiss()
            when (event) {
                MemoFeedbackEvent.Saved -> {
                    snackbarHostState.showSnackbar(
                        message = savedMessage,
                        duration = SnackbarDuration.Short
                    )
                }

                MemoFeedbackEvent.SaveFailed -> {
                    snackbarHostState.showSnackbar(
                        message = saveFailedMessage,
                        duration = SnackbarDuration.Long
                    )
                }

                MemoFeedbackEvent.TooLong -> {
                    snackbarHostState.showSnackbar(
                        message = tooLongMessage,
                        duration = SnackbarDuration.Long
                    )
                }

                is MemoFeedbackEvent.Deleted -> {
                    val result = snackbarHostState.showSnackbar(
                        message = deletedMessage,
                        actionLabel = undoActionLabel,
                        duration = SnackbarDuration.Long
                    )
                    if (result == SnackbarResult.ActionPerformed) {
                        viewModel.restoreMemo(event.deletedContent)
                    }
                }

                MemoFeedbackEvent.DeleteFailed -> {
                    snackbarHostState.showSnackbar(
                        message = deleteFailedMessage,
                        duration = SnackbarDuration.Long
                    )
                }

                MemoFeedbackEvent.Restored -> {
                    snackbarHostState.showSnackbar(
                        message = restoredMessage,
                        duration = SnackbarDuration.Short
                    )
                }
            }
        }
    }

    val handleToggleRecording: () -> Unit = {
        val grantedNow = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
        hasMicPermission = grantedNow

        if (!grantedNow) {
            micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        } else if (speechState.isListening) {
            voiceRecognizer.stopListening()
        } else if (!speechState.isAvailable) {
            handleLaunchSystemSpeechDialog()
        } else {
            ttsManager.stop()
            voiceRecognizer.startListening()
        }
    }

    val handleSaveMemo: () -> Unit = {
        val pendingPartial = speechState.partialText
        if (speechState.isListening) {
            voiceRecognizer.stopListening()
        }
        viewModel.saveCurrentMemo(partialInFlight = pendingPartial)
    }

    val handleClearDraft: () -> Unit = {
        if (speechState.isListening) {
            voiceRecognizer.stopListening()
        }
        viewModel.clearDraft()
    }

    val handleDeleteMemo: (VoiceMemo) -> Unit = { memo ->
        if (speakingMemoId == memo.id) {
            ttsManager.stop()
        }
        viewModel.deleteMemo(memo)
    }

    val handleReadAloud: (VoiceMemo) -> Unit = { memo ->
        if (speechState.isListening) {
            voiceRecognizer.stopListening()
        }
        ttsManager.toggleSpeak(memo.id, memo.content)
    }

    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing,
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground
                ),
                title = {
                    Text(
                        text = stringResource(R.string.top_bar_title),
                        style = MaterialTheme.typography.headlineMedium,
                        maxLines = 1
                    )
                },
                actions = {
                    FilterChip(
                        selected = isExtraLargeText,
                        onClick = { viewModel.toggleExtraLargeText() },
                        label = {
                            Text(
                                text = if (isExtraLargeText) {
                                    stringResource(R.string.text_size_normal)
                                } else {
                                    stringResource(R.string.text_size_larger)
                                },
                                style = MaterialTheme.typography.labelMedium.copy(
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            )
                        },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Rounded.FormatSize,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp)
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                            selectedLabelColor = MaterialTheme.colorScheme.onSecondaryContainer,
                            selectedLeadingIconColor = MaterialTheme.colorScheme.onSecondaryContainer
                        ),
                        modifier = Modifier
                            .height(48.dp)
                            .testTag("text_size_toggle_button")
                    )

                    Spacer(modifier = Modifier.width(6.dp))

                    IconButton(
                        onClick = {
                            voiceRecognizer.stopListening()
                            ttsManager.stop()
                            signOut(credentialManager = credentialManager, scope = scope)
                        },
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("sign_out_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.Logout,
                            contentDescription = stringResource(R.string.sign_out_cd),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(26.dp)
                        )
                    }
                }
            )
        },
        snackbarHost = {
            SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier.navigationBarsPadding()
            ) { data ->
                Snackbar(
                    snackbarData = data,
                    shape = RoundedCornerShape(18.dp),
                    containerColor = MaterialTheme.colorScheme.onBackground,
                    contentColor = MaterialTheme.colorScheme.background,
                    actionColor = MaterialTheme.colorScheme.primaryContainer
                )
            }
        },
        bottomBar = {
            AnimatedVisibility(
                visible = speakingMemoId != null,
                enter = slideInVertically { it } + fadeIn(),
                exit = slideOutVertically { it } + fadeOut()
            ) {
                SpeakingStatusBar(onStopClick = { ttsManager.stop() })
            }
        }
    ) { innerPadding ->
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            val isWideLayout = maxWidth >= 720.dp

            if (isWideLayout) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 24.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(24.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .verticalScroll(rememberScrollState()),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        VoiceRecorderPanel(
                            draftText = draftText,
                            speechState = speechState,
                            hasMicPermission = hasMicPermission,
                            isSaving = isSaving,
                            isExtraLargeText = isExtraLargeText,
                            onRequestMicPermission = {
                                micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                            },
                            onOpenAppSettings = handleOpenAppSettings,
                            onToggleRecording = handleToggleRecording,
                            onLaunchSystemSpeechDialog = handleLaunchSystemSpeechDialog,
                            onDraftTextChange = { viewModel.updateDraftText(it) },
                            onSaveMemo = handleSaveMemo,
                            onClearDraft = handleClearDraft
                        )
                    }

                    SavedMemosListColumn(
                        memosState = memosState,
                        speakingMemoId = speakingMemoId,
                        isExtraLargeText = isExtraLargeText,
                        onReadAloudClick = handleReadAloud,
                        onDeleteClick = handleDeleteMemo,
                        modifier = Modifier
                            .weight(1.1f)
                            .fillMaxHeight()
                    )
                }
            } else {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.TopCenter
                ) {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .widthIn(max = 600.dp),
                        contentPadding = PaddingValues(
                            start = 20.dp,
                            end = 20.dp,
                            top = 8.dp,
                            bottom = 32.dp
                        ),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        item(key = "voice_recorder_panel") {
                            VoiceRecorderPanel(
                                draftText = draftText,
                                speechState = speechState,
                                hasMicPermission = hasMicPermission,
                                isSaving = isSaving,
                                isExtraLargeText = isExtraLargeText,
                                onRequestMicPermission = {
                                    micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                },
                                onOpenAppSettings = handleOpenAppSettings,
                                onToggleRecording = handleToggleRecording,
                                onLaunchSystemSpeechDialog = handleLaunchSystemSpeechDialog,
                                onDraftTextChange = { viewModel.updateDraftText(it) },
                                onSaveMemo = handleSaveMemo,
                                onClearDraft = handleClearDraft
                            )
                        }

                        item(key = "section_divider") {
                            Column(modifier = Modifier.padding(top = 6.dp)) {
                                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                                Spacer(modifier = Modifier.height(16.dp))
                                SavedMemosSectionHeader(memosState = memosState)
                            }
                        }

                        when (val state = memosState) {
                            UiState.Loading -> {
                                item(key = "loading_state") {
                                    MemosLoadingState()
                                }
                            }

                            is UiState.Error -> {
                                item(key = "error_state") {
                                    MemosErrorState()
                                }
                            }

                            is UiState.Success -> {
                                if (state.data.isEmpty()) {
                                    item(key = "empty_state") {
                                        MemosEmptyState()
                                    }
                                } else {
                                    items(
                                        items = state.data,
                                        key = { memo -> memo.id.ifBlank { memo.hashCode().toString() } }
                                    ) { memo ->
                                        VoiceMemoCard(
                                            memo = memo,
                                            isSpeaking = speakingMemoId == memo.id,
                                            isExtraLargeText = isExtraLargeText,
                                            onReadAloudClick = { handleReadAloud(memo) },
                                            onDeleteClick = { handleDeleteMemo(memo) }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SavedMemosListColumn(
    memosState: UiState<List<VoiceMemo>>,
    speakingMemoId: String?,
    isExtraLargeText: Boolean,
    onReadAloudClick: (VoiceMemo) -> Unit,
    onDeleteClick: (VoiceMemo) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item(key = "wide_section_header") {
            SavedMemosSectionHeader(memosState = memosState)
        }
        when (memosState) {
            UiState.Loading -> item(key = "wide_loading") { MemosLoadingState() }
            is UiState.Error -> item(key = "wide_error") { MemosErrorState() }
            is UiState.Success -> {
                if (memosState.data.isEmpty()) {
                    item(key = "wide_empty") { MemosEmptyState() }
                } else {
                    items(
                        items = memosState.data,
                        key = { memo -> memo.id.ifBlank { memo.hashCode().toString() } }
                    ) { memo ->
                        VoiceMemoCard(
                            memo = memo,
                            isSpeaking = speakingMemoId == memo.id,
                            isExtraLargeText = isExtraLargeText,
                            onReadAloudClick = { onReadAloudClick(memo) },
                            onDeleteClick = { onDeleteClick(memo) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SavedMemosSectionHeader(
    memosState: UiState<List<VoiceMemo>>
) {
    val count = (memosState as? UiState.Success)?.data?.size ?: 0
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.saved_memos_title),
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onBackground
            )
            if (memosState is UiState.Success && count > 0) {
                Surface(
                    shape = RoundedCornerShape(50),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh
                ) {
                    Text(
                        text = stringResource(R.string.saved_memos_count_format, count),
                        style = MaterialTheme.typography.labelLarge.copy(fontSize = 16.sp),
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                    )
                }
            }
        }
        Text(
            text = stringResource(R.string.saved_memos_tap_guide),
            style = MaterialTheme.typography.bodyMedium.copy(fontSize = 17.sp),
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun SpeakingStatusBar(
    onStopClick: () -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.tertiary,
        contentColor = MaterialTheme.colorScheme.onTertiary,
        shadowElevation = 8.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.VolumeUp,
                    contentDescription = null,
                    modifier = Modifier.size(28.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = stringResource(R.string.stop_speaking_banner),
                    style = MaterialTheme.typography.titleMedium.copy(fontSize = 18.sp)
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Button(
                onClick = onStopClick,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.onTertiary,
                    contentColor = MaterialTheme.colorScheme.tertiary
                ),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .height(48.dp)
                    .testTag("stop_tts_button")
            ) {
                Icon(
                    imageVector = Icons.Rounded.Stop,
                    contentDescription = null,
                    modifier = Modifier.size(22.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = stringResource(R.string.stop_speaking_button),
                    style = MaterialTheme.typography.labelLarge.copy(fontSize = 16.sp)
                )
            }
        }
    }
}

@Composable
private fun MemosLoadingState() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 28.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        CircularProgressIndicator(
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(30.dp)
        )
        Spacer(modifier = Modifier.width(14.dp))
        Text(
            text = stringResource(R.string.loading_memos),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun MemosErrorState() {
    Surface(
        color = MaterialTheme.colorScheme.errorContainer,
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Rounded.ErrorOutline,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.size(28.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = stringResource(R.string.load_memos_error),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onErrorContainer
            )
        }
    }
}

@Composable
private fun MemosEmptyState() {
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 26.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Image(
                painter = painterResource(id = R.drawable.img_voice_memo_art),
                contentDescription = stringResource(R.string.auth_illustration_cd),
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(width = 164.dp, height = 120.dp)
                    .clip(RoundedCornerShape(18.dp))
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = stringResource(R.string.empty_memos_title),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.empty_memos_hint),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}
