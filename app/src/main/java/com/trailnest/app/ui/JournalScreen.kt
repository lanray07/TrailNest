package com.trailnest.app

import android.graphics.BitmapFactory
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.*
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.unit.dp
import com.trailnest.app.data.*
import com.trailnest.app.domain.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.DateFormat
import java.time.*
import java.time.format.TextStyle
import java.util.*

@OptIn(ExperimentalLayoutApi::class)
@Composable fun JournalScreen(walks: List<Walk>, photos: List<WalkPhoto>, imperial: Boolean, busy: Boolean, save: (Walk) -> Unit, delete: (String) -> Unit, addPhoto: (String, android.net.Uri) -> Unit, removePhoto: (WalkPhoto) -> Unit, recordingRevealKey: Int = 0, recording: @Composable () -> Unit = {}) {
    val locale = LocalConfiguration.current.locales[0]
    var editor by rememberSaveable { mutableStateOf<String?>(null) }
    var deleting by remember { mutableStateOf<Walk?>(null) }
    var deletingPhoto by remember { mutableStateOf<WalkPhoto?>(null) }
    var photoTarget by rememberSaveable { mutableStateOf<String?>(null) }
    var favouritesOnly by rememberSaveable { mutableStateOf(false) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        val id = photoTarget; photoTarget = null
        if (uri != null && id != null) addPhoto(id, uri)
    }
    val scroll = rememberLazyListState()
    LaunchedEffect(editor) { if (editor != null) scroll.animateScrollToItem(3) }
    LaunchedEffect(recordingRevealKey) { if (recordingRevealKey > 0) scroll.scrollToItem(if (editor == null) 3 else 4) }
    val totals = JournalRules.total(walks.map { ActivityEntry(it.date, it.metres, it.minutes) })
    val journalTitle = stringResource(R.string.journal_title)
    val visible = walks.filter { !favouritesOnly || it.favourite }
    LazyColumn(Modifier.fillMaxSize().testTag("journal-list").semantics { paneTitle = journalTitle; isTraversalGroup = true }, state = scroll, contentPadding = PaddingValues(24.dp), verticalArrangement = Arrangement.spacedBy(22.dp)) {
        item("header") { Column(verticalArrangement = Arrangement.spacedBy(16.dp)) { Heading(stringResource(R.string.journal_title)); TrailText(stringResource(R.string.journal_description)) } }
        item("totals") { TrailText(stringResource(R.string.journal_totals, pluralStringResource(R.plurals.walk_count, totals.walks, totals.walks), distanceText(totals.metres, imperial), totals.minutes)) }
        item("tools") { FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { editor = "new" }) { TrailText(stringResource(R.string.record_walk)) }
            FilterChip(favouritesOnly, { favouritesOnly = !favouritesOnly }, label = { TrailText(stringResource(R.string.favourites_only)) })
        } }
        if (editor != null) item("editor-$editor") {
            WalkEditor(walks.find { it.id == editor }, imperial, { save(it); editor = null }, { editor = null })
        }
        item("recording") { recording() }
        item("summary") { JournalSummary(walks, imperial) }
        if (busy) item("photo-progress") { Column { LinearProgressIndicator(Modifier.fillMaxWidth()); TrailText(stringResource(R.string.importing_photo)) } }
        if (visible.isEmpty() && editor == null) item("empty") { EmptyPanel(stringResource(R.string.journal_empty_title), stringResource(if (favouritesOnly) R.string.no_favourites else R.string.journal_empty_body)) }
        items(visible, key = { "walk-${it.id}" }) { walk -> TrailPanel(Modifier.fillMaxWidth().testTag("walk-${walk.id}")) {
            Heading(walk.title, true)
            TrailText(stringResource(R.string.walk_summary, DateFormat.getDateInstance(DateFormat.DEFAULT, locale).format(Date(walk.date)), distanceText(walk.metres, imperial), walk.minutes))
            if (walk.notes.isNotBlank()) TrailText(walk.notes)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                TextButton(onClick = { editor = walk.id }) { ContextActionText(stringResource(R.string.edit_walk), walk.title) }
                TextButton(onClick = { save(walk.copy(favourite = !walk.favourite)) }) { ContextActionText(stringResource(if (walk.favourite) R.string.unfavourite else R.string.favourite), walk.title) }
                TextButton(onClick = { deleting = walk }) { ContextActionText(stringResource(R.string.delete_entry), walk.title) }
            }
            TrailText(stringResource(R.string.personal_rating))
            FlowRow {
                (1..5).forEach { rating -> FilterChip(selected = walk.rating == rating, onClick = { save(walk.copy(rating = if (walk.rating == rating) 0 else rating)) }, label = { val description = stringResource(R.string.accessibility_rating, rating, walk.title); TrailText(stringResource(R.string.rating_value, rating), Modifier.clearAndSetSemantics { contentDescription = description }) }, modifier = Modifier.padding(end = 6.dp)) }
            }
            val ownPhotos = photos.filter { it.walkId == walk.id }
            if (ownPhotos.isNotEmpty()) FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) { ownPhotos.forEachIndexed { i, photo ->
                Column(Modifier.width(128.dp)) {
                    LocalWalkPhoto(photo, stringResource(R.string.walk_photo_description, i + 1, walk.title))
                    TextButton(onClick = { deletingPhoto = photo }) { ContextActionText(stringResource(R.string.remove_photo), stringResource(R.string.walk_photo_description, i + 1, walk.title)) }
                }
            } }
            OutlinedButton(onClick = { photoTarget = walk.id; picker.launch(arrayOf("image/*")) }, enabled = !busy && ownPhotos.size < 8) { ContextActionText(stringResource(R.string.add_photo), walk.title) }
            TrailText(stringResource(R.string.photo_privacy), style = MaterialTheme.typography.bodySmall)
        } }
    }
    deleting?.let { walk -> AlertDialog(onDismissRequest = { deleting = null }, title = { DialogTitle(stringResource(R.string.delete_walk_title)) }, text = { TrailText(stringResource(R.string.delete_walk_body, walk.title)) }, confirmButton = { TextButton(onClick = { delete(walk.id); if (editor == walk.id) editor = null; deleting = null }) { TrailText(stringResource(R.string.delete_entry)) } }, dismissButton = { TextButton(onClick = { deleting = null }) { TrailText(stringResource(R.string.cancel)) } }) }
    deletingPhoto?.let { photo -> AlertDialog(onDismissRequest = { deletingPhoto = null }, title = { DialogTitle(stringResource(R.string.remove_photo)) }, text = { TrailText(stringResource(R.string.remove_photo_body)) }, confirmButton = { TextButton(onClick = { removePhoto(photo); deletingPhoto = null }) { TrailText(stringResource(R.string.remove_photo)) } }, dismissButton = { TextButton(onClick = { deletingPhoto = null }) { TrailText(stringResource(R.string.cancel)) } }) }
}

