package com.trailnest.app.data

import com.trailnest.app.domain.TextFailure
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.UUID

/** App-private, bounded image copies. Re-encoding intentionally drops EXIF (including location). */
class PhotoStore(private val context: Context) {
    private val directory get() = File(context.filesDir, "walk_photos").also { check(it.isDirectory || it.mkdirs()) }
    fun file(name: String): File {
        require(Regex("[a-f0-9-]{36}\\.jpg").matches(name)) { throw TextFailure("error_invalid_photo_reference") }
        return File(directory, name)
    }
    fun import(uri: Uri): String {
        val bytes = context.contentResolver.openInputStream(uri)?.use { input ->
            val out = ByteArrayOutputStream(); val buffer = ByteArray(8192)
            while (true) { val count = input.read(buffer); if (count < 0) break; require(out.size() + count <= 20 * 1024 * 1024) { throw TextFailure("error_choose_a_photo_smaller_than_20_mb") }; out.write(buffer, 0, count) }
            out.toByteArray()
        } ?: throw TextFailure("error_the_selected_photo_cannot_be_opened")
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        require(bounds.outWidth > 0 && bounds.outHeight > 0) { throw TextFailure("error_choose_a_supported_image_file") }
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / sample > 1600) sample *= 2
        val decoded = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample }) ?: throw TextFailure("error_the_image_cannot_be_decoded")
        val orientation = runCatching { ExifInterface(bytes.inputStream()).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL) }.getOrDefault(ExifInterface.ORIENTATION_NORMAL)
        val matrix = Matrix().apply {
            when (orientation) {
                2 -> setScale(-1f, 1f); 3 -> setRotate(180f); 4 -> setScale(1f, -1f)
                5 -> { setRotate(90f); postScale(-1f, 1f) }; 6 -> setRotate(90f)
                7 -> { setRotate(270f); postScale(-1f, 1f) }; 8 -> setRotate(270f)
            }
        }
        val rotated = Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, matrix, true)
        val name = "${UUID.randomUUID()}.jpg"; val target = file(name)
        try { target.outputStream().use { check(rotated.compress(Bitmap.CompressFormat.JPEG, 85, it)) }; return name }
        catch (error: Exception) { target.delete(); throw error }
        finally { if (rotated !== decoded) rotated.recycle(); decoded.recycle() }
    }
    fun remove(name: String) { file(name).delete() }
    fun prune(referenced: Set<String>) {
        directory.listFiles()?.filter { it.isFile && Regex("[a-f0-9-]{36}\\.jpg").matches(it.name) && it.name !in referenced }?.forEach { it.delete() }
    }
}
