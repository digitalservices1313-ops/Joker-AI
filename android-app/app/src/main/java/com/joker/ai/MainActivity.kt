package com.joker.ai

import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.ScrollView
import android.widget.TextView
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
import java.util.concurrent.TimeUnit

data class ChatRequest(val message: String)
data class ChatResponse(val reply: String)

interface JokerApiService {
    @POST("/api/chat")
    suspend fun sendMessage(@Body req: ChatRequest): ChatResponse
}

class MainActivity : AppCompatActivity() {

    // Aapka 100% Live Cloud Backend URL
    private val BASE_URL = "https://joker-ai-brain.onrender.com/"

    // Render free server ke cold-start ke liye 60 seconds timeout
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val input = findViewById<EditText>(R.id.etMessage)
        val sendBtn = findViewById<Button>(R.id.btnSend)
        val chatLog = findViewById<TextView>(R.id.tvChat)
        val scrollView = findViewById<ScrollView>(R.id.scrollView)

        sendBtn.setOnClickListener {
            val userText = input.text.toString().trim()
            if (userText.isNotEmpty()) {
                chatLog.append("\nYou: $userText\n")
                chatLog.append("🃏 Joker AI: Soch raha hoon... 💭\n")
                input.text.clear()
                scrollView.post { scrollView.fullScroll(ScrollView.FOCUS_DOWN) }

                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        val response = api.sendMessage(ChatRequest(userText))
                        withContext(Dispatchers.Main) {
                            // "Soch raha hoon..." ko real AI reply se replace karenge
                            val currentText = chatLog.text.toString()
                            val cleanText = currentText.replace("🃏 Joker AI: Soch raha hoon... 💭\n", "")
                            chatLog.text = cleanText
                            chatLog.append("🃏 Joker AI: ${response.reply}\n\n")
                            scrollView.post { scrollView.fullScroll(ScrollView.FOCUS_DOWN) }
                        }
                    } catch (e: Exception) {
                        withContext(Dispatchers.Main) {
                            val currentText = chatLog.text.toString()
                            val cleanText = currentText.replace("🃏 Joker AI: Soch raha hoon... 💭\n", "")
                            chatLog.text = cleanText
                            chatLog.append("🃏 Joker AI [Connection Error]: ${e.localizedMessage ?: "Server se baat nahi ho paayi"}\n\n")
                            scrollView.post { scrollView.fullScroll(ScrollView.FOCUS_DOWN) }
                        }
                    }
                }
            }
        }
    }
}
