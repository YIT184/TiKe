package com.codex.shuatitimer.data.db

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import java.io.File

object UpgradeBackup {
    // Called before Room can open/migrate the database. Do not proceed if backup fails.
    fun preserveVersionOne(context: Context) {
        val source = context.getDatabasePath("shuati_timer.db")
        if (!source.exists()) return
        SQLiteDatabase.openDatabase(source.path, null, SQLiteDatabase.OPEN_READWRITE).use { db ->
            if (db.version != 1) return
            val directory = File(context.noBackupFilesDir, "before-v2")
            val complete = File(directory, "complete")
            if (complete.exists()) return
            directory.mkdirs()
            db.rawQuery("PRAGMA wal_checkpoint(FULL)", null).use { cursor ->
                check(cursor.moveToFirst() && cursor.getInt(0) == 0) { "旧数据备份暂不可用，请重试" }
            }
            source.copyTo(File(directory, source.name), overwrite = true)
            for (suffix in listOf("-wal", "-shm")) {
                val sidecar = File(source.path + suffix)
                if (sidecar.exists()) sidecar.copyTo(File(directory, source.name + suffix), overwrite = true)
            }
            complete.writeText("Database v1 preserved before migration")
        }
    }
}