@Composable private fun WalkEditor(existing: Walk?, imperial: Boolean, save: (Walk) -> Unit, close: () -> Unit) {
    val context = LocalContext.current
    val locale = LocalConfiguration.current.locales[0]
    val zone = ZoneId.systemDefault()
    var title by rememberSaveable { mutableStateOf(existing?.title ?: "") }
    var distance by rememberSaveable { mutableStateOf(existing?.let { JournalRules.editableDistance(it.metres, imperial, locale) } ?: "") }
    var minutes by rememberSaveable { mutableStateOf(existing?.minutes?.toString() ?: "") }
    var date by rememberSaveable { mutableStateOf(existing?.let { Instant.ofEpochMilli(it.date).atZone(zone).toLocalDate().toString() } ?: LocalDate.now().toString()) }
    var notes by rememberSaveable { mutableStateOf(existing?.notes ?: "") }
    var error by remember { mutableStateOf<String?>(null) }
    TrailPanel(Modifier.fillMaxWidth().testTag("journal-editor")) {
        Heading(stringResource(if (existing == null) R.string.record_walk else R.string.edit_walk), true)
        OutlinedTextField(title, { title = it.take(120) }, label = { TrailText(stringResource(R.string.walk_title)) }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(date, { date = it.take(10) }, textStyle = LocalTextStyle.current.copy(textDirection = androidx.compose.ui.text.style.TextDirection.Ltr), label = { TrailText(stringResource(R.string.walk_date)) }, supportingText = { TrailText(stringResource(R.string.date_format_hint)) }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(distance, { distance = it }, label = { TrailText(stringResource(if (imperial) R.string.distance_miles else R.string.distance_km)) }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(minutes, { minutes = it }, label = { TrailText(stringResource(R.string.duration_minutes)) }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(notes, { notes = it.take(5000) }, label = { TrailText(stringResource(R.string.walk_notes)) }, modifier = Modifier.fillMaxWidth())
        error?.let { FormError(it) }
        Button(onClick = {
            runCatching {
                JournalRules.validate(title, distance, minutes, locale)
                val day = JournalRules.date(date)
                val timestamp = if (existing != null && Instant.ofEpochMilli(existing.date).atZone(zone).toLocalDate() == day) existing.date else day.atTime(12, 0).atZone(zone).toInstant().toEpochMilli()
                Walk(existing?.id ?: UUID.randomUUID().toString(), title.trim(), timestamp, JournalRules.editedMetres(distance, imperial, existing?.metres, locale), minutes.toInt(), notes, existing?.rating ?: 0, existing?.favourite ?: false)
            }.onSuccess(save).onFailure { error = context.userMessage(it, R.string.error_journal) }
        }) { TrailText(stringResource(R.string.save_walk)) }
        TextButton(onClick = close) { TrailText(stringResource(R.string.cancel)) }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable private fun JournalSummary(walks: List<Walk>, imperial: Boolean) {
    val locale = LocalConfiguration.current.locales[0]
    val zone = ZoneId.systemDefault()
    var year by rememberSaveable { mutableIntStateOf(LocalDate.now().year) }
    var expanded by rememberSaveable { mutableStateOf(false) }
    val entries = walks.map { ActivityEntry(it.date, it.metres, it.minutes) }
    val summary = JournalRules.year(entries, year, zone)
    TrailPanel(Modifier.fillMaxWidth()) {
        Heading(stringResource(R.string.year_summary, year), true)
        TrailText(stringResource(R.string.journal_totals, pluralStringResource(R.plurals.walk_count, summary.walks, summary.walks), distanceText(summary.metres, imperial), summary.minutes))
        FlowRow {
            TextButton(onClick = { year-- }, enabled = year > 1970) { TrailText(stringResource(R.string.previous_year)) }
            TextButton(onClick = { year++ }, enabled = year < LocalDate.now().year) { TrailText(stringResource(R.string.next_year)) }
        }
        TextButton(onClick = { expanded = !expanded }) { TrailText(stringResource(if (expanded) R.string.hide_months else R.string.show_months)) }
        if (expanded) JournalRules.months(entries, year, zone).forEachIndexed { index, month ->
            TrailText(stringResource(R.string.month_summary, Month.of(index + 1).getDisplayName(TextStyle.FULL, locale), pluralStringResource(R.plurals.walk_count, month.walks, month.walks), distanceText(month.metres, imperial), month.minutes))
        }
    }
}

@Composable private fun LocalWalkPhoto(photo: WalkPhoto, description: String) {
    val context = LocalContext.current
    val bitmap by produceState<android.graphics.Bitmap?>(null, photo.fileName) {
        value = withContext(Dispatchers.IO) { runCatching { BitmapFactory.decodeFile(PhotoStore(context).file(photo.fileName).path, BitmapFactory.Options().apply { inSampleSize = 4 }) }.getOrNull() }
    }
    if (bitmap != null) Image(bitmap!!.asImageBitmap(), description, Modifier.fillMaxWidth().height(100.dp), contentScale = ContentScale.Crop)
    else ContextActionText(stringResource(R.string.photo_unavailable), description)
}
