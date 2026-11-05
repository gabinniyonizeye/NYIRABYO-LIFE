package com.example.nyirabyolife.ui.weekly

import android.content.Intent
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
import android.text.Editable
import android.text.TextWatcher
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
import com.example.nyirabyolife.ui.focus.ScheduledFocusActivity
import com.example.nyirabyolife.util.TaskAlarmScheduler
import com.example.nyirabyolife.util.ThemeHelper
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

        dbHelper.ensurePermanentWakeupAndBedtime(currentWeekOfYear, currentYear)
        TaskAlarmScheduler.schedulePermanentAlarms(this)

        if (!com.example.nyirabyolife.data.local.PreferencesManager.getInstance(this).isTasksCustomizedByUser()) {
            dbHelper.ensureDefaultSundayWeeklyPlanIfEmpty(currentWeekOfYear, currentYear)
        }

        binding.btnBack.setOnClickListener { finish() }

        binding.btnTestAlarm.setOnClickListener { testAlarmSound() }

        binding.btnResetTasks.setOnClickListener {
            AlertDialog.Builder(this, com.google.android.material.R.style.Theme_MaterialComponents_DayNight_Dialog_Alert)
                .setTitle("Reset All Scheduled Tasks?")
                .setMessage("This will remove all scheduled tasks and alarms so you can set your own fresh schedule from scratch.\n\nAre you sure you want to reset everything?")
                .setPositiveButton("Reset Everything") { _, _ ->
                    TaskAlarmScheduler.cancelAllAlarms(this)
                    dbHelper.resetAllTasks()
                    dbHelper.ensurePermanentWakeupAndBedtime(currentWeekOfYear, currentYear)
                    TaskAlarmScheduler.schedulePermanentAlarms(this)
                    loadTasksForSelectedDay()
                    renderDayTabs()
                    Toast.makeText(this, "Schedule reset! Permanent Wake Up (5:00 AM) and BedTime (22:00) preserved.", Toast.LENGTH_LONG).show()
                }
                .setNegativeButton("Cancel", null)
                .show()
        }

        binding.btnAddNewTask.setOnClickListener { showTaskDialog(null) }
        binding.layoutEmptyTasks.setOnClickListener { showTaskDialog(null) }

        applyThemeColors()
        setupSundayBanner()
        renderDayTabs()
        loadTasksForSelectedDay()
    }

    private fun applyThemeColors() {
        val theme = ThemeHelper.getActiveThemeColors(this)
        binding.btnBack.background = ThemeHelper.createPillDrawable(this, theme)
        binding.btnResetTasks.background = ThemeHelper.createPillDrawable(this, theme)
        binding.btnTestAlarm.background = ThemeHelper.createPillDrawable(this, theme)
        binding.btnTestAlarm.setTextColor(theme.secondaryAccent)

        val addPill = ThemeHelper.createPillDrawable(this, theme)
        addPill.setColor(theme.primaryAccent)
        binding.btnAddNewTask.background = addPill
        binding.btnAddNewTask.setTextColor(ContextCompat.getColor(this, R.color.bg_dark))

        binding.cardSundayBanner.background = ThemeHelper.createCardDrawable(this, theme, isElevated = true)
        binding.ivSundayCrown.setColorFilter(theme.primaryAccent)
        binding.lblSundayTitle.setTextColor(theme.primaryAccent)
        binding.tvDayTaskCount.setTextColor(theme.secondaryAccent)
    }

    private fun setupSundayBanner() {
        val todayDay = Calendar.getInstance().get(Calendar.DAY_OF_WEEK)
        if (todayDay == Calendar.SUNDAY) {
            val theme = ThemeHelper.getActiveThemeColors(this)
            binding.cardSundayBanner.background = ThemeHelper.createCardDrawable(this, theme, isElevated = true)
            binding.lblSundayTitle.text = "👑 TODAY IS SUNDAY: PRIME YOUR WEEK"
            binding.lblSundayTitle.setTextColor(theme.primaryAccent)
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
        val theme = ThemeHelper.getActiveThemeColors(this)
        for (task in tasks) {
            val itemBinding = ItemWeeklyPlanTaskBinding.inflate(inflater, binding.layoutTasksContainer, false)
            itemBinding.root.background = ThemeHelper.createCardDrawable(this, theme, isElevated = false)
            itemBinding.tvTaskTitle.text = task.title
            itemBinding.tvTaskTime.text = task.formattedTimeRange
            itemBinding.tvTaskTime.setTextColor(theme.primaryAccent)

            // Mode & Recurrence Badge
            val badgeItems = listOfNotNull(task.modeBadgeText, if (task.isDaily) "Daily Foundation" else null)
            if (badgeItems.isNotEmpty()) {
                itemBinding.tvTaskModeBadge.text = badgeItems.joinToString(" • ")
                itemBinding.tvTaskModeBadge.setTextColor(theme.secondaryAccent)
                itemBinding.tvTaskModeBadge.visibility = android.view.View.VISIBLE
            } else {
                itemBinding.tvTaskModeBadge.visibility = android.view.View.GONE
            }

            // Checkbox logic
            val theme = ThemeHelper.getActiveThemeColors(this)
            if (task.isCompleted) {
                itemBinding.ivTaskCheck.setImageResource(R.drawable.ic_check_circle)
                itemBinding.ivTaskCheck.setColorFilter(theme.secondaryAccent)
                itemBinding.tvTaskTitle.paintFlags = itemBinding.tvTaskTitle.paintFlags or Paint.STRIKE_THRU_TEXT_FLAG
                itemBinding.tvTaskTitle.setTextColor(ContextCompat.getColor(this, R.color.text_tertiary))
            } else {
                itemBinding.ivTaskCheck.setImageResource(R.drawable.ic_circle_outline)
                itemBinding.ivTaskCheck.setColorFilter(ContextCompat.getColor(this, R.color.text_tertiary))
                itemBinding.tvTaskTitle.paintFlags = itemBinding.tvTaskTitle.paintFlags and Paint.STRIKE_THRU_TEXT_FLAG.inv()
                itemBinding.tvTaskTitle.setTextColor(ContextCompat.getColor(this, R.color.text_primary))
            }

            val isFocusable = (task.taskMode in listOf("STUDY_FOCUS", "SILENT_DND", "MEDITATION", "BEDTIME")) && task.durationMinutes > 0
            if (isFocusable && !task.isCompleted) {
                itemBinding.btnStartFocus.visibility = android.view.View.VISIBLE
                itemBinding.btnStartFocus.setColorFilter(theme.primaryAccent)
                itemBinding.btnStartFocus.setOnClickListener {
                    val focusIntent = Intent(this, ScheduledFocusActivity::class.java).apply {
                        putExtra(ScheduledFocusActivity.EXTRA_TASK_ID, task.id)
                        putExtra(ScheduledFocusActivity.EXTRA_TASK_TITLE, task.title)
                        putExtra(ScheduledFocusActivity.EXTRA_TASK_MODE, task.taskMode)
                        putExtra(ScheduledFocusActivity.EXTRA_TASK_DURATION_MINUTES, task.durationMinutes)
                        putExtra(ScheduledFocusActivity.EXTRA_START_HOUR, task.timeHour)
                        putExtra(ScheduledFocusActivity.EXTRA_START_MINUTE, task.timeMinute)
                    }
                    startActivity(focusIntent)
                }
            } else {
                itemBinding.btnStartFocus.visibility = android.view.View.GONE
            }

            // Tapping task card or details opens the edit dialog
            itemBinding.layoutTaskDetails.setOnClickListener {
                showTaskDialog(task)
            }
            itemBinding.root.setOnClickListener {
                showTaskDialog(task)
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
                itemBinding.btnToggleAlarm.setColorFilter(theme.primaryAccent)
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

    private fun showTaskDialog(taskToEdit: WeeklyPlanTask? = null) {
        val dialogBinding = DialogAddWeeklyTaskBinding.inflate(layoutInflater)

        // 1. Recurrence Spinner: Single Day vs Daily
        val recurrenceOptions = listOf("Specific Day (Weekly Schedule)", "Every Day (Daily Foundation)")
        val recurrenceAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, recurrenceOptions)
        dialogBinding.spinnerRecurrence.adapter = recurrenceAdapter
        dialogBinding.spinnerRecurrence.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: android.view.View?, position: Int, id: Long) {
                val isDaily = (position == 1)
                dialogBinding.layoutDaySelection.visibility = if (isDaily) android.view.View.GONE else android.view.View.VISIBLE
            }
            override fun onNothingSelected(parent: android.widget.AdapterView<*>?) {}
        }

        // 2. Setup Day Spinner
        val daysList = listOf("Sunday", "Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday")
        val spinnerAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, daysList)
        dialogBinding.spinnerDayOfWeek.adapter = spinnerAdapter

        // Pre-select target day
        val targetDay = taskToEdit?.dayOfWeek ?: selectedDayOfWeek
        val preIndex = when (targetDay) {
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

        // 3. Discipline & Focus Mode Presets
        val modeLabels = listOf(
            "Standard (Normal)",
            "Study / Deep Focus 🎯",
            "Silent / DND 🔕",
            "Prayer & Meditation 🕊️",
            "BedTime / Total Lockdown 🌙"
        )
        val modeKeys = listOf("NORMAL", "STUDY_FOCUS", "SILENT_DND", "MEDITATION", "BEDTIME")
        val modeAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, modeLabels)
        dialogBinding.spinnerTaskMode.adapter = modeAdapter

        if (taskToEdit != null) {
            val mIdx = modeKeys.indexOf(taskToEdit.taskMode)
            if (mIdx >= 0) dialogBinding.spinnerTaskMode.setSelection(mIdx)
        }

        // 4. Duration Selector & Auto-Return Window Setup
        val durationLabels = listOf(
            "⚡ Direct Alarm (At that moment - No duration)",
            "30 minutes",
            "45 minutes",
            "1 hour (60 min)",
            "1.5 hours (90 min)",
            "2 hours (120 min)",
            "3 hours (180 min)",
            "4 hours (240 min)",
            "Custom minutes..."
        )
        val durationValues = listOf(0, 30, 45, 60, 90, 120, 180, 240, -1)
        val durationAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, durationLabels)
        dialogBinding.spinnerDuration.adapter = durationAdapter

        fun updateCalculatedPreview() {
            val h = dialogBinding.etHour.text.toString().toIntOrNull() ?: 9
            val m = dialogBinding.etMinute.text.toString().toIntOrNull() ?: 0
            val safeH = h.coerceIn(0, 23)
            val safeM = m.coerceIn(0, 59)

            val pos = dialogBinding.spinnerDuration.selectedItemPosition
            val dur = if (pos == durationValues.lastIndex) {
                dialogBinding.etCustomDuration.text.toString().toIntOrNull() ?: 60
            } else {
                durationValues.getOrElse(pos) { 0 }
            }.coerceAtLeast(0).coerceAtMost(720)

            if (dur <= 0) {
                dialogBinding.tvCalculatedWindow.text = "⏰ Direct Alarm at %02d:%02d".format(safeH, safeM)
                dialogBinding.tvDurationExplanation.text = "Loud alarm will sound at %02d:%02d without entering an extended focus session.".format(safeH, safeM)
            } else {
                val endTotal = safeH * 60 + safeM + dur
                val endH = (endTotal / 60) % 24
                val endM = endTotal % 60
                val durH = dur / 60
                val durRemM = dur % 60
                val durStr = if (durH > 0 && durRemM > 0) "${durH}h ${durRemM}m" else if (durH > 0) "${durH}h" else "${durRemM}m"

                dialogBinding.tvCalculatedWindow.text = "⏰ Schedule: %02d:%02d - %02d:%02d (%s)".format(safeH, safeM, endH, endM, durStr)
                dialogBinding.tvDurationExplanation.text = "Do Not Disturb & Focus will engage at %02d:%02d and automatically restore normal mode at %02d:%02d.".format(safeH, safeM, endH, endM)
            }
        }

        dialogBinding.spinnerDuration.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: android.view.View?, position: Int, id: Long) {
                val isCustom = (position == durationValues.lastIndex)
                dialogBinding.etCustomDuration.visibility = if (isCustom) android.view.View.VISIBLE else android.view.View.GONE
                updateCalculatedPreview()
            }
            override fun onNothingSelected(parent: android.widget.AdapterView<*>?) {}
        }

        val textWatcher = object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                updateCalculatedPreview()
            }
            override fun afterTextChanged(s: Editable?) {}
        }
        dialogBinding.etHour.addTextChangedListener(textWatcher)
        dialogBinding.etMinute.addTextChangedListener(textWatcher)
        dialogBinding.etCustomDuration.addTextChangedListener(textWatcher)

        // Pre-populate fields if editing or new
        if (taskToEdit != null) {
            dialogBinding.etTaskTitle.setText(taskToEdit.title)
            dialogBinding.spinnerRecurrence.setSelection(if (taskToEdit.isDaily) 1 else 0)
            dialogBinding.etHour.setText("%02d".format(taskToEdit.timeHour))
            dialogBinding.etMinute.setText("%02d".format(taskToEdit.timeMinute))
            dialogBinding.switchAlarm.isChecked = taskToEdit.isAlarmEnabled

            val dur = taskToEdit.durationMinutes
            val matchedIndex = durationValues.indexOf(dur)
            if (matchedIndex >= 0 && matchedIndex != durationValues.lastIndex) {
                dialogBinding.spinnerDuration.setSelection(matchedIndex)
            } else {
                dialogBinding.spinnerDuration.setSelection(durationValues.lastIndex)
                dialogBinding.etCustomDuration.setText(dur.toString())
                dialogBinding.etCustomDuration.visibility = android.view.View.VISIBLE
            }
        } else {
            // Default to next hour and Direct Alarm (0 min)
            val now = Calendar.getInstance()
            val defaultHour = (now.get(Calendar.HOUR_OF_DAY) + 1) % 24
            dialogBinding.etHour.setText("%02d".format(defaultHour))
            dialogBinding.etMinute.setText("00")
            dialogBinding.spinnerDuration.setSelection(0)
        }
        updateCalculatedPreview()

        dialogBinding.tvQuickPresets.setOnClickListener {
            // Cycle through foundation presets: 05:00, 05:15, 22:00
            val curHour = dialogBinding.etHour.text.toString()
            if (curHour == "05" && dialogBinding.etMinute.text.toString() == "00") {
                dialogBinding.etHour.setText("05")
                dialogBinding.etMinute.setText("15")
                dialogBinding.etTaskTitle.setText("Pray & Scripture at 5:15")
                dialogBinding.spinnerTaskMode.setSelection(3) // Meditation
                dialogBinding.spinnerDuration.setSelection(1) // 30 min
            } else if (curHour == "05" && dialogBinding.etMinute.text.toString() == "15") {
                dialogBinding.etHour.setText("22")
                dialogBinding.etMinute.setText("00")
                dialogBinding.etTaskTitle.setText("BedTime 22:00")
                dialogBinding.spinnerTaskMode.setSelection(4) // BedTime
                dialogBinding.spinnerDuration.setSelection(0) // Direct Alarm
            } else {
                dialogBinding.etHour.setText("05")
                dialogBinding.etMinute.setText("00")
                dialogBinding.etTaskTitle.setText("Wake Up at 5:00 AM")
                dialogBinding.spinnerTaskMode.setSelection(0)
                dialogBinding.spinnerDuration.setSelection(0) // Direct Alarm
            }
            updateCalculatedPreview()
        }

        val dialogTitle = if (taskToEdit != null) "Edit Task / Alarm" else "Schedule Task & Prime Alarm"
        val positiveBtnText = if (taskToEdit != null) "Save Changes" else "Schedule & Set Alarm"

        val builder = AlertDialog.Builder(this, com.google.android.material.R.style.Theme_MaterialComponents_DayNight_Dialog_Alert)
            .setTitle(dialogTitle)
            .setView(dialogBinding.root)
            .setPositiveButton(positiveBtnText) { _, _ ->
                val title = dialogBinding.etTaskTitle.text.toString().trim()
                if (title.isEmpty()) {
                    Toast.makeText(this, "Please enter a task title", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }

                val hour = dialogBinding.etHour.text.toString().toIntOrNull() ?: 9
                val minute = dialogBinding.etMinute.text.toString().toIntOrNull() ?: 0
                val safeHour = hour.coerceIn(0, 23)
                val safeMinute = minute.coerceIn(0, 59)

                val durPosition = dialogBinding.spinnerDuration.selectedItemPosition
                val selectedDuration = if (durPosition == durationValues.lastIndex) {
                    dialogBinding.etCustomDuration.text.toString().toIntOrNull() ?: 60
                } else {
                    durationValues.getOrElse(durPosition) { 0 }
                }.coerceAtLeast(0).coerceAtMost(720)

                val isDaily = (dialogBinding.spinnerRecurrence.selectedItemPosition == 1)
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

                val selectedMode = modeKeys.getOrElse(dialogBinding.spinnerTaskMode.selectedItemPosition) { "NORMAL" }
                val isAlarm = dialogBinding.switchAlarm.isChecked

                if (taskToEdit != null) {
                    val updatedTask = taskToEdit.copy(
                        title = title,
                        dayOfWeek = dayOfWeekCode,
                        timeHour = safeHour,
                        timeMinute = safeMinute,
                        isAlarmEnabled = isAlarm,
                        taskMode = selectedMode,
                        isDaily = isDaily,
                        durationMinutes = selectedDuration
                    )
                    dbHelper.updateWeeklyTask(updatedTask)
                    TaskAlarmScheduler.cancelTaskAlarm(this, taskToEdit.id)
                    if (isAlarm && !taskToEdit.isCompleted) {
                        TaskAlarmScheduler.scheduleTaskAlarm(this, updatedTask)
                    }
                    Toast.makeText(this, "Task updated! ⏰", Toast.LENGTH_SHORT).show()
                    selectedDayOfWeek = dayOfWeekCode
                } else {
                    val newTask = WeeklyPlanTask(
                        title = title,
                        dayOfWeek = dayOfWeekCode,
                        timeHour = safeHour,
                        timeMinute = safeMinute,
                        isAlarmEnabled = isAlarm,
                        isCompleted = false,
                        weekOfYear = currentWeekOfYear,
                        year = currentYear,
                        taskMode = selectedMode,
                        isDaily = isDaily,
                        durationMinutes = selectedDuration
                    )

                    if (isDaily) {
                        val ids = dbHelper.addDailyRecurringTask(newTask)
                        if (isAlarm) {
                            TaskAlarmScheduler.scheduleTaskAlarm(this, newTask.copy(id = ids.firstOrNull() ?: 0L))
                            Toast.makeText(this, "Daily foundation primed for all 7 days! ⏰ %02d:%02d".format(safeHour, safeMinute), Toast.LENGTH_LONG).show()
                        } else {
                            Toast.makeText(this, "Daily task added across all 7 days", Toast.LENGTH_SHORT).show()
                        }
                    } else {
                        val newId = dbHelper.addWeeklyTask(newTask)
                        if (isAlarm) {
                            TaskAlarmScheduler.scheduleTaskAlarm(this, newTask.copy(id = newId))
                            Toast.makeText(this, "Task scheduled! ⏰ Loud Alarm primed for %02d:%02d".format(safeHour, safeMinute), Toast.LENGTH_LONG).show()
                        } else {
                            Toast.makeText(this, "Task scheduled without alarm", Toast.LENGTH_SHORT).show()
                        }
                        selectedDayOfWeek = dayOfWeekCode
                    }
                }

                renderDayTabs()
                loadTasksForSelectedDay()
            }
            .setNegativeButton("Cancel", null)

        if (taskToEdit != null) {
            builder.setNeutralButton("Delete Task") { _, _ ->
                TaskAlarmScheduler.cancelTaskAlarm(this, taskToEdit.id)
                dbHelper.deleteWeeklyTask(taskToEdit.id)
                loadTasksForSelectedDay()
                Toast.makeText(this, "Task deleted", Toast.LENGTH_SHORT).show()
            }
        }

        builder.show()
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
