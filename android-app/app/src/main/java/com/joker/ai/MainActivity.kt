package com.joker.ai

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.graphics.Color
import android.hardware.camera2.CameraManager
import android.net.Uri
import android.os.BatteryManager
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
    private var isTorchOn = false

    private lateinit var layoutHome: View
    private lateinit var layoutChat: View
    private lateinit var tvNavHomeText: TextView
    private lateinit var tvNavChatText: TextView

    private lateinit var jokerOrb: JokerOrbView
    private lateinit var tvOrbStatus: TextView
    private lateinit var tvGreeting: TextView
    private lateinit var tvWeatherTemp: TextView
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
        tvWeatherTemp = findViewById(R.id.tvWeatherTemp)
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
        updateBatteryDiagnostics() // Real Battery on Card
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

        // Action Pills
        btnScreen.setOnClickListener { openRealGoogleLens() }
        btnMemory.setOnClickListener { showMemoryManagerDialog() }
        btnLanguage.setOnClickListener { showLanguageDialog() }

        sendBtn.setOnClickListener {
            val userText = input.text.toString().trim()
            if (userText.isNotEmpty()) {
                input.text.clear()
                processMessage(userText)
            }
        }

        setOrbIdle()
    }

    // ==========================================
    // REAL BATTERY DIAGNOSTICS
    // ==========================================
    private fun getBatteryInfo(): Pair<Int, Boolean> {
        val batteryStatus: Intent? = registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val level: Int = batteryStatus?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale: Int = batteryStatus?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
        val batteryPct = if (level >= 0 && scale > 0) (level * 100 / scale) else 100
        val status: Int = batteryStatus?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL
        return Pair(batteryPct, isCharging)
    }

    private fun updateBatteryDiagnostics() {
        val (batteryLevel, isCharging) = getBatteryInfo()
        val chargingIcon = if (isCharging) "⚡ " else "🔋 "
        tvWeatherTemp.text = "$chargingIcon$batteryLevel%"
    }

    // ==========================================
    // HARDWARE TORCH / FLASHLIGHT CONTROL
    // ==========================================
    private fun setTorchMode(enable: Boolean) {
        try {
            val cameraManager = getSystemService(Context.CAMERA_SERVICE) as CameraManager
            val cameraId = cameraManager.cameraIdList.firstOrNull()
            if (cameraId != null) {
                cameraManager.setTorchMode(cameraId, enable)
                isTorchOn = enable
                val toastMsg = if (enable) "🔦 Flashlight ON" else "🔦 Flashlight OFF"
                Toast.makeText(this, toastMsg, Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "Flashlight hardware nahi mila", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Toast.makeText(this, "Torch error: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun openRealGoogleLens() {
        try {
            val lensIntent = packageManager.getLaunchIntentForPackage("com.google.ar.lens")
            if (lensIntent != null) {
                lensIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                startActivity(lensIntent)
                return
            }

            val googleAppLensIntent = Intent().apply {
                setClassName("com.google.android.googlequicksearchbox", "com.google.android.apps.search.lens.LensActivity")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            startActivity(googleAppLensIntent)
        } catch (e: Exception) {
            try {
                val altIntent = Intent("com.google.ar.lens.LENS_ACTIVITY").apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                startActivity(altIntent)
            } catch (e2: Exception) {
                Toast.makeText(this, "Google Lens phone mein nahi mila", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun switchToHomeTab() {
        updateBatteryDiagnostics()
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
                    }
                    1 -> {
                        currentLanguage = "hi"
                        tts?.language = Locale("hi")
                        Toast.makeText(this, "Language: हिंदी सक्रिय", Toast.LENGTH_SHORT).show()
                    }
                    2 -> {
                        currentLanguage = "en"
                        tts?.language = Locale.US
                        Toast.makeText(this, "Language: English Active", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            .show()
    }

    private fun showUpdatesDialog() {
        AlertDialog.Builder(this)
            .setTitle("🚀 Joker AI System Updates")
            .setMessage("Version: 4.0.0 (Latest)\nStatus: All Neural Systems Operational\n\nFeatures:\n• Hardware Torch / Flashlight Controller\n• Real-Time Battery Diagnostics on Card\n• Voice-Reactive Freeze/Pulse 3D Orb\n• Direct Google Lens App Scanner\n• Neural Memory Vault\n• Autonomous App Actions")
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
            (lower.contains("torch") || lower.contains("flashlight")) && (lower.contains("off") || lower.contains("band")) -> "TORCH_OFF"
            lower.contains("torch") || lower.contains("flashlight") -> "TORCH_ON"
            lower.contains("battery") -> "BATTERY_CHECK"
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
                    "TORCH_ON" -> {
                        setTorchMode(true)
                    }
                    "TORCH_OFF" -> {
                        setTorchMode(false)
                    }
                    "BATTERY_CHECK" -> {
                        val (pct, isCharging) = getBatteryInfo()
                        val chargingText = if (isCharging) "charging par hai" else "charging par nahi hai"
                        val voiceMsg = "Boss, phone mein $pct% battery hai aur phone $chargingText."
                        tts?.speak(voiceMsg, TextToSpeech.QUEUE_FLUSH, null, "JokerBattery")
                        chatLog.append("🃏 Joker AI: $voiceMsg\n\n")
                        scrollView.post { scrollView.fullScroll(ScrollView.FOCUS_DOWN) }
                        updateBatteryDiagnostics()
                    }
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
                Toast.makeText(this@MainActivity, "Action execute error: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onDestroy() {
        if (isTorchOn) {
            setTorchMode(false)
        }
        speechRecognizer?.destroy()
        tts?.stop()
        tts?.shutdown()
        super.onDestroy()
    }
