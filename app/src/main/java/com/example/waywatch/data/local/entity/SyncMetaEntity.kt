package com.example.waywatch.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "sync_meta")
data class SyncMetaEntity(
    @PrimaryKey val key: String,
    val lastSyncAtMs: Long,
    val lastWindowStartMs: Long?
)
