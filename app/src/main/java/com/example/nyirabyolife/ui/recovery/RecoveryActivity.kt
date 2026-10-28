package com.example.nyirabyolife.ui.recovery

import android.content.Intent
import android.os.Bundle
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.example.nyirabyolife.R
import com.example.nyirabyolife.data.local.NyirabyoDbHelper
import com.example.nyirabyolife.data.local.PreferencesManager
import com.example.nyirabyolife.databinding.ActivityRecoveryBinding
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class RecoveryActivity : AppCompatActivity() {

    private lateinit var binding: ActivityRecoveryBinding
    private lateinit var prefs: PreferencesManager
    private lateinit var dbHelper: NyirabyoDbHelper

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityRecoveryBinding.inflate(layoutInflater)
        setContentView(binding.root)

        prefs = PreferencesManager.getInstance(this)
        dbHelper = NyirabyoDbHelper.getInstance(this)

        binding.btnBack.setOnClickListener { finish() }

        applyThemeColors()
        updateStreakUI()

        binding.cardUrgeCheckIn.setOnClickListener {
            val intent = Intent(this, UrgeCheckInActivity::class.java)
            startActivity(intent)
        }

        binding.btnResetStreak.setOnClickListener {
            promptGracefulReset()
        }

        loadUrgeLogs()
    }

    private fun applyThemeColors() {
        val theme = com.example.nyirabyolife.util.ThemeHelper.getActiveThemeColors(this)
        binding.btnBack.background = com.example.nyirabyolife.util.ThemeHelper.createPillDrawable(this, theme)
        binding.cardCleanStreak.background = com.example.nyirabyolife.util.ThemeHelper.createCardDrawable(this, theme, isElevated = true)
        binding.cardUrgeCheckIn.background = com.example.nyirabyolife.util.ThemeHelper.createCardDrawable(this, theme, isElevated = false)
        binding.btnResetStreak.background = com.example.nyirabyolife.util.ThemeHelper.createPillDrawable(this, theme)
        binding.tvCleanDaysCount.setTextColor(theme.primaryAccent)
    }

    override fun onResume() {
        super.onResume()
        updateStreakUI()
        loadUrgeLogs()
    }

    private fun updateStreakUI() {
        val days = prefs.getCleanDays()
        binding.tvCleanDaysCount.text = days.toString()
        binding.tvCleanDaysLabel.text = if (days == 1) "DAY OF VICTORY" else "DAYS OF VICTORY"
    }

    private fun promptGracefulReset() {
        AlertDialog.Builder(this, com.google.android.material.R.style.Theme_MaterialComponents_DayNight_Dialog_Alert)
            .setTitle("Grace & Fresh Beginning")
            .setMessage("“For though the righteous falls seven times, he rises again.” (Proverbs 24:16)\n\nA setback does not define you. Your identity is secure in Christ. Do you wish to restart your clean time now?")
            .setPositiveButton("Reset with Grace") { _, _ ->
                prefs.resetCleanStreak()
                updateStreakUI()
                Toast.makeText(this, "A new chapter begins today. Stand strong in the Lord.", Toast.LENGTH_LONG).show()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun loadUrgeLogs() {
        binding.layoutUrgeLogs.removeAllViews()
        val logs = dbHelper.getUrgeLogs()

        if (logs.isEmpty()) {
            val tvEmpty = TextView(this).apply {
                text = "No urge emergencies recorded. Stand steadfast!"
                setTextColor(ContextCompat.getColor(context, R.color.text_tertiary))
                textSize = 13f
            }
            binding.layoutUrgeLogs.addView(tvEmpty)
            return
        }

        val dateFormat = SimpleDateFormat("MMM d, HH:mm", Locale.getDefault())
        logs.take(10).forEach { log ->
            val dateStr = dateFormat.format(Date(log.timestamp))
            val logView = TextView(this).apply {
                text = "✓ $dateStr • Urge Level ${log.intensity}/5 • ${if (log.triggerContext.isNotBlank()) log.triggerContext else "Overcome in Faith"}"
                setTextColor(ContextCompat.getColor(context, R.color.text_secondary))
                textSize = 13f
                setPadding(0, 8, 0, 8)
            }
            binding.layoutUrgeLogs.addView(logView)
        }
    }
}
