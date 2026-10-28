package com.example.nyirabyolife.ui.lock

import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.graphics.Paint
import android.os.Build
import android.os.Bundle
import android.os.CountDownTimer
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.example.nyirabyolife.R
import com.example.nyirabyolife.data.local.NyirabyoDbHelper
import com.example.nyirabyolife.data.local.PreferencesManager
import com.example.nyirabyolife.data.local.ScriptureRepository
import com.example.nyirabyolife.data.model.Mission
import com.example.nyirabyolife.databinding.ActivityDisciplineLockBinding
import com.example.nyirabyolife.security.RecoveryAuthManager
import com.example.nyirabyolife.ui.recovery.UrgeCheckInActivity
import com.example.nyirabyolife.util.ThemeHelper
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class DisciplineLockActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_IS_ESCAPE_ROOM = "extra_is_escape_room"
    }

    private lateinit var binding: ActivityDisciplineLockBinding
    private lateinit var dbHelper: NyirabyoDbHelper
    private lateinit var prefs: PreferencesManager
    private lateinit var authManager: RecoveryAuthManager
    private var currentMissions: List<Mission> = emptyList()
    private var escapeRoomTimer: CountDownTimer? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Allow showing over Keyguard when screen is turned on by user
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            // DO NOT force screen on - this lets the display timeout and sleep naturally to save battery
            setTurnScreenOn(false)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED)
            @Suppress("DEPRECATION")
            window.clearFlags(WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON)
        }

        // CRITICAL: Ensure the screen goes to sleep naturally to save battery!
        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        binding = ActivityDisciplineLockBinding.inflate(layoutInflater)
        setContentView(binding.root)

        dbHelper = NyirabyoDbHelper.getInstance(this)
        prefs = PreferencesManager.getInstance(this)

        // Strict lock: Back button disabled
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                // Stay locked
            }
        })

        applyThemeColors()

        loadVerse()
        loadMissions()

        setupEscapeRoomOrNormalUnlock()

        authManager = RecoveryAuthManager(
            activity = this,
            onSuccess = {
                finish()
            },
            onFailed = {
                showPinFallbackDialog()
            }
        )

        binding.btnLockUrge.setOnClickListener {
            startActivity(Intent(this, UrgeCheckInActivity::class.java))
        }

        binding.btnLockPhone.setOnClickListener {
            try {
                val dialIntent = Intent(Intent.ACTION_DIAL).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                startActivity(dialIntent)
            } catch (_: Exception) {
                Toast.makeText(this, "Phone dialer unavailable", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        applyThemeColors()
        setupEscapeRoomOrNormalUnlock()
        loadMissions()
    }

    private fun applyThemeColors() {
        val themeColors = ThemeHelper.getActiveThemeColors(this)
        binding.tvLockTitle.setTextColor(themeColors.primaryAccent)
        binding.tvLockVerseRef.setTextColor(themeColors.primaryAccent)
        binding.tvLockMissionSummary.setTextColor(themeColors.secondaryAccent)

        binding.cardLockVerse.background = ThemeHelper.createCardDrawable(this, themeColors, isElevated = true)
        binding.cardLockMissions.background = ThemeHelper.createCardDrawable(this, themeColors, isElevated = false)
        binding.btnLockPhone.background = ThemeHelper.createPillDrawable(this, themeColors)
        binding.btnLockPhone.setTextColor(themeColors.primaryAccent)
        binding.btnLockUrge.background = ThemeHelper.createPillDrawable(this, themeColors)
        binding.btnLockUrge.setTextColor(themeColors.secondaryAccent)
    }

    private fun setupEscapeRoomOrNormalUnlock() {
        val themeColors = ThemeHelper.getActiveThemeColors(this)
        if (prefs.isEscapeRoomActive()) {
            binding.cardEscapeRoomBanner.visibility = View.VISIBLE
            binding.cardEscapeRoomBanner.background = ThemeHelper.createCardDrawable(this, themeColors, isElevated = true)
            binding.btnUnlock.text = "Strict Lockdown (Escape Room Active)"
            val lockPill = ThemeHelper.createPillDrawable(this, themeColors)
            binding.btnUnlock.background = lockPill
            binding.btnUnlock.setTextColor(ContextCompat.getColor(this, R.color.rose_alert))

            val remainingSec = prefs.getEscapeRoomRemainingSeconds()
            startEscapeRoomCountdown(remainingSec * 1000L)

            binding.btnUnlock.setOnClickListener {
                val remMins = (prefs.getEscapeRoomRemainingSeconds() / 60) + 1
                Toast.makeText(
                    this,
                    "Escape Room active (${remMins}m remaining). Only calls allowed.",
                    Toast.LENGTH_SHORT
                ).show()
            }
        } else {
            binding.cardEscapeRoomBanner.visibility = View.GONE
            binding.btnUnlock.text = "Unlock Device"
            val unlockPill = ThemeHelper.createPillDrawable(this, themeColors)
            unlockPill.setColor(themeColors.primaryAccent)
            binding.btnUnlock.background = unlockPill
            binding.btnUnlock.setTextColor(ContextCompat.getColor(this, R.color.bg_dark))
            binding.btnUnlock.setOnClickListener {
                handleUnlock()
            }
        }
    }

    private fun startEscapeRoomCountdown(millis: Long) {
        escapeRoomTimer?.cancel()
        escapeRoomTimer = object : CountDownTimer(millis, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                val totalSec = millisUntilFinished / 1000
                val mins = totalSec / 60
                val secs = totalSec % 60
                binding.tvEscapeRoomCountdown.text = "%02d:%02d REMAINING".format(mins, secs)
            }

            override fun onFinish() {
                prefs.clearEscapeRoomLockdown()
                setupEscapeRoomOrNormalUnlock()
                Toast.makeText(this@DisciplineLockActivity, "Escape Room completed! Victory.", Toast.LENGTH_LONG).show()
            }
        }.start()
    }

    private fun loadVerse() {
        if (prefs.isEscapeRoomActive()) {
            binding.tvLockVerseText.text = "“No temptation has overtaken you that is not common to man. God is faithful, and he will not let you be tempted beyond your ability, but with the temptation he will also provide the way of escape.”"
            binding.tvLockVerseRef.text = "1 Corinthians 10:13"
        } else {
            val verse = ScriptureRepository.getVerseOfTheDay()
            binding.tvLockVerseText.text = "“${verse.text}”"
            binding.tvLockVerseRef.text = verse.reference
        }
    }

    private fun loadMissions() {
        val themeColors = ThemeHelper.getActiveThemeColors(this)
        val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        val cal = Calendar.getInstance()
        val dayOfWeek = cal.get(Calendar.DAY_OF_WEEK)
        val week = cal.get(Calendar.WEEK_OF_YEAR)
        val year = cal.get(Calendar.YEAR)
        currentMissions = dbHelper.getSynchronizedDailyMissions(today, dayOfWeek, week, year)

        val completed = currentMissions.count { it.isCompleted }
        binding.tvLockMissionSummary.text = "$completed of ${currentMissions.size} complete"
        binding.tvLockMissionSummary.setTextColor(themeColors.secondaryAccent)

        if (currentMissions.isNotEmpty()) {
            binding.rowLockMission1.visibility = android.view.View.VISIBLE
            bindMissionRow(currentMissions[0], binding.tvLockMissionTitle1, binding.ivLockCheck1, binding.rowLockMission1)
        } else {
            binding.rowLockMission1.visibility = android.view.View.GONE
        }

        if (currentMissions.size > 1) {
            binding.rowLockMission2.visibility = android.view.View.VISIBLE
            bindMissionRow(currentMissions[1], binding.tvLockMissionTitle2, binding.ivLockCheck2, binding.rowLockMission2)
        } else {
            binding.rowLockMission2.visibility = android.view.View.GONE
        }

        if (currentMissions.size > 2) {
            binding.rowLockMission3.visibility = android.view.View.VISIBLE
            bindMissionRow(currentMissions[2], binding.tvLockMissionTitle3, binding.ivLockCheck3, binding.rowLockMission3)
        } else {
            binding.rowLockMission3.visibility = android.view.View.GONE
        }
    }

    private fun bindMissionRow(
        mission: Mission,
        tvTitle: TextView,
        ivCheck: ImageView,
        rowLayout: LinearLayout
    ) {
        val themeColors = ThemeHelper.getActiveThemeColors(this)
        tvTitle.text = mission.title

        if (mission.isCompleted) {
            ivCheck.setImageResource(R.drawable.ic_check_circle)
            ivCheck.setColorFilter(themeColors.secondaryAccent)
            tvTitle.paintFlags = tvTitle.paintFlags or Paint.STRIKE_THRU_TEXT_FLAG
            tvTitle.setTextColor(ContextCompat.getColor(this, R.color.text_tertiary))
        } else {
            ivCheck.setImageResource(R.drawable.ic_circle_outline)
            ivCheck.setColorFilter(ContextCompat.getColor(this, R.color.text_tertiary))
            tvTitle.paintFlags = tvTitle.paintFlags and Paint.STRIKE_THRU_TEXT_FLAG.inv()
            tvTitle.setTextColor(ContextCompat.getColor(this, R.color.text_primary))
        }

        rowLayout.setOnClickListener {
            val newStatus = !mission.isCompleted
            dbHelper.updateMissionStatus(mission.id, newStatus)
            loadMissions()
        }
    }

    private fun handleUnlock() {
        if (prefs.isEscapeRoomActive()) {
            Toast.makeText(this, "Escape Room lockdown is active. Stay disciplined.", Toast.LENGTH_SHORT).show()
            return
        }

        if (prefs.hasPin()) {
            if (prefs.isBiometricEnabled()) {
                authManager.authenticate()
            } else {
                showPinFallbackDialog()
            }
        } else {
            // No PIN configured yet, unlock directly
            finish()
        }
    }

    private fun showPinFallbackDialog() {
        val view = LayoutInflater.from(this).inflate(R.layout.dialog_pin_auth, null)
        val etPin = view.findViewById<EditText>(R.id.etPinInput)

        AlertDialog.Builder(this, com.google.android.material.R.style.Theme_MaterialComponents_DayNight_Dialog_Alert)
            .setTitle("Discipline Unlock")
            .setMessage("Enter your secret PIN to unlock the device.")
            .setView(view)
            .setPositiveButton("Unlock") { _, _ ->
                val entered = etPin.text.toString().trim()
                if (prefs.verifyPin(entered)) {
                    finish()
                } else {
                    Toast.makeText(this, "Incorrect PIN. Stay disciplined.", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    override fun onDestroy() {
        super.onDestroy()
        escapeRoomTimer?.cancel()
    }
}
