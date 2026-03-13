// Edited: 2026-01-08
// Purpose: Room database providing DAOs for local persistence of traffic reports and aggregation data.

package com.example.waywatch.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.waywatch.data.local.dao.AggregatedTrafficDao
import com.example.waywatch.data.local.dao.SyncMetaDao
import com.example.waywatch.data.local.entity.AggregatedTrafficEntity
import com.example.waywatch.data.local.entity.SyncMetaEntity

/**
 * Application-wide Room database. Holds the schema and serves DAO instances.
 *
 * Versioning: Updated to version=2 to include Phase 3 aggregation entities.
 */
@Database(
    entities = [
        TrafficReportEntity::class,
        AggregatedTrafficEntity::class,
        SyncMetaEntity::class
    ],
    version = 2,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase(){
    /** DAO for traffic report operations. */
    abstract fun trafficReportDao(): TrafficReportDao

    /** DAO for aggregated traffic data (Phase 3). */
    abstract fun aggregatedTrafficDao(): AggregatedTrafficDao

    /** DAO for sync metadata (Phase 3). */
    abstract fun syncMetaDao(): SyncMetaDao

    companion object {
        @Volatile private var INSTANCE: AppDatabase? = null

        /**
         * Migration from version 1 to version 2.
         * Adds aggregated_traffic and sync_meta tables for Phase 3 aggregation support.
         */
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // Create aggregated_traffic table
                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS aggregated_traffic (
                        routeId TEXT NOT NULL,
                        windowStartMs INTEGER NOT NULL,
                        segmentId TEXT NOT NULL,
                        severityAvg REAL NOT NULL,
                        severityP50 REAL,
                        severityP90 REAL,
                        sampleCount INTEGER NOT NULL,
                        lastAggregatedAtMs INTEGER NOT NULL,
                        PRIMARY KEY(routeId, windowStartMs, segmentId)
                    )
                """.trimIndent())

                // Create indexes for aggregated_traffic
                database.execSQL("CREATE INDEX IF NOT EXISTS index_aggregated_traffic_routeId ON aggregated_traffic(routeId)")
                database.execSQL("CREATE INDEX IF NOT EXISTS index_aggregated_traffic_windowStartMs ON aggregated_traffic(windowStartMs)")

                // Create sync_meta table
                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS sync_meta (
                        key TEXT NOT NULL PRIMARY KEY,
                        lastSyncAtMs INTEGER NOT NULL,
                        lastWindowStartMs INTEGER
                    )
                """.trimIndent())
            }
        }

        /** Returns a singleton database instance scoped to the application context. */
        fun get(context: Context): AppDatabase =
            INSTANCE ?: synchronized(this){
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "ceylon_queue_bus_pulse.db"
                )
                    .addMigrations(MIGRATION_1_2)
                    // Fallback only as last resort (for dev environments) - drops all tables on unrecoverable schema errors
                    .fallbackToDestructiveMigration(dropAllTables = true)
                    .build().also { INSTANCE = it }
            }

    }
}
