package com.copperbars.focusguard.monitor

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.copperbars.focusguard.MainActivity
import com.copperbars.focusguard.R
import com.copperbars.focusguard.data.FocusRepository
import com.copperbars.focusguard.security.PinCheck
import com.copperbars.focusguard.security.PinSecurity
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.first

class UsageMonitorService : Service() {
    companion object {
        const val ACTION_START = "com.copperbars.focusguard.action.START"
        const val ACTION_STOP = "com.copperbars.focusguard.action.STOP"
        private const val CHANNEL_ID = "focus_guard_monitor"
        private const val NOTIFICATION_ID = 4101

        fun start(context: android.content.Context) {
            val intent = Intent(context, UsageMonitorService::class.java).setAction(ACTION_START)
            ContextCompat.startForegroundService(context, intent)
        }

        fun stop(context: android.content.Context) {
            val intent = Intent(context, UsageMonitorService::class.java).setAction(ACTION_STOP)
            context.startService(intent)
        }
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private lateinit var repository: FocusRepository
    private lateinit var pinSecurity: PinSecurity
    private var monitorJob: Job? = null
    private var overlay: UsageOverlay? = null
    @Volatile private var blockedPackage: String? = null
    private val bypassUntilElapsed = mutableMapOf<String, Long>()

    override fun onCreate() {
        super.onCreate()
        repository = FocusRepository(applicationContext)
        pinSecurity = PinSecurity(repository)
        overlay = UsageOverlay(this) { pin -> verifyOverlayPin(pin) }
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            monitorJob?.cancel()
            overlay?.hide()
            scope.launch {
                repository.setMonitorEnabled(false)
                withContext(Dispatchers.Main.immediate) {
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    stopSelf()
                }
            }
            return START_NOT_STICKY
        }

        try {
            val type = if (Build.VERSION.SDK_INT >= 34) {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            } else {
                0
            }
            ServiceCompat.startForeground(this, NOTIFICATION_ID, buildNotification(), type)
        } catch (error: Exception) {
            stopSelf()
            return START_NOT_STICKY
        }

        if (monitorJob?.isActive != true) {
            monitorJob = scope.launch { monitorLoop() }
        }
        return START_STICKY
    }

    private suspend fun monitorLoop() {
        val usageManager = getSystemService(USAGE_STATS_SERVICE) as UsageStatsManager
        val powerManager = getSystemService(POWER_SERVICE) as PowerManager
        var queryFrom = System.currentTimeMillis() - 15_000L
        var foregroundPackage: String? = findCurrentForeground(usageManager, queryFrom)
        var lastTick = SystemClock.elapsedRealtime()

        while (scope.isActive) {
            val nowWall = System.currentTimeMillis()
            try {
                val events = usageManager.queryEvents(queryFrom, nowWall)
                val event = UsageEvents.Event()
                while (events.hasNextEvent()) {
                    events.getNextEvent(event)
                    val packageName = event.packageName ?: continue
                    when (event.eventType) {
                        UsageEvents.Event.MOVE_TO_FOREGROUND,
                        UsageEvents.Event.ACTIVITY_RESUMED -> {
                            val previous = foregroundPackage
                            if (previous != null && previous != packageName) {
                                repository.resetSessionUsage(previous)
                            }
                            foregroundPackage = packageName
                        }
                        UsageEvents.Event.MOVE_TO_BACKGROUND -> {
                            if (foregroundPackage == packageName) {
                                foregroundPackage = null
                                repository.resetSessionUsage(packageName)
                            }
                        }
                    }
                }
                queryFrom = nowWall

                val currentElapsed = SystemClock.elapsedRealtime()
                val delta = (currentElapsed - lastTick).coerceIn(0L, 2_000L)
                lastTick = currentElapsed

                val activePackage = foregroundPackage
                if (powerManager.isInteractive && delta > 0L &&
                    activePackage != null && activePackage != packageName
                ) {
                    val app = repository.getTrackedApp(activePackage)
                    if (app != null) {
                        repository.addUsage(activePackage, delta)
                        repository.addSessionUsage(activePackage, delta)
                    }
                }

                repository.finishExpiredFocusSession()
                val settings = repository.settings.first()
                val isFocusActive = settings.focusUntilMillis > nowWall
                val app = activePackage?.let { repository.getTrackedApp(it) }

                if (app != null) {
                    val dailyMillis = repository.getDailyUsage(app.packageName)
                    val shouldBlock = LimitPolicy.isBlocked(app, dailyMillis, isFocusActive)
                    val isBypassed = (bypassUntilElapsed[app.packageName] ?: 0L) > currentElapsed

                    if (shouldBlock && !isBypassed && settings.pinVerifier.isNotBlank()) {
                        val label = app.label.ifBlank { app.packageName }
                        withContext(Dispatchers.Main.immediate) {
                            overlay?.show(app.packageName, label, settings.warningMessage)
                        }
                        blockedPackage = app.packageName
                    } else if (blockedPackage == app.packageName && !shouldBlock) {
                        withContext(Dispatchers.Main.immediate) { overlay?.hide() }
                        blockedPackage = null
                    }
                } else {
                    withContext(Dispatchers.Main.immediate) { overlay?.hide() }
                    blockedPackage = null
                }

                if (blockedPackage != null && activePackage != blockedPackage) {
                    withContext(Dispatchers.Main.immediate) { overlay?.hide() }
                    blockedPackage = null
                }
            } catch (_: SecurityException) {
                withContext(Dispatchers.Main.immediate) { overlay?.hide() }
                updateNotification("Kullanım erişimi kaldırıldı. Ayarlardan yeniden izin ver.")
            } catch (_: Exception) {
                // A transient UsageStats or database issue should not crash the monitoring service.
            }
            delay(1_000L)
        }
    }

