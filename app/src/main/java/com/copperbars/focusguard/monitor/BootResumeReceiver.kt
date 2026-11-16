package com.copperbars.focusguard.monitor

import android.app.AppOpsManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Process
import android.provider.Settings
import com.copperbars.focusguard.data.FocusRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class BootResumeReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val pending = goAsync()
        val appContext = context.applicationContext
        val job = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        job.launch {
            try {
                val repository = FocusRepository(appContext)
                val settings = repository.settings.first()
                if (settings.monitorEnabled && hasUsageAccess(appContext) &&
                    Settings.canDrawOverlays(appContext)
                ) {
                    UsageMonitorService.start(appContext)
                }
            } catch (_: Exception) {
                // Permission revocation or Android background-start policy can prevent restoration.
            } finally {
                pending.finish()
                job.cancel()
            }
        }
    }

    private fun hasUsageAccess(context: Context): Boolean {
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        return appOps.checkOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            Process.myUid(),
            context.packageName
        ) == AppOpsManager.MODE_ALLOWED
    }
}
