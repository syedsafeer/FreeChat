package com.example.freechat

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Base64
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.io.PrintWriter
import java.net.ServerSocket
import java.net.Socket

class ChatService : Service() {

    companion object {
        const val NOTIFICATION_ID = 1
        const val CHANNEL_ID = "FreeChatForegroundChannel"
        const val ACTION_START_HOST = "ACTION_START_HOST"
        const val ACTION_CONNECT_CLIENT = "ACTION_CONNECT_CLIENT"
        const val ACTION_STOP_SERVICE = "ACTION_STOP_SERVICE"
        const val BROADCAST_CONNECTION_SUCCESS = "com.example.freechat.CONNECTION_SUCCESS"
        const val BROADCAST_NEW_MESSAGE = "com.example.freechat.NEW_MESSAGE"
        const val EXTRA_MESSAGE = "EXTRA_MESSAGE"
        const val EXTRA_PORT = "EXTRA_PORT"
        const val EXTRA_IP = "EXTRA_IP"
        const val HEADS_UP_CHANNEL_ID = "freechat_heads_up_channel"
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        createHeadsUpNotificationChannel()
    }

    override fun onDestroy() {
        super.onDestroy()
        SocketHandler.close()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent == null) return START_NOT_STICKY

        when (intent.action) {
            ACTION_START_HOST -> {
                val port = intent.getIntExtra(EXTRA_PORT, 8080)
                startForegroundServiceNotification()
                if (!SocketHandler.isConnected()) {
                    ChatRepository.updateConnectionState(ConnectionState.CONNECTING)
                    CoroutineScope(Dispatchers.IO).launch {
                        try {
                            Handler(Looper.getMainLooper()).post {
                                Toast.makeText(applicationContext, "Starting Host...", Toast.LENGTH_SHORT).show()
                            }
                            SocketHandler.startHost(port) {
                                ChatRepository.updateConnectionState(ConnectionState.HOSTING)
                                Handler(Looper.getMainLooper()).post {
                                    Toast.makeText(applicationContext, "Server Started, waiting for client...", Toast.LENGTH_SHORT).show()
                                }
                            }
                            Handler(Looper.getMainLooper()).post {
                                Toast.makeText(applicationContext, "Client Connected successfully!", Toast.LENGTH_SHORT).show()
                            }
                            ChatRepository.updateConnectionState(ConnectionState.CONNECTED)
                            sendBroadcast(Intent(BROADCAST_CONNECTION_SUCCESS).setPackage(packageName))
                        } catch (e: java.net.SocketException) {
                            // Graceful cancellation (Stop Host)
                            ChatRepository.updateConnectionState(ConnectionState.DISCONNECTED)
                        } catch (e: Exception) {
                            e.printStackTrace()
                            ChatRepository.updateConnectionState(ConnectionState.DISCONNECTED)
                            Handler(Looper.getMainLooper()).post {
                                Toast.makeText(applicationContext, "Connection Error: ${e.message}", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                }
            }
            ACTION_CONNECT_CLIENT -> {
                val ip = intent.getStringExtra(EXTRA_IP) ?: return START_NOT_STICKY
                val port = intent.getIntExtra(EXTRA_PORT, 8080)
                startForegroundServiceNotification()
                if (!SocketHandler.isConnected()) {
                    ChatRepository.updateConnectionState(ConnectionState.CONNECTING)
                    CoroutineScope(Dispatchers.IO).launch {
                        try {
                            SocketHandler.connectToHost(ip, port)
                            Handler(Looper.getMainLooper()).post {
                                Toast.makeText(applicationContext, "Client Connected successfully!", Toast.LENGTH_SHORT).show()
                            }
                            ChatRepository.updateConnectionState(ConnectionState.CONNECTED)
                            sendBroadcast(Intent(BROADCAST_CONNECTION_SUCCESS).setPackage(packageName))
                        } catch (e: Exception) {
                            e.printStackTrace()
                            ChatRepository.updateConnectionState(ConnectionState.DISCONNECTED)
                            Handler(Looper.getMainLooper()).post {
                                Toast.makeText(applicationContext, "Connection Error: ${e.message}", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                }
            }
            ACTION_STOP_SERVICE -> {
                SocketHandler.close()
                ChatRepository.updateConnectionState(ConnectionState.DISCONNECTED)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    stopForeground(STOP_FOREGROUND_REMOVE)
                } else {
                    stopForeground(true)
                }
                stopSelf()
            }
        }

        // Setup listener which broadcasts intents
        SocketHandler.setMessageListener { rawMessage ->
            // Process message for Repository and Heads-up Notification
            var senderName = "Unknown"
            var notificationBody = ""
            var message: Message? = null

            try {
                if (rawMessage.startsWith("TEXT:")) {
                    val content = rawMessage.substring(5)
                    val parts = content.split(":", limit = 2)
                    if (parts.size > 1) {
                        senderName = parts[0]
                        notificationBody = parts[1]
                    } else {
                        notificationBody = content
                    }
                    message = Message(MessageType.TEXT, Sender.OTHER, content)
                } else if (rawMessage.startsWith("VOICE:")) {
                    val content = rawMessage.substring(6)
                    val parts = content.split(":", limit = 2)
                    var base64Data = content
                    if (parts.size == 2) {
                        senderName = parts[0]
                        base64Data = parts[1]
                    }
                    notificationBody = "Sent a voice message"
                    val file = saveAudioFile(base64Data)
                    message = Message(MessageType.VOICE, Sender.OTHER, file.absolutePath)
                } else {
                    notificationBody = rawMessage
                    message = Message(MessageType.TEXT, Sender.OTHER, rawMessage)
                }

                if (message != null) {
                    ChatRepository.messages.add(message)
                    // Show notification if app is in background
                    if (!MainActivity.isAppInForeground) {
                        showHeadsUpNotification(senderName, notificationBody)
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }

            val broadcastIntent = Intent(BROADCAST_NEW_MESSAGE).apply {
                putExtra(EXTRA_MESSAGE, rawMessage)
                setPackage(packageName) // Restrict to own app for security
            }
            sendBroadcast(broadcastIntent)
        }

        return START_STICKY
    }
    
    private fun saveAudioFile(base64Data: String): java.io.File {
        val audioBytes = Base64.decode(base64Data, Base64.NO_WRAP)
        val tempFile = java.io.File(externalCacheDir, "received_voice_${System.currentTimeMillis()}.3gp")
        val fos = java.io.FileOutputStream(tempFile)
        fos.write(audioBytes)
        fos.close()
        return tempFile
    }

    private fun showHeadsUpNotification(senderName: String, messageBody: String) {
        val builder = NotificationCompat.Builder(this, HEADS_UP_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_email)
            .setContentTitle(senderName)
            .setContentText(messageBody)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setVibrate(longArrayOf(0, 250, 250, 250))
            .setAutoCancel(true)

        try {
            with(NotificationManagerCompat.from(this)) {
                notify(System.currentTimeMillis().toInt(), builder.build())
            }
        } catch (e: SecurityException) {
            e.printStackTrace()
        }
    }

    private fun startForegroundServiceNotification() {
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("FreeChat Active")
            .setContentText("Connected and running in background")
            .setSmallIcon(android.R.drawable.ic_menu_rotate)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "FreeChat Background Service",
                NotificationManager.IMPORTANCE_MIN
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
    }

    private fun createHeadsUpNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                HEADS_UP_CHANNEL_ID,
                "FreeChat Messages",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notifications for new messages"
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
    }
}

// Singleton moved here to persist with the Service process
object SocketHandler {
    private var socket: Socket? = null
    private var serverSocket: ServerSocket? = null
    private var writer: PrintWriter? = null
    private var reader: BufferedReader? = null
    private var messageListener: ((String) -> Unit)? = null
    private var isListening = false

    fun isConnected() = socket != null && socket!!.isConnected && !socket!!.isClosed

    fun setMessageListener(listener: ((String) -> Unit)?) {
        messageListener = listener
    }

    fun startHost(port: Int, onServerReady: (() -> Unit)? = null) {
        if (isConnected()) return
        serverSocket = ServerSocket(port)
        onServerReady?.invoke()
        socket = serverSocket?.accept()
        socket?.soTimeout = 7000 // 7-second timeout for dead connection detection
        setupStreams()
        startListening()
    }

    fun connectToHost(ip: String, port: Int) {
        if (isConnected()) return
        val tempSocket = Socket()
        try {
            tempSocket.connect(java.net.InetSocketAddress(ip, port), 30000)
            socket = tempSocket
            socket?.soTimeout = 7000 // 7-second timeout for dead connection detection
            setupStreams()
            startListening()
        } catch (e: java.net.SocketTimeoutException) {
            tempSocket.close()
            ChatRepository.updateConnectionState(ConnectionState.DISCONNECTED)
            throw e
        } catch (e: java.net.ConnectException) {
            tempSocket.close()
            ChatRepository.updateConnectionState(ConnectionState.DISCONNECTED)
            throw e
        } catch (e: java.io.IOException) {
            tempSocket.close()
            ChatRepository.updateConnectionState(ConnectionState.DISCONNECTED)
            throw e
        }
    }

    private fun setupStreams() {
        socket?.let {
            writer = PrintWriter(OutputStreamWriter(it.getOutputStream()), true)
            reader = BufferedReader(InputStreamReader(it.getInputStream()))
        }
    }

    fun send(message: String) {
        writer?.println(message)
        if (writer?.checkError() == true) {
            close()
            ChatRepository.updateConnectionState(ConnectionState.DISCONNECTED)
        }
    }

    private fun startListening() {
        if (isListening) return
        isListening = true
        
        // Start Bidirectional Ping
        startPing()

        CoroutineScope(Dispatchers.IO).launch {
            try {
                var line: String?
                while (true) {
                    line = reader?.readLine()
                    if (line == null) break // Remote disconnected

                    if (line == "<PING>") continue // Ignore ping

                    line.let { msg ->
                        // Fix for multi-line: Restore newlines from <br>
                        val originalMessage = msg.replace("<br>", "\n")
                        messageListener?.invoke(originalMessage)
                    }
                }
            } catch (e: java.net.SocketTimeoutException) {
                e.printStackTrace() // Timeout: Remote is DEAD
            } catch (e: java.net.SocketException) {
                e.printStackTrace()
            } catch (e: java.io.EOFException) {
                e.printStackTrace()
            } catch (e: java.io.IOException) {
                e.printStackTrace()
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                close()
                ChatRepository.updateConnectionState(ConnectionState.DISCONNECTED)
                isListening = false
            }
        }
    }

    private fun startPing() {
        CoroutineScope(Dispatchers.IO).launch {
            while (isConnected()) {
                try {
                    kotlinx.coroutines.delay(3000)
                    if (!isConnected()) break
                    
                    writer?.println("<PING>")
                    if (writer?.checkError() == true) { // Broken pipe detected
                       close()
                       ChatRepository.updateConnectionState(ConnectionState.DISCONNECTED)
                       break
                    }
                } catch (e: Exception) {
                    close()
                    ChatRepository.updateConnectionState(ConnectionState.DISCONNECTED)
                    break
                }
            }
        }
    }

    fun close() {
        try {
            isListening = false
            writer?.close()
            reader?.close()
            socket?.close()
            serverSocket?.close()
            socket = null
            serverSocket = null
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
