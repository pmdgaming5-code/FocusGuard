package com.copperbars.focusguard.admin

import android.app.Activity
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context

sealed interface KioskResult {
    data object Started : KioskResult
    data object DeviceOwnerRequired : KioskResult
    data class Failed(val reason: String) : KioskResult
}

object KioskController {
    fun isDeviceOwner(context: Context): Boolean {
        val manager = context.getSystemService(DevicePolicyManager::class.java)
        return manager.isDeviceOwnerApp(context.packageName)
    }

    fun start(activity: Activity, permittedPackages: List<String>): KioskResult {
        val manager = activity.getSystemService(DevicePolicyManager::class.java)
        if (!manager.isDeviceOwnerApp(activity.packageName)) {
            return KioskResult.DeviceOwnerRequired
        }

        return try {
            val admin = ComponentName(activity, FocusGuardAdminReceiver::class.java)
            val allowList = (permittedPackages + activity.packageName).distinct().toTypedArray()
            manager.setLockTaskPackages(admin, allowList)
            activity.startLockTask()
            KioskResult.Started
        } catch (error: SecurityException) {
            KioskResult.Failed("Android cihaz yöneticisi yetkisi doğrulanamadı.")
        } catch (error: Exception) {
            KioskResult.Failed(error.localizedMessage ?: "Kiosk modu başlatılamadı.")
        }
    }

    fun stop(activity: Activity) {
        try {
            activity.stopLockTask()
        } catch (_: Exception) {
            // Android already exited Lock Task Mode or this activity was not pinned.
        }
    }
}
