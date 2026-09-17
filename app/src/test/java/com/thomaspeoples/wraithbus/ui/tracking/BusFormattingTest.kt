package com.thomaspeoples.wraithbus.ui.tracking

import com.thomaspeoples.wraithbus.network.Bus
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Covers what the bus detail sheet actually renders. The raw ISO timestamps
 * these replaced shipped to a real device once -- "ETA:
 * 2026-09-16T18:57:39Z" -- so the formatting is the feature here, not a
 * detail of it.
 */
class BusFormattingTest {

    private fun bus(status: String?, delayS: Double?) = Bus(
        busId = "B1",
        line = "X93",
        bearing = null,
        direction = "inbound",
        operator = "EYMS",
        originName = null,
        destName = null,
        originAimedDep = null,
        destAimedArr = null,
        remainingKm = null,
        etaIso = null,
        etaSource = null,
        delayS = delayS,
        delaySource = null,
        status = status,
        lat = null,
        lon = null,
    )

    @Test
    fun formatsSecondsAsMinutesAndSeconds() {
        assertEquals("2m 21s", formatMinutesSeconds(141.0))
        assertEquals("0m 5s", formatMinutesSeconds(5.0))
        assertEquals("10m 0s", formatMinutesSeconds(600.0))
    }

    @Test
    fun treatsAnEarlyBusAsAMagnitudeNotANegative() {
        // An early bus carries a negative delay_s; "-3m -1s early" would be
        // nonsense to read on the sheet.
        assertEquals("2m 21s", formatMinutesSeconds(-141.0))
    }

    @Test
    fun describesDelayAccordingToStatus() {
        assertEquals("on time", formatDelay(bus("on_time", 0.0)))
        assertEquals("2m 21s late", formatDelay(bus("late", 141.0)))
        assertEquals("2m 21s early", formatDelay(bus("early", -141.0)))
    }

    @Test
    fun saysNothingWhenStatusIsUnknown() {
        // Roughly three-quarters of live buses read "unknown" at any given
        // moment, so this is the common case, not an edge one.
        assertEquals("", formatDelay(bus("unknown", null)))
        assertEquals("", formatDelay(bus(null, 141.0)))
    }

    @Test
    fun rendersAnIsoTimestampAsAWallClockTime() {
        // Asserted structurally rather than against a literal: the function
        // renders in the device's own zone, and a test that hardcodes one
        // would pass in Britain and fail everywhere else.
        val rendered = isoToLocalTime("2026-09-16T18:57:39Z")
        assertEquals(5, rendered.length)
        assertEquals(':', rendered[2])
    }

    @Test
    fun leavesInputItCannotParseAlone() {
        assertEquals("-", isoToLocalTime(null))
        assertEquals("-", isoToLocalTime(""))
        assertEquals("not a time", isoToLocalTime("not a time"))
    }
}
