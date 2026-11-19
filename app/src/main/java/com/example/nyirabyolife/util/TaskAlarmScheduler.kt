package com.example.nyirabyolife.util

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.example.nyirabyolife.data.local.NyirabyoDbHelper
import com.example.nyirabyolife.data.model.WeeklyPlanTask
import com.example.nyirabyolife.receiver.TaskAlarmReceiver
import java.util.Calendar

object TaskAlarmScheduler {

    const val EXTRA_TASK_ID = "extra_task_id"
    const val EXTRA_TASK_TITLE = "extra_task_title"
    const val EXTRA_TASK_TIME = "extra_task_time"
    const val EXTRA_TASK_MODE = "extra_task_mode"
    const val EXTRA_TASK_DURATION_MINUTES = "extra_task_duration_minutes"
    const val EXTRA_IS_DAILY = "extra_is_daily"

    // Permanent alarm IDs (outside any task ID range to avoid collisions)
    const val WAKEUP_ALARM_ID = 900001
    const val BEDTIME_ALARM_ID = 900002

    fun scheduleTaskAlarm(context: Context, task: WeeklyPlanTask) {
        if (!task.isAlarmEnabled || task.isCompleted) {
            cancelTaskAlarm(context, task.id)
            return
        }

        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return

        // Always cancel any prior alarm registration for this ID first so no stale trigger remains
        cancelTaskAlarm(context, task.id)

        // For each task in weekly_plans, schedule for its specific dayOfWeek occurrence
        val triggerTime = computeNextTriggerTimeMillis(task.dayOfWeek, task.timeHour, task.timeMinute)

        val intent = Intent(context, TaskAlarmReceiver::class.java).apply {
            action = TaskAlarmReceiver.ACTION_FIRE_ALARM
            putExtra(EXTRA_TASK_ID, task.id)
            putExtra(EXTRA_TASK_TITLE, task.title)
            putExtra(EXTRA_TASK_TIME, task.formattedTime)
            putExtra(EXTRA_TASK_MODE, task.taskMode)
            putExtra(EXTRA_TASK_DURATION_MINUTES, task.durationMinutes)
            putExtra(EXTRA_IS_DAILY, task.isDaily)
        }

        val pendingIntent = PendingIntent.getBroadcast(
            context,
            task.id.toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val showIntent = Intent(context, com.example.nyirabyolife.ui.home.HomeActivity::class.java)
        val showPendingIntent = PendingIntent.getActivity(
            context,
            task.id.toInt(),
            showIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        setExactAlarmSafely(alarmManager, triggerTime, pendingIntent, showPendingIntent)
    }

    /**
     * Permanent hardcoded alarms (900001, 900002) are retired so that the user has 100% control
     * over their own schedule and alarms. These methods now actively cancel any lingering ghost alarms.
     */
    fun cancelPermanentAlarms(context: Context) {
        cancelTaskAlarm(context, WAKEUP_ALARM_ID.toLong())
        cancelTaskAlarm(context, BEDTIME_ALARM_ID.toLong())
    }

    fun schedulePermanentWakeupAlarm(context: Context) {
        cancelTaskAlarm(context, WAKEUP_ALARM_ID.toLong())
    }

    fun schedulePermanentBedtimeAlarm(context: Context) {
        cancelTaskAlarm(context, BEDTIME_ALARM_ID.toLong())
    }

    fun schedulePermanentAlarms(context: Context) {
        cancelPermanentAlarms(context)
    }

    private fun setExactAlarmSafely(
        alarmManager: AlarmManager,
        triggerTime: Long,
        pendingIntent: PendingIntent,
        showPendingIntent: PendingIntent
    ) {
        try {
            alarmManager.setAlarmClock(
                AlarmManager.AlarmClockInfo(triggerTime, showPendingIntent),
                pendingIntent
            )
        } catch (_: SecurityException) {
            try {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerTime, pendingIntent)
            } catch (_: Exception) {
                alarmManager.set(AlarmManager.RTC_WAKEUP, triggerTime, pendingIntent)
            }
        }
    }

    fun cancelTaskAlarm(context: Context, taskId: Long) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        try {
            val intent = Intent(context, TaskAlarmReceiver::class.java).apply {
                action = TaskAlarmReceiver.ACTION_FIRE_ALARM
            }
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                taskId.toInt(),
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            alarmManager.cancel(pendingIntent)
            pendingIntent.cancel()
        } catch (_: Exception) {}

        try {
            val showIntent = Intent(context, com.example.nyirabyolife.ui.home.HomeActivity::class.java)
            val showPendingIntent = PendingIntent.getActivity(
                context,
                taskId.toInt(),
                showIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            alarmManager.cancel(showPendingIntent)
            showPendingIntent.cancel()
        } catch (_: Exception) {}
    }

    fun cancelAllAlarms(context: Context) {
        cancelPermanentAlarms(context)
        val db = NyirabyoDbHelper.getInstance(context)
        val allTasks = db.getAllWeeklyTasks()
        allTasks.forEach { cancelTaskAlarm(context, it.id) }
        purgeAllGhostAlarms(context)
    }

    /**
     * Sweeps Android's AlarmManager for any lingering alarms that do not match currently active,
     * alarm-enabled tasks in weekly_plans, ensuring no orphaned or deleted alarms can ever fire.
     */
    fun purgeAllGhostAlarms(context: Context) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        cancelPermanentAlarms(context)

        val db = NyirabyoDbHelper.getInstance(context)
        db.purgeStaleAndLegacyGhostTasks()

        val validTasks = db.getAllWeeklyTasks()
        val activeAlarmIds = validTasks
            .filter { it.isAlarmEnabled && !it.isCompleted }
            .map { it.id.toInt() }
            .toSet()

        // Cancel alarms for tasks that are completed or alarm-disabled
        validTasks.filter { !it.isAlarmEnabled || it.isCompleted }.forEach {
            cancelTaskAlarm(context, it.id)
        }

        // Broad sweep to cancel any orphaned PendingIntents up to ID 1000
        val intent = Intent(context, TaskAlarmReceiver::class.java).apply {
            action = TaskAlarmReceiver.ACTION_FIRE_ALARM
        }
        val showIntent = Intent(context, com.example.nyirabyolife.ui.home.HomeActivity::class.java)

        for (reqCode in 1..1000) {
            if (reqCode !in activeAlarmIds) {
                try {
                    val existingBroadcast = PendingIntent.getBroadcast(
                        context,
                        reqCode,
                        intent,
                        PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
                    )
                    if (existingBroadcast != null) {
                        alarmManager.cancel(existingBroadcast)
                        existingBroadcast.cancel()
                    }
                } catch (_: Exception) {}

                try {
                    val existingShow = PendingIntent.getActivity(
                        context,
                        reqCode,
                        showIntent,
                        PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
                    )
                    if (existingShow != null) {
                        alarmManager.cancel(existingShow)
                        existingShow.cancel()
                    }
                } catch (_: Exception) {}
            }
        }
    }

