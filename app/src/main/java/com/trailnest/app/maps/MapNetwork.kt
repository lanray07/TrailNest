package com.trailnest.app.maps

import android.content.Context
import com.trailnest.app.BuildConfig
import com.trailnest.app.domain.MapDownloadBudget
import okhttp3.Dispatcher
import okhttp3.OkHttpClient
import okhttp3.ResponseBody.Companion.toResponseBody
import org.maplibre.android.MapLibre
import org.maplibre.android.module.http.HttpRequestUtil
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.util.concurrent.TimeUnit

object MapNetwork {
    @Volatile private var initialised = false
    @Volatile var budget: MapDownloadBudget? = null; private set
    private val client = OkHttpClient.Builder()
        .dispatcher(Dispatcher().apply { maxRequests = 4; maxRequestsPerHost = 4 })
        .followRedirects(false).followSslRedirects(false)
        .callTimeout(30, TimeUnit.SECONDS)
        .addInterceptor { chain ->
            val original = chain.request()
            if (original.url.scheme != "https" || original.url.host != "tiles.openfreemap.org") throw IOException("Map resource unavailable")
            val current = budget
            if (current != null && !current.beginResource()) throw IOException("Map download limit reached")
            val request = original.newBuilder().header("User-Agent", "TrailNest/${BuildConfig.VERSION_NAME} (Android; com.trailnest.app)").build()
            val response = chain.proceed(request)
            if (current == null) return@addInterceptor response
            // Buffer before handing a response to native code: a partial resource
            // must never enter its offline database as a successful response.
            response.use {
                val body = response.body ?: throw IOException("Map resource unavailable")
                val output = ByteArrayOutputStream()
                body.byteStream().use { input ->
                    val chunk = ByteArray(8192)
                    while (true) {
                        val count = input.read(chunk)
                        if (count == -1) break
                        if (!current.consume(count.toLong())) throw IOException("Map download limit reached")
                        output.write(chunk, 0, count)
                    }
                }
                response.newBuilder().body(output.toByteArray().toResponseBody(body.contentType())).build()
            }
        }.build()
    @Synchronized fun initialise(context: Context) {
        if (initialised) return
        MapLibre.getInstance(context.applicationContext)
        HttpRequestUtil.setLogEnabled(false)
        HttpRequestUtil.setPrintRequestUrlOnFailure(false)
        HttpRequestUtil.setOkHttpClient(client)
        initialised = true
    }
    @Synchronized fun beginDownload(): MapDownloadBudget? {
        budget?.let { return if (it.stopped) null else it }
        return MapDownloadBudget().also { budget = it }
    }
    // Keep the conservative budget until process exit. In-flight native requests
    // cannot escape a cap by cancelling and immediately starting another region.
    fun cancelDownload() { client.dispatcher.cancelAll() }
}
