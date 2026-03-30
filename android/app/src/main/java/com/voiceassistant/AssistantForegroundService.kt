package com.voiceassistant

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.util.Locale

/**
 * Фоновый сервис, который непрерывно слушает будильное слово «Ассистент».
 * После его обнаружения переходит в режим приёма команды, отправляет
 * запрос на бэкенд и озвучивает ответ.
 *
 * Цикл:
 *   IDLE → слышит «ассистент» → ACTIVE (принимает команду)
 *        → PROCESSING (запрос к GigaChat) → озвучивает → IDLE
 */
class AssistantForegroundService : Service() {

    companion object {
        const val ACTION_START = "ACTION_START"
        const val ACTION_STOP  = "ACTION_STOP"

        private const val CHANNEL_ID = "assistant_channel"
        private const val NOTIF_ID   = 1

        /** Ключевое слово для активации. Можно изменить на любое другое. */
        private val WAKE_WORDS = listOf("ассистент", "assistant", "привет ассистент")
    }

    private enum class State { IDLE, ACTIVE, PROCESSING }

    private var state = State.IDLE
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val mainHandler = Handler(Looper.getMainLooper())

    private var recognizer: SpeechRecognizer? = null
    private var tts: TextToSpeech? = null
    private var ttsReady = false

    // История диалога для GigaChat
    private val history = mutableListOf<ChatMessage>()

    // ── Lifecycle ─────────────────────────────────────────────────────────

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForeground(NOTIF_ID, buildNotification("Скажи «Ассистент»…"))
        // SpeechRecognizer обязан создаваться на главном потоке
        mainHandler.post {
            setupTts()
            setupRecognizer()
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) stopSelf()
        return START_STICKY
    }

    override fun onDestroy() {
        scope.cancel()
        mainHandler.post {
            recognizer?.destroy()
            tts?.shutdown()
        }
        super.onDestroy()
    }

    // ── TTS ───────────────────────────────────────────────────────────────

    private fun setupTts() {
        tts = TextToSpeech(this) { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts?.language = Locale("ru", "RU")
                ttsReady = true
                // После инициализации TTS начинаем слушать
                mainHandler.postDelayed({ startListening(wakeWordMode = true) }, 500)
            }
        }
    }

    private fun speak(text: String, onDone: (() -> Unit)? = null) {
        if (!ttsReady) { onDone?.invoke(); return }
        tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(id: String?) {}
            override fun onDone(id: String?) { mainHandler.post { onDone?.invoke() } }
            @Deprecated("Deprecated in Java")
            override fun onError(id: String?) { mainHandler.post { onDone?.invoke() } }
        })
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "SVC_UTT")
    }

    // ── SpeechRecognizer ──────────────────────────────────────────────────

    private fun setupRecognizer() {
        recognizer = SpeechRecognizer.createSpeechRecognizer(this).apply {
            setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(p: android.os.Bundle?) {}
                override fun onBeginningOfSpeech() {}
                override fun onRmsChanged(v: Float) {}
                override fun onBufferReceived(b: ByteArray?) {}
                override fun onEndOfSpeech() {}
                override fun onEvent(t: Int, p: android.os.Bundle?) {}

                override fun onResults(results: android.os.Bundle?) {
                    val matches = results
                        ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        ?: run { scheduleRestart(); return }

                    when (state) {
                        State.IDLE -> {
                            val text = matches.joinToString(" ").lowercase()
                            if (WAKE_WORDS.any { text.contains(it) }) {
                                onWakeWordDetected()
                            } else {
                                scheduleRestart()
                            }
                        }
                        State.ACTIVE -> {
                            val command = matches.firstOrNull() ?: run { scheduleRestart(); return }
                            onCommandReceived(command)
                        }
                        State.PROCESSING -> {}
                    }
                }

                override fun onPartialResults(partial: android.os.Bundle?) {
                    if (state == State.IDLE) {
                        val text = partial
                            ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                            ?.firstOrNull()?.lowercase() ?: return
                        if (WAKE_WORDS.any { text.contains(it) }) {
                            recognizer?.stopListening()
                        }
                    }
                }

                override fun onError(error: Int) {
                    // Большинство ошибок — просто тишина или таймаут; перезапускаем
                    when (error) {
                        SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> {
                            // Пересоздаём распознаватель
                            mainHandler.postDelayed({
                                recognizer?.destroy()
                                setupRecognizer()
                            }, 500)
                        }
                        else -> scheduleRestart()
                    }
                }
            })
        }
    }

    private fun startListening(wakeWordMode: Boolean) {
        state = if (wakeWordMode) State.IDLE else State.ACTIVE
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ru-RU")
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
            // В режиме ожидания слушаем дольше
            if (wakeWordMode) {
                putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 3000L)
                putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 3000L)
            }
        }
        recognizer?.startListening(intent)
    }

    /** Перезапуск через небольшую паузу (чтобы не перегружать систему). */
    private fun scheduleRestart(delayMs: Long = 300) {
        if (state == State.PROCESSING) return
        mainHandler.postDelayed({ startListening(wakeWordMode = true) }, delayMs)
    }

    // ── Логика состояний ──────────────────────────────────────────────────

    private fun onWakeWordDetected() {
        state = State.ACTIVE
        updateNotification("Слушаю…")
        speak("Слушаю") {
            startListening(wakeWordMode = false)
        }
    }

    private fun onCommandReceived(command: String) {
        state = State.PROCESSING
        updateNotification("«$command»")

        // Звонок — обрабатываем напрямую
        val parsed = CommandParser.parse(command)
        if (parsed is com.voiceassistant.model.Command.Call) {
            speak("Звоню ${parsed.contact}")
            val intent = Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
                putExtra("CALL_CONTACT", parsed.contact)
            }
            startActivity(intent)
            scheduleRestart(1500)
            return
        }

        // Вопрос → GigaChat
        history.add(ChatMessage("user", command))
        scope.launch {
            try {
                val resp = BackendApiClient.api.ask(AskRequest(history.toList()))
                history.add(ChatMessage("assistant", resp.answer))
                mainHandler.post {
                    updateNotification(resp.answer.take(80))
                    speak(resp.answer) { scheduleRestart(500) }
                }
            } catch (e: Exception) {
                mainHandler.post {
                    speak("Ошибка соединения") { scheduleRestart(1000) }
                }
            }
        }
    }

    // ── Notification ──────────────────────────────────────────────────────

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID, "Голосовой ассистент",
            NotificationManager.IMPORTANCE_LOW,
        ).apply { description = "Фоновое прослушивание" }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun buildNotification(text: String) =
        NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Голосовой ассистент")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setOngoing(true)
            .addAction(
                android.R.drawable.ic_delete,
                "Остановить",
                PendingIntent.getService(
                    this, 0,
                    Intent(this, AssistantForegroundService::class.java).apply {
                        action = ACTION_STOP
                    },
                    PendingIntent.FLAG_IMMUTABLE,
                )
            )
            .setContentIntent(
                PendingIntent.getActivity(
                    this, 0,
                    Intent(this, MainActivity::class.java),
                    PendingIntent.FLAG_IMMUTABLE,
                )
            )
            .build()

    private fun updateNotification(text: String) {
        getSystemService(NotificationManager::class.java)
            .notify(NOTIF_ID, buildNotification(text))
    }
}
