package com.example.waywatch.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object (DAO) for managing traffic reports in the Room database.
 * Provides methods to perform CRUD operations on the `traffic_reports` table.
 */
@Dao
interface TrafficReportDao {

    /**
     * Observes all traffic reports stored in the database, ordered by their timestamp in descending order.
     * This method returns a reactive `Flow` that emits updates whenever the data changes.
     *
     * @return A `Flow` emitting a list of `TrafficReportEntity` objects.
     */
    @Query("SELECT * FROM traffic_reports ORDER BY timestampMs DESC")
    fun observeReports(): Flow<List<TrafficReportEntity>>

    /**
     * Inserts a list of traffic reports into the database.
     * If a conflict occurs (e.g., duplicate primary keys), the existing records will be replaced.
     *
     * @param reports The list of `TrafficReportEntity` objects to insert.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertReports(reports: List<TrafficReportEntity>)

    /**
     * Inserts a single traffic report into the database.
     * If a conflict occurs (e.g., duplicate primary key), the existing record will be replaced.
     *
     * @param report The `TrafficReportEntity` object to insert.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertReport(report: TrafficReportEntity)

    /**
     * Deletes all traffic reports from the database.
     * This operation clears the `traffic_reports` table.
     */
    @Query("DELETE FROM traffic_reports")
    suspend fun clearAll()

    /** Convenience method for syncing remote data: replace existing rows for matching primary keys. */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(reports: List<TrafficReportEntity>)
}
