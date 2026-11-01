package com.trailnest.app.domain

/** Conservative provider ceilings include styles, glyphs and sprites as resources.
 * Shared by every map request, not one independent counter per tile source. */
class MapDownloadBudget(private val byteLimit: Long = 40_000_000, private val resourceLimit: Int = 2800) {
    var bytes: Long = 0; private set
    var resources: Int = 0; private set
    var stopped: Boolean = false; private set
    @Synchronized fun beginResource(): Boolean {
        if (stopped || resources >= resourceLimit) { stopped = true; return false }
        resources++; return true
    }
    @Synchronized fun consume(count: Long): Boolean {
        require(count >= 0)
        if (stopped || count > byteLimit - bytes) { stopped = true; return false }
        bytes += count; return true
    }
    @Synchronized fun stop() { stopped = true }
}

class OpenFreeMapProvider : MapProvider {
    override val id = "openfreemap"
    override val attribution = "© OpenMapTiles © OpenStreetMap contributors"
    override val permission = TilePermission(id, "https://openfreemap.org/tos/ (2026-10-14)", true)
    override fun styleUrl(country: String) = STYLE_URL
    companion object { const val STYLE_URL = "https://tiles.openfreemap.org/styles/liberty" }
}
