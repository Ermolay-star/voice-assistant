package com.voiceassistant

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
import java.util.Locale

class VoiceAssistantService(private val context: Context) {

    interface Listener {
        fun onListeningStarted()
        fun onPartialResult(text: String)
        fun onResult(text: String)
        fun onError(message: String)
        fun onSpeechFinished()
    }

    var listener: Listener? = null

    private val recognizer = SpeechRecognizer.createSpeechRecognizer(context)
    private var tts: TextToSpeech? = null
    private var ttsReady = false

    init {
        setupRecognizer()
        setupTts()
    }

    // ── Speech Recognizer ─────────────────────────────────────────────────

    private fun setupRecognizer() {
        recognizer.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(p: Bundle?) { listener?.onListeningStarted() }
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(v: Float) {}
            override fun onBufferReceived(b: ByteArray?) {}
            override fun onEndOfSpeech() {}
            override fun onEvent(t: Int, p: Bundle?) {}

            override fun onError(error: Int) {
                val msg = when (error) {
                    SpeechRecognizer.ERROR_NO_MATCH, SpeechRecognizer.ERROR_SPEECH_TIMEOUT ->
                        "Не расслышал — попробуй ещё раз"
                    SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS ->
                        "Нет разрешения на микрофон"
                    SpeechRecognizer.ERROR_NETWORK ->
                        "Нет интернета для распознавания речи"
                    else -> "Ошибка распознавания ($error)"
                }
                listener?.onError(msg)
            }

            override fun onResults(results: Bundle?) {
                val text = results
                    ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    ?.firstOrNull() ?: return
                listener?.onResult(text)
            }

            override fun onPartialResults(partial: Bundle?) {
                val text = partial
                    ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    ?.firstOrNull() ?: return
                listener?.onPartialResult(text)
            }
        })
    }

    fun startListening() {
        recognizer.startListening(
            Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ru-RU")
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            }
        )
    }

    fun stopListening() = recognizer.stopListening()

    // ── Text To Speech ────────────────────────────────────────────────────

    private fun setupTts() {
        tts = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts?.language = Locale("ru", "RU")
                ttsReady = true
                tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(id: String?) {}
                    override fun onDone(id: String?) { listener?.onSpeechFinished() }
                    @Deprecated("Deprecated in Java")
                    override fun onError(id: String?) {}
                })
            }
        }
    }

    fun speak(text: String) {
        if (!ttsReady) return
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "VA_UTT")
    }

    fun stopSpeaking() = tts?.stop()

    /** Возвращает экземпляр TTS для VoicePickerSheet. */
    fun getCurrentTts(): TextToSpeech? = if (ttsReady) tts else null

    /** Возвращает список доступных offline-голосов, русские — первыми. */
    fun getAvailableVoices(): List<Voice> =
        tts?.voices
            ?.filter { !it.isNetworkConnectionRequired }
            ?.sortedWith(
                compareByDescending<Voice> { it.locale.language == "ru" }.thenBy { it.name }
            ) ?: emptyList()

    fun getCurrentVoice(): Voice? = tts?.voice

    fun setVoice(voice: Voice) {
        tts?.voice = voice
    }

    fun destroy() {
        recognizer.destroy()
        tts?.shutdown()
    }
}
