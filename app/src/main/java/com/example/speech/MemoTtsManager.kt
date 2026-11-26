package com.example.speech

import android.content.Context
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale

class MemoTtsManager(
    context: Context,
    private val onError: () -> Unit = {}
) : TextToSpeech.OnInitListener {

    private var tts: TextToSpeech? = TextToSpeech(context.applicationContext, this)
    private var isReady = false

    private val _speakingMemoId = MutableStateFlow<String?>(null)
    val speakingMemoId: StateFlow<String?> = _speakingMemoId.asStateFlow()

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val engine = tts ?: return
            val koreanResult = engine.setLanguage(Locale.KOREA)
            if (koreanResult == TextToSpeech.LANG_MISSING_DATA ||
                koreanResult == TextToSpeech.LANG_NOT_SUPPORTED
            ) {
                engine.language = Locale.getDefault()
            }
            // Slightly slower, crisp cadence for comfortable listening
            engine.setSpeechRate(0.90f)
            engine.setPitch(1.0f)
            engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {
                    _speakingMemoId.value = utteranceId
                }

                override fun onDone(utteranceId: String?) {
                    if (_speakingMemoId.value == utteranceId) {
                        _speakingMemoId.value = null
                    }
                }

                @Deprecated("Deprecated in Java")
                override fun onError(utteranceId: String?) {
                    if (_speakingMemoId.value == utteranceId) {
                        _speakingMemoId.value = null
                    }
                }

                override fun onError(utteranceId: String?, errorCode: Int) {
                    if (_speakingMemoId.value == utteranceId) {
                        _speakingMemoId.value = null
                    }
                }
            })
            isReady = true
        } else {
            isReady = false
        }
    }

    fun toggleSpeak(memoId: String, text: String) {
        if (_speakingMemoId.value == memoId) {
            stop()
            return
        }
        val engine = tts
        if (!isReady || engine == null) {
            onError()
            return
        }
        val cleanText = text.trim()
        if (cleanText.isEmpty()) return

        _speakingMemoId.value = memoId
        val params = Bundle().apply {
            putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, memoId)
        }
        val result = engine.speak(cleanText, TextToSpeech.QUEUE_FLUSH, params, memoId)
        if (result == TextToSpeech.ERROR) {
            _speakingMemoId.value = null
            onError()
        }
    }

    fun stop() {
        try {
            tts?.stop()
        } catch (_: Exception) {
            // Ignore stop failures during teardown
        } finally {
            _speakingMemoId.value = null
        }
    }

    fun release() {
        stop()
        try {
            tts?.shutdown()
        } catch (_: Exception) {
            // Safe shutdown
        } finally {
            tts = null
            isReady = false
        }
    }
}
