package com.example.waywatch.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.waywatch.data.local.entity.SyncMetaEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SyncMetaDao {
    @Query("SELECT * FROM sync_meta WHERE `key` = :key LIMIT 1")
    fun observeByKey(key: String): Flow<SyncMetaEntity?>

    @Query("SELECT * FROM sync_meta WHERE `key` = :key LIMIT 1")
    suspend fun get(key: String): SyncMetaEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(item: SyncMetaEntity)

}