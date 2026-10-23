package com.trailnest.app

import android.Manifest
import android.content.Intent
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.*
import androidx.core.content.ContextCompat
import android.content.pm.PackageManager
import android.speech.RecognizerIntent
import android.media.AudioManager
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import androidx.lifecycle.lifecycleScope
import com.trailnest.app.data.*
import com.trailnest.app.domain.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

class MainActivity : ComponentActivity(), LocationListener {
    private val model: TrailViewModel by viewModels()
    private var notice by mutableStateOf<String?>(null)
    private var fix by mutableStateOf<Location?>(null)
    private var locating by mutableStateOf(false)
    private var voiceReady by mutableStateOf(false)
    private var voiceCommand by mutableStateOf(VoiceCommand.UNKNOWN)
    private var tts: TextToSpeech? = null
    private var exportRoute: ImportedRoute? = null
    private var recordingPage by mutableIntStateOf(0)
    private val recordingPermission = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            if (android.os.Build.VERSION.SDK_INT >= 33) notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
            else startRecording()
        } else notice = getString(R.string.recording_permission)
    }
    private val notificationPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { startRecording() }
    private fun startRecording() {
        val precise = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val providers = locationManager.getProviders(true).filter { it == LocationManager.NETWORK_PROVIDER || (precise && it == LocationManager.GPS_PROVIDER) }
        if (providers.isEmpty()) { notice = getString(R.string.recording_location_disabled); return }
        runCatching { ContextCompat.startForegroundService(this, Intent(this, WalkRecordingService::class.java)) }
            .onFailure { notice = getString(R.string.recording_start_failed) }
    }
    private fun recordingAction(action: String) {
        runCatching { when (action) {
            "start" -> recordingPermission.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
            "pause" -> {
                // Stopping the service releases all location subscriptions before editing/saving.
                stopService(Intent(this, WalkRecordingService::class.java))
                RecordingStore.get(this).pause()
            }
            "discard" -> {
                stopService(Intent(this, WalkRecordingService::class.java))
                RecordingStore.get(this).state.value?.id?.let { RecordingStore.get(this).clear(it) }
            }
            else -> Unit
        } }.onFailure { notice = userMessage(it, R.string.recording_start_failed) }
    }
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.action == WalkRecordingService.OPEN) recordingPage++
    }
    private val audioManager by lazy { getSystemService(AUDIO_SERVICE) as AudioManager }
    private val audioFocus by lazy { AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
        .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE).setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build())
        .setOnAudioFocusChangeListener { change -> if (change < 0) tts?.stop() }.build() }
    private val locationManager by lazy { getSystemService(LOCATION_SERVICE) as LocationManager }
    private val permission = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { grants ->
        if (grants.values.any { it }) startLocation() else { notice = getString(R.string.ui_location_permission_was_declined_route_overview_is_still_availabl) }
    }
    private val importer = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) lifecycleScope.launch {
            runCatching { withContext(Dispatchers.IO) { contentResolver.openInputStream(uri)?.let { Gpx.read(it, getString(R.string.imported_walk)) } ?: throw TextFailure("error_unable_to_open_this_file") } }
                .onSuccess { model.save(it); notice = getString(R.string.ui_gpx_imported_privately_access_and_directions_have_not_been_review) }
                .onFailure { notice = userMessage(it, R.string.error_import) }
        }
    }
    private val exporter = registerForActivityResult(ActivityResultContracts.CreateDocument("application/gpx+xml")) { uri ->
        val route = exportRoute; exportRoute = null
        if (uri != null && route != null) lifecycleScope.launch {
            runCatching { withContext(Dispatchers.IO) { val stream = contentResolver.openOutputStream(uri) ?: throw TextFailure("error_unable_to_open_destination"); stream.bufferedWriter().use { it.write(Gpx.export(route)) } } }
                .onSuccess { notice = getString(R.string.ui_gpx_exported) }.onFailure { notice = userMessage(it, R.string.error_export) }
        }
    }
    private val recognition = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val text = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
        voiceCommand = text?.let { parseCommand(it, resources.configuration.locales[0]) } ?: VoiceCommand.UNKNOWN
        if (voiceCommand == VoiceCommand.UNKNOWN) notice = getString(R.string.ui_command_not_recognised_use_the_touch_controls_english_commands_ar)
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (intent.action == WalkRecordingService.OPEN) recordingPage++
        tts = TextToSpeech(this) { status ->
            voiceReady = status == TextToSpeech.SUCCESS && (tts?.setLanguage(resources.configuration.locales[0]) ?: -1) >= TextToSpeech.LANG_AVAILABLE
        }
        tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) = Unit
            override fun onDone(utteranceId: String?) { audioManager.abandonAudioFocusRequest(audioFocus) }
            @Deprecated("Platform callback")
            override fun onError(utteranceId: String?) { audioManager.abandonAudioFocusRequest(audioFocus) }
        })
        setContent {
            TrailApp(model, notice, { notice = null }, fix, locating, voiceReady, voiceCommand,
                onConsumeCommand = { voiceCommand = VoiceCommand.UNKNOWN },
                recordingPage = recordingPage, onRecordingAction = ::recordingAction,
                onImport = { importer.launch(arrayOf("application/gpx+xml", "application/xml", "text/xml", "application/octet-stream")) },
                onExport = { exportRoute = it; exporter.launch("${it.title.replace(Regex("[^a-zA-Z0-9 -]"), "").take(80)}.gpx") },
                onLocation = { if (locating) stopLocation() else permission.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)) },
                onSpeak = {
                    if (!voiceReady) notice = getString(R.string.ui_no_compatible_voice_is_installed_for_this_language)
                    else if (audioManager.requestAudioFocus(audioFocus) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) tts?.speak(it, TextToSpeech.QUEUE_FLUSH, null, "trailnest-prompt")
                    else notice = getString(R.string.ui_audio_is_unavailable_use_the_on_screen_progress)
                },
                onListen = {
                    val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM).putExtra(RecognizerIntent.EXTRA_LANGUAGE, resources.configuration.locales[0].toLanguageTag())
                    runCatching { recognition.launch(intent) }.onFailure { notice = getString(R.string.ui_speech_recognition_is_unavailable_on_this_device_use_touch_contro) }
                },
                onShare = { text -> startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text), getString(R.string.share_plan_title))) },
                onNotice = { notice = it })
        }
    }
    private fun startLocation() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) return
        try {
            val providers = locationManager.getProviders(true).filter { it == LocationManager.GPS_PROVIDER || it == LocationManager.NETWORK_PROVIDER }
            if (providers.isEmpty()) { notice = getString(R.string.ui_enable_location_in_device_settings_some_fire_tablets_have_no_gps_); return }
            providers.forEach { locationManager.requestLocationUpdates(it, 3000L, 2f, this) }
            locating = true
        } catch (_: SecurityException) { notice = getString(R.string.ui_location_permission_is_required) }
    }
    private fun stopLocation() { locationManager.removeUpdates(this); locating = false; fix = null }
    override fun onLocationChanged(location: Location) { fix = location }
    override fun onStop() { super.onStop(); stopLocation(); tts?.stop(); audioManager.abandonAudioFocusRequest(audioFocus) }
    override fun onDestroy() { tts?.shutdown(); super.onDestroy() }
}
