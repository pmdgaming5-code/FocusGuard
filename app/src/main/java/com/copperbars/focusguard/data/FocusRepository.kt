package com.copperbars.focusguard.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.copperbars.focusguard.monitor.FocusEndWorker
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.focusDataStore by preferencesDataStore(name = "focus_guard_settings")

data class AppSettings(
    val warningMessage: String = "Ders çalışma zamanı! Ödevini tamamla ve görevlerini bitir.",
    val focusUntilMillis: Long = 0L,
    val monitorEnabled: Boolean = false,
    val pinSalt: String = "",
    val pinVerifier: String = "",
    val failedPinAttempts: Int = 0,
    val pinLockUntilMillis: Long = 0L
)

class FocusRepository(context: Context) {
    private val appContext = context.applicationContext
    private val dao = FocusDatabase.get(appContext).focusDao()
    private val store = appContext.focusDataStore

    private object Keys {
        val warning = stringPreferencesKey("warning_message")
        val focusUntil = longPreferencesKey("focus_until_millis")
        val monitor = booleanPreferencesKey("monitor_enabled")
        val pinSalt = stringPreferencesKey("pin_salt")
        val pinVerifier = stringPreferencesKey("pin_verifier")
        val pinFailures = intPreferencesKey("pin_failures")
        val pinLockUntil = longPreferencesKey("pin_lock_until")
    }

    val settings: Flow<AppSettings> = store.data.map { p ->
        AppSettings(
            warningMessage = p[Keys.warning] ?: AppSettings().warningMessage,
            focusUntilMillis = p[Keys.focusUntil] ?: 0L,
            monitorEnabled = p[Keys.monitor] ?: false,
            pinSalt = p[Keys.pinSalt] ?: "",
            pinVerifier = p[Keys.pinVerifier] ?: "",
            failedPinAttempts = p[Keys.pinFailures] ?: 0,
            pinLockUntilMillis = p[Keys.pinLockUntil] ?: 0L
        )
    }

    val trackedApps: Flow<List<TrackedAppEntity>> = dao.observeTrackedApps()
    val recentSessions: Flow<List<FocusSessionEntity>> = dao.observeRecentSessions()

    fun todayKey(): String = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
        .format(java.util.Date())

    fun todayUsage(): Flow<List<DailyUsageEntity>> = dao.observeDailyUsage(todayKey())

    suspend fun getTrackedApps(): List<TrackedAppEntity> = dao.getTrackedApps()
    suspend fun getTrackedApp(packageName: String): TrackedAppEntity? = dao.getTrackedApp(packageName)
    suspend fun getDailyUsage(packageName: String): Long = dao.getDailyUsage(packageName, todayKey()) ?: 0L

    suspend fun addUsage(packageName: String, deltaMillis: Long) {
        if (deltaMillis <= 0L) return
        val day = todayKey()
        val total = (dao.getDailyUsage(packageName, day) ?: 0L) + deltaMillis
        dao.saveDailyUsage(DailyUsageEntity(packageName, day, total, System.currentTimeMillis()))
    }

    suspend fun addSessionUsage(packageName: String, deltaMillis: Long) {
        if (deltaMillis > 0L) dao.addSessionUsage(packageName, deltaMillis)
    }

    suspend fun resetSessionUsage(packageName: String) {
        dao.resetSessionUsage(packageName)
    }

    suspend fun saveTrackedApp(app: TrackedAppEntity) = dao.saveTrackedApp(app)
    suspend fun removeTrackedApp(packageName: String) = dao.removeTrackedApp(packageName)

    suspend fun updateWarningMessage(message: String) {
        store.edit { it[Keys.warning] = message.take(240) }
    }

    suspend fun setMonitorEnabled(enabled: Boolean) {
        store.edit { it[Keys.monitor] = enabled }
    }

    suspend fun savePinRecord(salt: String, verifier: String) {
        store.edit {
            it[Keys.pinSalt] = salt
            it[Keys.pinVerifier] = verifier
            it[Keys.pinFailures] = 0
            it[Keys.pinLockUntil] = 0L
        }
    }

    suspend fun recordWrongPin(): Long {
        var lockUntil = 0L
        store.edit { p ->
            val failures = (p[Keys.pinFailures] ?: 0) + 1
            val delayMillis = if (failures < 5) 0L else
                (30_000L * (failures - 4).coerceAtMost(10))
            lockUntil = if (delayMillis > 0L) System.currentTimeMillis() + delayMillis else 0L
            p[Keys.pinFailures] = failures
            p[Keys.pinLockUntil] = lockUntil
        }
        return lockUntil
    }

    suspend fun clearPinFailures() {
        store.edit {
            it[Keys.pinFailures] = 0
            it[Keys.pinLockUntil] = 0L
        }
    }

    suspend fun startFocusSession(minutes: Int) {
        val safeMinutes = minutes.coerceIn(1, 240)
        val now = System.currentTimeMillis()
        val end = now + TimeUnit.MINUTES.toMillis(safeMinutes.toLong())
        dao.completeActiveSessions()
        dao.insertFocusSession(
            FocusSessionEntity(
                label = "$safeMinutes dakika odak",
                startedAtMillis = now,
                endsAtMillis = end
            )
        )
        store.edit { it[Keys.focusUntil] = end }
        val request = OneTimeWorkRequestBuilder<FocusEndWorker>()
            .setInitialDelay(safeMinutes.toLong(), TimeUnit.MINUTES)
            .build()
        WorkManager.getInstance(appContext).enqueueUniqueWork(
            FocusEndWorker.UNIQUE_NAME,
            ExistingWorkPolicy.REPLACE,
            request
        )
    }

    suspend fun cancelFocusSession() {
        store.edit { it[Keys.focusUntil] = 0L }
        dao.completeActiveSessions()
        WorkManager.getInstance(appContext).cancelUniqueWork(FocusEndWorker.UNIQUE_NAME)
    }

    suspend fun finishExpiredFocusSession() {
        val snapshot = settings.first()
        if (snapshot.focusUntilMillis > 0L && snapshot.focusUntilMillis <= System.currentTimeMillis()) {
            store.edit { it[Keys.focusUntil] = 0L }
            dao.completeActiveSessions()
        }
    }
}
