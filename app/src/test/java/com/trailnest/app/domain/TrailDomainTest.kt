package com.trailnest.app.domain

import org.junit.Assert.*
import org.junit.Test
import java.util.Locale

class TrailDomainTest {
    @Test fun distanceAtEquator() { assertEquals(111195.0, TrailMath.distance(GeoPoint(0.0, 0.0), GeoPoint(0.0, 1.0)), 20.0) }
    @Test fun progressProjectsOntoSegmentRatherThanNearestWaypoint() {
        val route = listOf(GeoPoint(0.0, 0.0), GeoPoint(0.0, 0.01))
        val progress = TrailMath.progress(route, GeoPoint(0.0, 0.005), 5.0)
        assertEquals(TrailMath.length(route) / 2, progress.remainingMetres, 1.0)
        assertFalse(progress.offRoute); assertFalse(progress.uncertain)
    }
    @Test fun poorAccuracyDoesNotProduceConfidentOffRouteWarning() {
        val route = listOf(GeoPoint(0.0, 0.0), GeoPoint(0.0, 0.01))
        val progress = TrailMath.progress(route, GeoPoint(0.01, 0.005), 100.0)
        assertTrue(progress.uncertain); assertFalse(progress.offRoute)
    }
    @Test fun goodAccuracyDetectsDeviation() { assertTrue(TrailMath.progress(listOf(GeoPoint(0.0, 0.0), GeoPoint(0.0, 0.01)), GeoPoint(0.01, 0.005), 5.0).offRoute) }
    @Test fun duplicatePointsDoNotDivideByZero() { val p = TrailMath.progress(listOf(GeoPoint(0.0, 0.0), GeoPoint(0.0, 0.0)), GeoPoint(0.0, 0.0), 5.0); assertEquals(0.0, p.remainingMetres, 0.0) }
    @Test(expected = IllegalArgumentException::class) fun invalidCoordinatesRejected() { GeoPoint(Double.NaN, 0.0) }
    @Test fun offlineTilesRequireExplicitContract() { assertFalse(TilePermission("OS", null, true).mayDownload); assertFalse(TilePermission("OS", "contract", false).mayDownload); assertTrue(TilePermission("licensed", "contract", true).mayDownload) }
    @Test fun publicationRequiresReviewerAndRights() { assertFalse(RouteProvenance("partner", null, "partner", "reviewer", "2026-10-23", true).canPublish) }
    @Test fun unconfiguredBillingCannotGrantPremium() { assertFalse(PurchaseState.Unavailable.isEntitled(1000)); assertFalse(PurchaseState.Verified(500, 100).isEntitled(1000)); assertFalse(PurchaseState.Verified(Long.MAX_VALUE, 0).isEntitled(86400001)); assertTrue(PurchaseState.Verified(2000, 100).isEntitled(1000)) }
    @Test fun localeIsSeparateFromCoverage() { assertTrue(Countries.all.all { it.coverage == Coverage.PLANNED && !it.offlineTiles }); assertEquals(10, Countries.all.map { it.iso }.distinct().size) }
    @Test fun voiceCommandsAreExplicitAndLocaleAware() { assertEquals(VoiceCommand.PAUSE, parseCommand("Pause navigation.", Locale.UK)); assertEquals(VoiceCommand.UNKNOWN, parseCommand("Pause navigation.", Locale.GERMAN)) }
    @Test fun unitsConvert() { assertEquals("1.0 mi", TrailMath.formatDistance(1609.344, true, Locale.US)); assertEquals("1.0 km", TrailMath.formatDistance(1000.0, false, Locale.UK)) }
}
