package com.trailnest.app.data

import android.content.Context
import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "walks")
data class Walk(@PrimaryKey val id: String, val title: String, val date: Long, val metres: Double, val minutes: Int, val notes: String, val rating: Int, val favourite: Boolean)
@Entity(tableName = "routes")
data class ImportedRoute(@PrimaryKey val id: String, val title: String, val geometry: String, val metres: Double, val importedAt: Long, val sha256: String, val packed: Boolean = false)
@Dao interface TrailDao {
    @Query("SELECT * FROM walks ORDER BY date DESC") fun walks(): Flow<List<Walk>>
    @Upsert suspend fun save(walk: Walk)
    @Query("DELETE FROM walks WHERE id = :id") suspend fun deleteWalk(id: String)
    @Query("SELECT * FROM routes ORDER BY importedAt DESC") fun routes(): Flow<List<ImportedRoute>>
    @Upsert suspend fun save(route: ImportedRoute)
    @Query("DELETE FROM routes WHERE id = :id") suspend fun deleteRoute(id: String)
}
@Database(entities = [Walk::class, ImportedRoute::class], version = 1, exportSchema = false)
abstract class TrailDatabase : RoomDatabase() {
    abstract fun dao(): TrailDao
    companion object { fun create(context: Context) = Room.databaseBuilder(context, TrailDatabase::class.java, "trailnest.db").build() }
}
