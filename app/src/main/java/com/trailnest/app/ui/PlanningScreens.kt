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
    Page(stringResource(R.string.ui_ready_when_you_are), stringResource(R.string.packs_description)) {
        Button(onClick = onImport) { Text(stringResource(R.string.ui_import_gpx)) }
        Text(stringResource(R.string.packs_usage, routes.count { it.packed }, routes.filter { it.packed }.sumOf { it.geometry.toByteArray().size }))
        if (routes.isEmpty()) EmptyPanel(stringResource(R.string.packs_empty_title), stringResource(R.string.packs_empty_description))
        routes.forEach { route -> TrailPanel(Modifier.fillMaxWidth()) {
            SectionLabel(stringResource(if (route.packed) R.string.ui_offline_geometry_pack else R.string.ui_private_gpx_import))
            Heading(route.title, true); Text(distanceText(route.metres, imperial))
            Text(stringResource(R.string.ui_user_supplied_access_unreviewed_no_turn_instructions))
            Text(stringResource(if (Gpx.digest(route.geometry) == route.sha256) R.string.ui_sha_256_integrity_check_passed else R.string.ui_integrity_check_failed), style = MaterialTheme.typography.bodySmall)
            Button(onClick = { open(route) }) { Text(stringResource(R.string.ui_open_route)) }
            OutlinedButton(onClick = { pack(route) }) { Text(stringResource(if (route.packed) R.string.ui_remove_offline_pack else R.string.ui_save_geometry_offline)) }
            TextButton(onClick = { deleting = route }) { Text(stringResource(R.string.ui_delete_import)) }
        } }
    }
    deleting?.let { route -> AlertDialog(onDismissRequest = { deleting = null }, title = { Text(stringResource(R.string.ui_delete_imported_route)) }, text = { Text(stringResource(R.string.ui_the_route_and_its_offline_pack_will_be_removed_from_this_device)) }, confirmButton = { TextButton(onClick = { delete(route.id); deleting = null }) { Text(stringResource(R.string.ui_delete)) } }, dismissButton = { TextButton(onClick = { deleting = null }) { Text(stringResource(R.string.ui_keep_route)) } }) }
}
@Composable fun CoverageScreen() {
    Page(stringResource(R.string.ui_good_information_comes_first), stringResource(R.string.coverage_description)) {
        Countries.all.forEach { country -> TrailPanel(Modifier.fillMaxWidth()) { Heading(countryLabel(country.iso), true); Text(stringResource(R.string.country_planned, country.iso)); Text(stringResource(R.string.ui_offline_basemap_downloads_unavailable)) } }
    }
}
@Composable fun SettingsScreen(prefs: Preferences, toggle: (String) -> Unit, setting: (String, String) -> Unit, voiceReady: Boolean) {
    Page(stringResource(R.string.ui_make_yourself_at_home)) {
        TrailPanel(Modifier.fillMaxWidth()) {
            Heading(stringResource(R.string.ui_your_preferences), true)
            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) { Switch(prefs[booleanPreferencesKey("dark")] ?: false, { toggle("dark") }); Text(stringResource(R.string.ui_dark_appearance)) }
            Text(stringResource(R.string.ui_distance_units))
            Row { FilterChip(prefs[stringPreferencesKey("units")] != "metric", { setting("units", "imperial") }, label = { Text(stringResource(R.string.ui_miles)) }); Spacer(Modifier.width(12.dp)); FilterChip(prefs[stringPreferencesKey("units")] == "metric", { setting("units", "metric") }, label = { Text(stringResource(R.string.ui_kilometres)) }) }
            Text(stringResource(if (voiceReady) R.string.voice_available else R.string.voice_unavailable))
            Text(stringResource(R.string.ui_recognition_depends_on_your_device_s_installed_service_it_may_req))
        }
        TrailPanel(Modifier.fillMaxWidth()) { Heading(stringResource(R.string.ui_trailnest_premium), true); Text(stringResource(R.string.ui_purchasing_is_not_available_in_this_build_no_products_prices_or_p)); Text(stringResource(R.string.ui_your_checklist_journal_and_one_offline_geometry_pack_are_availabl)) }
        TrailPanel(Modifier.fillMaxWidth()) { Heading(stringResource(R.string.ui_your_privacy), true); Text(stringResource(R.string.ui_walks_imports_and_preferences_stay_on_this_device_no_accounts_ads)); Text(stringResource(R.string.ui_system_speech_services_may_process_voice_data_under_their_own_pri)) }
        TrailPanel(Modifier.fillMaxWidth()) { Heading(stringResource(R.string.ui_responsible_access), true); Text(stringResource(R.string.ui_follow_local_signs_respect_wildlife_and_livestock_leave_gates_as_)); Text(stringResource(R.string.ui_uk_reference_gov_uk_government_publications_the_countryside_code_)) }
        Text(stringResource(R.string.build_notice, BuildConfig.VERSION_NAME))
    }
}
