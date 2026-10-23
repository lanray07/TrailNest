package com.trailnest.app

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.datastore.preferences.core.*
import com.trailnest.app.data.*
import com.trailnest.app.domain.*
import java.text.DateFormat
import java.util.Date
import java.util.UUID

val checklistItems = listOf("Drinking water", "Suitable footwear", "Weather-appropriate clothing", "Charged phone", "Power bank", "Snacks", "Offline route information", "Check current conditions", "Emergency contact plan", "Dog lead, if relevant", "First-aid essentials")
@Composable fun ChecklistScreen(prefs: Preferences, toggle: (String) -> Unit) {
    val complete = checklistItems.indices.count { prefs[booleanPreferencesKey("kit_$it")] == true }
    Page("A little preparation goes a long way.", "Your personal pre-walk checklist. Adapt your kit to the route, weather and your needs.") {
        Text("$complete of ${checklistItems.size} essentials checked")
        LinearProgressIndicator(progress = { complete.toFloat() / checklistItems.size }, modifier = Modifier.fillMaxWidth())
        checklistItems.forEachIndexed { i, item ->
            Row(Modifier.fillMaxWidth(), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Checkbox(prefs[booleanPreferencesKey("kit_$i")] ?: false, { toggle("kit_$i") })
                Text(item)
            }
        }
        Text("A completed checklist cannot guarantee safety. Check current conditions, access and your own ability.")
        OutlinedButton(onClick = { checklistItems.indices.filter { prefs[booleanPreferencesKey("kit_$it")] == true }.forEach { toggle("kit_$it") } }) { Text("Reset checklist") }
    }
}
@Composable fun JournalScreen(walks: List<Walk>, imperial: Boolean, save: (Walk) -> Unit, delete: (String) -> Unit) {
    var adding by rememberSaveable { mutableStateOf(false) }
    var title by rememberSaveable { mutableStateOf("") }; var distance by rememberSaveable { mutableStateOf("") }
    var duration by rememberSaveable { mutableStateOf("") }; var notes by rememberSaveable { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }; var deleting by remember { mutableStateOf<Walk?>(null) }
    Page("Your walking story.", "A private space for the places you’ve been and the moments worth keeping.") {
        Text("${walks.size} walks  ·  ${TrailMath.formatDistance(walks.sumOf { it.metres }, imperial)}  ·  ${walks.sumOf { it.minutes }} min")
        Button(onClick = { adding = !adding }) { Text(if (adding) "Close entry" else "Record a walk") }
        if (adding) TrailPanel {
            OutlinedTextField(title, { title = it }, label = { Text("Walk title") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(distance, { distance = it }, label = { Text(if (imperial) "Distance (miles)" else "Distance (km)") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(duration, { duration = it }, label = { Text("Duration (minutes)") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(notes, { notes = it }, label = { Text("Notes") }, modifier = Modifier.fillMaxWidth())
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            Button(onClick = {
                val d = distance.toDoubleOrNull(); val minutes = duration.toIntOrNull()
                if (title.isBlank() || d == null || !d.isFinite() || d <= 0 || d > 1000 || minutes == null || minutes !in 1..10080) error = "Enter a title, distance between 0 and 1,000, and duration between 1 and 10,080 minutes."
                else { save(Walk(UUID.randomUUID().toString(), title.trim().take(120), System.currentTimeMillis(), d * if (imperial) 1609.344 else 1000.0, minutes, notes.take(5000), 0, false)); adding = false; title = ""; distance = ""; duration = ""; notes = ""; error = null }
            }) { Text("Save walk") }
        }
        if (walks.isEmpty() && !adding) EmptyPanel("Every walk has a story.", "Your first walk will appear here. Add a completed walk to start your personal journal.")
        walks.forEach { walk -> TrailPanel(Modifier.fillMaxWidth()) {
            Heading(walk.title, true)
            Text("${DateFormat.getDateInstance().format(Date(walk.date))} · ${TrailMath.formatDistance(walk.metres, imperial)} · ${walk.minutes} min")
            if (walk.notes.isNotBlank()) Text(walk.notes)
            Row { TextButton(onClick = { save(walk.copy(favourite = !walk.favourite)) }) { Text(if (walk.favourite) "Unfavourite" else "Favourite") }; TextButton(onClick = { deleting = walk }) { Text("Delete entry") } }
            Row { (1..5).forEach { rating -> TextButton(onClick = { save(walk.copy(rating = rating)) }, contentPadding = PaddingValues(4.dp), modifier = Modifier.width(48.dp)) { Text(if (rating <= walk.rating) "★" else "☆") } } }
        } }
    }
    deleting?.let { walk -> AlertDialog(onDismissRequest = { deleting = null }, title = { Text("Delete this walk?") }, text = { Text("“${walk.title}” will be permanently removed from this device.") }, confirmButton = { TextButton(onClick = { delete(walk.id); deleting = null }) { Text("Delete") } }, dismissButton = { TextButton(onClick = { deleting = null }) { Text("Keep walk") } }) }
}
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
        TrailPanel(Modifier.fillMaxWidth()) { Heading("Your privacy", true); Text("Walks, imports and preferences stay on this device. No accounts, ads or analytics. Location is used only while the route screen is active and stops when the app leaves the foreground. Sharing opens the Android Sharesheet only when you choose it."); Text("System speech services may process voice data under their own privacy terms. Uninstalling clears local data. Backups are disabled.") }
        TrailPanel(Modifier.fillMaxWidth()) { Heading("Responsible access", true); Text("Follow local signs, respect wildlife and livestock, leave gates as found, and take litter home. Rules vary by country and region; check the relevant land manager’s current guidance."); Text("UK reference: gov.uk/government/publications/the-countryside-code. The England and Wales code is not a global access policy.") }
        Text("TrailNest 0.1.0 · Development build · Not approved for Appstore release")
    }
}
