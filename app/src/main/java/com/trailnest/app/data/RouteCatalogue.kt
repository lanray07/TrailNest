package com.trailnest.app.data

import android.content.Context
import com.trailnest.app.domain.GeoPoint
import com.trailnest.app.domain.TrailMath
import org.json.JSONObject

data class CatalogueRoute(val route: ImportedRoute, val reviewedAt: String, val source: String, val licence: String)
object RouteCatalogue {
    fun load(context: Context): List<CatalogueRoute> {
        val root = JSONObject(context.assets.open("reviewed-routes.json").bufferedReader().use { it.readText() })
        val records = root.getJSONArray("routes")
        return (0 until records.length()).map { index ->
            val r = records.getJSONObject(index)
            require(r.getString("publicationStatus") == "published" && r.getString("geometryStatus") == "continuous")
            require(r.getString("verificationStatus") == "ai_desk_reviewed" && !r.getBoolean("humanApproved"))
            val array = r.getJSONArray("geometry")
            val points = (0 until array.length()).map { i -> array.getJSONObject(i).let { GeoPoint(it.getDouble("lat"), it.getDouble("lon")) } }
            require(points.size >= 2)
            val geometry = points.joinToString(";") { "${it.lat},${it.lon}" }
            val source = r.getString("source")
            require(source.startsWith("https://www.openstreetmap.org/relation/"))
            val provenance = "TrailNest source: ${r.getString("attribution")}; ${r.getString("licence")}; ${r.getString("licenceUrl")}; $source; ${r.getString("reviewMethod")}; ${r.getString("reviewedAt")}. One-way section; plan your return."
            CatalogueRoute(ImportedRoute("catalogue:${r.getString("id")}", r.getString("title"), geometry, TrailMath.length(points), 0, Gpx.digest(geometry), provenance = provenance), r.getString("reviewedAt"), source, r.getString("licence"))
        }
    }
}
