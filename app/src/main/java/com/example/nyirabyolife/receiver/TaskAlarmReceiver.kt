package com.example.nyirabyolife.receiver

import android.app.ActivityOptions
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import android.widget.Toast
import androidx.core.app.NotificationCompat
import com.example.nyirabyolife.R
import com.example.nyirabyolife.data.local.NyirabyoDbHelper
import com.example.nyirabyolife.ui.alarm.TaskAlarmActivity
import com.example.nyirabyolife.util.AlarmSoundPlayer
import com.example.nyirabyolife.util.TaskAlarmScheduler

class TaskAlarmReceiver : BroadcastReceiver() {

    companion object {
        const val ACTION_FIRE_ALARM = "com.example.nyirabyolife.ACTION_FIRE_ALARM"
        const val ACTION_DISMISS_ALARM = "com.example.nyirabyolife.ACTION_DISMISS_ALARM"
        const val ACTION_SNOOZE_ALARM = "com.example.nyirabyolife.ACTION_SNOOZE_ALARM"
        const val CHANNEL_ID = "nyirabyo_task_alarms"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: ACTION_FIRE_ALARM

        val taskId = intent.getLongExtra(TaskAlarmScheduler.EXTRA_TASK_ID, -1L)
        val taskTitle = intent.getStringExtra(TaskAlarmScheduler.EXTRA_TASK_TITLE) ?: "Scheduled Task"
        val taskTime = intent.getStringExtra(TaskAlarmScheduler.EXTRA_TASK_TIME) ?: ""
        val taskMode = intent.getStringExtra(TaskAlarmScheduler.EXTRA_TASK_MODE) ?: "NORMAL"
        val durationMinutes = intent.getIntExtra(TaskAlarmScheduler.EXTRA_TASK_DURATION_MINUTES, 0)
        val isDaily = intent.getBooleanExtra(TaskAlarmScheduler.EXTRA_IS_DAILY, false)

        when (action) {
            ACTION_DISMISS_ALARM -> {
                AlarmSoundPlayer.stopAlarm()
                val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
                notificationManager?.cancel(taskId.toInt())
                Toast.makeText(context, "Alarm dismissed", Toast.LENGTH_SHORT).show()
                return
            }

            ACTION_SNOOZE_ALARM -> {
                AlarmSoundPlayer.stopAlarm()
                val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
                notificationManager?.cancel(taskId.toInt())
                TaskAlarmScheduler.scheduleSnooze(context, taskId, taskTitle, taskTime, 10, taskMode, durationMinutes, isDaily)
                Toast.makeText(context, "Alarm snoozed for 10 minutes", Toast.LENGTH_SHORT).show()
                return
            }

            ACTION_FIRE_ALARM -> {
                // 1. START AUDIBLE ALARM IMMEDIATELY in background
                AlarmSoundPlayer.startAlarm(context, taskTitle)

                // 2. Re-arm the alarm for next day / week so it NEVER stops repeating
                rearmAlarm(context, taskId, isDaily)

                // 3. Build high-priority notification channel
                createNotificationChannel(context)

                // 4. PendingIntent for Dismiss Action
                val dismissIntent = Intent(context, TaskAlarmReceiver::class.java).apply {
                    this.action = ACTION_DISMISS_ALARM
                    putExtra(TaskAlarmScheduler.EXTRA_TASK_ID, taskId)
                }
                val dismissPendingIntent = PendingIntent.getBroadcast(
                    context,
                    (taskId + 10000).toInt(),
                    dismissIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )

                // 5. PendingIntent for Snooze Action
                val snoozeIntent = Intent(context, TaskAlarmReceiver::class.java).apply {
                    this.action = ACTION_SNOOZE_ALARM
                    putExtra(TaskAlarmScheduler.EXTRA_TASK_ID, taskId)
                    putExtra(TaskAlarmScheduler.EXTRA_TASK_TITLE, taskTitle)
                    putExtra(TaskAlarmScheduler.EXTRA_TASK_TIME, taskTime)
                    putExtra(TaskAlarmScheduler.EXTRA_TASK_MODE, taskMode)
                    putExtra(TaskAlarmScheduler.EXTRA_TASK_DURATION_MINUTES, durationMinutes)
                    putExtra(TaskAlarmScheduler.EXTRA_IS_DAILY, isDaily)
                }
                val snoozePendingIntent = PendingIntent.getBroadcast(
                    context,
                    (taskId + 20000).toInt(),
                    snoozeIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )

                // 6. Full-Screen Intent for TaskAlarmActivity
                val fullScreenIntent = Intent(context, TaskAlarmActivity::class.java).apply {
                    addFlags(
                        Intent.FLAG_ACTIVITY_NEW_TASK or
                                Intent.FLAG_ACTIVITY_CLEAR_TOP or
                                Intent.FLAG_ACTIVITY_SINGLE_TOP
                    )
                    putExtra(TaskAlarmScheduler.EXTRA_TASK_ID, taskId)
                    putExtra(TaskAlarmScheduler.EXTRA_TASK_TITLE, taskTitle)
                    putExtra(TaskAlarmScheduler.EXTRA_TASK_TIME, taskTime)
                    putExtra(TaskAlarmScheduler.EXTRA_TASK_MODE, taskMode)
                    putExtra(TaskAlarmScheduler.EXTRA_TASK_DURATION_MINUTES, durationMinutes)
                }

                // Android 14 (API 34) Background Activity Launch Options
                val bundleOptions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                    ActivityOptions.makeBasic().apply {
                        setPendingIntentBackgroundActivityStartMode(ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED)
                    }.toBundle()
                } else null

                val fullScreenPendingIntent = PendingIntent.getActivity(
                    context,
                    taskId.toInt(),
                    fullScreenIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                    bundleOptions
                )

                val soundUri = RingtoneManager.getActualDefaultRingtoneUri(context, RingtoneManager.TYPE_ALARM)
                    ?: RingtoneManager.getActualDefaultRingtoneUri(context, RingtoneManager.TYPE_RINGTONE)

                val notification = NotificationCompat.Builder(context, CHANNEL_ID)
                    .setSmallIcon(R.drawable.ic_alarm)
                    .setContentTitle("⏰ Loud Alarm: $taskTime")
                    .setContentText(taskTitle)
                    .setPriority(NotificationCompat.PRIORITY_MAX)
                    .setCategory(NotificationCompat.CATEGORY_ALARM)
                    .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                    .setSound(soundUri)
                    .setVibrate(longArrayOf(0, 800, 400, 800, 400, 1000))
                    .setFullScreenIntent(fullScreenPendingIntent, true)
                    .setContentIntent(fullScreenPendingIntent)
                    .setOngoing(true)
                    .setAutoCancel(false)
                    .addAction(R.drawable.ic_check_circle, "Dismiss", dismissPendingIntent)
                    .addAction(R.drawable.ic_alarm, "Snooze (10m)", snoozePendingIntent)
                    .build()

                val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
                notificationManager?.notify(taskId.toInt(), notification)

                // Directly launch activity as well
                try {
                    context.startActivity(fullScreenIntent, bundleOptions)
                } catch (_: Exception) {}
            }
        }
    }

    private fun rearmAlarm(context: Context, taskId: Long, isDaily: Boolean) {
        if (taskId == TaskAlarmScheduler.WAKEUP_ALARM_ID.toLong()) {
            TaskAlarmScheduler.schedulePermanentWakeupAlarm(context)
            return
        }
        if (taskId == TaskAlarmScheduler.BEDTIME_ALARM_ID.toLong()) {
            TaskAlarmScheduler.schedulePermanentBedtimeAlarm(context)
            return
        }

        val db = NyirabyoDbHelper.getInstance(context)
        val task = db.getWeeklyTaskById(taskId) ?: return
        if (!task.isAlarmEnabled) return

        TaskAlarmScheduler.scheduleTaskAlarm(context, task)
    }

    private fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val audioAttributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ALARM)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()

            val soundUri = RingtoneManager.getActualDefaultRingtoneUri(context, RingtoneManager.TYPE_ALARM)
                ?: RingtoneManager.getActualDefaultRingtoneUri(context, RingtoneManager.TYPE_RINGTONE)

            val channel = NotificationChannel(
                CHANNEL_ID,
                "NYIRABYO Task Alarms",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Loud full-screen alarm for scheduled daily tasks"
                enableLights(true)
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 800, 400, 800, 400, 1000)
                setSound(soundUri, audioAttributes)
                lockscreenVisibility = NotificationCompat.VISIBILITY_PUBLIC
                setBypassDnd(true)
            }

            val notificationManager =
                context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            notificationManager?.createNotificationChannel(channel)
        }
    }
}
