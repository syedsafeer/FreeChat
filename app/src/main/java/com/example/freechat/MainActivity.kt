package com.example.freechat

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.media.MediaRecorder
import android.os.Build
import android.os.Bundle
import android.util.Base64
import android.view.MotionEvent
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.UUID

class MainActivity : AppCompatActivity() {

    // UI Components
    private lateinit var rvChat: RecyclerView
    private lateinit var etUsername: EditText
    private lateinit var etTargetIP: EditText
    private lateinit var etMessage: EditText
    private lateinit var btnStartHost: Button
    private lateinit var btnConnect: Button
    private lateinit var btnSend: Button
    private lateinit var btnClearChat: Button
    private lateinit var btnMic: ImageButton
    private lateinit var btnThemeToggle: ImageButton
    private lateinit var layoutRecording: LinearLayout

    // SharedPrefs
    private lateinit var sharedPreferences: SharedPreferences
    private val PREFS_NAME = "FreeChatPrefs"
    private val KEY_THEME = "isDarkMode"
    private val KEY_USERNAME = "username"

    // ViewModel
    private lateinit var chatViewModel: ChatViewModel

    // RecyclerView Adapter
    private lateinit var chatAdapter: ChatAdapter
    
    // Audio Variables
    private var mediaRecorder: MediaRecorder? = null
    private var audioFile: File? = null

