package com.copperbars.focusguard.monitor

import com.copperbars.focusguard.data.TrackedAppEntity

object LimitPolicy {
    fun isBlocked(
        app: TrackedAppEntity,
        dailyUsedMillis: Long,
        focusModeActive: Boolean
    ): Boolean {
        val dailyReached = app.limitsEnabled &&
            app.dailyLimitMinutes > 0 &&
            dailyUsedMillis >= app.dailyLimitMinutes.toLong() * 60_000L
        val sessionReached = app.limitsEnabled &&
            app.sessionLimitMinutes > 0 &&
            app.sessionUsageMillis >= app.sessionLimitMinutes.toLong() * 60_000L
        val focusBlocked = focusModeActive && app.focusBlocked
        return dailyReached || sessionReached || focusBlocked
    }
}
