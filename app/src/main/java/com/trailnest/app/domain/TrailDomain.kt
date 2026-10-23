package com.trailnest.app.domain

import java.util.Locale
import kotlin.math.*

data class GeoPoint(val lat: Double, val lon: Double) {
    init { require(lat.isFinite() && lon.isFinite() && lat in -90.0..90.0 && lon in -180.0..180.0) }
}
enum class Coverage { VERIFIED, LIMITED, PLANNED, UNAVAILABLE }
data class Country(val iso: String, val name: String, val languages: List<String>, val imperial: Boolean = false, val coverage: Coverage = Coverage.PLANNED, val offlineTiles: Boolean = false)
object Countries {
    val all = listOf(
        Country("GB", "United Kingdom", listOf("en-GB"), true), Country("US", "United States", listOf("en-US"), true),
        Country("CA", "Canada", listOf("en-CA", "fr-CA")), Country("DE", "Germany", listOf("de")),
        Country("FR", "France", listOf("fr")), Country("IT", "Italy", listOf("it")), Country("ES", "Spain", listOf("es")),
        Country("NL", "Netherlands", listOf("nl")), Country("JP", "Japan", listOf("ja")), Country("AU", "Australia", listOf("en-AU")))
}
data class RouteProvenance(val source: String, val licence: String?, val attribution: String, val reviewedBy: String? = null, val reviewedAt: String? = null, val published: Boolean = false) {
    val canPublish get() = published && !licence.isNullOrBlank() && !reviewedBy.isNullOrBlank() && !reviewedAt.isNullOrBlank()
}
data class TilePermission(val provider: String, val contractReference: String?, val permitsOffline: Boolean) {
    val mayDownload get() = permitsOffline && !contractReference.isNullOrBlank()
}
interface MapProvider { val id: String; val attribution: String; val permission: TilePermission; fun styleUrl(country: String): String? }
class UnconfiguredMapProvider : MapProvider {
    override val id = "unconfigured"
    override val attribution = "No basemap provider configured"
    override val permission = TilePermission(id, null, false)
    override fun styleUrl(country: String): String? = null
}
sealed interface PurchaseState { data object Unavailable : PurchaseState; data class Verified(val expiresAt: Long, val checkedAt: Long) : PurchaseState }
fun PurchaseState.isEntitled(now: Long): Boolean = this is PurchaseState.Verified && now < expiresAt && now >= checkedAt && now - checkedAt < 24 * 60 * 60 * 1000
interface BillingProvider { suspend fun restore(): PurchaseState; suspend fun purchase(sku: String): PurchaseState }
class UnconfiguredBilling : BillingProvider {
    override suspend fun restore() = PurchaseState.Unavailable
    override suspend fun purchase(sku: String) = PurchaseState.Unavailable
}
object TrailMath {
    fun distance(a: GeoPoint, b: GeoPoint): Double {
        val p1 = Math.toRadians(a.lat); val p2 = Math.toRadians(b.lat)
        val dlat = p2 - p1; val dlon = Math.toRadians(b.lon - a.lon)
        val h = sin(dlat / 2).pow(2) + cos(p1) * cos(p2) * sin(dlon / 2).pow(2)
        return 6371008.8 * 2 * asin(sqrt(h.coerceIn(0.0, 1.0)))
    }
    fun length(points: List<GeoPoint>) = points.zipWithNext().sumOf { (a, b) -> distance(a, b) }
    data class Progress(val remainingMetres: Double, val distanceFromRoute: Double, val uncertain: Boolean, val offRoute: Boolean)
    fun progress(points: List<GeoPoint>, fix: GeoPoint, accuracy: Double): Progress {
        require(points.size >= 2)
        require(accuracy.isFinite() && accuracy >= 0)
        // Local tangent projection with wrapped longitude deltas supports dateline crossings.
        fun deltaLon(lon: Double) = ((lon - fix.lon + 540) % 360 - 180)
        fun project(p: GeoPoint): Pair<Double, Double> = Pair(Math.toRadians(deltaLon(p.lon)) * 6371008.8 * cos(Math.toRadians(fix.lat)), Math.toRadians(p.lat - fix.lat) * 6371008.8)
        var nearest = Double.POSITIVE_INFINITY; var remaining = 0.0
        var after = length(points)
        points.zipWithNext().forEach { (a, b) ->
            val segment = distance(a, b); after -= segment
            val (ax, ay) = project(a); val (bx, by) = project(b)
            val dx = bx - ax; val dy = by - ay; val denominator = dx * dx + dy * dy
            val t = if (denominator == 0.0) 0.0 else (-(ax * dx + ay * dy) / denominator).coerceIn(0.0, 1.0)
            val d = hypot(ax + t * dx, ay + t * dy)
            if (d < nearest) { nearest = d; remaining = after + segment * (1 - t) }
        }
        return Progress(remaining.coerceAtLeast(0.0), nearest, accuracy > 40, accuracy <= 40 && nearest > max(40.0, accuracy * 2))
    }
    fun formatDistance(metres: Double, imperial: Boolean, locale: Locale = Locale.getDefault()): String =
        if (imperial) String.format(locale, "%.1f mi", metres / 1609.344) else String.format(locale, "%.1f km", metres / 1000)
}
enum class VoiceCommand { REPEAT, DISTANCE, MAP, PAUSE, RESUME, SAVE, UNKNOWN }
fun parseCommand(text: String, locale: Locale): VoiceCommand {
    if (locale.language != "en") return VoiceCommand.UNKNOWN
    return when (text.lowercase(locale).trim().trimEnd('.', '?')) {
        "repeat direction" -> VoiceCommand.REPEAT; "how far is left" -> VoiceCommand.DISTANCE
        "show the map" -> VoiceCommand.MAP; "pause navigation" -> VoiceCommand.PAUSE
        "resume walking" -> VoiceCommand.RESUME; "save this walk" -> VoiceCommand.SAVE
        else -> VoiceCommand.UNKNOWN
    }
}
