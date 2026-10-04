package com.homeport.app.network

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.wifi.WifiManager
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import com.homeport.app.ui.MainActivity
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collectLatest

import android.net.Uri
import android.provider.Settings

private const val TAG = "MeshForegroundService"
private const val CHANNEL_ID = "portal_mesh_channel"
private const val NOTIFICATION_ID = 9182

/**
 * Foreground Service that keeps CPU and Wi-Fi radio awake when the device screen turns off.
 * Prevents OS from suspending local P2P TCP/WebSocket connections, enabling uninterrupted
 * file browsing, media streaming, and multi-device mesh transfers in the background.
 */
class MeshForegroundService : Service() {

    private var wakeLock: PowerManager.WakeLock? = null
    private var wifiLock: WifiManager.WifiLock? = null
    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        Log.i(TAG, "MeshForegroundService created")
        createNotificationChannel()
        acquireLocks()
        observeConnections()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        acquireLocks()
        val notification = buildNotification("Maintaining local P2P mesh link")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val serviceType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE or ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            } else {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE
            }
            startForeground(NOTIFICATION_ID, notification, serviceType)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
        return START_STICKY
    }

    private fun acquireLocks() {
        try {
            val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
            if (wakeLock == null) {
                wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "Portal::MeshWakeLock").apply {
                    setReferenceCounted(false)
                }
            }
            if (wakeLock?.isHeld == false) {
                wakeLock?.acquire(24 * 60 * 60 * 1000L) // 24 hours max safeguard
                Log.i(TAG, "WakeLock acquired: CPU will not sleep on screen-off")
            }

            val wifiManager = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
            if (wifiLock == null) {
                @Suppress("DEPRECATION")
                wifiLock = wifiManager.createWifiLock(WifiManager.WIFI_MODE_FULL_HIGH_PERF, "Portal::MeshWifiLock").apply {
                    setReferenceCounted(false)
                }
            }
            if (wifiLock?.isHeld == false) {
                wifiLock?.acquire()
                Log.i(TAG, "WifiLock acquired: Wi-Fi radio locked active during screen-off")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to acquire locks: ${e.message}", e)
        }
    }

    private fun releaseLocks() {
        try {
            if (wakeLock?.isHeld == true) {
                wakeLock?.release()
                Log.i(TAG, "WakeLock released")
            }
            if (wifiLock?.isHeld == true) {
                wifiLock?.release()
                Log.i(TAG, "WifiLock released")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to release locks: ${e.message}", e)
        }
    }

    private fun observeConnections() {
        val client = HomePortClient.getInstance(this)
        val serverManager = AndroidMeshServerManager.getInstance(this)

        serviceScope.launch {
            combineState(client, serverManager)
        }
    }

    private suspend fun combineState(client: HomePortClient, serverManager: AndroidMeshServerManager) {
        coroutineScope {
            launch {
                client.connectedDevice.collectLatest { device ->
                    updateLiveNotification(device?.name, serverManager.incomingClients.value.size)
                }
            }
            launch {
                serverManager.incomingClients.collectLatest { clients ->
                    updateLiveNotification(client.connectedDevice.value?.name, clients.size)
                }
            }
        }
    }

    private fun updateLiveNotification(receiverPeerName: String?, senderClientCount: Int) {
        val isSender = senderClientCount > 0
        val isReceiver = !receiverPeerName.isNullOrEmpty()

        val text = when {
            isSender && isReceiver -> "Dual Mesh Active · Serving $senderClientCount peer(s) & Connected to $receiverPeerName"
            isSender -> "Serving storage to $senderClientCount receiver(s) · Screen-off active"
            isReceiver -> "Connected to $receiverPeerName · Screen-off link active"
            else -> "Portal P2P Mesh Active · Screen-off link ready"
        }

        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(NOTIFICATION_ID, buildNotification(text))
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Portal Mesh Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Keeps peer-to-peer data connection active when screen is turned off"
                setShowBadge(false)
            }
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(contentText: String): Notification {
        val openIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Portal P2P Mesh")
            .setContentText(contentText)
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setOngoing(true)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(pendingIntent)
            .build()
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
        releaseLocks()
        Log.i(TAG, "MeshForegroundService destroyed")
    }

    companion object {
        fun start(context: Context) {
            try {
                val intent = Intent(context, MeshForegroundService::class.java)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to start MeshForegroundService: ${e.message}", e)
            }
        }

        fun stop(context: Context) {
            try {
                val intent = Intent(context, MeshForegroundService::class.java)
                context.stopService(intent)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to stop MeshForegroundService: ${e.message}", e)
            }
        }

        fun syncState(context: Context) {
            val client = HomePortClient.getInstance(context)
            val serverManager = AndroidMeshServerManager.getInstance(context)
            val hasConnection = client.connectedDevice.value != null ||
                                client.connectionStatus.value == ConnectionStatus.CONNECTED ||
                                serverManager.incomingClients.value.isNotEmpty() ||
                                serverManager.isRunning.value

            if (hasConnection) {
                start(context)
            } else {
                stop(context)
            }
        }

        fun isBatteryOptimizationIgnored(context: Context): Boolean {
            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
                powerManager.isIgnoringBatteryOptimizations(context.packageName)
            } else {
                true
            }
        }

        fun requestBatteryOptimizationExemption(context: Context) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
                val packageName = context.packageName
                if (!powerManager.isIgnoringBatteryOptimizations(packageName)) {
                    try {
                        val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                            data = Uri.parse("package:$packageName")
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        context.startActivity(intent)
                    } catch (e: Exception) {
                        try {
                            val intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS).apply {
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            }
                            context.startActivity(intent)
                        } catch (_: Exception) {}
                    }
                }
            }
        }
    }
}
