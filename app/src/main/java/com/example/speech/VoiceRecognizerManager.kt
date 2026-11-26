package com.example.speech

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import com.example.R
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.util.Locale

data class SpeechRecognitionState(
    val isListening: Boolean = false,
    val partialText: String = "",
    val voiceLevel: Float = 0f,
    val errorMessage: String? = null,
    val isAvailable: Boolean = true,
    val suggestSystemDialogFallback: Boolean = false
)

class VoiceRecognizerManager(
    context: Context,
    private val onFinalResult: (String) -> Unit
) {
    private val appContext = context.applicationContext
    private val mainHandler = Handler(Looper.getMainLooper())
    private var speechRecognizer: SpeechRecognizer? = null
    private var usingOnDeviceFallback = false
    private var usingDefaultLocaleFallback = false

    private val _state = MutableStateFlow(
        SpeechRecognitionState(
            isAvailable = isAnyRecognizerAvailable()
        )
    )
    val state: StateFlow<SpeechRecognitionState> = _state.asStateFlow()

    private val recognitionListener = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) {
            Log.d(TAG, "onReadyForSpeech")
            _state.update {
                it.copy(
                    isListening = true,
                    errorMessage = null,
                    voiceLevel = 0.12f,
                    suggestSystemDialogFallback = false
                )
            }
        }

        override fun onBeginningOfSpeech() {
            Log.d(TAG, "onBeginningOfSpeech")
            _state.update { it.copy(isListening = true, errorMessage = null) }
        }

        override fun onRmsChanged(rmsdB: Float) {
            val normalized = ((rmsdB + 2f) / 12f).coerceIn(0f, 1f)
            _state.update { it.copy(voiceLevel = normalized) }
        }

        override fun onBufferReceived(buffer: ByteArray?) = Unit

        override fun onEndOfSpeech() {
            Log.d(TAG, "onEndOfSpeech")
            _state.update { it.copy(voiceLevel = 0f) }
        }

        override fun onError(error: Int) {
            Log.w(TAG, "SpeechRecognizer onError code=$error, onDevice=$usingOnDeviceFallback")

            // If we already captured partial speech before timeout/no-match, preserve it
            val currentPartial = _state.value.partialText.trim()
            if (currentPartial.isNotEmpty() &&
                (error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT || error == SpeechRecognizer.ERROR_NO_MATCH)
            ) {
                onFinalResult(currentPartial)
                destroyRecognizerOnMain()
                _state.update {
                    it.copy(
                        isListening = false,
                        partialText = "",
                        voiceLevel = 0f,
                        errorMessage = null
                    )
                }
                return
            }

            // Automatic retry with device default locale if Korean pack is missing (API 31+ codes 12/13)
            if (!usingDefaultLocaleFallback &&
                (error == ERROR_LANGUAGE_NOT_SUPPORTED_CODE || error == ERROR_LANGUAGE_UNAVAILABLE_CODE)
            ) {
                usingDefaultLocaleFallback = true
                destroyRecognizerOnMain()
                startListeningInternal(preferOnDevice = usingOnDeviceFallback)
                return
            }

            // Automatic retry with on-device recognizer on API 33+ if network/server/client recognizer failed
            if (!usingOnDeviceFallback &&
                canUseOnDeviceRecognizer() &&
                (error == SpeechRecognizer.ERROR_CLIENT ||
                    error == SpeechRecognizer.ERROR_SERVER ||
                    error == SpeechRecognizer.ERROR_NETWORK ||
                    error == SpeechRecognizer.ERROR_NETWORK_TIMEOUT ||
                    error == ERROR_SERVER_DISCONNECTED_CODE)
            ) {
                usingOnDeviceFallback = true
                destroyRecognizerOnMain()
                startListeningInternal(preferOnDevice = true)
                return
            }

            // Always tear down recognizer after an error so next tap starts cleanly without ERROR_RECOGNIZER_BUSY
            destroyRecognizerOnMain()

            val suggestFallback = error == SpeechRecognizer.ERROR_CLIENT ||
                error == SpeechRecognizer.ERROR_SERVER ||
                error == SpeechRecognizer.ERROR_AUDIO ||
                error == SpeechRecognizer.ERROR_RECOGNIZER_BUSY ||
                error == ERROR_SERVER_DISCONNECTED_CODE

            val message = when (error) {
                SpeechRecognizer.ERROR_NO_MATCH,
                SpeechRecognizer.ERROR_SPEECH_TIMEOUT ->
                    appContext.getString(R.string.speech_error_no_match)

                SpeechRecognizer.ERROR_NETWORK,
                SpeechRecognizer.ERROR_NETWORK_TIMEOUT ->
                    appContext.getString(R.string.speech_error_network)

                SpeechRecognizer.ERROR_AUDIO ->
                    appContext.getString(R.string.speech_error_audio)

                SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS ->
                    appContext.getString(R.string.speech_error_permission)

                SpeechRecognizer.ERROR_RECOGNIZER_BUSY ->
                    appContext.getString(R.string.speech_error_busy)

                SpeechRecognizer.ERROR_CLIENT ->
                    appContext.getString(R.string.speech_error_client)

                SpeechRecognizer.ERROR_SERVER,
                ERROR_SERVER_DISCONNECTED_CODE ->
                    appContext.getString(R.string.speech_error_server)

                ERROR_LANGUAGE_NOT_SUPPORTED_CODE,
                ERROR_LANGUAGE_UNAVAILABLE_CODE ->
                    appContext.getString(R.string.speech_error_language)

                else -> appContext.getString(R.string.speech_error_generic, error)
            }

            _state.update {
                it.copy(
                    isListening = false,
                    partialText = "",
                    voiceLevel = 0f,
                    errorMessage = message,
                    suggestSystemDialogFallback = suggestFallback
                )
            }
        }

        override fun onResults(results: Bundle?) {
            val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            val bestText = matches?.firstOrNull()?.trim()
                ?: _state.value.partialText.trim()
            Log.d(TAG, "onResults length=${bestText.length}")
            if (bestText.isNotEmpty()) {
                onFinalResult(bestText)
            }
            destroyRecognizerOnMain()
            _state.update {
                it.copy(
                    isListening = false,
                    partialText = "",
                    voiceLevel = 0f,
                    errorMessage = null,
                    suggestSystemDialogFallback = false
                )
            }
        }

        override fun onPartialResults(partialResults: Bundle?) {
            val partialMatches =
                partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            val liveText = partialMatches?.firstOrNull()?.trim().orEmpty()
            if (liveText.isNotEmpty()) {
                _state.update { it.copy(partialText = liveText) }
            }
        }

        override fun onEvent(eventType: Int, params: Bundle?) = Unit
    }

    fun startListening() {
        runOnMain {
            usingOnDeviceFallback = false
            usingDefaultLocaleFallback = false

            val standardAvailable = SpeechRecognizer.isRecognitionAvailable(appContext)
            val onDeviceAvailable = canUseOnDeviceRecognizer()

            if (!standardAvailable && !onDeviceAvailable) {
                _state.update {
                    it.copy(
                        isAvailable = false,
                        isListening = false,
                        errorMessage = appContext.getString(R.string.speech_unavailable),
                        suggestSystemDialogFallback = true
                    )
                }
                return@runOnMain
            }

            startListeningInternal(preferOnDevice = !standardAvailable && onDeviceAvailable)
        }
    }

    private fun startListeningInternal(preferOnDevice: Boolean) {
        runOnMain {
            // Recreate fresh recognizer every session to avoid stale binder / ERROR_CLIENT / BUSY states
            destroyRecognizerOnMain()

            val recognizer = try {
                if (preferOnDevice && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    SpeechRecognizer.createOnDeviceSpeechRecognizer(appContext)
                } else {
                    SpeechRecognizer.createSpeechRecognizer(appContext)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to create SpeechRecognizer", e)
                null
            }

            if (recognizer == null) {
                _state.update {
                    it.copy(
                        isAvailable = false,
                        isListening = false,
                        errorMessage = appContext.getString(R.string.speech_unavailable),
                        suggestSystemDialogFallback = true
                    )
                }
                return@runOnMain
            }

            recognizer.setRecognitionListener(recognitionListener)
            speechRecognizer = recognizer

            val languageTag = if (usingDefaultLocaleFallback) {
                Locale.getDefault().toLanguageTag()
            } else {
                "ko-KR"
            }

            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(
                    RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                    RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
                )
                putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, appContext.packageName)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, languageTag)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, languageTag)
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
                putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 3000L)
                putExtra(
                    RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS,
                    2500L
                )
            }

            _state.update {
                it.copy(
                    isAvailable = true,
                    isListening = true,
                    partialText = "",
                    errorMessage = null,
                    suggestSystemDialogFallback = false
                )
            }

            try {
                recognizer.startListening(intent)
            } catch (e: Exception) {
                Log.e(TAG, "startListening failed", e)
                destroyRecognizerOnMain()
                _state.update {
                    it.copy(
                        isListening = false,
                        errorMessage = appContext.getString(R.string.speech_error_client),
                        suggestSystemDialogFallback = true
                    )
                }
            }
        }
    }

    fun stopListening() {
        runOnMain {
            val pendingPartial = _state.value.partialText.trim()
            try {
                speechRecognizer?.stopListening()
            } catch (e: Exception) {
                Log.w(TAG, "stopListening failed", e)
                if (pendingPartial.isNotEmpty()) {
                    onFinalResult(pendingPartial)
                }
                destroyRecognizerOnMain()
            }
            _state.update { it.copy(isListening = false, voiceLevel = 0f) }
        }
    }

    fun buildSystemSpeechIntent(): Intent? {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(
                RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
            )
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ko-KR")
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "ko-KR")
            putExtra(
                RecognizerIntent.EXTRA_PROMPT,
                appContext.getString(R.string.speech_system_dialog_prompt)
            )
        }
        return if (intent.resolveActivity(appContext.packageManager) != null) intent else null
    }

    fun showSystemDialogUnavailableError() {
        _state.update {
            it.copy(
                errorMessage = appContext.getString(R.string.speech_system_dialog_unavailable),
                suggestSystemDialogFallback = false
            )
        }
    }

    fun clearError() {
        _state.update { it.copy(errorMessage = null, suggestSystemDialogFallback = false) }
    }

    fun release() {
        runOnMain {
            destroyRecognizerOnMain()
        }
    }

    private fun destroyRecognizerOnMain() {
        try {
            speechRecognizer?.cancel()
            speechRecognizer?.destroy()
        } catch (_: Exception) {
            // Safe teardown
        } finally {
            speechRecognizer = null
        }
    }

    private fun isAnyRecognizerAvailable(): Boolean {
        return SpeechRecognizer.isRecognitionAvailable(appContext) || canUseOnDeviceRecognizer()
    }

    private fun canUseOnDeviceRecognizer(): Boolean {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            SpeechRecognizer.isOnDeviceRecognitionAvailable(appContext)
    }

    private fun runOnMain(block: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            block()
        } else {
            mainHandler.post(block)
        }
    }

    private companion object {
        const val TAG = "VoiceRecognizer"
        const val ERROR_SERVER_DISCONNECTED_CODE = 11
        const val ERROR_LANGUAGE_NOT_SUPPORTED_CODE = 12
        const val ERROR_LANGUAGE_UNAVAILABLE_CODE = 13
    }
}
