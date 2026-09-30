package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "saved_tracks")
data class TrackEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val title: String,
    val description: String,
    val linesJson: String,
    val startX: Float = 100f,
    val startY: Float = 100f,
    val startAngle: Float = 0f,
    val createdAt: Long = System.currentTimeMillis(),
    val bestDistance: Float = 0f,
    val bestSpeed: Float = 0f,
    val isPreset: Boolean = false
)
