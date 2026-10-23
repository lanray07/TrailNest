package com.trailnest.app.data

import com.trailnest.app.domain.TextFailure
import android.util.Xml
import com.trailnest.app.domain.GeoPoint
import com.trailnest.app.domain.TrailMath
import org.xmlpull.v1.XmlPullParser
import java.io.InputStream
import java.security.MessageDigest
import java.util.UUID

object Gpx {
    fun digest(text: String) = MessageDigest.getInstance("SHA-256").digest(text.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
    fun points(route: ImportedRoute): List<GeoPoint> = route.geometry.split(';').map { it.split(',').let { p -> GeoPoint(p[0].toDouble(), p[1].toDouble()) } }
    fun read(input: InputStream, fallbackTitle: String): ImportedRoute {
        val bytes = input.use { stream ->
            val output = java.io.ByteArrayOutputStream()
            val buffer = ByteArray(8192)
            while (output.size() <= 5 * 1024 * 1024) {
                val count = stream.read(buffer, 0, minOf(buffer.size, 5 * 1024 * 1024 + 1 - output.size()))
                if (count < 0) break
                output.write(buffer, 0, count)
            }
            output.toByteArray()
        }
        require(bytes.size <= 5 * 1024 * 1024) { throw TextFailure("error_gpx_must_be_smaller_than_5_mb") }
        val text = bytes.toString(Charsets.UTF_8)
        require(!text.contains("<!DOCTYPE", true) && !text.contains("<!ENTITY", true)) { throw TextFailure("error_external_xml_declarations_are_not_allowed") }
        val parser = Xml.newPullParser()
        parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, true)
        parser.setInput(text.reader())
        val points = mutableListOf<GeoPoint>(); var title = fallbackTitle
        var rootSeen = false; var segments = 0; var routes = 0; var tracks = 0
        while (parser.eventType != XmlPullParser.END_DOCUMENT) {
            if (parser.eventType == XmlPullParser.START_TAG) {
                if (parser.depth == 1) require(parser.name == "gpx") { throw TextFailure("error_the_document_root_must_be_gpx") }
                when (parser.name) {
                    "gpx" -> rootSeen = true
                    "trk" -> { tracks++; require(tracks <= 1 && routes == 0) { throw TextFailure("error_import_one_track_at_a_time") } }
                    "trkseg" -> { segments++; require(segments <= 1) { throw TextFailure("error_multiple_track_segments_could_conceal_gaps_import_one_continuous_") } }
                    "rte" -> { routes++; require(routes <= 1 && tracks == 0) { throw TextFailure("error_import_one_continuous_route_or_track") } }
                    "name" -> if (title == fallbackTitle) title = parser.nextText().take(120).ifBlank { fallbackTitle }
                    "trkpt", "rtept" -> {
                        require(!(tracks > 0 && routes > 0)) { throw TextFailure("error_mixed_routes_and_tracks_are_not_supported") }
                        require(points.size < 50000) { throw TextFailure("error_gpx_exceeds_50_000_points") }
                        points.add(GeoPoint(parser.getAttributeValue(null, "lat").toDouble(), parser.getAttributeValue(null, "lon").toDouble()))
                    }
                }
            }
            parser.next()
        }
        require(rootSeen && points.size >= 2) { throw TextFailure("error_choose_a_gpx_file_with_at_least_two_track_or_route_points") }
        require(TrailMath.length(points) > 0) { throw TextFailure("error_the_route_has_no_length") }
        val geometry = points.joinToString(";") { "${it.lat},${it.lon}" }
        return ImportedRoute(UUID.randomUUID().toString(), title, geometry, TrailMath.length(points), System.currentTimeMillis(), digest(geometry))
    }
    fun export(route: ImportedRoute): String {
        val title = route.title.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;").replace("'", "&apos;")
        return "<?xml version=\"1.0\" encoding=\"UTF-8\"?><gpx version=\"1.1\" creator=\"TrailNest\" xmlns=\"http://www.topografix.com/GPX/1/1\"><trk><name>$title</name><trkseg>" + points(route).joinToString("") { "<trkpt lat=\"${it.lat}\" lon=\"${it.lon}\"/>" } + "</trkseg></trk></gpx>"
    }
}
