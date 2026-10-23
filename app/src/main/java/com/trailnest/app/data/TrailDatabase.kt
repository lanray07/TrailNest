package com.trailnest.app.data

import android.content.Context
import androidx.room.*
import kotlinx.coroutines.flow.Flow
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Entity(tableName = "walks")
data class Walk(@PrimaryKey val id: String, val title: String, val date: Long, val metres: Double, val minutes: Int, val notes: String, val rating: Int, val favourite: Boolean)
@Entity(tableName = "routes")
data class ImportedRoute(@PrimaryKey val id: String, val title: String, val geometry: String, val metres: Double, val importedAt: Long, val sha256: String, val packed: Boolean = false)
@Entity(tableName = "walk_photos", foreignKeys = [ForeignKey(entity = Walk::class, parentColumns = ["id"], childColumns = ["walkId"], onDelete = ForeignKey.CASCADE)], indices = [Index("walkId")])
data class WalkPhoto(@PrimaryKey val id: String, val walkId: String, val fileName: String)
@Dao interface TrailDao {
    @Query("SELECT * FROM walks ORDER BY date DESC") fun walks(): Flow<List<Walk>>
    @Upsert suspend fun save(walk: Walk)
    @Query("DELETE FROM walks WHERE id = :id") suspend fun deleteWalk(id: String)
    @Query("SELECT * FROM walks WHERE id = :id") suspend fun walk(id: String): Walk?
    @Query("SELECT * FROM walk_photos ORDER BY id") fun photos(): Flow<List<WalkPhoto>>
    @Query("SELECT * FROM walk_photos WHERE walkId = :id") suspend fun photosFor(id: String): List<WalkPhoto>
    @Query("SELECT fileName FROM walk_photos") suspend fun photoFileNames(): List<String>
    @Insert suspend fun addPhoto(photo: WalkPhoto)
    @Query("DELETE FROM walk_photos WHERE id = :id") suspend fun deletePhoto(id: String)
    @Query("SELECT * FROM routes ORDER BY importedAt DESC") fun routes(): Flow<List<ImportedRoute>>
    @Upsert suspend fun save(route: ImportedRoute)
    @Query("DELETE FROM routes WHERE id = :id") suspend fun deleteRoute(id: String)
}
@Database(entities = [Walk::class, ImportedRoute::class, WalkPhoto::class], version = 2, exportSchema = false)
abstract class TrailDatabase : RoomDatabase() {
    abstract fun dao(): TrailDao
    companion object {
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS `walk_photos` (`id` TEXT NOT NULL, `walkId` TEXT NOT NULL, `fileName` TEXT NOT NULL, PRIMARY KEY(`id`), FOREIGN KEY(`walkId`) REFERENCES `walks`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_walk_photos_walkId` ON `walk_photos` (`walkId`)")
            }
        }
        @Volatile private var instance: TrailDatabase? = null
        fun create(context: Context): TrailDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(context.applicationContext, TrailDatabase::class.java, "trailnest.db").addMigrations(MIGRATION_1_2).build().also { instance = it }
        }
    }
}