    fun scheduleSnooze(context: Context, taskId: Long, title: String, timeFormatted: String, snoozeMinutes: Int = 10, taskMode: String = "NORMAL", durationMinutes: Int = 0, isDaily: Boolean = false) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val triggerTime = System.currentTimeMillis() + (snoozeMinutes * 60 * 1000L)

        val intent = Intent(context, TaskAlarmReceiver::class.java).apply {
            action = TaskAlarmReceiver.ACTION_FIRE_ALARM
            putExtra(EXTRA_TASK_ID, taskId)
            putExtra(EXTRA_TASK_TITLE, "$title (Snooze)")
            putExtra(EXTRA_TASK_TIME, timeFormatted)
            putExtra(EXTRA_TASK_MODE, taskMode)
            putExtra(EXTRA_TASK_DURATION_MINUTES, durationMinutes)
            putExtra(EXTRA_IS_DAILY, isDaily)
        }

        val pendingIntent = PendingIntent.getBroadcast(
            context,
            taskId.toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val showIntent = Intent(context, com.example.nyirabyolife.ui.home.HomeActivity::class.java)
        val showPendingIntent = PendingIntent.getActivity(
            context, taskId.toInt(), showIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        setExactAlarmSafely(alarmManager, triggerTime, pendingIntent, showPendingIntent)
    }

    /**
     * Reschedules all alarms — called on boot, app update, time change.
     * Cleans up ghost alarms first, then schedules ONLY currently active, alarm-enabled tasks.
     */
    fun rescheduleAllActiveAlarms(context: Context) {
        purgeAllGhostAlarms(context)

        val db = NyirabyoDbHelper.getInstance(context)
        val allTasks = db.getAllWeeklyTasks()
        allTasks.filter { it.isAlarmEnabled && !it.isCompleted }.forEach {
            scheduleTaskAlarm(context, it)
        }
    }

    /**
     * Compute the next trigger for a DAILY task (fires every day at the given H:MM).
     * Always returns a future time — if H:MM already passed today, fires tomorrow.
     */
    fun computeNextDailyTriggerMillis(hour: Int, minute: Int): Long {
        val now = Calendar.getInstance()
        val target = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        // If that time is now or already past, fire tomorrow
        if (target.timeInMillis <= now.timeInMillis) {
            target.add(Calendar.DAY_OF_YEAR, 1)
        }
        return target.timeInMillis
    }

    /**
     * Compute the next trigger for a WEEKLY task (fires on a specific day-of-week).
     */
    fun computeNextTriggerTimeMillis(dayOfWeek: Int, hour: Int, minute: Int): Long {
        val now = Calendar.getInstance()
        val target = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

        val currentDay = now.get(Calendar.DAY_OF_WEEK)
        var daysDiff = dayOfWeek - currentDay

        if (daysDiff < 0 || (daysDiff == 0 && target.timeInMillis <= now.timeInMillis)) {
            daysDiff += 7
        }

        target.add(Calendar.DAY_OF_YEAR, daysDiff)
        return target.timeInMillis
    }
}
