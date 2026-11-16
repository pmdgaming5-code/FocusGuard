package com.copperbars.focusguard.monitor

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.copperbars.focusguard.MainActivity
import com.copperbars.focusguard.R
import com.copperbars.focusguard.data.FocusRepository

class FocusEndWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    companion object {
        const val UNIQUE_NAME = "focus_guard_focus_end"
        private const val CHANNEL_ID = "focus_sessions"
    }

    override suspend fun doWork(): Result {
        val repository = FocusRepository(applicationContext)
        val before = repository.settings
        repository.finishExpiredFocusSession()
        val nowActive = repository.settings
        // WorkManager is persistent; the service also checks the stored end timestamp.
        showCompletionNotification()
        return Result.success()
    }

    private fun showCompletionNotification() {
        val manager = applicationContext.getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= 26) {
            manager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    "Odak oturumları",
                    NotificationManager.IMPORTANCE_DEFAULT
                )
            )
        }

        val intent = Intent(applicationContext, MainActivity::class.java)
        val pending = PendingIntent.getActivity(
            applicationContext,
            41,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle("Odak oturumu tamamlandı")
            .setContentText("Güzel iş! Kısa bir mola verebilirsin.")
            .setContentIntent(pending)
            .setAutoCancel(true)
            .build()
        manager.notify(4202, notification)
    }
}
