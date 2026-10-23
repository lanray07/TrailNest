package com.trailnest.app.domain

import org.junit.Assert.*
import org.junit.Test

class WalkRecordingTest {
    private fun engine() = WalkRecording(RecordingDraft("session", 1234), 0).apply { resume(0) }
    @Test fun activeTimeExcludesPausesAndRepeatedResumeDoesNotCountDowntime() {
        val e = engine(); e.tick(5_000); e.pause(8_000); e.tick(50_000)
        assertEquals(8_000L, e.draft.activeMillis)
        e.resume(60_000); e.pause(63_000)
        assertEquals(11_000L, e.draft.activeMillis)
    }
    @Test fun resumeNeverBridgesPausedTravel() {
        val e = engine(); e.fix(GeoPoint(0.0, 0.0), 5f, 0, 0)
        e.pause(1000); e.resume(10_000)
        e.fix(GeoPoint(1.0, 1.0), 5f, 10_000, 10_000)
        assertEquals(0.0, e.draft.metres, 0.0)
    }
    @Test fun acceptedMovementAddsDistance() {
        val e = engine(); e.fix(GeoPoint(0.0, 0.0), 5f, 0, 0)
        e.fix(GeoPoint(0.0, 0.0001), 5f, 5000, 5000)
        assertEquals(11.1195, e.draft.metres, 0.01)
    }
    @Test fun staleFutureAndInaccurateFixesAreRejected() {
        val e = engine()
        assertFalse(e.fix(GeoPoint(0.0, 0.0), 41f, 0, 0))
        assertFalse(e.fix(GeoPoint(0.0, 0.0), Float.NaN, 0, 0))
        assertFalse(e.fix(GeoPoint(0.0, 0.0), 5f, 0, 16_000))
        assertFalse(e.fix(GeoPoint(0.0, 0.0), 5f, 20_000, 19_000))
        assertFalse(e.draft.hasFix)
    }
    @Test fun outagesAndImplausibleJumpsDoNotAddDistance() {
        val e = engine(); e.fix(GeoPoint(0.0, 0.0), 5f, 0, 0)
        e.fix(GeoPoint(1.0, 1.0), 5f, 5000, 5000)
        e.fix(GeoPoint(1.0, 1.0001), 5f, 40_000, 40_000)
        assertEquals(0.0, e.draft.metres, 0.0)
    }
    @Test fun duplicateAndOutOfOrderFixesAreIgnored() {
        val e = engine(); e.fix(GeoPoint(0.0, 0.0), 5f, 5000, 5000)
        assertFalse(e.fix(GeoPoint(0.0, 0.0001), 5f, 5000, 5000))
        assertFalse(e.fix(GeoPoint(0.0, 0.0001), 5f, 4000, 5000))
        assertEquals(0.0, e.draft.metres, 0.0)
    }
    @Test fun noFixAndPausedStatesRemainHonest() {
        val e = engine(); e.fix(GeoPoint(0.0, 0.0), 5f, 0, 0)
        assertTrue(e.draft.hasFix); e.tick(16_000); assertFalse(e.draft.hasFix)
        e.pause(17_000); assertFalse(e.fix(GeoPoint(0.0, 0.0001), 5f, 18_000, 18_000))
    }
    @Test fun recoveredDraftCannotCountProcessDowntimeOrOldCoordinates() {
        val e = WalkRecording(RecordingDraft("session", 1234, 50_000, 42.0, interrupted = true), 200_000)
        e.tick(300_000); assertEquals(50_000L, e.draft.activeMillis)
        e.resume(300_000); e.fix(GeoPoint(5.0, 5.0), 5f, 301_000, 301_000)
        assertEquals(51_000L, e.draft.activeMillis); assertEquals(42.0, e.draft.metres, 0.0)
        assertFalse(e.draft.interrupted)
    }
    @Test fun zeroDistanceRecordingCanBeEditedWithoutInventingMovement() {
        JournalRules.validate("No GPS walk", "0", "1")
        assertEquals(0.0, JournalRules.editedMetres("0", false, 0.0), 0.0)
    }
    @Test fun cachedLocationFromBeforeResumeCannotBridgeAPause() {
        val e = engine(); e.pause(5000); e.resume(10_000)
        assertFalse(e.fix(GeoPoint(0.0, 0.0), 5f, 9000, 11_000))
        e.fix(GeoPoint(0.0, 0.0001), 5f, 12_000, 12_000)
        assertEquals(0.0, e.draft.metres, 0.0)
    }
}
