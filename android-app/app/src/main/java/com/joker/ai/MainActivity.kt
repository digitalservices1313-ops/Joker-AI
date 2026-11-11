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
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.POST

data class ChatRequest(val message: String)
data class ChatResponse(val reply: String)

interface JokerApiService {
    @POST("/api/chat")
    suspend fun sendMessage(@Body req: ChatRequest): ChatResponse
}

class MainActivity : AppCompatActivity() {

    // Backend URL (Backend connect karne ke baad ise replace karenge)
    private val BASE_URL = "https://your-backend-service.onrender.com/"

    private val api: JokerApiService? by lazy {
        try {
            Retrofit.Builder()
                .baseUrl(BASE_URL)
                .addConverterFactory(GsonConverterFactory.create())
                .build()
                .create(JokerApiService::class.java)
        } catch (e: Exception) {
            null
        }
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
                input.text.clear()
                scrollView.post { scrollView.fullScroll(ScrollView.FOCUS_DOWN) }

                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        val response = api?.sendMessage(ChatRequest(userText))
                        withContext(Dispatchers.Main) {
                            val replyText = response?.reply ?: "Joker AI: Command received: '$userText'. (Ready for next step)"
                            chatLog.append("\n🃏 Joker AI: $replyText\n")
                            scrollView.post { scrollView.fullScroll(ScrollView.FOCUS_DOWN) }
                        }
                    } catch (e: Exception) {
                        // Fallback response taaki bina server ke bhi app test ho sake
                        withContext(Dispatchers.Main) {
                            chatLog.append("\n🃏 Joker AI (Local Test): Hello! I am active. Live cloud brain connect karne ke liye backend set karenge.\n")
                            scrollView.post { scrollView.fullScroll(ScrollView.FOCUS_DOWN) }
                        }
                    }
                }
            }
        }
    }
}
