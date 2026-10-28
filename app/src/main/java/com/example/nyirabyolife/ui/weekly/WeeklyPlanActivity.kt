package com.example.nyirabyolife.ui.weekly

import android.graphics.Color
import android.graphics.Paint
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.example.nyirabyolife.R
import com.example.nyirabyolife.data.local.NyirabyoDbHelper
import com.example.nyirabyolife.data.model.WeeklyPlanTask
import com.example.nyirabyolife.databinding.ActivityWeeklyPlanBinding
import com.example.nyirabyolife.databinding.DialogAddWeeklyTaskBinding
import com.example.nyirabyolife.databinding.ItemWeeklyPlanTaskBinding
import com.example.nyirabyolife.util.TaskAlarmScheduler
import java.util.Calendar

class WeeklyPlanActivity : AppCompatActivity() {

    private lateinit var binding: ActivityWeeklyPlanBinding
    private lateinit var dbHelper: NyirabyoDbHelper

    private var selectedDayOfWeek = Calendar.SUNDAY
    private var currentWeekOfYear = 1
    private var currentYear = 2026

    private var previewMediaPlayer: MediaPlayer? = null
    private val handler = Handler(Looper.getMainLooper())

    private val dayNames = listOf(
        Pair(Calendar.SUNDAY, "SUN"),
        Pair(Calendar.MONDAY, "MON"),
        Pair(Calendar.TUESDAY, "TUE"),
        Pair(Calendar.WEDNESDAY, "WED"),
        Pair(Calendar.THURSDAY, "THU"),
        Pair(Calendar.FRIDAY, "FRI"),
        Pair(Calendar.SATURDAY, "SAT")
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityWeeklyPlanBinding.inflate(layoutInflater)
        setContentView(binding.root)

        dbHelper = NyirabyoDbHelper.getInstance(this)

        val cal = Calendar.getInstance()
        currentWeekOfYear = cal.get(Calendar.WEEK_OF_YEAR)
        currentYear = cal.get(Calendar.YEAR)
        selectedDayOfWeek = cal.get(Calendar.DAY_OF_WEEK)

        // Ensure starter plan exists if empty
        dbHelper.ensureDefaultSundayWeeklyPlanIfEmpty(currentWeekOfYear, currentYear)

        binding.btnBack.setOnClickListener { finish() }

        binding.btnTestAlarm.setOnClickListener { testAlarmSound() }

        binding.btnAddNewTask.setOnClickListener { promptAddTask() }

        setupSundayBanner()
        renderDayTabs()
        loadTasksForSelectedDay()
    }

    private fun setupSundayBanner() {
        val todayDay = Calendar.getInstance().get(Calendar.DAY_OF_WEEK)
        if (todayDay == Calendar.SUNDAY) {
            binding.cardSundayBanner.setBackgroundResource(R.drawable.card_background_elevated)
            binding.lblSundayTitle.text = "👑 TODAY IS SUNDAY: PRIME YOUR WEEK"
            binding.lblSundayTitle.setTextColor(ContextCompat.getColor(this, R.color.gold_primary))
            binding.tvSundayDescription.text = "Today is Sunday! Set all your targets, deep work sessions, prayer times, and alarms. You will step into the week prepared, disciplined, and focused with God."
        }
    }

    private fun renderDayTabs() {
        binding.layoutDayTabs.removeAllViews()
        val inflater = LayoutInflater.from(this)

        for ((dayCode, dayLabel) in dayNames) {
            val tabView = TextView(this).apply {
                text = dayLabel
                textSize = 12f
                setPadding(36, 18, 36, 18)
                letterSpacing = 0.08f
            }

            val isSelected = (dayCode == selectedDayOfWeek)
            if (isSelected) {
                tabView.setBackgroundResource(R.drawable.button_gold)
                tabView.setTextColor(ContextCompat.getColor(this, R.color.bg_dark))
                tabView.paintFlags = tabView.paintFlags or Paint.FAKE_BOLD_TEXT_FLAG
            } else {
                tabView.setBackgroundResource(R.drawable.pill_background)
                tabView.setTextColor(ContextCompat.getColor(this, R.color.text_secondary))
                tabView.paintFlags = tabView.paintFlags and Paint.FAKE_BOLD_TEXT_FLAG.inv()
            }

            val marginParams = android.widget.LinearLayout.LayoutParams(
                android.widget.LinearLayout.LayoutParams.WRAP_CONTENT,
                android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                marginEnd = 16
            }
            tabView.layoutParams = marginParams

            tabView.setOnClickListener {
                selectedDayOfWeek = dayCode
                renderDayTabs()
                loadTasksForSelectedDay()
            }

            binding.layoutDayTabs.addView(tabView)
        }
    }

