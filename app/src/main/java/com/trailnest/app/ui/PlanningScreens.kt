package com.trailnest.app

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import androidx.datastore.preferences.core.*
import com.trailnest.app.data.*
import com.trailnest.app.domain.*

@Composable fun PacksScreen(routes: List<ImportedRoute>, imperial: Boolean, onImport: () -> Unit, open: (ImportedRoute) -> Unit, pack: (ImportedRoute) -> Unit, delete: (String) -> Unit) {
    var deleting by remember { mutableStateOf<ImportedRoute?>(null) }
    Page("Ready when you are.", "Private GPX geometry stored on your device. Basemap tiles, elevation and reviewed directions are not included.") {
        Button(onClick = onImport) { Text("Import GPX") }
        Text("${routes.count { it.packed }} of 1 free pack saved · ${routes.filter { it.packed }.sumOf { it.geometry.toByteArray().size }} bytes of geometry")
        if (routes.isEmpty()) EmptyPanel("Take your route with you.", "Import a GPX track, inspect its geometry, then save it as your offline route pack. No map downloads are configured.")
        routes.forEach { route -> TrailPanel(Modifier.fillMaxWidth()) {
            SectionLabel(if (route.packed) "Offline geometry pack" else "Private GPX import")
            Heading(route.title, true); Text(TrailMath.formatDistance(route.metres, imperial))
            Text("User supplied · Access unreviewed · No turn instructions")
            Text(if (Gpx.digest(route.geometry) == route.sha256) "SHA-256 integrity check passed" else "Integrity check failed", style = MaterialTheme.typography.bodySmall)
            Button(onClick = { open(route) }) { Text("Open route") }
            OutlinedButton(onClick = { pack(route) }) { Text(if (route.packed) "Remove offline pack" else "Save geometry offline") }
            TextButton(onClick = { deleting = route }) { Text("Delete import") }
        } }
    }
    deleting?.let { route -> AlertDialog(onDismissRequest = { deleting = null }, title = { Text("Delete imported route?") }, text = { Text("The route and its offline pack will be removed from this device.") }, confirmButton = { TextButton(onClick = { delete(route.id); deleting = null }) { Text("Delete") } }, dismissButton = { TextButton(onClick = { deleting = null }) { Text("Keep route") } }) }
}
@Composable fun CoverageScreen() {
    Page("Good information comes first.", "Language support does not imply route coverage. Every market starts with licensed data and editorial review.") {
        Countries.all.forEach { country -> TrailPanel(Modifier.fillMaxWidth()) { Heading(country.name, true); Text("${country.iso} · Planned · No published reviewed routes"); Text("Offline basemap downloads unavailable") } }
    }
}
@Composable fun SettingsScreen(prefs: Preferences, toggle: (String) -> Unit, setting: (String, String) -> Unit, voiceReady: Boolean) {
    Page("Make yourself at home.") {
        TrailPanel(Modifier.fillMaxWidth()) {
            Heading("Your preferences", true)
            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) { Switch(prefs[booleanPreferencesKey("dark")] ?: false, { toggle("dark") }); Text("  Dark appearance") }
            Text("Distance units")
            Row { FilterChip(prefs[stringPreferencesKey("units")] != "metric", { setting("units", "imperial") }, label = { Text("Miles") }); Spacer(Modifier.width(12.dp)); FilterChip(prefs[stringPreferencesKey("units")] == "metric", { setting("units", "metric") }, label = { Text("Kilometres") }) }
            Text("Spoken output: ${if (voiceReady) "available for device language" else "no compatible voice installed"}")
            Text("Recognition depends on your device’s installed service. It may require an internet connection. Touch controls are always available.")
        }
        TrailPanel(Modifier.fillMaxWidth()) { Heading("TrailNest Premium", true); Text("Purchasing is not available in this build. No products, prices or paid entitlements are configured."); Text("Your checklist, journal and one offline geometry pack are available free. GPX tools are included in this development release.") }
        TrailPanel(Modifier.fillMaxWidth()) { Heading("Your privacy", true); Text("Walks, imports and preferences stay on this device. No accounts, ads or analytics. Route progress stops when the app leaves the foreground. Live walk recording starts only when you choose it and continues with a foreground service until paused or finished, including with the screen off. Recording stores only estimated distance and active duration; coordinates are discarded. Sharing opens the Android Sharesheet only when you choose it."); Text("System speech services may process voice data under their own privacy terms. Uninstalling clears local data. Backups are disabled.") }
        TrailPanel(Modifier.fillMaxWidth()) { Heading("Responsible access", true); Text("Follow local signs, respect wildlife and livestock, leave gates as found, and take litter home. Rules vary by country and region; check the relevant land manager’s current guidance."); Text("UK reference: gov.uk/government/publications/the-countryside-code. The England and Wales code is not a global access policy.") }
        Text(stringResource(R.string.build_notice, BuildConfig.VERSION_NAME))
    }
}