    private fun findCurrentForeground(manager: UsageStatsManager, from: Long): String? {
        return try {
            val events = manager.queryEvents(from, System.currentTimeMillis())
            val event = UsageEvents.Event()
            var current: String? = null
            while (events.hasNextEvent()) {
                events.getNextEvent(event)
                when (event.eventType) {
                    UsageEvents.Event.MOVE_TO_FOREGROUND,
                    UsageEvents.Event.ACTIVITY_RESUMED -> current = event.packageName
                    UsageEvents.Event.MOVE_TO_BACKGROUND -> if (current == event.packageName) current = null
                }
            }
            current
        } catch (_: Exception) {
            null
        }
    }

    private fun verifyOverlayPin(pin: String) {
        scope.launch {
            when (val result = pinSecurity.verify(pin)) {
                PinCheck.Success -> {
                    val packageName = blockedPackage
                    if (packageName != null) {
                        bypassUntilElapsed[packageName] = SystemClock.elapsedRealtime() + 5 * 60_000L
                    }
                    withContext(Dispatchers.Main.immediate) { overlay?.hide() }
                    blockedPackage = null
                }
                PinCheck.Invalid -> withContext(Dispatchers.Main.immediate) {
                    overlay?.showPinError("PIN yanlış. Kilit devam ediyor.")
                }
                PinCheck.NotConfigured -> withContext(Dispatchers.Main.immediate) {
                    overlay?.showPinError("Önce FocusGuard ayarlarından PIN oluştur.")
                }
                is PinCheck.Locked -> {
                    val seconds = ((result.untilMillis - System.currentTimeMillis()).coerceAtLeast(0L) / 1000L) + 1
                    withContext(Dispatchers.Main.immediate) {
                        overlay?.showPinError("Çok fazla hatalı deneme. " + seconds + " saniye bekle.")
                    }
                }
            }
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    "FocusGuard izleme",
                    NotificationManager.IMPORTANCE_LOW
                ).apply { description = "Uygulama süre sınırlarının etkin olduğunu bildirir." }
            )
        }
    }

    private fun buildNotification(): Notification {
        val openIntent = Intent(this, MainActivity::class.java)
        val openPending = PendingIntent.getActivity(
            this, 1, openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val stopIntent = Intent(this, UsageMonitorService::class.java).setAction(ACTION_STOP)
        val stopPending = PendingIntent.getService(
            this, 2, stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle("FocusGuard etkin")
            .setContentText("Uygulama kullanım süreleri takip ediliyor.")
            .setContentIntent(openPending)
            .setOngoing(true)
            .addAction(0, "Takibi duraklat", stopPending)
            .build()
    }

    private fun updateNotification(message: String) {
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(
            NOTIFICATION_ID,
            NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
                .setContentTitle("FocusGuard")
                .setContentText(message)
                .setOngoing(false)
                .build()
        )
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        monitorJob?.cancel()
        overlay?.hide()
        scope.cancel()
        super.onDestroy()
    }
}
