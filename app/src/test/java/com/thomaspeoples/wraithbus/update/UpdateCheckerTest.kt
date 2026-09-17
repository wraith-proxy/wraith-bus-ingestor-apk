package com.thomaspeoples.wraithbus.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The update check is the one piece of this app that does nothing visible
 * when it breaks -- it fails silently by design, so a regression here would
 * never surface as a crash, only as an app that quietly stops noticing
 * releases.
 */
class UpdateCheckerTest {

    @Test
    fun parsesAWellFormedReleaseTag() {
        assertEquals(Triple(1, 0, 2), UpdateChecker.parseVersion("android-1.0.2-debug"))
        assertEquals(Triple(12, 7, 30), UpdateChecker.parseVersion("android-12.7.30-debug"))
    }

    @Test
    fun rejectsTagsThatAreNotThisAppsReleases() {
        // The platform repo cuts vX.Y.Z tags on every merge to main;
        // mistaking one of those for an app release would offer a bogus
        // update to a version of something else entirely.
        assertNull(UpdateChecker.parseVersion("v1.0.2"))
        assertNull(UpdateChecker.parseVersion("android-1.0.2"))
        assertNull(UpdateChecker.parseVersion("android-1.0-debug"))
        assertNull(UpdateChecker.parseVersion(""))
    }

    @Test
    fun ordersByMajorThenMinorThenPatch() {
        assertTrue(UpdateChecker.isNewer(Triple(1, 0, 3), Triple(1, 0, 2)))
        assertTrue(UpdateChecker.isNewer(Triple(1, 1, 0), Triple(1, 0, 9)))
        assertTrue(UpdateChecker.isNewer(Triple(2, 0, 0), Triple(1, 9, 9)))
    }

    @Test
    fun doesNotOfferAnUpdateToTheSameOrOlderVersion() {
        assertFalse(UpdateChecker.isNewer(Triple(1, 0, 2), Triple(1, 0, 2)))
        assertFalse(UpdateChecker.isNewer(Triple(1, 0, 1), Triple(1, 0, 2)))
        assertFalse(UpdateChecker.isNewer(Triple(0, 9, 9), Triple(1, 0, 0)))
    }
}
