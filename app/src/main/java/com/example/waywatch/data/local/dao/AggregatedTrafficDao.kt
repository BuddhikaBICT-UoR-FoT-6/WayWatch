package com.example.waywatch.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.example.waywatch.data.local.entity.AggregatedTrafficEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface AggregatedTrafficDao {
    @Query("SELECT * FROM aggregated_traffic WHERE routeId = :routeId ORDER BY windowStartMs DESC")
    fun observeAggregates(routeId: String): Flow<List<AggregatedTrafficEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(items: List<AggregatedTrafficEntity>)

    @Query("DELETE FROM aggregated_traffic WHERE routeId = :routeId AND windowStartMs = :windowStartMs")
    suspend fun deleteWindow(routeId: String, windowStartMs: Long)

    @Transaction
    suspend fun overwriteWindow(routeId: String, windowStartMs: Long, items: List<AggregatedTrafficEntity>) {
        deleteWindow(routeId, windowStartMs)
        upsertAll(items)
    }

}