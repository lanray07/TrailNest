package com.trailnest.app

import android.database.sqlite.SQLiteDatabase
import android.graphics.Bitmap
import android.media.ExifInterface
import android.net.Uri
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.trailnest.app.data.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class PersonalDataDeviceTest {
    @Test fun versionOneDatabaseMigratesWithoutLosingJournalOrRoute() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val name = "migration-test-${UUID.randomUUID()}.db"
        val path = context.getDatabasePath(name).apply { parentFile!!.mkdirs() }
        SQLiteDatabase.openOrCreateDatabase(path, null).use { db ->
            db.execSQL("CREATE TABLE walks(id TEXT NOT NULL PRIMARY KEY,title TEXT NOT NULL,date INTEGER NOT NULL,metres REAL NOT NULL,minutes INTEGER NOT NULL,notes TEXT NOT NULL,rating INTEGER NOT NULL,favourite INTEGER NOT NULL)")
            db.execSQL("CREATE TABLE routes(id TEXT NOT NULL PRIMARY KEY,title TEXT NOT NULL,geometry TEXT NOT NULL,metres REAL NOT NULL,importedAt INTEGER NOT NULL,sha256 TEXT NOT NULL,packed INTEGER NOT NULL)")
            db.execSQL("INSERT INTO walks VALUES('existing','Keep this walk',123,1200,30,'Keep these notes',4,1)")
            db.execSQL("INSERT INTO routes VALUES('route','Keep route','0.0,0.0;0.0,0.001',111,123,'digest',1)")
            db.version = 1
        }
        val migrated = Room.databaseBuilder(context, TrailDatabase::class.java, name).addMigrations(TrailDatabase.MIGRATION_1_2).build()
        try {
            val kept = migrated.dao().walk("existing")!!
            assertEquals("Keep these notes", kept.notes); assertTrue(kept.favourite); assertEquals(4, kept.rating)
            migrated.openHelper.readableDatabase.query("SELECT title FROM routes WHERE id='route'").use { assertTrue(it.moveToFirst()); assertEquals("Keep route", it.getString(0)) }
            migrated.dao().addPhoto(WalkPhoto("photo", kept.id, "test.jpg"))
            assertEquals(1, migrated.dao().photosFor(kept.id).size)
            migrated.dao().deleteWalk(kept.id)
            assertTrue(migrated.dao().photosFor(kept.id).isEmpty())
        } finally { migrated.close(); context.deleteDatabase(name) }
    }

    @Test fun photoCopyDropsLocationMetadataAndKeepsOriginal() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val original = File(context.cacheDir, "photo-test-${UUID.randomUUID()}.jpg")
        val bitmap = Bitmap.createBitmap(4, 3, Bitmap.Config.ARGB_8888)
        original.outputStream().use { assertTrue(bitmap.compress(Bitmap.CompressFormat.JPEG, 90, it)) }; bitmap.recycle()
        ExifInterface(original.path).apply { setAttribute(ExifInterface.TAG_GPS_LATITUDE, "1/1,0/1,0/1"); setAttribute(ExifInterface.TAG_GPS_LATITUDE_REF, "N"); saveAttributes() }
        val store = PhotoStore(context); var name: String? = null
        try {
            name = store.import(Uri.fromFile(original))
            assertTrue(original.exists()); assertNotNull(ExifInterface(original.path).getAttribute(ExifInterface.TAG_GPS_LATITUDE))
            assertNull(ExifInterface(store.file(name).path).getAttribute(ExifInterface.TAG_GPS_LATITUDE))
            assertTrue(runCatching { store.file("../outside.jpg") }.isFailure)
        } finally { name?.let(store::remove); original.delete() }
    }
}
