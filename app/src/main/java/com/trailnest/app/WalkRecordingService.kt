package com.trailnest.app

import android.app.*
import android.content.Intent
import android.content.pm.ServiceInfo
import android.location.*
import android.os.*
import android.Manifest
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import com.trailnest.app.data.RecordingStore
import com.trailnest.app.domain.GeoPoint

/** Started by a visible Activity. Never automatically restarts after process death. */
class WalkRecordingService : Service(), LocationListener {
    private val store by lazy { RecordingStore.get(this) }
    private val locations by lazy { getSystemService(LocationManager::class.java) }
    private val handler = Handler(Looper.getMainLooper())
    private var listening = false
    private val ticker = object : Runnable {
        override fun run() {
            try { store.tick(); notifyState() } catch (error: Exception) { store.error.value = error.message; stopSelf(); return }
            handler.postDelayed(this, 5_000)
        }
    }
    override fun onBind(intent: Intent?) = null
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == PAUSE) {
            if (store.state.value == null) stopSelf() else { pause(); notifyState() }
            return START_NOT_STICKY
        }
        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL, getString(R.string.recording_channel), NotificationManager.IMPORTANCE_LOW))
        try {
            if (Build.VERSION.SDK_INT >= 29) startForeground(ID, notification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION)
            else startForeground(ID, notification())
            val enabled = if (Build.VERSION.SDK_INT >= 28) locations.isLocationEnabled else
                locations.isProviderEnabled(LocationManager.GPS_PROVIDER) || locations.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
            check(enabled)
            val precise = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
            val providers = locations.getProviders(true).filter { it == LocationManager.NETWORK_PROVIDER || (precise && it == LocationManager.GPS_PROVIDER) }
            check(providers.isNotEmpty())
            locations.removeUpdates(this)
            store.start()
            providers.forEach { locations.requestLocationUpdates(it, 3_000L, 0f, this) }
            listening = true
            handler.removeCallbacks(ticker); handler.post(ticker)
        } catch (_: Exception) { store.error.value = getString(R.string.recording_start_failed); pause(); stopForeground(STOP_FOREGROUND_REMOVE); stopSelf() }
        return START_NOT_STICKY
    }
    private fun pause() {
        runCatching { locations.removeUpdates(this) }; listening = false
        runCatching { store.pause() }.onFailure { store.error.value = it.message }
        handler.removeCallbacks(ticker)
    }
    override fun onLocationChanged(location: Location) {
        if (!listening) return
        runCatching {
            if (location.hasAccuracy()) store.fix(GeoPoint(location.latitude, location.longitude), location.accuracy, location.elapsedRealtimeNanos / 1_000_000)
        }.onFailure { store.error.value = it.message; pause(); stopSelf() }
    }
    override fun onProviderDisabled(provider: String) { pause(); notifyState() }
    private fun notification(): Notification {
        val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java).setAction(OPEN)
            .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val paused = store.state.value?.running != true
        return Notification.Builder(this, CHANNEL).setSmallIcon(R.drawable.ic_recording)
            .setContentTitle(getString(if (paused) R.string.recording_paused else R.string.recording_active))
            .setContentText(getString(R.string.recording_notification)).setContentIntent(open).setOngoing(true)
            .setOnlyAlertOnce(true).apply {
                if (!paused) addAction(Notification.Action.Builder(null, getString(R.string.pause_recording),
                    PendingIntent.getService(this@WalkRecordingService, 1, Intent(this@WalkRecordingService, WalkRecordingService::class.java).setAction(PAUSE), PendingIntent.FLAG_IMMUTABLE)).build())
            }.build()
    }
    private fun notifyState() {
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        getSystemService(NotificationManager::class.java).notify(ID, notification())
    }
    override fun onDestroy() { pause(); super.onDestroy() }
    companion object {
        const val PAUSE = "com.trailnest.app.PAUSE_RECORDING"
        const val OPEN = "com.trailnest.app.OPEN_RECORDING"
        private const val CHANNEL = "walk_recording"
        private const val ID = 41
    }
}
