package com.smrti.app.data

import org.junit.Assert.assertEquals
import org.junit.Test

class FormatTest {
    @Test fun intervals() {
        assertEquals("10 min", formatInterval(10))
        assertEquals("1 hr", formatInterval(60))
        assertEquals("1 hr 30 min", formatInterval(90))
    }

    @Test fun clock() {
        assertEquals("07:05", formatClock(7 * 60 + 5))
        assertEquals("22:00", formatClock(22 * 60))
    }

    @Test fun migrationsAreContiguous() {
        // every migration must start where the previous one ended, so no phone is left without an upgrade path
        ALL_MIGRATIONS.toList().zipWithNext().forEach { (a, b) -> assertEquals(a.endVersion, b.startVersion) }
        assertEquals(1, ALL_MIGRATIONS.first().startVersion)
    }
}
