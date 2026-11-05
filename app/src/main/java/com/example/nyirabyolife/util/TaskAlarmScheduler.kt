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

        // For daily tasks, always schedule for the next upcoming occurrence from today
        // regardless of which dayOfWeek the task was originally saved under
        val triggerTime = if (task.isDaily) {
            computeNextDailyTriggerMillis(task.timeHour, task.timeMinute)
        } else {
            computeNextTriggerTimeMillis(task.dayOfWeek, task.timeHour, task.timeMinute)
        }

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
     * Schedule one of the two permanent (hardcoded) system alarms:
     * - Wake Up at 05:30 every day (WAKEUP_ALARM_ID)
     * - Bedtime at 22:30 every day (BEDTIME_ALARM_ID)
     * These guarantee exactly 7 hours of sleep and survive resets.
     */
    fun schedulePermanentWakeupAlarm(context: Context) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val triggerTime = computeNextDailyTriggerMillis(5, 30)

        val intent = Intent(context, TaskAlarmReceiver::class.java).apply {
            action = TaskAlarmReceiver.ACTION_FIRE_ALARM
            putExtra(EXTRA_TASK_ID, WAKEUP_ALARM_ID.toLong())
            putExtra(EXTRA_TASK_TITLE, "⏰ Wake Up — 05:30 AM")
            putExtra(EXTRA_TASK_TIME, "05:30")
            putExtra(EXTRA_TASK_MODE, "NORMAL")
            putExtra(EXTRA_TASK_DURATION_MINUTES, 0)
            putExtra(EXTRA_IS_DAILY, true)
        }

        val pendingIntent = PendingIntent.getBroadcast(
            context,
            WAKEUP_ALARM_ID,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val showIntent = Intent(context, com.example.nyirabyolife.ui.home.HomeActivity::class.java)
        val showPendingIntent = PendingIntent.getActivity(
            context, WAKEUP_ALARM_ID, showIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        setExactAlarmSafely(alarmManager, triggerTime, pendingIntent, showPendingIntent)
    }

    fun schedulePermanentBedtimeAlarm(context: Context) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val triggerTime = computeNextDailyTriggerMillis(22, 30)

        val intent = Intent(context, TaskAlarmReceiver::class.java).apply {
            action = TaskAlarmReceiver.ACTION_FIRE_ALARM
            putExtra(EXTRA_TASK_ID, BEDTIME_ALARM_ID.toLong())
            putExtra(EXTRA_TASK_TITLE, "🌙 BedTime — 22:30")
            putExtra(EXTRA_TASK_TIME, "22:30")
            putExtra(EXTRA_TASK_MODE, "BEDTIME")
            putExtra(EXTRA_TASK_DURATION_MINUTES, 0)
            putExtra(EXTRA_IS_DAILY, true)
        }

        val pendingIntent = PendingIntent.getBroadcast(
            context,
            BEDTIME_ALARM_ID,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val showIntent = Intent(context, com.example.nyirabyolife.ui.home.HomeActivity::class.java)
        val showPendingIntent = PendingIntent.getActivity(
            context, BEDTIME_ALARM_ID, showIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        setExactAlarmSafely(alarmManager, triggerTime, pendingIntent, showPendingIntent)
    }

    fun schedulePermanentAlarms(context: Context) {
        schedulePermanentWakeupAlarm(context)
        schedulePermanentBedtimeAlarm(context)
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
    }

    fun cancelAllAlarms(context: Context) {
        val db = NyirabyoDbHelper.getInstance(context)
        // Cancel across all weeks (daily tasks span multiple week entries)
        val allTasks = db.getAllWeeklyTasks()
        allTasks.forEach { cancelTaskAlarm(context, it.id) }
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
     * Fetches ALL tasks (not just current week) so daily tasks are always included.
     */
    fun rescheduleAllActiveAlarms(context: Context) {
        val db = NyirabyoDbHelper.getInstance(context)
        // Always reschedule permanent alarms first
        schedulePermanentAlarms(context)
        // Reschedule all user tasks across all weeks
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
