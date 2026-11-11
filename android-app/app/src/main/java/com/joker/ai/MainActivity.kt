package com.joker.ai

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
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
data class ChatResponse(val reply: String)

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

    // Audio Permission Request Handler
    private val audioPermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            startInAppListening()
        } else {
            Toast.makeText(this, "Microphone permission zaroori hai", Toast.LENGTH_SHORT).show()
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

        // Initialize Text-to-Speech (Joker AI Voice Output)
        tts = TextToSpeech(this) { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts?.language = Locale.getDefault()
                tts?.setSpeechRate(1.05f)
            }
        }

        // Initialize In-App Direct Speech Recognizer (Bina Google Popup ke)
        setupSpeechRecognizer()

        // Send Button Click
        sendBtn.setOnClickListener {
            val userText = input.text.toString().trim()
            if (userText.isNotEmpty()) {
                input.text.clear()
                processMessage(userText)
            }
        }

        // Mic Button Click
        micBtn.setOnClickListener {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                startInAppListening()
            } else {
                audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            }
        }
    }

    private fun setupSpeechRecognizer() {
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            Toast.makeText(this, "Speech recognition not available", Toast.LENGTH_SHORT).show()
            return
        }

        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this).apply {
            setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {
                    chatLog.append("\n🎤 Joker AI: Sun raha hoon, boliye...\n")
                    scrollView.post { scrollView.fullScroll(ScrollView.FOCUS_DOWN) }
                    micBtn.setColorFilter(android.graphics.Color.parseColor("#00FF66")) // Listening status (Green)
                }

                override fun onBeginningOfSpeech() {}
                override fun onRmsChanged(rmsdB: Float) {}
                override fun onBufferReceived(buffer: ByteArray?) {}

                override fun onEndOfSpeech() {
                    isListening = false
                    micBtn.clearColorFilter()
                }

                override fun onError(error: Int) {
                    isListening = false
                    micBtn.clearColorFilter()
                }

                override fun onResults(results: Bundle?) {
                    isListening = false
                    micBtn.clearColorFilter()
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
    }

    private fun startInAppListening() {
        if (isListening) {
            speechRecognizer?.stopListening()
            isListening = false
            micBtn.clearColorFilter()
            return
        }

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
        }

        isListening = true
        speechRecognizer?.startListening(intent)
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

                    // Joker AI bol kar sunayega
                    tts?.speak(response.reply, TextToSpeech.QUEUE_FLUSH, null, "JokerResponse")
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

    override fun onDestroy() {
        speechRecognizer?.destroy()
        tts?.stop()
        tts?.shutdown()
        super.onDestroy()
    }
}
