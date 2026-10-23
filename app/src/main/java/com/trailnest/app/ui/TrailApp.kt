package com.trailnest.app

import android.location.Location
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.ui.draw.scale
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
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

@Composable fun TrailApp(model: TrailViewModel, notice: String?, dismiss: () -> Unit, fix: Location?, locating: Boolean, voiceReady: Boolean, command: VoiceCommand, onConsumeCommand: () -> Unit, onImport: () -> Unit, onExport: (ImportedRoute) -> Unit, onLocation: () -> Unit, onSpeak: (String) -> Unit, onListen: () -> Unit, onShare: (String) -> Unit, onNotice: (String) -> Unit, recordingPage: Int = 0, onRecordingAction: (String) -> Unit = {}) {
    val prefs by model.prefs.collectAsStateWithLifecycle()
    val walks by model.walks.collectAsStateWithLifecycle()
    val routes by model.routes.collectAsStateWithLifecycle()
    val photos by model.photos.collectAsStateWithLifecycle()
    val photoBusy by model.photoBusy.collectAsStateWithLifecycle()
    val modelMessage by model.message.collectAsStateWithLifecycle()
    val recordingError by model.recordingError.collectAsStateWithLifecycle()
    LaunchedEffect(recordingError) { recordingError?.let { model.message.value = it; model.recordingError.value = null } }
    val dark = prefs[booleanPreferencesKey("dark")] ?: false
    val imperial = prefs[stringPreferencesKey("units")] != "metric"
    val scheme = if (dark) darkColorScheme(primary = Color(0xFFA7D3BC), background = Color(0xFF17241E), surface = Color(0xFF22332A), onSurface = Ivory) else lightColorScheme(primary = Forest, background = Ivory, surface = Color(0xFFFCFBF7), onSurface = Ink, outlineVariant = Color(0xFFDDDFD5))
    var page by rememberSaveable { mutableStateOf("Discover") }
    var selectedId by rememberSaveable { mutableStateOf<String?>(null) }
    LaunchedEffect(recordingPage) { if (recordingPage > 0) { page = "My walks"; selectedId = null } }
    val recording by model.recording.collectAsStateWithLifecycle()
    val recordingBusy by model.recordingBusy.collectAsStateWithLifecycle()
    val selected = routes.find { it.id == selectedId }
    val context = androidx.compose.ui.platform.LocalContext.current
    MaterialTheme(colorScheme = scheme) {
        Surface(Modifier.fillMaxSize(), color = scheme.background) {
            Box(Modifier.fillMaxSize()) {
                TrailScaffold(page, { page = it; selectedId = null }) {
                            if (selected != null) RouteScreen(selected, fix, locating, imperial, voiceReady, command, onConsumeCommand, onLocation, onSpeak, onListen, onExport, onShare, { selectedId = null }, onNotice)
                            else when (page) {
                                "Discover" -> DiscoverScreen({ page = it }, onImport)
                                "My walks" -> JournalScreen(walks, photos, imperial, photoBusy, model::save, model::deleteWalk, model::addPhoto, model::deletePhoto,
                                    recordingRevealKey = recordingPage, recording = { RecordingPanel(recording, imperial, recordingBusy, onRecordingAction, model::finishRecording) })
                                "Offline packs" -> PacksScreen(routes, imperial, onImport, {
                                    if (Gpx.digest(it.geometry) == it.sha256) selectedId = it.id
                                    else onNotice(context.getString(R.string.error_integrity))
                                }, { route ->
                                    if (!route.packed && routes.count { it.packed } >= 1) onNotice(context.getString(R.string.error_pack_limit))
                                    else if (Gpx.digest(route.geometry) != route.sha256) onNotice(context.getString(R.string.error_integrity))
                                    else model.save(route.copy(packed = !route.packed))
                                }, model::deleteRoute)
                                "Checklist" -> ChecklistScreen(model.checklist(prefs), model::checkItem, model::addItem, model::renameItem, model::removeItem, model::moveItem, model::resetChecklist, model::restoreChecklist)
                                "Settings" -> SettingsScreen(prefs, model::toggle, model::setting, voiceReady)
                                "Coverage" -> CoverageScreen()
                            }
                }
                if (notice != null) AlertDialog(onDismissRequest = dismiss, title = { TrailText(stringResource(R.string.app_name)) }, text = { TrailText(notice) }, confirmButton = { TextButton(onClick = dismiss) { TrailText(stringResource(R.string.ui_got_it)) } })
                else if (modelMessage != null) AlertDialog(onDismissRequest = { model.message.value = null }, title = { TrailText(stringResource(R.string.app_name)) }, text = { TrailText(modelMessage!!) }, confirmButton = { TextButton(onClick = { model.message.value = null }) { TrailText(stringResource(R.string.ui_got_it)) } })
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable fun DiscoverScreen(navigate: (String) -> Unit, onImport: () -> Unit) {
    var search by rememberSaveable { mutableStateOf("") }
    var filter by rememberSaveable { mutableIntStateOf(0) }
    val filters = listOf(stringResource(R.string.filter_0), stringResource(R.string.filter_1), stringResource(R.string.filter_2), stringResource(R.string.filter_3), stringResource(R.string.filter_4))
    Page(stringResource(R.string.headline), stringResource(R.string.tagline)) {
        // Original editorial illustration; it does not depict a published route.
        Image(painterResource(R.drawable.landscape), stringResource(R.string.landscape_description), Modifier.fillMaxWidth().height(180.dp), contentScale = androidx.compose.ui.layout.ContentScale.Crop)
        OutlinedTextField(search, { search = it }, Modifier.fillMaxWidth(), label = { TrailText(stringResource(R.string.ui_search_a_town_region_or_national_park)) }, leadingIcon = { Icon(Icons.Outlined.Search, null) }, singleLine = true, shape = RoundedCornerShape(50))
        TextButton(onClick = { navigate("Coverage") }) { Icon(Icons.Outlined.Place, null); TrailText(stringResource(R.string.ui_united_kingdom_coverage_planned)) }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) { filters.forEachIndexed { index, label -> FilterChip(selected = filter == index, onClick = { filter = index }, label = { TrailText(label) }) } }
        TrailPanel(Modifier.fillMaxWidth()) {
            SectionLabel(stringResource(R.string.ui_your_next_adventure))
            Heading(stringResource(R.string.no_routes), true)
            TrailText(if (search.isNotBlank()) stringResource(R.string.search_empty , search) else stringResource(R.string.coverage_message))
            if (filter != 0) TrailText(stringResource(R.string.filter_empty, filters[filter]))
            Button(onClick = { navigate("Coverage") }) { TrailText(stringResource(R.string.ui_explore_coverage), Modifier.weight(1f, fill = false)); Icon(Icons.Outlined.ChevronRight, null, Modifier.scale(if (LocalLayoutDirection.current == LayoutDirection.Rtl) -1f else 1f, 1f)) }
        }
        TrailPanel(Modifier.fillMaxWidth()) {
            SectionLabel(stringResource(R.string.ui_be_prepared)); Heading(stringResource(R.string.ui_a_little_preparation_goes_a_long_way), true)
            listOf(0, 1, 3).forEach { TrailText(stringResource(R.string.preparation_item, checklistDefault(it))) }
            OutlinedButton(onClick = { navigate("Checklist") }) { TrailText(stringResource(R.string.ui_open_checklist)) }
        }
        SectionLabel(stringResource(R.string.ui_continue_your_journey))
        OutlinedButton(onClick = onImport, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Outlined.UploadFile, null); TrailText(stringResource(R.string.ui_import_your_own_gpx)) }
        TrailText(stringResource(R.string.ui_imported_routes_remain_private_and_unreviewed_check_access_and_co), style = MaterialTheme.typography.bodySmall)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            TextButton(onClick = { navigate("Offline packs") }) { TrailText(stringResource(R.string.ui_offline_route_packs)) }
            TextButton(onClick = { navigate("My walks") }) { TrailText(stringResource(R.string.ui_walk_journal)) }
        }
    }
}
