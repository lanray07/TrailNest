package com.trailnest.app

import android.location.Location
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.datastore.preferences.core.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.trailnest.app.data.*
import com.trailnest.app.domain.*

@Composable fun TrailApp(model: TrailViewModel, notice: String?, dismiss: () -> Unit, fix: Location?, locating: Boolean, voiceReady: Boolean, command: VoiceCommand, onConsumeCommand: () -> Unit, onImport: () -> Unit, onExport: (ImportedRoute) -> Unit, onLocation: () -> Unit, onSpeak: (String) -> Unit, onListen: () -> Unit, onShare: (String) -> Unit, onNotice: (String) -> Unit) {
    val prefs by model.prefs.collectAsStateWithLifecycle()
    val walks by model.walks.collectAsStateWithLifecycle()
    val routes by model.routes.collectAsStateWithLifecycle()
    val dark = prefs[booleanPreferencesKey("dark")] ?: false
    val imperial = prefs[stringPreferencesKey("units")] != "metric"
    val scheme = if (dark) darkColorScheme(primary = Color(0xFFA7D3BC), background = Color(0xFF17241E), surface = Color(0xFF22332A), onSurface = Ivory) else lightColorScheme(primary = Forest, background = Ivory, surface = Color(0xFFFCFBF7), onSurface = Ink, outlineVariant = Color(0xFFDDDFD5))
    var page by rememberSaveable { mutableStateOf("Discover") }
    var selectedId by rememberSaveable { mutableStateOf<String?>(null) }
    val selected = routes.find { it.id == selectedId }
    val labels = listOf(stringResource(R.string.discover), stringResource(R.string.my_walks), stringResource(R.string.offline), stringResource(R.string.checklist), stringResource(R.string.settings))
    val keys = listOf("Discover", "My walks", "Offline packs", "Checklist", "Settings")
    val icons = listOf(Icons.Outlined.Explore, Icons.Outlined.BookmarkBorder, Icons.Outlined.Download, Icons.Outlined.Checklist, Icons.Outlined.Settings)
    MaterialTheme(colorScheme = scheme) {
        Surface(Modifier.fillMaxSize(), color = scheme.background) {
            BoxWithConstraints(Modifier.safeDrawingPadding()) {
                val wide = maxWidth >= 720.dp
                Column {
                    Row(Modifier.weight(1f)) {
                        if (wide) Column(Modifier.width(180.dp).fillMaxHeight().padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Image(painterResource(R.drawable.ic_trailnest), "TrailNest", Modifier.size(64.dp).align(Alignment.CenterHorizontally))
                            Text("TrailNest", fontSize = 26.sp, modifier = Modifier.align(Alignment.CenterHorizontally))
                            Spacer(Modifier.height(20.dp))
                            keys.forEachIndexed { i, key -> NavigationDrawerItem(label = { Text(labels[i]) }, selected = page == key, onClick = { page = key; selectedId = null }, icon = { Icon(icons[i], null) }) }
                            Spacer(Modifier.weight(1f)); Text("A brighter outdoors.\nOne step at a time.", Modifier.padding(12.dp), style = MaterialTheme.typography.bodySmall)
                        }
                        Box(Modifier.weight(1f)) {
                            if (selected != null) RouteScreen(selected, fix, locating, imperial, voiceReady, command, onConsumeCommand, onLocation, onSpeak, onListen, onExport, onShare, { selectedId = null }, onNotice)
                            else when (page) {
                                "Discover" -> DiscoverScreen({ page = it }, onImport)
                                "My walks" -> JournalScreen(walks, imperial, model::save, model::deleteWalk)
                                "Offline packs" -> PacksScreen(routes, imperial, onImport, {
                                    if (Gpx.digest(it.geometry) == it.sha256) selectedId = it.id
                                    else onNotice("Integrity check failed. Reimport the original GPX.")
                                }, { route ->
                                    if (!route.packed && routes.count { it.packed } >= 1) onNotice("The free tier stores one route pack. Remove the existing pack first. Premium purchasing is not yet configured.")
                                    else if (Gpx.digest(route.geometry) != route.sha256) onNotice("Integrity check failed. Reimport the original GPX.")
                                    else model.save(route.copy(packed = !route.packed))
                                }, model::deleteRoute)
                                "Checklist" -> ChecklistScreen(prefs, model::toggle)
                                "Settings" -> SettingsScreen(prefs, model::toggle, model::setting, voiceReady)
                                "Coverage" -> CoverageScreen()
                            }
                        }
                    }
                    if (!wide) NavigationBar {
                        keys.forEachIndexed { i, key -> NavigationBarItem(selected = page == key, onClick = { page = key; selectedId = null }, icon = { Icon(icons[i], labels[i]) }, label = { Text(if (i == 2) "Offline" else labels[i], fontSize = 10.sp) }) }
                    }
                }
                if (notice != null) AlertDialog(onDismissRequest = dismiss, title = { Text("TrailNest") }, text = { Text(notice) }, confirmButton = { TextButton(onClick = dismiss) { Text("Got it") } })
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable fun DiscoverScreen(navigate: (String) -> Unit, onImport: () -> Unit) {
    var search by rememberSaveable { mutableStateOf("") }
    var filter by rememberSaveable { mutableStateOf("All walks") }
    Page(stringResource(R.string.headline), stringResource(R.string.tagline)) {
        // Original editorial illustration; it does not depict a published route.
        Image(painterResource(R.drawable.landscape), "Illustrated hills and winding footpath, an imaginary landscape", Modifier.fillMaxWidth().height(180.dp), contentScale = androidx.compose.ui.layout.ContentScale.Crop)
        OutlinedTextField(search, { search = it }, Modifier.fillMaxWidth(), label = { Text("Search a town, region or national park") }, leadingIcon = { Icon(Icons.Outlined.Search, null) }, singleLine = true, shape = RoundedCornerShape(50))
        TextButton(onClick = { navigate("Coverage") }) { Icon(Icons.Outlined.Place, null); Text("United Kingdom · Coverage planned") }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) { listOf("All walks", "Woodland", "Coastal", "Riverside", "Hills").forEach { FilterChip(selected = filter == it, onClick = { filter = it }, label = { Text(it) }) } }
        TrailPanel(Modifier.fillMaxWidth()) {
            SectionLabel("Your next adventure")
            Heading(stringResource(R.string.no_routes), true)
            Text(if (search.isNotBlank()) "No reviewed routes match “$search”. Try importing your own GPX while our collection is prepared." else stringResource(R.string.coverage_message))
            if (filter != "All walks") Text("${filter} filter selected. No reviewed routes are available yet.")
            Button(onClick = { navigate("Coverage") }) { Text("Explore coverage"); Icon(Icons.Outlined.ChevronRight, null) }
        }
        TrailPanel(Modifier.fillMaxWidth()) {
            SectionLabel("Be prepared"); Heading("A little preparation goes a long way.", true)
            listOf("Drinking water", "Suitable footwear", "Charged phone").forEach { Text("•  $it") }
            OutlinedButton(onClick = { navigate("Checklist") }) { Text("Open checklist") }
        }
        SectionLabel("Continue your journey")
        OutlinedButton(onClick = onImport, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Outlined.UploadFile, null); Text("Import your own GPX") }
        Text("Imported routes remain private and unreviewed. Check access and conditions before you walk.", style = MaterialTheme.typography.bodySmall)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            TextButton(onClick = { navigate("Offline packs") }) { Text("Offline route packs") }
            TextButton(onClick = { navigate("My walks") }) { Text("Walk journal") }
        }
    }
}