    private val chatReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val start = "com.example.freechat.NEW_MESSAGE"
            if (intent?.action == start) {
                val message = intent.getStringExtra("EXTRA_MESSAGE")
                if (message != null) {
                    handleIncomingMessage(message)
                }
            }
        }
    }

    companion object {
        var isAppInForeground = false
        const val CHANNEL_ID = "freechat_high_priority_v2"
    }

    @SuppressLint("ClickableViewAccessibility", "SetTextI18n", "UnspecifiedRegisterReceiverFlag")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        createNotificationChannel()

        // Request Notification Permission for Android 13+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.POST_NOTIFICATIONS), 101)
            }
        }

        // Initialize ViewModel
        chatViewModel = ViewModelProvider(this).get(ChatViewModel::class.java)

        // Theme Logic
        sharedPreferences = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val isDarkMode = sharedPreferences.getBoolean(KEY_THEME, false)
        if (isDarkMode) {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
        } else {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
        }
        
        setContentView(R.layout.activity_main)

        // Initialize Views
        rvChat = findViewById(R.id.rvChat)
        etUsername = findViewById(R.id.etUsername)
        etTargetIP = findViewById(R.id.etTargetIP)
        etMessage = findViewById(R.id.etMessage)
        btnStartHost = findViewById(R.id.btnStartHost)
        btnConnect = findViewById(R.id.btnConnect)
        btnSend = findViewById(R.id.btnSend)
        btnClearChat = findViewById(R.id.btnClearChat)
        btnMic = findViewById(R.id.btnMic)
        btnThemeToggle = findViewById(R.id.btnThemeToggle)
        layoutRecording = findViewById(R.id.layoutRecording)

        // Load Username
        val savedUsername = sharedPreferences.getString(KEY_USERNAME, "")
        if (!savedUsername.isNullOrEmpty()) {
            etUsername.setText(savedUsername)
        }

        // Update Theme Icon
        updateThemeIcon(isDarkMode)

        // Setup RecyclerView with ViewModel data
        chatAdapter = ChatAdapter(chatViewModel.messages)
        rvChat.layoutManager = LinearLayoutManager(this).apply {
            stackFromEnd = true
        }
        rvChat.adapter = chatAdapter

        // Theme Toggle
        btnThemeToggle.setOnClickListener {
            val currentMode = sharedPreferences.getBoolean(KEY_THEME, false)
            val newMode = !currentMode
            sharedPreferences.edit().putBoolean(KEY_THEME, newMode).apply()
            
            if (newMode) {
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
            } else {
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
            }
            updateThemeIcon(newMode)
        }

        // Host Logic
        btnStartHost.setOnClickListener {
            if (btnStartHost.text.toString() == "Stop Host") {
                CoroutineScope(Dispatchers.IO).launch {
                    val intent = Intent(this@MainActivity, ChatService::class.java).apply {
                        action = ChatService.ACTION_STOP_SERVICE
                    }
                    startService(intent)
                }
            } else {
                saveUsername()
                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        addSystemMessage("Status: Starting Service Host...")
                        val intent = Intent(this@MainActivity, ChatService::class.java).apply {
                            action = "ACTION_START_HOST"
                            putExtra("EXTRA_PORT", 8080)
                        }
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                            startForegroundService(intent)
                        } else {
                            startService(intent)
                        }
                        addSystemMessage("Status: Service Started.")
                    } catch (e: Exception) {
                        addSystemMessage("Error (Host): ${e.message}")
                        e.printStackTrace()
                    }
                }
            }
        }

        // Client Logic
        btnConnect.setOnClickListener {
            if (btnConnect.text.toString() == "Disconnect") {
                 CoroutineScope(Dispatchers.IO).launch {
                    val intent = Intent(this@MainActivity, ChatService::class.java).apply {
                        action = ChatService.ACTION_STOP_SERVICE
                    }
                    startService(intent)
                }
            } else {
                val ip = etTargetIP.text.toString().trim()
                if (ip.isEmpty()) {
                    addSystemMessage("Error: Please enter Target IP.")
                    return@setOnClickListener
                }

                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        saveUsername()
                        addSystemMessage("Status: Starting Service Client...")
                        val intent = Intent(this@MainActivity, ChatService::class.java).apply {
                            action = "ACTION_CONNECT_CLIENT"
                            putExtra("EXTRA_IP", ip)
                            putExtra("EXTRA_PORT", 8080)
                        }
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                            startForegroundService(intent)
                        } else {
                            startService(intent)
                        }
                        addSystemMessage("Status: Service Started.")
                    } catch (e: Exception) {
                        addSystemMessage("Error (Client): ${e.message}")
                        e.printStackTrace()
                    }
                }
            }
        }

        // Observation
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                ChatRepository.connectionState.collectLatest { state ->
                    when (state) {
                        ConnectionState.CONNECTED -> {
                            btnConnect.text = "Disconnect"
                            btnConnect.isEnabled = true
                            btnStartHost.text = "Start Host" // Reset text but verify enabled state
                            btnStartHost.isEnabled = false
                            etTargetIP.isEnabled = false
                            etTargetIP.setText("") // Privacy: Clear IP
                        }
                        ConnectionState.DISCONNECTED -> {
                            btnConnect.text = "Connect"
                            btnConnect.isEnabled = true
                            btnStartHost.text = "Start Host"
                            btnStartHost.isEnabled = true
                            etTargetIP.isEnabled = true
                        }
                        ConnectionState.HOSTING -> {
                            btnStartHost.text = "Stop Host"
                            btnStartHost.isEnabled = true
                            btnConnect.text = "Connect"
                            btnConnect.isEnabled = false
                            etTargetIP.isEnabled = false
                        }
                        ConnectionState.CONNECTING -> {
                            btnConnect.text = "Connecting..."
                            btnConnect.isEnabled = false
                            btnStartHost.isEnabled = false
                            etTargetIP.isEnabled = false
                        }
                    }
                }
            }
        }

        // Register Receiver
        val filter = IntentFilter("com.example.freechat.NEW_MESSAGE")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(chatReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(chatReceiver, filter)
        }

        // Restore connection status message if just recreated
        if (SocketHandler.isConnected()) {
             // Optional: Update UI to show connected state
        }

        // Send Text Message Logic
        btnSend.setOnClickListener {
            saveUsername()
            val messageText = etMessage.text.toString()
            if (messageText.isNotEmpty() && SocketHandler.isConnected()) {
                val username = etUsername.text.toString().ifEmpty { "User" }
                
                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        // Fix: Replace newline with <br> to prevent socket splitting
                        val safeMessageText = messageText.replace("\n", "<br>")
                        // Send: User:Message
                        val socketMessage = "$username:$safeMessageText"
                        SocketHandler.send("TEXT:$socketMessage")
                        
                        // Local: Keep original newlines for local display
                        addMessageToChat(Message(MessageType.TEXT, Sender.ME, messageText))
                        
                        withContext(Dispatchers.Main) {
                            etMessage.text.clear()
                        }
                    } catch (e: Exception) {
                        addSystemMessage("Error Sending: ${e.message}")
                    }
                }
            } else if (!SocketHandler.isConnected()) {
                addSystemMessage("Error: Not connected.")
            }
        }

        // Voice Recording Logic
        btnMic.setOnTouchListener { _, motionEvent ->
            if (checkPermissions()) {
                when (motionEvent.action) {
                    MotionEvent.ACTION_DOWN -> {
                        startRecordingui()
                        startRecordingLogic()
                    }
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                        stopRecordingUi()
                        stopRecordingAndSend()
                    }
                }
            } else {
                requestPermissions()
            }
            true
        }

        // Clear Chat
        btnClearChat.setOnClickListener {
            chatViewModel.clearMessages()
            // Here notifyDataSetChanged is acceptable as we are clearing the whole list
            chatAdapter.notifyDataSetChanged()
        }
    }

    override fun onResume() {
        super.onResume()
        isAppInForeground = true
        
        // Sync with ChatRepository
        if (ChatRepository.messages.isNotEmpty()) {
            chatViewModel.messages.clear()
            chatViewModel.messages.addAll(ChatRepository.messages)
            chatAdapter.notifyDataSetChanged()
            if (chatViewModel.messages.isNotEmpty()) {
                rvChat.scrollToPosition(chatViewModel.messages.size - 1)
            }
        }
    }

    override fun onPause() {
        super.onPause()
        isAppInForeground = false
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "FreeChat Messages"
            val descriptionText = "Notifications for new messages"
            val importance = NotificationManager.IMPORTANCE_HIGH
            val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
                description = descriptionText
            }
            val notificationManager: NotificationManager =
                getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }
    
    // Removed showNotification from here as it is moved to ChatService

    private fun updateThemeIcon(isDarkMode: Boolean) {
        if (isDarkMode) {
            btnThemeToggle.setImageResource(R.drawable.ic_sun)
        } else {
            btnThemeToggle.setImageResource(R.drawable.ic_moon)
        }
    }

    private fun startRecordingui() {
        layoutRecording.visibility = View.VISIBLE
        layoutRecording.animate().alpha(1.0f).duration = 200
    }

    private fun stopRecordingUi() {
        layoutRecording.visibility = View.GONE
    }

    private fun startRecordingLogic() {
        try {
            audioFile = File(externalCacheDir, "audiorecord_${UUID.randomUUID()}.3gp")
            mediaRecorder = MediaRecorder().apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.THREE_GPP)
                setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB)
                setOutputFile(audioFile!!.absolutePath)
                prepare()
                start()
            }
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, "Recording Failed", Toast.LENGTH_SHORT).show()
        }
    }

    private fun stopRecordingAndSend() {
        try {
            mediaRecorder?.stop()
            mediaRecorder?.release()
            mediaRecorder = null
            
            if (SocketHandler.isConnected() && audioFile != null && audioFile!!.exists()) {
                val username = etUsername.text.toString().ifEmpty { "User" }
                addMessageToChat(Message(MessageType.VOICE, Sender.ME, audioFile!!.absolutePath)) // saveToRepo=true by default
                sendAudioFile(audioFile!!, username)
            } else {
               // Toast.makeText(this, "Not connected or file error", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun sendAudioFile(file: File, username: String) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val bytes = FileInputStream(file).readBytes()
                val base64Audio = Base64.encodeToString(bytes, Base64.NO_WRAP)
                // Fixed: Send format VOICE:Username:Base64Data
                SocketHandler.send("VOICE:$username:$base64Audio")
            } catch (e: Exception) {
                addSystemMessage("Error sending audio: ${e.message}")
            }
        }
    }

    private fun handleIncomingMessage(rawMessage: String) {
        runOnUiThread {
            if (rawMessage.startsWith("TEXT:")) {
                // Format: TEXT:Username:Message Content
                val content = rawMessage.substring(5)
                
                // NO Notification Logic here (handled by Service)

                // We show the full "Username:Message" for OTHER
                // Do not save to repo again, Service already did
                addMessageToChat(Message(MessageType.TEXT, Sender.OTHER, content), false, saveToRepo = false)
            } else if (rawMessage.startsWith("VOICE:")) {
                try {
                    val content = rawMessage.substring(6)
                    val parts = content.split(":", limit = 2)
                    
                    var senderName = "Unknown"
                    var base64Data = content

                    // Parse VOICE:Username:Base64Data
                    if (parts.size == 2) {
                        senderName = parts[0]
                        base64Data = parts[1]
                    }

                    // NO Notification Logic here

                    val tempFile = saveAudioFile(base64Data)
                    // Do not save to repo again, Service already did
                    addMessageToChat(Message(MessageType.VOICE, Sender.OTHER, tempFile.absolutePath), false, saveToRepo = false)
                } catch (e: Exception) {
                    addSystemMessage("Error receiving voice note.")
                }
            } else {
                addMessageToChat(Message(MessageType.TEXT, Sender.OTHER, rawMessage), false, saveToRepo = false)
            }
        }
    }

    private fun saveAudioFile(base64Data: String): File {
        val audioBytes = Base64.decode(base64Data, Base64.NO_WRAP)
        val tempFile = File(externalCacheDir, "received_voice_${System.currentTimeMillis()}.3gp")
        val fos = FileOutputStream(tempFile)
        fos.write(audioBytes)
        fos.close()
        return tempFile
    }

    private fun addMessageToChat(message: Message, threadSafe: Boolean = true, saveToRepo: Boolean = true) {
        if (saveToRepo) {
            ChatRepository.messages.add(message)
        }
        val action = {
            chatViewModel.addMessage(message)
            // Efficiently notify only the new insertion
            chatAdapter.notifyItemInserted(chatViewModel.messages.size - 1)
            rvChat.scrollToPosition(chatViewModel.messages.size - 1)
        }

        if (threadSafe) runOnUiThread(action) else action()
    }

    private fun addSystemMessage(text: String) {
        addMessageToChat(Message(MessageType.TEXT, Sender.OTHER, "System: $text"), saveToRepo = true)
    }
    
    // Permissions Logic
    private fun checkPermissions(): Boolean {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
    }

    private fun requestPermissions() {
        ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.RECORD_AUDIO), 200)
    }

    private fun saveUsername() {
        val username = etUsername.text.toString()
        if (username.isNotEmpty()) {
            sharedPreferences.edit().putString(KEY_USERNAME, username).apply()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        mediaRecorder?.release()
        chatAdapter.releaseMediaPlayer()
        
        unregisterReceiver(chatReceiver)

        // Only close if actually exiting app, not on theme change recreation
        if (isFinishing) {
             // For Service-based architecture, we might want to keep it running? 
             // The user requirement says "stays alive even when the app is completely closed".
             // So we do NOT stop the service here.
             // If we really want to stop it, we'd send ACTION_STOP_SERVICE intent.
        }
    }
}
