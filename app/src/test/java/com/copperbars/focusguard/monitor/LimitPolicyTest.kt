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
    fun focusBlocksOnlyWhenSelectedAppIsMarked() {
        val blockedApp = TrackedAppEntity("sample.app", "Sample", focusBlocked = true)
        val allowedApp = TrackedAppEntity("other.app", "Other", focusBlocked = false)
        assertTrue(LimitPolicy.isBlocked(blockedApp, 0L, true))
        assertFalse(LimitPolicy.isBlocked(allowedApp, 0L, true))
        assertFalse(LimitPolicy.isBlocked(blockedApp, 0L, false))
    }
}
