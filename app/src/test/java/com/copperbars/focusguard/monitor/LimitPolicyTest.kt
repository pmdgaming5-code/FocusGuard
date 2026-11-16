package com.copperbars.focusguard.monitor

import com.copperbars.focusguard.data.TrackedAppEntity
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LimitPolicyTest {
    @Test
    fun unlimitedAppIsNotBlocked() {
        val app = TrackedAppEntity("sample.app", "Sample")
        assertFalse(LimitPolicy.isBlocked(app, 86_400_000L, false))
    }

    @Test
    fun dailyLimitBlocksAtBoundary() {
        val app = TrackedAppEntity("sample.app", "Sample", dailyLimitMinutes = 5)
        assertFalse(LimitPolicy.isBlocked(app, 299_999L, false))
        assertTrue(LimitPolicy.isBlocked(app, 300_000L, false))
    }

    @Test
    fun sessionLimitBlocksAtBoundary() {
        val app = TrackedAppEntity(
            "sample.app",
            "Sample",
            sessionLimitMinutes = 10,
            sessionUsageMillis = 600_000L
        )
        assertTrue(LimitPolicy.isBlocked(app, 0L, false))
    }

    @Test
    fun globalDailyLimitUsesAggregateTrackedUsage() {
        assertFalse(LimitPolicy.isGlobalDailyLimitReached(0, 9_000_000L))
        assertFalse(LimitPolicy.isGlobalDailyLimitReached(30, 1_799_999L))
        assertTrue(LimitPolicy.isGlobalDailyLimitReached(30, 1_800_000L))
    }

    @Test
    fun disabledLimitsDoNotApplyDailyOrSessionThresholds() {
        val app = TrackedAppEntity(
            "sample.app",
            "Sample",
            dailyLimitMinutes = 1,
            sessionLimitMinutes = 1,
            sessionUsageMillis = 60_000L,
            limitsEnabled = false
        )
        assertFalse(LimitPolicy.isBlocked(app, 60_000L, false))
    }

    @Test
    fun focusBlockStillAppliesWhenTimeLimitsAreDisabled() {
        val app = TrackedAppEntity(
            "sample.app",
            "Sample",
            dailyLimitMinutes = 1,
            limitsEnabled = false,
            focusBlocked = true
        )
        assertTrue(LimitPolicy.isBlocked(app, 60_000L, true))
    }

    @Test
    fun focusBlocksOnlyWhenSelectedAppIsMarked() {
        val blockedApp = TrackedAppEntity("sample.app", "Sample", focusBlocked = true)
        val allowedApp = TrackedAppEntity("other.app", "Other", focusBlocked = false)
        assertTrue(LimitPolicy.isBlocked(blockedApp, 0L, true))
        assertFalse(LimitPolicy.isBlocked(allowedApp, 0L, true))
        assertFalse(LimitPolicy.isBlocked(blockedApp, 0L, false))
    }
}
