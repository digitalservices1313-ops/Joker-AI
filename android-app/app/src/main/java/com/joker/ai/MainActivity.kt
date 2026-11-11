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
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
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

    // Fallback Intent if in-app speech recognizer service is unavailable
    private val fallbackSpeechLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK && result.data != null) {
            val matches = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
            val spokenText = matches?.firstOrNull()
            if (!spokenText.isNullOrEmpty()) {
                processMessage(spokenText)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val input = findViewById<EditText>(R.id.etMessage)
        val sendBtn = findViewById<Button>(R.id.btnSend)
        micBtn = findViewById(R.id.btnMic)
        chatLog = findViewById(R.id.tvChat)
        scrollView = findViewById(R.id.scrollView)

        tts = TextToSpeech(this) { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts?.language = Locale.getDefault()
                tts?.setSpeechRate(1.05f)
            }
        }

        setupSpeechRecognizer()

        sendBtn.setOnClickListener {
            val userText = input.text.toString().trim()
            if (userText.isNotEmpty()) {
                input.text.clear()
                processMessage(userText)
            }
        }

        micBtn.setOnClickListener {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                startInAppListening()
            } else {
                audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            }
        }
    }

    private fun setupSpeechRecognizer() {
        if (!SpeechRecognizer.isRecognitionAvailable(this)) return

        try {
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this).apply {
                setRecognitionListener(object : RecognitionListener {
                    override fun onReadyForSpeech(params: Bundle?) {
                        micBtn.setColorFilter(Color.parseColor("#00FF66")) // Listening Green
                        Toast.makeText(this@MainActivity, "🎤 Sun raha hoon... Boliye!", Toast.LENGTH_SHORT).show()
                    }

                    override fun onBeginningOfSpeech() {}
                    override fun onRmsChanged(rmsdB: Float) {}
                    override fun onBufferReceived(buffer: ByteArray?) {}

                    override fun onEndOfSpeech() {
                        resetMicUI()
                    }

                    override fun onError(error: Int) {
                        resetMicUI()
                        // Agar direct in-app fail ho to Google fallback use karega
                        if (error == SpeechRecognizer.ERROR_NO_MATCH || error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT) {
                            Toast.makeText(this@MainActivity, "Kuch sunai nahi diya, dobara boliye", Toast.LENGTH_SHORT).show()
                        } else {
                            startFallbackVoiceInput()
                        }
                    }

                    override fun onResults(results: Bundle?) {
                        resetMicUI()
                        val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        val spokenText = matches?.firstOrNull()
                        if (!spokenText.isNullOrEmpty()) {
                            processMessage(spokenText)
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

    private fun resetMicUI() {
        isListening = false
        micBtn.clearColorFilter()
    }

    private fun startInAppListening() {
        if (isListening) {
            speechRecognizer?.stopListening()
            resetMicUI()
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
        resetMicUI()
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
        }
        try {
            fallbackSpeechLauncher.launch(intent)
        } catch (e: Exception) {
            Toast.makeText(this, "Voice recognition support nahi hai", Toast.LENGTH_SHORT).show()
        }
    }

    private fun processMessage(userText: String) {
        chatLog.append("\nYou: $userText\n")
        chatLog.append("🃏 Joker AI: Soch raha hoon... 💭\n")
        scrollView.post { scrollView.fullScroll(ScrollView.FOCUS_DOWN) }

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

                    // Smart Action Detection: AI response se ya user text se 100% detect karega
                    val finalAction = detectAction(response.action, userText)
                    val finalTarget = response.target ?: extractTarget(userText)

                    if (finalAction != "NONE") {
                        executeAgentAction(finalAction, finalTarget)
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    val currentText = chatLog.text.toString()
                    val cleanText = currentText.replace("🃏 Joker AI: Soch raha hoon... 💭\n", "")
                    chatLog.text = cleanText
                    chatLog.append("🃏 Joker AI [Error]: ${e.localizedMessage ?: "Connection error"}\n\n")
                    scrollView.post { scrollView.fullScroll(ScrollView.FOCUS_DOWN) }
                }
            }
        }
    }

    // Fail-Safe Action Detection (AI bhul bhi jaye to bhi phone command chalegi)
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

    // 100% Guaranteed Action Launcher
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
