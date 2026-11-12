package com.joker.ai

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.provider.Settings
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.POST
import java.util.Calendar
import java.util.Locale
import java.util.concurrent.TimeUnit

data class ChatRequest(val message: String)
data class ChatResponse(
    val reply: String,
    val action: String? = null,
    val target: String? = null
)

interface JokerApiService {
    @POST("/api/chat")
    suspend fun sendMessage(@Body req: ChatRequest): ChatResponse
}

class MainActivity : AppCompatActivity() {

    private val BASE_URL = "https://joker-ai-brain.onrender.com/"

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    private val api: JokerApiService by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(JokerApiService::class.java)
    }

    private var tts: TextToSpeech? = null
    private var speechRecognizer: SpeechRecognizer? = null
    private var isListening = false

    private lateinit var layoutHome: View
    private lateinit var layoutChat: View
    private lateinit var tvNavHomeText: TextView
    private lateinit var tvNavChatText: TextView

    private lateinit var jokerOrb: JokerOrbView
    private lateinit var tvOrbStatus: TextView
    private lateinit var tvGreeting: TextView
    private lateinit var chatLog: TextView
    private lateinit var scrollView: ScrollView
    private lateinit var micBtn: ImageButton

    private val audioPermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            startInAppListening()
        } else {
            Toast.makeText(this, "Microphone permission zaroori hai", Toast.LENGTH_SHORT).show()
        }
    }

    private val fallbackSpeechLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK && result.data != null) {
            val matches = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
            val spokenText = matches?.firstOrNull()
            if (!spokenText.isNullOrEmpty()) {
                processMessage(spokenText)
            }
        } else {
            setOrbIdle()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Views Initialization
        layoutHome = findViewById(R.id.layoutHome)
        layoutChat = findViewById(R.id.layoutChat)
        tvNavHomeText = findViewById(R.id.tvNavHomeText)
        tvNavChatText = findViewById(R.id.tvNavChatText)

        jokerOrb = findViewById(R.id.jokerOrb)
        tvOrbStatus = findViewById(R.id.tvOrbStatus)
        tvGreeting = findViewById(R.id.tvGreeting)
        chatLog = findViewById(R.id.tvChat)
        scrollView = findViewById(R.id.scrollView)

        val input = findViewById<EditText>(R.id.etMessage)
        val sendBtn = findViewById<Button>(R.id.btnSend)
        micBtn = findViewById(R.id.btnMic)

        val navHome = findViewById<View>(R.id.navHome)
        val navChat = findViewById<View>(R.id.navChat)
        val navSettings = findViewById<View>(R.id.navSettings)
        val navUpdates = findViewById<View>(R.id.navUpdates)

        val btnScreen = findViewById<View>(R.id.btnScreen)
        val btnMemory = findViewById<View>(R.id.btnMemory)
        val btnLanguage = findViewById<View>(R.id.btnLanguage)

        // Dynamic Time Greeting
        updateGreeting()

        // Setup TTS with Voice Reactive Orb
        setupTTS()

        // Setup Speech Recognizer
        setupSpeechRecognizer()

        // 1. Home Tab Click
        navHome.setOnClickListener {
            switchToHomeTab()
        }

        // 2. Chat Tab Click
        navChat.setOnClickListener {
            switchToChatTab()
        }

        // 3. Center Mic Click (Home par aakar aawaz sunega)
        micBtn.setOnClickListener {
            switchToHomeTab()
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                startInAppListening()
            } else {
                audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            }
        }

        // 4. Settings Click (Opens System Settings)
        navSettings.setOnClickListener {
            try {
                val intent = Intent(Settings.ACTION_SETTINGS).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                startActivity(intent)
            } catch (e: Exception) {
                Toast.makeText(this, "Settings open nahi ho paaya", Toast.LENGTH_SHORT).show()
            }
        }

        // 5. Updates Click (Shows System Update Dialog)
        navUpdates.setOnClickListener {
            showUpdatesDialog()
        }

        // Quick Action Pills
        btnScreen.setOnClickListener {
            Toast.makeText(this, "📱 Screen Intelligence: Active", Toast.LENGTH_SHORT).show()
        }
        btnMemory.setOnClickListener {
            Toast.makeText(this, "🧠 Neural Memory: 100% Operational", Toast.LENGTH_SHORT).show()
        }
        btnLanguage.setOnClickListener {
            Toast.makeText(this, "🌐 Language: Hindi / English Auto-Detect", Toast.LENGTH_SHORT).show()
        }

        // Send Button Click
        sendBtn.setOnClickListener {
            val userText = input.text.toString().trim()
            if (userText.isNotEmpty()) {
                input.text.clear()
                processMessage(userText)
            }
        }
    }

    private fun switchToHomeTab() {
        layoutHome.visibility = View.VISIBLE
        layoutChat.visibility = View.GONE
        tvNavHomeText.setTextColor(Color.parseColor("#FF0033"))
        tvNavChatText.setTextColor(Color.parseColor("#888899"))
    }

    private fun switchToChatTab() {
        layoutHome.visibility = View.GONE
        layoutChat.visibility = View.VISIBLE
        tvNavHomeText.setTextColor(Color.parseColor("#888899"))
        tvNavChatText.setTextColor(Color.parseColor("#FF0033"))
    }

    private fun updateGreeting() {
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        val greet = when (hour) {
            in 4..11 -> "Good morning, Boss"
            in 12..16 -> "Good afternoon, Boss"
            in 17..21 -> "Good evening, Boss"
            else -> "Good night, Boss"
        }
        tvGreeting.text = greet
    }

    private fun showUpdatesDialog() {
        AlertDialog.Builder(this)
            .setTitle("🚀 Joker AI System Updates")
            .setMessage("Version: 2.5.0 (Latest)\nStatus: All Neural Systems Operational\n\nChangelog:\n• Video 1 3D Fluid Red Particle Orb\n• Dedicated Clean Home & Chat Tabs\n• Center Elevated Voice Controller\n• Direct Phone Settings Launcher\n• Autonomous App Actions Enabled")
            .setPositiveButton("Awesome", null)
            .show()
    }

    private fun setupTTS() {
        tts = TextToSpeech(this) { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts?.language = Locale.getDefault()
                tts?.setSpeechRate(1.05f)

                tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) {
                        runOnUiThread {
                            jokerOrb.setState(OrbState.SPEAKING)
                            tvOrbStatus.text = "SPEAKING... // AUDIO OUT"
                        }
                    }

                    override fun onDone(utteranceId: String?) {
                        runOnUiThread { setOrbIdle() }
                    }

                    override fun onError(utteranceId: String?) {
                        runOnUiThread { setOrbIdle() }
                    }
                })
            }
        }
    }

    private fun setOrbIdle() {
        jokerOrb.setState(OrbState.IDLE)
        tvOrbStatus.text = "● STANDBY // READY FOR COMMANDS"
        micBtn.clearColorFilter()
    }

    private fun setupSpeechRecognizer() {
        if (!SpeechRecognizer.isRecognitionAvailable(this)) return

        try {
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this).apply {
                setRecognitionListener(object : RecognitionListener {
                    override fun onReadyForSpeech(params: Bundle?) {
                        micBtn.setColorFilter(Color.parseColor("#00FF66"))
                        jokerOrb.setState(OrbState.LISTENING)
                        tvOrbStatus.text = "● LISTENING... // AUDIO FEED ACTIVE"
                    }

                    override fun onBeginningOfSpeech() {}
                    override fun onRmsChanged(rmsdB: Float) {}
                    override fun onBufferReceived(buffer: ByteArray?) {}

                    override fun onEndOfSpeech() {
                        isListening = false
                        jokerOrb.setState(OrbState.THINKING)
                        tvOrbStatus.text = "● PROCESSING... // NEURAL NET"
                        micBtn.clearColorFilter()
                    }

                    override fun onError(error: Int) {
                        isListening = false
                        micBtn.clearColorFilter()
                        if (error == SpeechRecognizer.ERROR_NO_MATCH || error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT) {
                            setOrbIdle()
                            Toast.makeText(this@MainActivity, "Kuch sunai nahi diya, dobara boliye", Toast.LENGTH_SHORT).show()
                        } else {
                            startFallbackVoiceInput()
                        }
                    }

                    override fun onResults(results: Bundle?) {
                        isListening = false
                        micBtn.clearColorFilter()
                        val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        val spokenText = matches?.firstOrNull()
                        if (!spokenText.isNullOrEmpty()) {
                            processMessage(spokenText)
                        } else {
                            setOrbIdle()
                        }
                    }

                    override fun onPartialResults(partialResults: Bundle?) {}
                    override fun onEvent(eventType: Int, params: Bundle?) {}
                })
            }
        } catch (e: Exception) {
            speechRecognizer = null
        }
    }

    private fun startInAppListening() {
        if (isListening) {
            speechRecognizer?.stopListening()
            isListening = false
            setOrbIdle()
            return
        }

        if (speechRecognizer != null) {
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
                putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, packageName)
            }
            isListening = true
            try {
                speechRecognizer?.startListening(intent)
            } catch (e: Exception) {
                startFallbackVoiceInput()
            }
        } else {
            startFallbackVoiceInput()
        }
    }

    private fun startFallbackVoiceInput() {
        isListening = false
        micBtn.clearColorFilter()
        jokerOrb.setState(OrbState.LISTENING)
        tvOrbStatus.text = "● LISTENING... // AUDIO IN"
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
        }
        try {
            fallbackSpeechLauncher.launch(intent)
        } catch (e: Exception) {
            setOrbIdle()
            Toast.makeText(this, "Voice recognition support nahi hai", Toast.LENGTH_SHORT).show()
        }
    }

    private fun processMessage(userText: String) {
        chatLog.append("\nYou: $userText\n")
        chatLog.append("🃏 Joker AI: Soch raha hoon... 💭\n")
        scrollView.post { scrollView.fullScroll(ScrollView.FOCUS_DOWN) }

        jokerOrb.setState(OrbState.THINKING)
        tvOrbStatus.text = "● THINKING... // NEURAL PROCESSING"

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val response = api.sendMessage(ChatRequest(userText))
                withContext(Dispatchers.Main) {
                    val currentText = chatLog.text.toString()
                    val cleanText = currentText.replace("🃏 Joker AI: Soch raha hoon... 💭\n", "")
                    chatLog.text = cleanText
                    chatLog.append("🃏 Joker AI: ${response.reply}\n\n")
                    scrollView.post { scrollView.fullScroll(ScrollView.FOCUS_DOWN) }

                    tts?.speak(response.reply, TextToSpeech.QUEUE_FLUSH, null, "JokerResponse")

                    val finalAction = detectAction(response.action, userText)
                    val finalTarget = response.target ?: extractTarget(userText)

                    if (finalAction != "NONE") {
                        executeAgentAction(finalAction, finalTarget)
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    setOrbIdle()
                    val currentText = chatLog.text.toString()
                    val cleanText = currentText.replace("🃏 Joker AI: Soch raha hoon... 💭\n", "")
                    chatLog.text = cleanText
                    chatLog.append("🃏 Joker AI [Error]: ${e.localizedMessage ?: "Connection error"}\n\n")
                    scrollView.post { scrollView.fullScroll(ScrollView.FOCUS_DOWN) }
                }
            }
        }
    }

    private fun detectAction(serverAction: String?, userText: String): String {
        if (!serverAction.isNullOrEmpty() && serverAction != "NONE") {
            return serverAction
        }
        val lower = userText.lowercase()
        return when {
            lower.contains("youtube") -> "OPEN_YOUTUBE"
            lower.contains("whatsapp") -> "OPEN_WHATSAPP"
            lower.contains("camera") -> "OPEN_CAMERA"
            lower.contains("setting") -> "OPEN_SETTINGS"
            lower.contains("search") || lower.contains("khojo") || lower.contains("google") -> "WEB_SEARCH"
            else -> "NONE"
        }
    }

    private fun extractTarget(userText: String): String {
        val lower = userText.lowercase()
        return if (lower.contains("search") || lower.contains("google")) {
            userText.replace(Regex("(?i)google|search|par|karo|khojo|in"), "").trim()
        } else ""
    }

    private fun executeAgentAction(action: String, target: String) {
        CoroutineScope(Dispatchers.Main).launch {
            delay(1000)
            try {
                when (action) {
                    "OPEN_YOUTUBE" -> {
                        val launchIntent = packageManager.getLaunchIntentForPackage("com.google.android.youtube")
                        if (launchIntent != null) {
                            launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            startActivity(launchIntent)
                        } else {
                            val webIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.youtube.com"))
                            webIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            startActivity(webIntent)
                        }
                    }
                    "OPEN_WHATSAPP" -> {
                        val launchIntent = packageManager.getLaunchIntentForPackage("com.whatsapp")
                        if (launchIntent != null) {
                            launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            startActivity(launchIntent)
                        } else {
                            val waWeb = Intent(Intent.ACTION_VIEW, Uri.parse("https://wa.me/"))
                            waWeb.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            startActivity(waWeb)
                        }
                    }
                    "OPEN_CAMERA" -> {
                        val cameraIntent = Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA)
                        cameraIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        startActivity(cameraIntent)
                    }
                    "OPEN_SETTINGS" -> {
                        val settingsIntent = Intent(Settings.ACTION_SETTINGS)
                        settingsIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        startActivity(settingsIntent)
                    }
                    "WEB_SEARCH" -> {
                        val query = if (target.isNotEmpty()) target else "latest news"
                        val searchIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/search?q=${Uri.encode(query)}"))
                        searchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        startActivity(searchIntent)
                    }
                }
            } catch (e: Exception) {
                Toast.makeText(this@MainActivity, "App open error: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onDestroy() {
        speechRecognizer?.destroy()
        tts?.stop()
        tts?.shutdown()
        super.onDestroy()
    }
}
