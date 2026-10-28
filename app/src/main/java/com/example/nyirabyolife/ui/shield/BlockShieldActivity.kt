package com.example.nyirabyolife.ui.shield

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.CountDownTimer
import android.provider.Settings
import android.view.LayoutInflater
import android.widget.EditText
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.example.nyirabyolife.R
import com.example.nyirabyolife.data.local.PreferencesManager
import com.example.nyirabyolife.databinding.ActivityBlockShieldBinding
import com.example.nyirabyolife.ui.home.HomeActivity
import com.example.nyirabyolife.ui.recovery.UrgeCheckInActivity
import com.example.nyirabyolife.util.ThemeHelper

class BlockShieldActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_PACKAGE_NAME = "extra_package_name"
        const val EXTRA_BLOCK_REASON = "extra_block_reason"
    }

    private lateinit var binding: ActivityBlockShieldBinding
    private lateinit var prefs: PreferencesManager
    private var blockedPkg: String = ""
    private var blockReason: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        }

        binding = ActivityBlockShieldBinding.inflate(layoutInflater)
        setContentView(binding.root)

        prefs = PreferencesManager.getInstance(this)
        blockedPkg = intent.getStringExtra(EXTRA_PACKAGE_NAME) ?: ""
        blockReason = intent.getStringExtra(EXTRA_BLOCK_REASON) ?: "DISTRACTION_RESTRICTED"

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                returnToHome()
            }
        })

        bindAppDetails()
        applyThemeColors()

        binding.btnReturnHome.setOnClickListener {
            returnToHome()
        }

        binding.btnShieldUrge.setOnClickListener {
            startActivity(Intent(this, UrgeCheckInActivity::class.java))
            finish()
        }

        binding.btnEmergencyPass.setOnClickListener {
            if (blockReason == "SETTINGS_GUARD" && prefs.hasPin()) {
                showPinUnlockSettingsDialog()
            } else {
                showMindfulDelayDialog()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        applyThemeColors()
    }

    private fun applyThemeColors() {
        val theme = ThemeHelper.getActiveThemeColors(this)
        binding.ivShieldIcon.setColorFilter(theme.primaryAccent)
        binding.tvShieldHeader.setTextColor(theme.primaryAccent)

        binding.cardBlockedAppInfo.background = ThemeHelper.createCardDrawable(this, theme, isElevated = false)
        binding.cardShieldScripture.background = ThemeHelper.createCardDrawable(this, theme, isElevated = true)

        val homePill = ThemeHelper.createPillDrawable(this, theme)
        homePill.setColor(theme.primaryAccent)
        binding.btnReturnHome.background = homePill
        binding.btnReturnHome.setTextColor(ContextCompat.getColor(this, R.color.bg_dark))

        binding.btnShieldUrge.background = ThemeHelper.createPillDrawable(this, theme)
        binding.btnShieldUrge.setTextColor(theme.secondaryAccent)
        binding.btnEmergencyPass.background = ThemeHelper.createPillDrawable(this, theme)
        binding.btnEmergencyPass.setTextColor(theme.primaryAccent)
        binding.tvShieldRef.setTextColor(theme.primaryAccent)
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        setIntent(intent)
        blockedPkg = intent?.getStringExtra(EXTRA_PACKAGE_NAME) ?: ""
        blockReason = intent?.getStringExtra(EXTRA_BLOCK_REASON) ?: "DISTRACTION_RESTRICTED"
        applyThemeColors()
        bindAppDetails()
    }

    private fun bindAppDetails() {
        val pm = packageManager
        try {
            if (blockedPkg == "com.android.settings") {
                binding.tvBlockedAppName.text = "Phone Settings"
                binding.ivBlockedAppIcon.setImageResource(R.drawable.ic_settings)
            } else {
                val appInfo = pm.getApplicationInfo(blockedPkg, 0)
                binding.tvBlockedAppName.text = pm.getApplicationLabel(appInfo)
                binding.ivBlockedAppIcon.setImageDrawable(pm.getApplicationIcon(appInfo))
            }
        } catch (_: Exception) {
            binding.tvBlockedAppName.text = blockedPkg
            binding.ivBlockedAppIcon.setImageResource(R.drawable.ic_shield)
        }

        binding.btnEmergencyPass.visibility = android.view.View.VISIBLE

        when (blockReason) {
            "BEDTIME_LOCKDOWN" -> {
                binding.tvBlockReason.text = "🌙 BedTime Total Lockdown (22:00 – 05:00)\nAll apps are asleep. Phone calls & emergency dialer are always available."
                binding.tvShieldVerse.text = "“In peace I will lie down and sleep, for you alone, LORD, make me dwell in safety.”"
                binding.tvShieldRef.text = "Psalm 4:8"
                binding.btnEmergencyPass.visibility = android.view.View.GONE
            }
            "SOCIAL_MEDIA_WINDOW_CLOSED" -> {
                binding.tvBlockReason.text = "Social & Communication Window Closed\nWhatsApp, Instagram, YouTube & all social apps are restricted before 07:00 and after 20:00."
                binding.tvShieldVerse.text = "“Turn my eyes away from worthless things; preserve my life according to your word.”"
                binding.tvShieldRef.text = "Psalm 119:37"
                binding.btnEmergencyPass.visibility = android.view.View.GONE
            }
            "USAGE_LIMIT_EXCEEDED" -> {
                val limit = prefs.getAppDailyLimit(blockedPkg)
                val used = prefs.getAppUsageMinutesToday(blockedPkg)
                if (prefs.isInstagramOrTikTok(blockedPkg)) {
                    binding.tvBlockReason.text = "30-Minute Daily Limit Reached (${used}m spent / ${limit}m budget)"
                    binding.tvShieldVerse.text = "“All things are lawful for me, but not all things are helpful. All things are lawful for me, but I will not be dominated by anything.”"
                    binding.tvShieldRef.text = "1 Corinthians 6:12"
                } else {
                    binding.tvBlockReason.text = "Daily Limit Reached (${used}m spent / ${limit}m budget)"
                    binding.tvShieldVerse.text = "“Look carefully then how you walk, not as unwise but as wise, making the best use of the time, because the days are evil.”"
                    binding.tvShieldRef.text = "Ephesians 5:15-16"
                }
                binding.btnEmergencyPass.visibility = android.view.View.GONE
            }
            "FOCUS_SESSION_ACTIVE" -> {
                val mode = prefs.getActiveFocusMode()
                val remMins = (prefs.getFocusRemainingSeconds() / 60) + 1
                binding.tvBlockReason.text = "Deep Focus in Progress: $mode (${remMins}m remaining)"
                binding.btnEmergencyPass.visibility = android.view.View.GONE
            }
            "SETTINGS_GUARD" -> {
                binding.tvBlockReason.text = "Settings Guard: Anti-Impulsive Security Barrier"
                if (prefs.hasPin()) {
                    binding.btnEmergencyPass.text = "Enter PIN to Unlock Settings →"
                } else {
                    binding.btnEmergencyPass.text = "Mindful Pause (15s delay to access)"
                }
            }
            else -> {
                binding.tvBlockReason.text = "Restricted under your NYIRABYO discipline rules."
                // Social media and WhatsApp NEVER have a mindful pause bypass
                if (blockedPkg == "com.whatsapp" || blockedPkg == "com.whatsapp.w4b" || prefs.isSocialMediaOrDistraction(blockedPkg)) {
                    binding.btnEmergencyPass.visibility = android.view.View.GONE
                } else {
                    binding.btnEmergencyPass.text = "Mindful Pause (15s delay for 2m pass)"
                }
            }
        }
    }

    private fun returnToHome() {
        val homeIntent = Intent(this, HomeActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        startActivity(homeIntent)
        finish()
    }

    private fun showPinUnlockSettingsDialog() {
        val view = LayoutInflater.from(this).inflate(R.layout.dialog_pin_auth, null)
        val etPin = view.findViewById<EditText>(R.id.etPinInput)

        AlertDialog.Builder(this, com.google.android.material.R.style.Theme_MaterialComponents_DayNight_Dialog_Alert)
            .setTitle("Unlock System Settings")
            .setMessage("Enter your Recovery PIN to access system settings for 2 minutes.")
            .setView(view)
            .setPositiveButton("Verify PIN") { _, _ ->
                val entered = etPin.text.toString().trim()
                if (prefs.verifyPin(entered)) {
                    prefs.grantTemporaryUnlock(blockedPkg, 2)
                    Toast.makeText(this, "2-Minute intentional access granted.", Toast.LENGTH_SHORT).show()
                    try {
                        startActivity(Intent(Settings.ACTION_SETTINGS))
                    } catch (_: Exception) {}
                    finish()
                } else {
                    Toast.makeText(this, "Incorrect PIN. Guarding settings.", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showMindfulDelayDialog() {
        var secondsLeft = 15
        val dialog = AlertDialog.Builder(this, com.google.android.material.R.style.Theme_MaterialComponents_DayNight_Dialog_Alert)
            .setTitle("Mindful Cooling-Off Pause")
            .setMessage("Wait 15 seconds. Breathe deeply. Are you sure you need this right now?\n\nRemaining: $secondsLeft seconds...")
            .setCancelable(false)
            .setPositiveButton("Proceed (0)") { _, _ ->
                prefs.grantTemporaryUnlock(blockedPkg, 2)
                Toast.makeText(this, "2-Minute intentional pass granted.", Toast.LENGTH_SHORT).show()
                val launchIntent = packageManager.getLaunchIntentForPackage(blockedPkg)
                    ?: if (blockedPkg == "com.android.settings") Intent(Settings.ACTION_SETTINGS) else null
                if (launchIntent != null) {
                    startActivity(launchIntent)
                }
                finish()
            }
            .setNegativeButton("Choose Discipline (Cancel)") { d, _ ->
                d.dismiss()
                returnToHome()
            }
            .create()

        dialog.show()
        val posBtn = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
        posBtn.isEnabled = false

        object : CountDownTimer(15000, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                secondsLeft = (millisUntilFinished / 1000).toInt() + 1
                dialog.setMessage("Wait 15 seconds. Breathe deeply. Are you sure you need this right now?\n\nRemaining: $secondsLeft seconds...")
                posBtn.text = "Wait ($secondsLeft)"
            }

            override fun onFinish() {
                dialog.setMessage("15 seconds completed. If this is truly necessary, proceed deliberately.")
                posBtn.isEnabled = true
                posBtn.text = "Proceed for 2 min"
            }
        }.start()
    }
}
