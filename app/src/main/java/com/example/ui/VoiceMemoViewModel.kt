package com.example.ui

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.VoiceMemo
import com.example.data.VoiceMemoRepository
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface UiState<out T> {
    data object Loading : UiState<Nothing>
    data class Success<T>(val data: T) : UiState<T>
    data class Error(val message: String) : UiState<Nothing>
}

sealed interface MemoFeedbackEvent {
    data object Saved : MemoFeedbackEvent
    data object SaveFailed : MemoFeedbackEvent
    data object TooLong : MemoFeedbackEvent
    data class Deleted(val deletedContent: String) : MemoFeedbackEvent
    data object DeleteFailed : MemoFeedbackEvent
    data object Restored : MemoFeedbackEvent
}

class VoiceMemoViewModel(
    private val repository: VoiceMemoRepository,
    currentUserId: String
) : ViewModel() {

    val memosState: StateFlow<UiState<List<VoiceMemo>>> = repository.observeMemos(currentUserId)
        .map<List<VoiceMemo>, UiState<List<VoiceMemo>>> { UiState.Success(it) }
        .catch { error ->
            val firestoreError = generateSequence(error) { it.cause }
                .filterIsInstance<FirebaseFirestoreException>()
                .firstOrNull()
            if (firestoreError == null && error is CancellationException) throw error
            val rootError = firestoreError ?: error
            Log.w(TAG, "Error observing memos", rootError)
            emit(UiState.Error(rootError.message ?: "메모를 불러오지 못했습니다."))
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = UiState.Loading
        )

    private val _draftText = MutableStateFlow("")
    val draftText: StateFlow<String> = _draftText.asStateFlow()

    private val _isSaving = MutableStateFlow(false)
    val isSaving: StateFlow<Boolean> = _isSaving.asStateFlow()

    private val _isExtraLargeText = MutableStateFlow(false)
    val isExtraLargeText: StateFlow<Boolean> = _isExtraLargeText.asStateFlow()

    private val _feedbackEvents = MutableSharedFlow<MemoFeedbackEvent>(extraBufferCapacity = 4)
    val feedbackEvents: SharedFlow<MemoFeedbackEvent> = _feedbackEvents.asSharedFlow()

    fun appendSpokenText(segment: String) {
        val clean = segment.trim()
        if (clean.isEmpty()) return
        val current = _draftText.value.trim()
        val combined = if (current.isEmpty()) clean else "$current $clean"
        _draftText.value = combined.take(VoiceMemoRepository.MAX_CONTENT_LENGTH)
    }

    fun updateDraftText(newText: String) {
        _draftText.value = newText.take(VoiceMemoRepository.MAX_CONTENT_LENGTH)
    }

    fun clearDraft() {
        _draftText.value = ""
    }

    fun toggleExtraLargeText() {
        _isExtraLargeText.value = !_isExtraLargeText.value
    }

    fun saveCurrentMemo(partialInFlight: String = "") {
        if (_isSaving.value) return
        val combined = listOf(_draftText.value.trim(), partialInFlight.trim())
            .filter { it.isNotEmpty() }
            .joinToString(" ")
        if (combined.isEmpty()) return
        if (combined.length > VoiceMemoRepository.MAX_CONTENT_LENGTH) {
            _feedbackEvents.tryEmit(MemoFeedbackEvent.TooLong)
            return
        }

        viewModelScope.launch {
            _isSaving.value = true
            val result = repository.addMemo(combined)
            _isSaving.value = false
            if (result.isSuccess) {
                _draftText.value = ""
                _feedbackEvents.emit(MemoFeedbackEvent.Saved)
            } else {
                _feedbackEvents.emit(MemoFeedbackEvent.SaveFailed)
            }
        }
    }

    fun deleteMemo(memo: VoiceMemo) {
        if (memo.id.isBlank()) return
        viewModelScope.launch {
            val result = repository.deleteMemo(memo.id)
            if (result.isSuccess) {
                _feedbackEvents.emit(MemoFeedbackEvent.Deleted(memo.content))
            } else {
                _feedbackEvents.emit(MemoFeedbackEvent.DeleteFailed)
            }
        }
    }

    fun restoreMemo(content: String) {
        val clean = content.trim()
        if (clean.isEmpty()) return
        viewModelScope.launch {
            val result = repository.addMemo(clean)
            if (result.isSuccess) {
                _feedbackEvents.emit(MemoFeedbackEvent.Restored)
            } else {
                _feedbackEvents.emit(MemoFeedbackEvent.SaveFailed)
            }
        }
    }

    private companion object {
        const val TAG = "VoiceMemoVM"
        const val STOP_TIMEOUT_MILLIS = 5000L
    }
}
