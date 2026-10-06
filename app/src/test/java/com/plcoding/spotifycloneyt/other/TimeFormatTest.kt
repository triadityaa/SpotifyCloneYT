package com.plcoding.spotifycloneyt.other

import org.junit.Assert.assertEquals
import org.junit.Test

class TimeFormatTest {

    @Test
    fun formatPlaybackTime_formatsMinutesAndSeconds() {
        assertEquals("00:00", formatPlaybackTime(0L))
        assertEquals("00:59", formatPlaybackTime(59_999L))
        assertEquals("03:25", formatPlaybackTime(205_000L))
        assertEquals("59:59", formatPlaybackTime(3_599_000L))
    }

    @Test
    fun formatPlaybackTime_addsHoursFromOneHour() {
        assertEquals("1:00:00", formatPlaybackTime(3_600_000L))
        assertEquals("2:03:04", formatPlaybackTime(7_384_000L))
    }

    @Test
    fun formatPlaybackTime_treatsNegativeTimeAsZero() {
        assertEquals("00:00", formatPlaybackTime(-1L))
    }
}
