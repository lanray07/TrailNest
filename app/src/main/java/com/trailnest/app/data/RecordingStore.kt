package com.trailnest.app.data

import android.content.Context
import android.os.SystemClock
import com.trailnest.app.domain.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONObject
import java.util.UUID

/** Process-wide owner. Mutations run on main, including service callbacks. */
class RecordingStore private constructor(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("private_recording", Context.MODE_PRIVATE)
    private val restored = prefs.getString("draft", null)?.let { raw ->
        runCatching { JSONObject(raw).let {
            RecordingDraft(it.getString("id"), it.getLong("started"), it.getLong("active"), it.getDouble("metres"),
                interrupted = it.optBoolean("running") || it.optBoolean("interrupted"))
        } }.getOrNull()
    }
    private var engine = restored?.let { WalkRecording(it, SystemClock.elapsedRealtime()) }
    private val mutable = MutableStateFlow(restored)
    val state = mutable.asStateFlow()
    val error = MutableStateFlow<String?>(null)
    fun start() {
        if (engine == null) engine = WalkRecording(RecordingDraft(UUID.randomUUID().toString(), System.currentTimeMillis()), SystemClock.elapsedRealtime())
        if (engine!!.draft.running) return
        engine!!.resume(SystemClock.elapsedRealtime()); publish()
    }
    fun pause() { engine?.pause(SystemClock.elapsedRealtime()); publish() }
    fun tick() { engine?.tick(SystemClock.elapsedRealtime()); publish() }
    fun fix(point: GeoPoint, accuracy: Float, measuredAt: Long) {
        if (engine?.fix(point, accuracy, measuredAt, SystemClock.elapsedRealtime()) == true) publish()
    }
    fun clear(id: String) { if (engine?.draft?.id == id) { engine = null; publish() } }
    private fun publish() {
        val draft = engine?.draft
        val raw = draft?.let { JSONObject().put("id", it.id).put("started", it.startedAt).put("active", it.activeMillis)
            .put("metres", it.metres).put("running", it.running).put("interrupted", it.interrupted).toString() }
        mutable.value = draft
        check(prefs.edit().putString("draft", raw).commit()) { throw TextFailure("error_unable_to_checkpoint_recording_free_device_storage_before_continu") }
    }
    companion object {
        @Volatile private var instance: RecordingStore? = null
        fun get(context: Context): RecordingStore = instance ?: synchronized(this) {
            instance ?: RecordingStore(context).also { instance = it }
        }
    }
}
