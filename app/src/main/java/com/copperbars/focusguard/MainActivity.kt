package com.copperbars.focusguard

import android.app.AppOpsManager
import android.content.Intent
import android.content.pm.LauncherApps
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Process
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.core.app.ActivityCompat
import androidx.lifecycle.lifecycleScope
import com.copperbars.focusguard.data.FocusRepository
import com.copperbars.focusguard.monitor.UsageMonitorService
import com.copperbars.focusguard.ui.FocusGuardRoot
import com.copperbars.focusguard.ui.FocusGuardTheme
import kotlinx.coroutines.launch

data class LaunchableApp(
    val packageName: String,
    val label: String
)

class MainActivity : ComponentActivity() {
    lateinit var repository: FocusRepository
        private set

    private lateinit var launchableApps: List<LaunchableApp>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        repository = FocusRepository(applicationContext)
        launchableApps = loadLaunchableApps()

        setContent {
            FocusGuardTheme {
                FocusGuardRoot(
                    activity = this,
                    repository = repository,
                    launchableApps = launchableApps,
                    hasUsageAccess = ::hasUsageAccess,
                    hasOverlayAccess = { Settings.canDrawOverlays(this) },
                    onOpenUsageSettings = ::openUsageSettings,
                    onOpenOverlaySettings = ::openOverlaySettings,
                    onStartMonitoring = ::startMonitoring,
                    onStopMonitoring = ::stopMonitoring
                )
            }
        }
    }

    private fun loadLaunchableApps(): List<LaunchableApp> {
        return try {
            val launcherApps = getSystemService(LauncherApps::class.java)
            launcherApps.getActivityList(null, Process.myUserHandle())
                .map {
                    LaunchableApp(
                        packageName = it.applicationInfo.packageName,
                        label = it.label?.toString()?.ifBlank { it.applicationInfo.packageName }
                            ?: it.applicationInfo.packageName
                    )
                }
                .filter { it.packageName != packageName }
                .distinctBy { it.packageName }
                .sortedBy { it.label.lowercase() }
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun hasUsageAccess(): Boolean {
        val appOps = getSystemService(APP_OPS_SERVICE) as AppOpsManager
        val mode = appOps.checkOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            Process.myUid(),
            packageName
        )
        return mode == AppOpsManager.MODE_ALLOWED
    }

    private fun openUsageSettings() {
        startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
    }

    private fun openOverlaySettings() {
        val intent = Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            Uri.parse("package:" + packageName)
        )
        startActivity(intent)
    }

    fun startMonitoring(): String {
        if (!hasUsageAccess()) {
            openUsageSettings()
            return "Önce FocusGuard için Kullanım erişimini aç."
        }
        if (!Settings.canDrawOverlays(this)) {
            openOverlaySettings()
            return "Odak uyarısını göstermek için üstte gösterme iznini aç."
        }
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) !=
            android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(android.Manifest.permission.POST_NOTIFICATIONS),
                71
            )
        }
        lifecycleScope.launch { repository.setMonitorEnabled(true) }
        UsageMonitorService.start(this)
        return "FocusGuard takibi başlatıldı."
    }

    fun stopMonitoring() {
        UsageMonitorService.stop(this)
        lifecycleScope.launch { repository.setMonitorEnabled(false) }
    }
}
