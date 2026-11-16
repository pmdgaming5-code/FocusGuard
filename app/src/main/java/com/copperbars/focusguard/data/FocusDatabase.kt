package com.copperbars.focusguard.data

import android.content.Context
import androidx.room.Dao
import androidx.room.ColumnInfo
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "tracked_apps")
data class TrackedAppEntity(
    @PrimaryKey val packageName: String,
    val label: String,
    val dailyLimitMinutes: Int = 0,
    val sessionLimitMinutes: Int = 0,
    val focusBlocked: Boolean = false,
    val sessionUsageMillis: Long = 0L,
    @ColumnInfo(defaultValue = "1") val limitsEnabled: Boolean = true
)

@Entity(tableName = "daily_usage", primaryKeys = ["packageName", "dayKey"])
data class DailyUsageEntity(
    val packageName: String,
    val dayKey: String,
    val totalMillis: Long,
    val updatedAtMillis: Long
)

@Entity(tableName = "focus_sessions")
data class FocusSessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val label: String,
    val startedAtMillis: Long,
    val endsAtMillis: Long,
    val completed: Boolean = false
)

@Dao
interface FocusDao {
    @Query("SELECT * FROM tracked_apps ORDER BY label COLLATE NOCASE")
    fun observeTrackedApps(): Flow<List<TrackedAppEntity>>

    @Query("SELECT * FROM tracked_apps ORDER BY label COLLATE NOCASE")
    suspend fun getTrackedApps(): List<TrackedAppEntity>

    @Query("SELECT * FROM tracked_apps WHERE packageName = :packageName LIMIT 1")
    suspend fun getTrackedApp(packageName: String): TrackedAppEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveTrackedApp(app: TrackedAppEntity)

    @Query("DELETE FROM tracked_apps WHERE packageName = :packageName")
    suspend fun removeTrackedApp(packageName: String)

    @Query("UPDATE tracked_apps SET sessionUsageMillis = 0 WHERE packageName = :packageName")
    suspend fun resetSessionUsage(packageName: String)

    @Query("UPDATE tracked_apps SET sessionUsageMillis = sessionUsageMillis + :delta WHERE packageName = :packageName")
    suspend fun addSessionUsage(packageName: String, delta: Long)

    @Query("SELECT totalMillis FROM daily_usage WHERE packageName = :packageName AND dayKey = :dayKey LIMIT 1")
    suspend fun getDailyUsage(packageName: String, dayKey: String): Long?

    @Query("SELECT COALESCE(SUM(d.totalMillis), 0) FROM daily_usage AS d INNER JOIN tracked_apps AS a ON a.packageName = d.packageName WHERE d.dayKey = :dayKey")
    suspend fun getTrackedAppsDailyTotal(dayKey: String): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveDailyUsage(usage: DailyUsageEntity)

    @Query("SELECT * FROM daily_usage WHERE dayKey = :dayKey ORDER BY totalMillis DESC")
    fun observeDailyUsage(dayKey: String): Flow<List<DailyUsageEntity>>

    @Insert
    suspend fun insertFocusSession(session: FocusSessionEntity): Long

    @Query("SELECT * FROM focus_sessions ORDER BY startedAtMillis DESC LIMIT 20")
    fun observeRecentSessions(): Flow<List<FocusSessionEntity>>

    @Query("SELECT * FROM focus_sessions WHERE completed = 0 ORDER BY startedAtMillis DESC LIMIT 1")
    suspend fun getActiveSession(): FocusSessionEntity?

    @Query("UPDATE focus_sessions SET completed = 1 WHERE completed = 0")
    suspend fun completeActiveSessions()
}

@Database(
    entities = [TrackedAppEntity::class, DailyUsageEntity::class, FocusSessionEntity::class],
    version = 2,
    exportSchema = false
)
abstract class FocusDatabase : RoomDatabase() {
    abstract fun focusDao(): FocusDao

    companion object {
        @Volatile private var INSTANCE: FocusDatabase? = null

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    "ALTER TABLE tracked_apps ADD COLUMN limitsEnabled INTEGER NOT NULL DEFAULT 1"
                )
            }
        }

        fun get(context: Context): FocusDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    FocusDatabase::class.java,
                    "focus_guard.db"
                ).addMigrations(MIGRATION_1_2).build().also { INSTANCE = it }
            }
        }
    }
}
