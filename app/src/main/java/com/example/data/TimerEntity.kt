package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "timers")
data class TimerEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,
    val category: String, // Building, Troops, Spells, Clan War
    val durationSeconds: Long, // Initial duration selected
    val startTimeMs: Long, // Start epoch millis
    val targetTimeMs: Long, // Absolute epoch millis when finished
    val isPaused: Boolean = false,
    val pausedRemainingSeconds: Long = durationSeconds,
    val isCompletedNotificationShown: Boolean = false
) {
    /**
     * Compute remaining seconds dynamically based on targetTimeMs.
     */
    fun getRemainingSeconds(): Long {
        return if (isPaused) {
            pausedRemainingSeconds
        } else {
            val diff = (targetTimeMs - System.currentTimeMillis()) / 1000
            if (diff < 0) 0 else diff
        }
    }

    /**
     * Compute completed status.
     */
    fun isFinished(): Boolean {
        return getRemainingSeconds() <= 0
    }

    /**
     * Get the percentage of completion from 0.0 to 1.0.
     */
    fun getProgress(): Float {
        if (durationSeconds <= 0) return 1f
        val remaining = getRemainingSeconds().toFloat()
        val total = durationSeconds.toFloat()
        // Clash style: progress goes from 1.0 down to 0.0, or filled (0.0 to 1.0).
        // Let's return the elapsed percentage (from 0.0 to 1.0, where 1.0 is completed)
        val elapsed = total - remaining
        return (elapsed / total).coerceIn(0f, 1f)
    }
}
