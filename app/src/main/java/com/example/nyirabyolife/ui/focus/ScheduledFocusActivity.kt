package com.example.nyirabyolife.ui.focus

import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.CountDownTimer
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.provider.Settings
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.example.nyirabyolife.R
import com.example.nyirabyolife.data.local.NyirabyoDbHelper
import com.example.nyirabyolife.data.local.PreferencesManager
import com.example.nyirabyolife.data.local.ScriptureRepository
import com.example.nyirabyolife.databinding.ActivityScheduledFocusBinding
import com.example.nyirabyolife.security.RecoveryAuthManager
import com.example.nyirabyolife.util.DndModeHelper
import com.example.nyirabyolife.util.ThemeHelper
import java.util.Calendar

class ScheduledFocusActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_TASK_ID = "extra_focus_task_id"
        const val EXTRA_TASK_TITLE = "extra_focus_task_title"
        const val EXTRA_TASK_MODE = "extra_focus_task_mode"
        const val EXTRA_TASK_DURATION_MINUTES = "extra_focus_duration_minutes"
        const val EXTRA_START_HOUR = "extra_focus_start_hour"
        const val EXTRA_START_MINUTE = "extra_focus_start_minute"
    }

    private lateinit var binding: ActivityScheduledFocusBinding
    private lateinit var prefs: PreferencesManager
    private lateinit var dbHelper: NyirabyoDbHelper

    private var taskId: Long = -1L
    private var taskTitle: String = "Focus & Study Session"
    private var taskMode: String = "STUDY_FOCUS"
    private var durationMinutes: Int = 60
    private var startHour: Int = 0
    private var startMinute: Int = 0

    private var countDownTimer: CountDownTimer? = null
    private var totalMillis: Long = 60 * 60 * 1000L
    private var millisRemaining: Long = 60 * 60 * 1000L

    /** True once DND has been enabled and the countdown has been started. */
    private var isSessionStarted = false

    override fun onCreate(savedInstanceState: Bundle?) {
        showOverLockScreen()
        super.onCreate(savedInstanceState)
        binding = ActivityScheduledFocusBinding.inflate(layoutInflater)
        setContentView(binding.root)

        prefs = PreferencesManager.getInstance(this)
        dbHelper = NyirabyoDbHelper.getInstance(this)

        taskId = intent.getLongExtra(EXTRA_TASK_ID, -1L)
        taskTitle = intent.getStringExtra(EXTRA_TASK_TITLE)
            ?: prefs.getActiveFocusTitle().ifEmpty { "Focus Session" }
        taskMode = intent.getStringExtra(EXTRA_TASK_MODE)
            ?: prefs.getActiveFocusMode().ifEmpty { "STUDY_FOCUS" }
        durationMinutes = intent.getIntExtra(
            EXTRA_TASK_DURATION_MINUTES,
            prefs.getActiveFocusTotalMinutes().takeIf { it > 0 } ?: 60
        )

        val cal = Calendar.getInstance()
        startHour = intent.getIntExtra(EXTRA_START_HOUR, cal.get(Calendar.HOUR_OF_DAY))
        startMinute = intent.getIntExtra(EXTRA_START_MINUTE, cal.get(Calendar.MINUTE))

        // Restore elapsed time if returning to an existing session
        val remainingSec = prefs.getFocusRemainingSeconds()
        if (remainingSec > 0 && prefs.isFocusSessionActive()) {
            millisRemaining = remainingSec * 1000L
            totalMillis = (durationMinutes * 60 * 1000L).coerceAtLeast(millisRemaining)
        } else {
            totalMillis = durationMinutes * 60 * 1000L
            millisRemaining = totalMillis
            prefs.setActiveFocusSession(durationMinutes, taskMode, taskTitle, taskId)
        }

        applyThemeColors()
        setupUI()

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                promptEarlyExitConfirmation()
            }
        })

        // Start the session (DND + countdown) — or gate behind permission prompt
        tryStartSession()
    }

    /**
     * Called from onCreate and from onResume (after returning from system DND settings).
     *
     * On Pixel 5 (stock Android), [AudioManager.setRingerMode] with RINGER_MODE_SILENT
     * silently (no exception) downgrades to RINGER_MODE_VIBRATE if the user has NOT
     * granted Do Not Disturb access to this app in:
     *   Settings → Apps → Special App Access → Do Not Disturb.
     *
     * Zeroing STREAM_RING volume stops sound but NOT vibration — only ringer mode
     * SILENT stops vibration. So the permission is required for true silence.
     *
     * Strategy:
     *  - If permission is already granted → enable DND + start countdown immediately.
     *  - If NOT granted → show a MANDATORY dialog. No "Later" button.
     *    User must either grant access or cancel the session.
     */
    private fun tryStartSession() {
        if (isSessionStarted) return  // countdown already running

        if (DndModeHelper.isDndPermissionGranted(this)) {
            // Full silence available — enable DND and start
            val isStrict = (taskMode == "SILENT_DND" || taskMode == "BEDTIME")
            DndModeHelper.enableFocusDnd(this, strictSilence = isStrict)
            isSessionStarted = true
            startCountdown()
        } else {
            // DND permission not granted → show mandatory dialog
            showMandatoryDndPermissionDialog()
        }
    }

    /**
     * When the user comes back from the DND settings screen, re-check permission
     * and start the session if it is now granted.
     */
    override fun onResume() {
        super.onResume()
        if (!isSessionStarted) {
            tryStartSession()
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Mandatory DND permission dialog — no "Later" button
    // ─────────────────────────────────────────────────────────────────────────

    private fun showMandatoryDndPermissionDialog() {
        AlertDialog.Builder(
            this,
            com.google.android.material.R.style.Theme_MaterialComponents_DayNight_Dialog_Alert
        )
            .setTitle("🔕 Required: Full Silence Permission")
            .setMessage(
                "To silence calls completely (no ring, no vibration) during focus sessions, " +
                "NYIRABYOLife needs Do Not Disturb access.\n\n" +
                "Steps:\n" +
                "1. Tap \"Grant Access\" below\n" +
                "2. Find NYIRABYOLife in the list\n" +
                "3. Toggle it ON\n" +
                "4. Press Back — focus session will start automatically\n\n" +
                "You only need to do this once."
            )
            .setCancelable(false)   // ← cannot be dismissed by tapping outside
            .setPositiveButton("Grant Access") { _, _ ->
                startActivity(Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS))
            }
            .setNegativeButton("Cancel Session") { _, _ ->
                // End the session cleanly and go back
                DndModeHelper.disableFocusDnd(this)
                prefs.clearActiveFocusSession()
                finish()
            }
            .show()
    }

    // ─────────────────────────────────────────────────────────────────────────
    // UI setup & theming
    // ─────────────────────────────────────────────────────────────────────────

    private fun showOverLockScreen() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
            val km = getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager
            km?.requestDismissKeyguard(this, null)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                        WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                        WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD
            )
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    private fun applyThemeColors() {
        val theme = ThemeHelper.getActiveThemeColors(this)

        binding.rootView.setBackgroundColor(theme.background)
        binding.cardCountdown.background = ThemeHelper.createCardDrawable(this, theme, isElevated = true)
        binding.cardScripture.background = ThemeHelper.createCardDrawable(this, theme, isElevated = false)

        binding.tvTaskTitle.setTextColor(theme.primaryAccent)
        binding.tvTimeWindow.setTextColor(theme.secondaryAccent)
        binding.tvScriptureRef.setTextColor(theme.primaryAccent)

        val pillDnd = ThemeHelper.createPillDrawable(this, theme)
        binding.tvDndStatusBadge.background = pillDnd

        val pillMode = ThemeHelper.createPillDrawable(this, theme)
        binding.tvModeBadge.background = pillMode
        binding.tvModeBadge.setTextColor(theme.secondaryAccent)

        val btnFinishPill = ThemeHelper.createPillDrawable(this, theme)
        btnFinishPill.setColor(theme.primaryAccent)
        binding.btnCompleteEarly.background = btnFinishPill
        binding.btnCompleteEarly.setTextColor(ContextCompat.getColor(this, R.color.bg_dark))

        binding.progressBarFocus.progressTintList =
            android.content.res.ColorStateList.valueOf(theme.primaryAccent)
    }

    private fun setupUI() {
        binding.tvTaskTitle.text = taskTitle

        val modeLabel = when (taskMode) {
            "STUDY_FOCUS"  -> "STUDY FOCUS 🎯"
            "SILENT_DND"   -> "SILENT DND 🔕"
            "MEDITATION"   -> "PRAYER & MEDITATION 🕊️"
            "BEDTIME"      -> "BEDTIME TOTAL LOCKDOWN 🌙"
            else           -> "DEEP DISCIPLINE FOCUS ⚡"
        }
        binding.tvModeBadge.text = modeLabel

        val endTotalMinutes = startHour * 60 + startMinute + durationMinutes
        val endHour = (endTotalMinutes / 60) % 24
        val endMin = endTotalMinutes % 60
        val durHours = durationMinutes / 60
        val durRemainderMins = durationMinutes % 60
        val durText = when {
            durHours > 0 && durRemainderMins > 0 -> "${durHours}h ${durRemainderMins}m"
            durHours > 0 -> "${durHours}h"
            else -> "${durRemainderMins}m"
        }

        binding.tvTimeWindow.text =
            "%02d:%02d - %02d:%02d (%s)".format(startHour, startMinute, endHour, endMin, durText)
        binding.tvEndTimeNotice.text =
            "Session ends & normal mode returns at %02d:%02d".format(endHour, endMin)

        // Scripture anchor
        val verse = ScriptureRepository.getVerseOfTheDay()
        binding.tvScriptureRef.text = verse.reference.uppercase()
        binding.tvScriptureText.text = "\u201c${verse.text}\u201d"

        binding.btnCompleteEarly.setOnClickListener { promptEarlyExitConfirmation() }
        binding.btnEmergencyExit.setOnClickListener { promptEmergencyExitWithAuth() }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Countdown
    // ─────────────────────────────────────────────────────────────────────────

    private fun startCountdown() {
        countDownTimer?.cancel()
        countDownTimer = object : CountDownTimer(millisRemaining, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                millisRemaining = millisUntilFinished
                updateCountdownUI(millisRemaining)
            }

            override fun onFinish() {
                onSessionCompletedAutomatically()
            }
        }.start()
    }

    private fun updateCountdownUI(millis: Long) {
        val totalSec = millis / 1000
        val hours = totalSec / 3600
        val minutes = (totalSec % 3600) / 60
        val seconds = totalSec % 60

        binding.tvCountdown.text = if (hours > 0) {
            "%02d:%02d:%02d".format(hours, minutes, seconds)
        } else {
            "%02d:%02d".format(minutes, seconds)
        }

        if (totalMillis > 0) {
            val progressInt = ((millis.toDouble() / totalMillis.toDouble()) * 1000).toInt()
            binding.progressBarFocus.progress = progressInt.coerceIn(0, 1000)
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Session end / exit
    // ─────────────────────────────────────────────────────────────────────────

    private fun onSessionCompletedAutomatically() {
        updateCountdownUI(0)
        binding.tvCountdown.text = "00:00:00"
        binding.tvEndTimeNotice.text = "Glorious work! Focus completed. Normal mode restored."
        binding.btnCompleteEarly.text = "Praise God: Returned to Normal Mode"

        DndModeHelper.disableFocusDnd(this)
        prefs.clearActiveFocusSession()

        if (taskId != -1L) {
            dbHelper.updateWeeklyTaskStatus(taskId, true)
        }

        vibrateCompletion()
        Toast.makeText(this, "🎉 Focus duration over! Sound and apps restored.", Toast.LENGTH_LONG).show()

        window.decorView.postDelayed({ finish() }, 2500L)
    }

    private fun promptEarlyExitConfirmation() {
        AlertDialog.Builder(
            this,
            com.google.android.material.R.style.Theme_MaterialComponents_DayNight_Dialog_Alert
        )
            .setTitle("Finish Focus Session?")
            .setMessage(
                "Are you sure you have completed this activity?\n\n" +
                "This will end the focus window, mark the task completed, " +
                "and automatically restore normal sound and normal mode."
            )
            .setPositiveButton("Complete & Restore Normal Mode") { _, _ ->
                countDownTimer?.cancel()
                DndModeHelper.disableFocusDnd(this)
                prefs.clearActiveFocusSession()
                if (taskId != -1L) {
                    dbHelper.updateWeeklyTaskStatus(taskId, true)
                }
                Toast.makeText(this, "Focus session finished. Returned to normal mode.", Toast.LENGTH_SHORT).show()
                finish()
            }
            .setNegativeButton("Continue Focusing", null)
            .show()
    }

    private fun promptEmergencyExitWithAuth() {
        if (!prefs.hasPin()) {
            promptEarlyExitConfirmation()
            return
        }

        val auth = RecoveryAuthManager(
            activity = this,
            onSuccess = {
                countDownTimer?.cancel()
                DndModeHelper.disableFocusDnd(this)
                prefs.clearActiveFocusSession()
                Toast.makeText(this, "Emergency exit verified. Returned to normal mode.", Toast.LENGTH_SHORT).show()
                finish()
            }
        )
        auth.authenticate()
    }

    private fun vibrateCompletion() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vm = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                vm?.defaultVibrator?.vibrate(
                    VibrationEffect.createWaveform(longArrayOf(0, 400, 200, 400, 200, 600), -1)
                )
            } else {
                @Suppress("DEPRECATION")
                val v = getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    v?.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 400, 200, 400, 200, 600), -1))
                } else {
                    @Suppress("DEPRECATION")
                    v?.vibrate(600)
                }
            }
        } catch (_: Exception) {}
    }

    override fun onDestroy() {
        super.onDestroy()
        countDownTimer?.cancel()
    }
}
