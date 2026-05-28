package com.example.notification

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import android.os.Vibrator
import android.os.VibratorManager
import android.os.VibrationEffect
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.data.AppDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class AlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val timerId = intent.getIntExtra("TIMER_ID", 0)
        val timerName = intent.getStringExtra("TIMER_NAME") ?: "Upgrade complete"
        val timerCategory = intent.getStringExtra("TIMER_CATEGORY") ?: "Building"

        // Perform offline updates to the database: set notification as shown
        if (timerId != 0) {
            val db = AppDatabase.getDatabase(context)
            CoroutineScope(Dispatchers.IO).launch {
                val timer = db.timerDao().getTimerById(timerId)
                if (timer != null) {
                    db.timerDao().updateTimer(timer.copy(isCompletedNotificationShown = true))
                }
            }
        }

        // Send notifications
        sendNotification(context, timerId, timerName, timerCategory)
        triggerVibrationAndSound(context)
    }

    private fun sendNotification(context: Context, id: Int, name: String, category: String) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channelId = "clash_timer_notifications"

        // Initialize notification channel for Oreo and above
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Clash Upgrades Channel",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notifies when your Clash of Clans upgrades are complete."
                enableLights(true)
                enableVibration(true)
                // Set default alert sound attributes
                val audioAttributes = AudioAttributes.Builder()
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .setUsage(AudioAttributes.USAGE_NOTIFICATION_EVENT)
                    .build()
                setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION), audioAttributes)
            }
            notificationManager.createNotificationChannel(channel)
        }

        // Tap action: open main activity
        val openAppIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            id,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Choose a custom icon indicator based on the category
        val categoryText = when (category) {
            "Building" -> "🔨 Builder Finished: $name"
            "Troops" -> "⚔️ Training Finished: $name"
            "Spells" -> "🧪 Brewing Finished: $name"
            "Clan War" -> "🏆 War Event Finished: $name"
            else -> "🎮 Clash Timer Finished: $name"
        }

        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm) // Safe fallback drawable
            .setContentTitle("Clash Timer Complete!")
            .setContentText(categoryText)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION))
            .build()

        notificationManager.notify(id, notification)
    }

    private fun triggerVibrationAndSound(context: Context) {
        // Safe vibration effect for gaming response
        try {
            val vibrationPattern = longArrayOf(0, 400, 200, 400, 200, 600) // Clash Triple Beat
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                vibratorManager?.defaultVibrator?.vibrate(VibrationEffect.createWaveform(vibrationPattern, -1))
            } else {
                @Suppress("DEPRECATION")
                val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
                vibrator?.vibrate(vibrationPattern, -1)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // Play extra alert audio
        try {
            val alertSound = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM) ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            val ringtone = RingtoneManager.getRingtone(context, alertSound)
            ringtone?.play()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