    private fun loadTasksForSelectedDay() {
        binding.layoutTasksContainer.removeAllViews()
        val tasks = dbHelper.getWeeklyTasksForDay(selectedDayOfWeek, currentWeekOfYear, currentYear)

        val fullDayName = when (selectedDayOfWeek) {
            Calendar.SUNDAY -> "Sunday"
            Calendar.MONDAY -> "Monday"
            Calendar.TUESDAY -> "Tuesday"
            Calendar.WEDNESDAY -> "Wednesday"
            Calendar.THURSDAY -> "Thursday"
            Calendar.FRIDAY -> "Friday"
            Calendar.SATURDAY -> "Saturday"
            else -> "Day"
        }

        binding.tvSelectedDayTitle.text = "$fullDayName Plans"
        val activeAlarmsCount = tasks.count { it.isAlarmEnabled && !it.isCompleted }
        binding.tvDayTaskCount.text = "${tasks.size} tasks • $activeAlarmsCount alarms set"

        if (tasks.isEmpty()) {
            binding.layoutEmptyTasks.visibility = android.view.View.VISIBLE
            return
        }
        binding.layoutEmptyTasks.visibility = android.view.View.GONE

        val inflater = LayoutInflater.from(this)
        for (task in tasks) {
            val itemBinding = ItemWeeklyPlanTaskBinding.inflate(inflater, binding.layoutTasksContainer, false)
            itemBinding.tvTaskTitle.text = task.title
            itemBinding.tvTaskTime.text = task.formattedTime

            // Checkbox logic
            if (task.isCompleted) {
                itemBinding.ivTaskCheck.setImageResource(R.drawable.ic_check_circle)
                itemBinding.ivTaskCheck.setColorFilter(ContextCompat.getColor(this, R.color.emerald_accent))
                itemBinding.tvTaskTitle.paintFlags = itemBinding.tvTaskTitle.paintFlags or Paint.STRIKE_THRU_TEXT_FLAG
                itemBinding.tvTaskTitle.setTextColor(ContextCompat.getColor(this, R.color.text_tertiary))
            } else {
                itemBinding.ivTaskCheck.setImageResource(R.drawable.ic_circle_outline)
                itemBinding.ivTaskCheck.setColorFilter(ContextCompat.getColor(this, R.color.text_tertiary))
                itemBinding.tvTaskTitle.paintFlags = itemBinding.tvTaskTitle.paintFlags and Paint.STRIKE_THRU_TEXT_FLAG.inv()
                itemBinding.tvTaskTitle.setTextColor(ContextCompat.getColor(this, R.color.text_primary))
            }

            itemBinding.ivTaskCheck.setOnClickListener {
                val newStatus = !task.isCompleted
                dbHelper.updateWeeklyTaskStatus(task.id, newStatus)
                if (newStatus) {
                    TaskAlarmScheduler.cancelTaskAlarm(this, task.id)
                } else if (task.isAlarmEnabled) {
                    TaskAlarmScheduler.scheduleTaskAlarm(this, task.copy(isCompleted = false))
                }
                loadTasksForSelectedDay()
            }

            // Alarm toggle icon
            if (task.isAlarmEnabled) {
                itemBinding.btnToggleAlarm.setImageResource(R.drawable.ic_alarm)
                itemBinding.btnToggleAlarm.setColorFilter(ContextCompat.getColor(this, R.color.gold_primary))
                itemBinding.btnToggleAlarm.alpha = 1.0f
            } else {
                itemBinding.btnToggleAlarm.setImageResource(R.drawable.ic_alarm)
                itemBinding.btnToggleAlarm.setColorFilter(ContextCompat.getColor(this, R.color.text_muted))
                itemBinding.btnToggleAlarm.alpha = 0.4f
            }

            itemBinding.btnToggleAlarm.setOnClickListener {
                val newAlarmStatus = !task.isAlarmEnabled
                dbHelper.toggleWeeklyTaskAlarm(task.id, newAlarmStatus)
                if (newAlarmStatus) {
                    TaskAlarmScheduler.scheduleTaskAlarm(this, task.copy(isAlarmEnabled = true))
                    Toast.makeText(this, "⏰ Loud Alarm primed for ${task.formattedTime}", Toast.LENGTH_SHORT).show()
                } else {
                    TaskAlarmScheduler.cancelTaskAlarm(this, task.id)
                    Toast.makeText(this, "Alarm disabled for this task", Toast.LENGTH_SHORT).show()
                }
                loadTasksForSelectedDay()
            }

            // Delete task
            itemBinding.btnDeleteTask.setOnClickListener {
                TaskAlarmScheduler.cancelTaskAlarm(this, task.id)
                dbHelper.deleteWeeklyTask(task.id)
                loadTasksForSelectedDay()
            }

            binding.layoutTasksContainer.addView(itemBinding.root)
        }
    }

