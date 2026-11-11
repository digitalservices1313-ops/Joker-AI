package com.joker.ai

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.speech.RecognizerIntent
import android.speech.tts.TextToSpeech
import android.widget.Button
import android.widget.EditText
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
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
    private lateinit var chatLog: TextView
    private lateinit var scrollView: ScrollView

    // Speech-to-Text Result Listener
    private val speechLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK && result.data != null) {
            val matches = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
            val spokenText = matches?.get(0)
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
        val micBtn = findViewById<Button>(R.id.btnMic)
        chatLog = findViewById(R.id.tvChat)
        scrollView = findViewById(R.id.scrollView)

        // Initialize Text-to-Speech Engine
        tts = TextToSpeech(this) { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts?.language = Locale.getDefault()
                tts?.setSpeechRate(1.05f) // Thodi energetic voice
            }
        }

        // Send Button Click (Typed text)
        sendBtn.setOnClickListener {
            val userText = input.text.toString().trim()
            if (userText.isNotEmpty()) {
                input.text.clear()
                processMessage(userText)
            }
        }

        // Mic Button Click (Voice recognition)
        micBtn.setOnClickListener {
            startVoiceInput()
        }
    }

    private fun startVoiceInput() {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
            putExtra(RecognizerIntent.EXTRA_PROMPT, "Joker AI sun raha hai... Boliye!")
        }
        try {
            speechLauncher.launch(intent)
        } catch (e: Exception) {
            Toast.makeText(this, "Voice input support nahi hai", Toast.LENGTH_SHORT).show()
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

                    // Joker AI bol kar sunayega!
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
        tts?.stop()
        tts?.shutdown()
        super.onDestroy()
    }
}
