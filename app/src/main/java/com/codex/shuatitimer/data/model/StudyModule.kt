package com.codex.shuatitimer.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "modules")
data class StudyModule(
    @PrimaryKey val name: String,
    val updatedAt: Long = 0,
    val deleted: Boolean = false
)