    private fun promptAddTask() {
        val dialogBinding = DialogAddWeeklyTaskBinding.inflate(layoutInflater)

        // Setup Day Spinner
        val daysList = listOf("Sunday", "Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday")
        val spinnerAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, daysList)
        dialogBinding.spinnerDayOfWeek.adapter = spinnerAdapter

        // Pre-select current day tab
        val preIndex = when (selectedDayOfWeek) {
            Calendar.SUNDAY -> 0
            Calendar.MONDAY -> 1
            Calendar.TUESDAY -> 2
            Calendar.WEDNESDAY -> 3
            Calendar.THURSDAY -> 4
            Calendar.FRIDAY -> 5
            Calendar.SATURDAY -> 6
            else -> 0
        }
        dialogBinding.spinnerDayOfWeek.setSelection(preIndex)

        // Default to next hour
        val now = Calendar.getInstance()
        val defaultHour = (now.get(Calendar.HOUR_OF_DAY) + 1) % 24
        dialogBinding.etHour.setText("%02d".format(defaultHour))
        dialogBinding.etMinute.setText("00")

        dialogBinding.tvQuickPresets.setOnClickListener {
            dialogBinding.etHour.setText("09")
            dialogBinding.etMinute.setText("30")
        }

        AlertDialog.Builder(this, com.google.android.material.R.style.Theme_MaterialComponents_DayNight_Dialog_Alert)
            .setTitle("Schedule Task & Prime Alarm")
            .setView(dialogBinding.root)
            .setPositiveButton("Schedule & Set Alarm") { _, _ ->
                val title = dialogBinding.etTaskTitle.text.toString().trim()
                if (title.isEmpty()) {
                    Toast.makeText(this, "Please enter a task title", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }

                val hour = dialogBinding.etHour.text.toString().toIntOrNull() ?: 9
                val minute = dialogBinding.etMinute.text.toString().toIntOrNull() ?: 0
                val safeHour = hour.coerceIn(0, 23)
                val safeMinute = minute.coerceIn(0, 59)

                val selectedDayIndex = dialogBinding.spinnerDayOfWeek.selectedItemPosition
                val dayOfWeekCode = when (selectedDayIndex) {
                    0 -> Calendar.SUNDAY
                    1 -> Calendar.MONDAY
                    2 -> Calendar.TUESDAY
                    3 -> Calendar.WEDNESDAY
                    4 -> Calendar.THURSDAY
                    5 -> Calendar.FRIDAY
                    6 -> Calendar.SATURDAY
                    else -> Calendar.SUNDAY
                }

                val isAlarm = dialogBinding.switchAlarm.isChecked

                val newTask = WeeklyPlanTask(
                    title = title,
                    dayOfWeek = dayOfWeekCode,
                    timeHour = safeHour,
                    timeMinute = safeMinute,
                    isAlarmEnabled = isAlarm,
                    isCompleted = false,
                    weekOfYear = currentWeekOfYear,
                    year = currentYear
                )

                val newId = dbHelper.addWeeklyTask(newTask)
                if (isAlarm) {
                    TaskAlarmScheduler.scheduleTaskAlarm(this, newTask.copy(id = newId))
                    Toast.makeText(this, "Task scheduled! ⏰ Loud Alarm primed for %02d:%02d".format(safeHour, safeMinute), Toast.LENGTH_LONG).show()
                } else {
                    Toast.makeText(this, "Task scheduled without alarm", Toast.LENGTH_SHORT).show()
                }

                selectedDayOfWeek = dayOfWeekCode
                renderDayTabs()
                loadTasksForSelectedDay()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun testAlarmSound() {
        try {
            previewMediaPlayer?.stop()
            previewMediaPlayer?.release()
        } catch (_: Exception) {}

        val alertUri: Uri = RingtoneManager.getActualDefaultRingtoneUri(this, RingtoneManager.TYPE_ALARM)
            ?: RingtoneManager.getActualDefaultRingtoneUri(this, RingtoneManager.TYPE_RINGTONE)
            ?: android.provider.Settings.System.DEFAULT_ALARM_ALERT_URI

        try {
            previewMediaPlayer = MediaPlayer().apply {
                setDataSource(applicationContext, alertUri)
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .setLegacyStreamType(AudioManager.STREAM_ALARM)
                        .build()
                )
                prepare()
                start()
            }
            Toast.makeText(this, "🔊 Testing Alarm Ringtone (Playing for 4 seconds)...", Toast.LENGTH_SHORT).show()

            handler.postDelayed({
                try {
                    previewMediaPlayer?.stop()
                    previewMediaPlayer?.release()
                    previewMediaPlayer = null
                } catch (_: Exception) {}
            }, 4000L)
        } catch (e: Exception) {
            Toast.makeText(this, "Could not preview ringtone: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            previewMediaPlayer?.stop()
            previewMediaPlayer?.release()
            previewMediaPlayer = null
        } catch (_: Exception) {}
    }
}
