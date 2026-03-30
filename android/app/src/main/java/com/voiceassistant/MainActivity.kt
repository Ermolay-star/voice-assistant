package com.voiceassistant

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import com.voiceassistant.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val vm: AssistantViewModel by viewModels()
    private lateinit var voice: VoiceAssistantService
    private val adapter = MessageAdapter()
    private var listening = false

    // ── Запросы разрешений ────────────────────────────────────────────────

    private val permAudio = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        if (grants[Manifest.permission.RECORD_AUDIO] == true) startListening()
        else Toast.makeText(this, "Нужно разрешение на микрофон", Toast.LENGTH_SHORT).show()
    }

    private val permCall = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) vm.callIntent.value?.let { startActivity(it) }
        vm.clearCallIntent()
    }

    // ── Lifecycle ─────────────────────────────────────────────────────────

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupRecycler()
        setupVoice()
        setupInput()
        setupWakeSwitch()
        observe()

        binding.btnMic.setOnClickListener {
            if (listening) stopListening() else checkAndListen()
        }
        binding.btnNewChat.setOnClickListener { vm.clearChat() }
        binding.btnVoice.setOnClickListener {
            voice.getCurrentTts()?.let { tts ->
                VoicePickerSheet(tts, voice.getCurrentVoice()) { v ->
                    voice.setVoice(v)
                    Toast.makeText(this, "Голос: ${v.locale.displayLanguage}", Toast.LENGTH_SHORT).show()
                }.show(supportFragmentManager, "voices")
            }
        }

        // Если сервис передал команду звонка через Intent
        handleIncomingIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIncomingIntent(intent)
    }

    override fun onDestroy() { super.onDestroy(); voice.destroy() }

    // ── Intent от фонового сервиса (звонок) ──────────────────────────────

    private fun handleIncomingIntent(intent: Intent?) {
        val contact = intent?.getStringExtra("CALL_CONTACT") ?: return
        vm.addUser("Позвони $contact")
        vm.handleCommand(com.voiceassistant.model.Command.Call(contact))
    }

    // ── Wake switch ───────────────────────────────────────────────────────

    private fun setupWakeSwitch() {
        binding.switchWake.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) startWakeService() else stopWakeService()
        }
    }

    private fun startWakeService() {
        val ok = ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
        if (ok != PackageManager.PERMISSION_GRANTED) {
            permAudio.launch(arrayOf(Manifest.permission.RECORD_AUDIO, Manifest.permission.READ_CONTACTS))
            binding.switchWake.isChecked = false
            return
        }
        val svcIntent = Intent(this, AssistantForegroundService::class.java)
            .apply { action = AssistantForegroundService.ACTION_START }
        startForegroundService(svcIntent)
        binding.tvWakeStatus.text = "Слушаю «Ассистент»…"
    }

    private fun stopWakeService() {
        val svcIntent = Intent(this, AssistantForegroundService::class.java)
            .apply { action = AssistantForegroundService.ACTION_STOP }
        startService(svcIntent)
        binding.tvWakeStatus.text = "Фоновый режим выключен"
    }

    // ── Setup ─────────────────────────────────────────────────────────────

    private fun setupRecycler() {
        binding.rvMessages.layoutManager =
            LinearLayoutManager(this).apply { stackFromEnd = true }
        binding.rvMessages.adapter = adapter
    }

    private fun setupVoice() {
        voice = VoiceAssistantService(this)
        voice.listener = object : VoiceAssistantService.Listener {
            override fun onListeningStarted() = runOnUiThread {
                listening = true; micActive(true)
                binding.etInput.hint = "Слушаю…"
            }
            override fun onPartialResult(text: String) = runOnUiThread {
                binding.etInput.hint = "«$text»"
            }
            override fun onResult(text: String) = runOnUiThread {
                listening = false; micActive(false)
                binding.etInput.hint = getString(R.string.hint_message)
                vm.addUser(text)
                vm.handleCommand(CommandParser.parse(text))
            }
            override fun onError(message: String) = runOnUiThread {
                listening = false; micActive(false)
                binding.etInput.hint = getString(R.string.hint_message)
                Toast.makeText(this@MainActivity, message, Toast.LENGTH_SHORT).show()
            }
            override fun onSpeechFinished() {}
        }
    }

    private fun setupInput() {
        binding.etInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, st: Int, c: Int, a: Int) {}
            override fun onTextChanged(s: CharSequence?, st: Int, b: Int, c: Int) {}
            override fun afterTextChanged(s: Editable?) {
                val has = !s.isNullOrBlank()
                binding.btnSend.visibility = if (has) View.VISIBLE else View.GONE
                binding.btnMic.visibility  = if (has) View.GONE  else View.VISIBLE
            }
        })
        binding.btnSend.setOnClickListener {
            val text = binding.etInput.text.toString().trim()
            if (text.isNotEmpty()) { binding.etInput.text.clear(); vm.sendText(text) }
        }
    }

    // ── Observe ───────────────────────────────────────────────────────────

    private fun observe() {
        vm.messages.observe(this) { msgs ->
            adapter.submitList(msgs.toList())
            if (msgs.isNotEmpty()) binding.rvMessages.scrollToPosition(msgs.size - 1)
            msgs.lastOrNull { !it.isUser }?.let { voice.speak(it.text) }
        }
        vm.typing.observe(this) { isTyping ->
            binding.tvTyping.visibility = if (isTyping) View.VISIBLE else View.GONE
        }
        vm.callIntent.observe(this) { intent ->
            intent ?: return@observe
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.CALL_PHONE)
                == PackageManager.PERMISSION_GRANTED
            ) {
                startActivity(intent); vm.clearCallIntent()
            } else {
                permCall.launch(Manifest.permission.CALL_PHONE)
            }
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────

    private fun checkAndListen() {
        val a = ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
        val c = ContextCompat.checkSelfPermission(this, Manifest.permission.READ_CONTACTS)
        if (a == PackageManager.PERMISSION_GRANTED && c == PackageManager.PERMISSION_GRANTED) {
            startListening()
        } else {
            permAudio.launch(arrayOf(Manifest.permission.RECORD_AUDIO, Manifest.permission.READ_CONTACTS))
        }
    }

    private fun startListening() = voice.startListening()

    private fun stopListening() {
        listening = false; micActive(false); voice.stopListening()
    }

    private fun micActive(active: Boolean) {
        val color = ContextCompat.getColor(
            this, if (active) R.color.mic_active else R.color.mic_idle
        )
        binding.btnMic.backgroundTintList = android.content.res.ColorStateList.valueOf(color)
    }
}
