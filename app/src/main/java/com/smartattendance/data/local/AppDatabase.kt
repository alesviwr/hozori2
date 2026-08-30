package com.smartattendance.data.local

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import com.smartattendance.domain.model.ReportSummary
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Room فقط برای داده Local «لازم»: کش گزارش جلسات استاد برای نمایش سریع
 * و کارکرد آفلاین صفحه گزارش‌ها. داده امنیتی (Token/Device) اینجا نیست.
 */
@Entity(tableName = "cached_reports")
data class CachedReportEntity(
    @PrimaryKey val sessionId: String,
    val courseName: String,
    val date: String,
    val startedAt: Long,
    val presentCount: Int,
    val absentCount: Int,
)

@Dao
interface CachedReportDao {

    @Query("SELECT * FROM cached_reports ORDER BY startedAt DESC")
    fun observeAll(): Flow<List<CachedReportEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<CachedReportEntity>)

    @Query("DELETE FROM cached_reports")
    suspend fun clear()
}

@Database(entities = [CachedReportEntity::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun cachedReportDao(): CachedReportDao
}

/** نگاشت کش Room به مدل دامنه */
fun CachedReportEntity.toDomain() = ReportSummary(
    sessionId = sessionId,
    courseName = courseName,
    date = date,
    startedAt = startedAt,
    presentCount = presentCount,
    absentCount = absentCount,
)

fun List<CachedReportEntity>.toDomain(): List<ReportSummary> = map { it.toDomain() }
