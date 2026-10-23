package com.codex.shuatitimer.data.repository

import androidx.room.withTransaction
import com.codex.shuatitimer.data.db.AppDatabase
import com.codex.shuatitimer.data.model.Session
import com.codex.shuatitimer.data.model.StudyModule
import com.codex.shuatitimer.sync.SyncBundle
import com.codex.shuatitimer.sync.SyncCodec

/**
 * Repository wrapping SessionDao for session persistence.
 */
class SessionRepository(private val db: AppDatabase) {
    private val dao = db.sessionDao()
    private val modules = db.moduleDao()
    val allSessions = dao.getAllSessions()
    val activeModules = modules.observeActive()

    private suspend fun nextRevision() = maxOf(
        System.currentTimeMillis(), dao.maxRevision() + 1, modules.maxRevision() + 1
    )

    suspend fun insert(session: Session): Long = db.withTransaction {
        dao.insert(session.copy(updatedAt = nextRevision()))
    }

    /** All segments are committed together, so a failed save can safely be retried. */
    suspend fun insertSegments(sessions: List<Session>) = db.withTransaction {
        require(sessions.isNotEmpty()) { "没有可保存的分段" }
        val activeNames = modules.all().filterNot { it.deleted }.map { it.name }.toSet()
        require(sessions.all { it.subject in activeNames }) { "有模块已被删除，请重新选择" }
        val revision = nextRevision()
        sessions.forEach { session ->
            val existing = dao.findBySyncId(session.syncId)
            dao.insert(session.copy(id = existing?.id ?: 0L, updatedAt = revision))
        }
    }

    suspend fun update(session: Session) = db.withTransaction {
        val current = dao.findBySyncId(session.syncId)
        if (current != null && !current.deleted) {
            dao.update(session.copy(id = current.id, updatedAt = nextRevision()))
        }
    }

    suspend fun delete(session: Session) = db.withTransaction {
        dao.findBySyncId(session.syncId)?.let {
            dao.update(it.copy(deleted = true, updatedAt = nextRevision()))
        }
    }

    suspend fun deleteAll() = db.withTransaction { dao.markAllDeleted(nextRevision()) }

    suspend fun preserveLegacyModule(name: String) = db.withTransaction {
        if (name.isNotBlank() && modules.find(name) == null) modules.put(StudyModule(name))
    }

    suspend fun addModule(rawName: String): String? = db.withTransaction {
        val name = rawName.trim()
        when {
            name.isBlank() -> "请输入模块名称"
            name.length > 20 -> "模块名称不能超过 20 个字"
            name.any { it.isISOControl() } -> "模块名称不能包含换行或控制字符"
            modules.find(name)?.deleted == false -> "这个模块已经存在"
            else -> { modules.put(StudyModule(name, nextRevision())); null }
        }
    }

    suspend fun deleteModule(name: String) = db.withTransaction {
        modules.find(name)?.let { modules.put(it.copy(deleted = true, updatedAt = nextRevision())) }
    }

    suspend fun snapshot(): SyncBundle = db.withTransaction {
        SyncBundle(dao.allForSync(), modules.all())
    }

    suspend fun merge(incoming: SyncBundle): Int = db.withTransaction {
        SyncCodec.validate(incoming)
        var changes = 0
        val localSessions = dao.allForSync().associateBy { it.syncId }
        val localModules = modules.all().associateBy { it.name }
        for (remote in incoming.sessions) {
            val local = localSessions[remote.syncId]
            if (local == null || SyncCodec.remoteWins(local, remote)) {
                dao.insert(remote.copy(id = local?.id ?: 0))
                changes++
            }
        }
        for (remote in incoming.modules) {
            val local = localModules[remote.name]
            if (local == null || remote.updatedAt > local.updatedAt ||
                (remote.updatedAt == local.updatedAt && remote.deleted && !local.deleted)) {
                modules.put(remote)
                changes++
            }
        }
        changes
    }
}
