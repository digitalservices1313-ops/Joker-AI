package com.joker.ai

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
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
    private var currentLanguage = "hi_IN"

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
    private lateinit var sharedPrefs: SharedPreferences

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

        sharedPrefs = getSharedPreferences("JokerMemoryVault", Context.MODE_PRIVATE)

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

        updateGreeting()
        setupTTS()
        setupSpeechRecognizer()

        // Tab Navigation
        navHome.setOnClickListener { switchToHomeTab() }
        navChat.setOnClickListener { switchToChatTab() }

        micBtn.setOnClickListener {
            switchToHomeTab()
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                startInAppListening()
            } else {
                audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            }
        }

        navSettings.setOnClickListener {
            try {
                val intent = Intent(Settings.ACTION_SETTINGS).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                startActivity(intent)
            } catch (e: Exception) {
                Toast.makeText(this, "Settings open error", Toast.LENGTH_SHORT).show()
            }
        }

        navUpdates.setOnClickListener { showUpdatesDialog() }

        // ==========================================================
        // 1. SCREEN PILL -> DIRECT REAL GOOGLE LENS LAUNCHER
        // ==========================================================
        btnScreen.setOnClickListener {
            openRealGoogleLens()
        }

        // 2. MEMORIES PILL
        btnMemory.setOnClickListener {
            showMemoryManagerDialog()
        }

        // 3. LANGUAGE PILL
        btnLanguage.setOnClickListener {
            showLanguageDialog()
        }

        // Send Message
        sendBtn.setOnClickListener {
            val userText = input.text.toString().trim()
            if (userText.isNotEmpty()) {
                input.text.clear()
                processMessage(userText)
            }
        }
    }

    // Direct Google Lens App Launcher (No Website!)
    private fun openRealGoogleLens() {
        try {
            // 1. Standalone Google Lens App check
            val lensIntent = packageManager.getLaunchIntentForPackage("com.google.ar.lens")
            if (lensIntent != null) {
                lensIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                startActivity(lensIntent)
                return
            }

            // 2. Google Search App Built-in Lens Activity (Har phone me hoti hai)
            val googleAppLensIntent = Intent().apply {
                setClassName("com.google.android.googlequicksearchbox", "com.google.android.apps.search.lens.LensActivity")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            startActivity(googleAppLensIntent)
        } catch (e: Exception) {
            try {
                // 3. Generic Lens Action Intent
                val altIntent = Intent("com.google.ar.lens.LENS_ACTIVITY").apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                startActivity(altIntent)
            } catch (e2: Exception) {
                // 4. Play store fallback if not installed
                Toast.makeText(this, "Google Lens phone mein nahi mila, Play Store khol rahe hain...", Toast.LENGTH_SHORT).show()
                try {
                    val playStoreIntent = Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=com.google.ar.lens")).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    startActivity(playStoreIntent)
                } catch (e3: Exception) {
                    Toast.makeText(this, "Google Lens open nahi ho paaya", Toast.LENGTH_SHORT).show()
                }
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

    private fun showMemoryManagerDialog() {
        val allMemories = sharedPrefs.all
        val memoryList = if (allMemories.isEmpty()) {
            "No memories saved yet.\nAdd something you want Joker to remember!"
        } else {
            allMemories.entries.joinToString("\n\n") { "• ${it.value}" }
        }

        AlertDialog.Builder(this)
            .setTitle("🧠 Joker Neural Memory Vault")
            .setMessage(memoryList)
            .setPositiveButton("➕ Add Memory") { _, _ ->
                val input = EditText(this).apply {
                    hint = "e.g. My car is Red or I love Biryani"
                    setTextColor(Color.BLACK)
                }
                AlertDialog.Builder(this)
                    .setTitle("Remember a Fact")
                    .setView(input)
                    .setPositiveButton("Save") { _, _ ->
                        val fact = input.text.toString().trim()
                        if (fact.isNotEmpty()) {
                            sharedPrefs.edit().putString("mem_${System.currentTimeMillis()}", fact).apply()
                            Toast.makeText(this, "Memory Saved into Neural Core!", Toast.LENGTH_SHORT).show()
                        }
                    }
                    .setNegativeButton("Cancel", null)
                    .show()
            }
            .setNeutralButton("🗑️ Clear All") { _, _ ->
                sharedPrefs.edit().clear().apply()
                Toast.makeText(this, "All memories wiped!", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Close", null)
            .show()
    }

    private fun getMemoryContext(): String {
        val all = sharedPrefs.all
        if (all.isEmpty()) return ""
        return "User Memories: " + all.values.joinToString(", ")
    }

    private fun showLanguageDialog() {
        val languages = arrayOf("🇮🇳 Hinglish (Default Mix)", "🇮🇳 Hindi (हिंदी)", "🇬🇧 English (US)")
        AlertDialog.Builder(this)
            .setTitle("🌐 Select Voice Language")
            .setItems(languages) { _, which ->
                when (which) {
                    0 -> {
                        currentLanguage = "hi_IN"
                        tts?.language = Locale("hi", "IN")
                        Toast.makeText(this, "Language: Hinglish Active", Toast.LENGTH_SHORT).show()
                        tts?.speak("Hinglish language active hai, Boss!", TextToSpeech.QUEUE_FLUSH, null, "LangTest")
                    }
                    1 -> {
                        currentLanguage = "hi"
                        tts?.language = Locale("hi")
                        Toast.makeText(this, "Language: हिंदी सक्रिय", Toast.LENGTH_SHORT).show()
                        tts?.speak("हिंदी भाषा सक्रिय कर दी गई है।", TextToSpeech.QUEUE_FLUSH, null, "LangTest")
                    }
                    2 -> {
                        currentLanguage = "en"
                        tts?.language = Locale.US
                        Toast.makeText(this, "Language: English Active", Toast.LENGTH_SHORT).show()
                        tts?.speak("English voice synthesis activated, Boss!", TextToSpeech.QUEUE_FLUSH, null, "LangTest")
                    }
                }
            }
            .show()
    }

    private fun showUpdatesDialog() {
        AlertDialog.Builder(this)
            .setTitle("🚀 Joker AI System Updates")
            .setMessage("Version: 3.2.0 (Latest)\nStatus: All Neural Systems Operational\n\nFeatures:\n• Direct Google Lens Scanner Integration\n• Video 1 3D Fluid Red Particle Orb\n• Dedicated Clean Home & Chat Tabs\n• Neural Memory Vault Active\n• Voice Language Switcher\n• Autonomous App Actions")
            .setPositiveButton("Awesome", null)
            .show()
    }

    private fun setupTTS() {
        tts = TextToSpeech(this) { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts?.language = Locale("hi", "IN")
                tts?.setSpeechRate(1.05f)

                tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) {
                        runOnUiThread {
                            jokerOrb.setState(OrbState.SPEAKING)
                            tvOrbStatus.text = "● SPEAKING... // VOCAL FEED"
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
                        resetMicUI()
                        if (error == SpeechRecognizer.ERROR_NO_MATCH || error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT) {
                            setOrbIdle()
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

    private fun resetMicUI() {
        isListening = false
        micBtn.clearColorFilter()
    }

    private fun startInAppListening() {
        if (isListening) {
            speechRecognizer?.stopListening()
            resetMicUI()
            setOrbIdle()
            return
        }

        val langLocale = when (currentLanguage) {
            "hi" -> Locale("hi")
            "en" -> Locale.US
            else -> Locale.getDefault()
        }

        if (speechRecognizer != null) {
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, langLocale)
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

        val memoryContext = getMemoryContext()
        val finalPrompt = if (memoryContext.isNotEmpty()) {
            "[$memoryContext] $userText"
        } else {
            userText
        }

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val response = api.sendMessage(ChatRequest(finalPrompt))
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
            lower.contains("lens") -> "OPEN_LENS"
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
                    "OPEN_LENS" -> {
                        openRealGoogleLens()
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
