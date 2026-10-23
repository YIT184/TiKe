package com.codex.shuatitimer.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.codex.shuatitimer.data.model.Session
import com.codex.shuatitimer.data.model.StudyModule
import com.codex.shuatitimer.data.model.Subject

@Database(
    entities = [Session::class, StudyModule::class],
    version = 3,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun sessionDao(): SessionDao
    abstract fun moduleDao(): ModuleDao

    companion object {
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE sessions ADD COLUMN segmentGroupId TEXT")
                db.execSQL("ALTER TABLE sessions ADD COLUMN segmentIndex INTEGER")
                db.execSQL("ALTER TABLE sessions ADD COLUMN segmentCount INTEGER")
                db.execSQL("ALTER TABLE sessions ADD COLUMN segmentStartElapsedMs INTEGER NOT NULL DEFAULT 0")
            }
        }
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE sessions ADD COLUMN syncId TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE sessions ADD COLUMN updatedAt INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE sessions ADD COLUMN deleted INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE sessions ADD COLUMN recoveredFromScreenshot INTEGER NOT NULL DEFAULT 0")
                db.execSQL("UPDATE sessions SET syncId = lower(hex(randomblob(16))), updatedAt = endWallClock")
                db.execSQL("CREATE UNIQUE INDEX index_sessions_syncId ON sessions(syncId)")
                db.execSQL("CREATE TABLE IF NOT EXISTS modules (name TEXT NOT NULL PRIMARY KEY, updatedAt INTEGER NOT NULL, deleted INTEGER NOT NULL)")
                seedModules(db)
                db.execSQL("INSERT OR IGNORE INTO modules(name, updatedAt, deleted) SELECT DISTINCT subject, 0, 0 FROM sessions WHERE trim(subject) != ''")
            }
        }

        private fun seedModules(db: SupportSQLiteDatabase) {
            Subject.entries.filter { it != Subject.CUSTOM }.forEach {
                db.execSQL("INSERT OR IGNORE INTO modules(name, updatedAt, deleted) VALUES (?, 0, 0)", arrayOf(it.displayName))
            }
        }

        val SEED_CALLBACK = object : Callback() {
            override fun onCreate(db: SupportSQLiteDatabase) { seedModules(db) }
        }
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                if (INSTANCE == null) UpgradeBackup.preserveVersionOne(context.applicationContext)
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "shuati_timer.db"
                ).addMigrations(MIGRATION_1_2, MIGRATION_2_3).addCallback(SEED_CALLBACK).build()
                    .also { INSTANCE = it }
            }
        }
    }
}
