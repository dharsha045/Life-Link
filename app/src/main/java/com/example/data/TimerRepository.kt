package com.example.data

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.example.notification.AlarmReceiver
import kotlinx.coroutines.flow.Flow

class TimerRepository(
    private val context: Context,
    private val timerDao: TimerDao
) {
    val allTimers: Flow<List<TimerEntity>> = timerDao.getAllTimers()

    suspend fun insertTimer(name: String, category: String, durationSeconds: Long): Long {
        val now = System.currentTimeMillis()
        val target = now + (durationSeconds * 1000)
        
        val timer = TimerEntity(
            name = name,
            category = category,
            durationSeconds = durationSeconds,
            startTimeMs = now,
            targetTimeMs = target,
            isPaused = false,
            pausedRemainingSeconds = durationSeconds,
            isCompletedNotificationShown = false
        )
        
        val newId = timerDao.insertTimer(timer).toInt()
        val savedTimer = timer.copy(id = newId)
        
        registerAlarm(savedTimer)
        return newId.toLong()
    }

    suspend fun updateTimer(timer: TimerEntity) {
        timerDao.updateTimer(timer)
        if (timer.isPaused || timer.getRemainingSeconds() <= 0) {
            cancelAlarm(timer.id)
        } else {
            registerAlarm(timer)
        }
    }

    suspend fun deleteTimer(timer: TimerEntity) {
        cancelAlarm(timer.id)
        timerDao.deleteTimer(timer)
    }

    suspend fun deleteTimerById(id: Int) {
        cancelAlarm(id)
        timerDao.deleteTimerById(id)
    }

    suspend fun togglePauseTimer(timer: TimerEntity) {
        val updatedTimer = if (timer.isPaused) {
            // Resume: recalculate start and target time with the remaining duration
            val remaining = timer.pausedRemainingSeconds
            val now = System.currentTimeMillis()
            val target = now + (remaining * 1000)
            timer.copy(
                isPaused = false,
                startTimeMs = now,
                targetTimeMs = target,
                durationSeconds = timer.durationSeconds // keep total starting scale intact
            )
        } else {
            // Pause: save the current remaining scale and clear the target running timer
            val remaining = timer.getRemainingSeconds()
            timer.copy(
                isPaused = true,
                pausedRemainingSeconds = remaining,
                targetTimeMs = System.currentTimeMillis() // reference timestamp
            )
        }
        updateTimer(updatedTimer)
    }

    suspend fun resetTimer(timer: TimerEntity) {
        val now = System.currentTimeMillis()
        val target = now + (timer.durationSeconds * 1000)
        val resetTimer = timer.copy(
            isPaused = false,
            startTimeMs = now,
            targetTimeMs = target,
            pausedRemainingSeconds = timer.durationSeconds,
            isCompletedNotificationShown = false
        )
        updateTimer(resetTimer)
    }

    private fun registerAlarm(timer: TimerEntity) {
        if (timer.isPaused) return
        val remaining = timer.getRemainingSeconds()
        if (remaining <= 0) return

        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val intent = Intent(context, AlarmReceiver::class.java).apply {
            putExtra("TIMER_ID", timer.id)
            putExtra("TIMER_NAME", timer.name)
            putExtra("TIMER_CATEGORY", timer.category)
        }

        val pendingIntent = PendingIntent.getBroadcast(
            context,
            timer.id,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (alarmManager.canScheduleExactAlarms()) {
                    alarmManager.setExactAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        timer.targetTimeMs,
                        pendingIntent
                    )
                } else {
                    alarmManager.setAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        timer.targetTimeMs,
                        pendingIntent
                    )
                }
            } else {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    timer.targetTimeMs,
                    pendingIntent
                )
            }
        } catch (e: Exception) {
            // Safe fallback if exact alarm permission is rejected
            try {
                alarmManager.set(
                    AlarmManager.RTC_WAKEUP,
                    timer.targetTimeMs,
                    pendingIntent
                )
            } catch (ex: Exception) {
                ex.printStackTrace()
            }
        }
    }

    private fun cancelAlarm(timerId: Int) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val intent = Intent(context, AlarmReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            timerId,
            intent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        )
        if (pendingIntent != null) {
            try {
                alarmManager.cancel(pendingIntent)
                pendingIntent.cancel()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}
