package com.smrti.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdaterTest {
    @Test fun versionOrder() {
        assertTrue(isNewer("1.3", "1.2"))
        assertTrue(isNewer("1.10", "1.9"))
        assertTrue(isNewer("1.2.1", "1.2"))
        assertTrue(isNewer("v2.0", "1.9"))
        assertFalse(isNewer("1.2", "1.2"))
        assertFalse(isNewer("1.2.0", "1.2"))
        assertFalse(isNewer("1.1", "1.2"))
    }

    @Test fun parsesVersionJson() {
        val i = parseInfo("""{"version":"v1.3","date":"2026-11-23","sha256":"ab","notes":["One","Two"]}""")
        assertEquals("1.3", i.version)
        assertEquals(listOf("One", "Two"), i.notes)
        assertEquals("ab", i.sha256)
    }

    @Test
    fun gapTellsWhatKindOfUpdateItIs() {
        assertEquals(VersionGap.Major, versionGap("2.0", "1.9"))
        assertEquals(VersionGap.Minor, versionGap("1.3", "1.2"))
        assertEquals(VersionGap.Patch, versionGap("1.2.1", "1.2"))
        assertEquals(VersionGap.Same, versionGap("1.2", "1.2"))
        assertEquals(VersionGap.InstalledNewer, versionGap("1.2", "1.3"))
    }
}
