package com.trailnest.app.data

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
        require(bytes.size <= 5 * 1024 * 1024) { "GPX must be smaller than 5 MB." }
        val text = bytes.toString(Charsets.UTF_8)
        require(!text.contains("<!DOCTYPE", true) && !text.contains("<!ENTITY", true)) { "External XML declarations are not allowed." }
        val parser = Xml.newPullParser()
        parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, true)
        parser.setInput(text.reader())
        val points = mutableListOf<GeoPoint>(); var title = fallbackTitle
        var rootSeen = false; var segments = 0; var routes = 0; var tracks = 0
        while (parser.eventType != XmlPullParser.END_DOCUMENT) {
            if (parser.eventType == XmlPullParser.START_TAG) {
                if (parser.depth == 1) require(parser.name == "gpx") { "The document root must be GPX." }
                when (parser.name) {
                    "gpx" -> rootSeen = true
                    "trk" -> { tracks++; require(tracks <= 1 && routes == 0) { "Import one track at a time." } }
                    "trkseg" -> { segments++; require(segments <= 1) { "Multiple track segments could conceal gaps. Import one continuous segment." } }
                    "rte" -> { routes++; require(routes <= 1 && tracks == 0) { "Import one continuous route or track." } }
                    "name" -> if (title == fallbackTitle) title = parser.nextText().take(120).ifBlank { fallbackTitle }
                    "trkpt", "rtept" -> {
                        require(!(tracks > 0 && routes > 0)) { "Mixed routes and tracks are not supported." }
                        require(points.size < 50000) { "GPX exceeds 50,000 points." }
                        points.add(GeoPoint(parser.getAttributeValue(null, "lat").toDouble(), parser.getAttributeValue(null, "lon").toDouble()))
                    }
                }
            }
            parser.next()
        }
        require(rootSeen && points.size >= 2) { "Choose a GPX file with at least two track or route points." }
        require(TrailMath.length(points) > 0) { "The route has no length." }
        val geometry = points.joinToString(";") { "${it.lat},${it.lon}" }
        return ImportedRoute(UUID.randomUUID().toString(), title, geometry, TrailMath.length(points), System.currentTimeMillis(), digest(geometry))
    }
    fun export(route: ImportedRoute): String {
        val title = route.title.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;").replace("'", "&apos;")
        return "<?xml version=\"1.0\" encoding=\"UTF-8\"?><gpx version=\"1.1\" creator=\"TrailNest\" xmlns=\"http://www.topografix.com/GPX/1/1\"><trk><name>$title</name><trkseg>" + points(route).joinToString("") { "<trkpt lat=\"${it.lat}\" lon=\"${it.lon}\"/>" } + "</trkseg></trk></gpx>"
    }
}
