package com.trailnest.app.domain

import org.junit.Assert.*
import org.junit.Test
import java.time.*

class PersonalPlanningTest {
    @Test fun editingNotesDoesNotRoundTripAndAlterDistance() {
        val exact = 1234.567891
        for (imperial in listOf(true, false)) assertEquals(exact, JournalRules.editedMetres(JournalRules.editableDistance(exact, imperial), imperial, exact), 0.0)
        assertEquals(2000.0, JournalRules.editedMetres("2", false, exact), 0.0)
    }
    @Test fun legacyChecklistRetainsStableIdsAndCheckedState() {
        val items = PersonalChecklist.initial { it == 3 }
        assertEquals(11, items.size); assertTrue(items.single { it.id == "kit_3" }.checked)
        assertEquals(items.size, items.map { it.id }.distinct().size)
    }
    @Test fun resetRetainsCustomItemsAndOrder() {
        val list = listOf(ChecklistItem("own", "Binoculars", true)) + PersonalChecklist.initial { true }
        val reset = PersonalChecklist.reset(list)
        assertEquals(list.map { it.id }, reset.map { it.id }); assertTrue(reset.none { it.checked }); assertEquals("Binoculars", reset.first().title)
    }
    @Test fun emptyOrOverlongChecklistNamesAreRejected() {
        for (title in listOf("  ", "x".repeat(121))) assertTrue(runCatching { PersonalChecklist.validateTitle(title) }.isFailure)
        assertEquals("Water", PersonalChecklist.validateTitle("  Water  "))
    }
    @Test fun journalValidationRejectsNonFiniteAndFractionalDuration() {
        for ((distance, minutes) in listOf("NaN" to "10", "Infinity" to "10", "-1" to "10", "1" to "1.5", "1" to "0")) assertTrue(runCatching { JournalRules.validate("Walk", distance, minutes) }.isFailure)
        JournalRules.validate("Walk", "2.5", "30")
    }
    @Test fun journalDateRejectsInvalidLeapDayAndFutureDate() {
        val today = LocalDate.of(2026, 10, 23)
        assertTrue(runCatching { JournalRules.date("2025-02-29", today) }.isFailure)
        assertTrue(runCatching { JournalRules.date("2026-10-24", today) }.isFailure)
        assertEquals(LocalDate.of(2024, 2, 29), JournalRules.date("2024-02-29", today))
    }
    @Test fun summariesUseCalendarYearAndDeviceZoneAtBoundaries() {
        val entries = listOf(ActivityEntry(Instant.parse("2025-12-31T23:30:00Z").toEpochMilli(), 1200.0, 30), ActivityEntry(Instant.parse("2026-01-15T12:00:00Z").toEpochMilli(), 2000.0, 50))
        val utc = JournalRules.year(entries, 2026, ZoneOffset.UTC)
        assertEquals(1, utc.walks)
        val tokyo = JournalRules.months(entries, 2026, ZoneId.of("Asia/Tokyo"))
        assertEquals(12, tokyo.size); assertEquals(2, tokyo.first().walks); assertEquals(3200.0, tokyo.first().metres, 0.0); assertEquals(80L, tokyo.first().minutes)
        assertTrue(tokyo.drop(1).all { it.walks == 0 })
    }
    @Test fun emptyYearHasZeroTotals() { assertEquals(ActivityTotal(0, 0.0, 0), JournalRules.year(emptyList(), 2026, ZoneOffset.UTC)) }
    @Test fun returnTimeRejectsAmbiguousAndMissingDstTimes() {
        val london = ZoneId.of("Europe/London")
        assertTrue(runCatching { SharedWalkPlan.parseReturn("2026-03-29T01:30", london) }.isFailure)
        assertTrue(runCatching { SharedWalkPlan.parseReturn("2026-10-25T01:30", london) }.isFailure)
        assertEquals(ZoneOffset.ofHours(1), SharedWalkPlan.parseReturn("2026-07-01T12:00", london).offset)
    }
    @Test fun sharedPlanIncludesExplicitReturnOffsetAndContactWithoutLiveClaims() {
        val plan = SharedWalkPlan("My private GPX", GeoPoint(51.0, -1.0), "3.0 km", 60, ZonedDateTime.parse("2026-10-23T15:00:00+01:00[Europe/London]"), "Home contact")
        val text = plan.text(Instant.parse("2026-10-23T10:00:00Z"))
        assertTrue(text.contains("+01:00 [Europe/London]")); assertTrue(text.contains("Home contact")); assertTrue(text.contains("no live tracking"))
        assertTrue(runCatching { plan.text(Instant.parse("2026-10-23T16:00:00Z")) }.isFailure)
    }
}
