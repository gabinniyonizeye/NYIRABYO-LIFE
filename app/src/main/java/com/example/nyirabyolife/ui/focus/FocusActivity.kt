package com.example.nyirabyolife.ui.focus

import android.content.Context
import android.os.Build
import android.os.Bundle
import android.os.CountDownTimer
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.example.nyirabyolife.R
import com.example.nyirabyolife.data.local.NyirabyoDbHelper
import com.example.nyirabyolife.data.local.PreferencesManager
import com.example.nyirabyolife.data.model.FocusSession
import com.example.nyirabyolife.databinding.ActivityFocusBinding

class FocusActivity : AppCompatActivity() {

    private lateinit var binding: ActivityFocusBinding
    private var selectedMinutes = 25
    private var currentMode = "Coding"
    private var countDownTimer: CountDownTimer? = null
    private var isTimerRunning = false
    private var millisRemaining = 25 * 60 * 1000L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityFocusBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnBack.setOnClickListener { finish() }

        setupModeSelectors()
        setupPresets()
        updateTimerDisplay(millisRemaining)

        binding.btnToggleTimer.setOnClickListener {
            if (isTimerRunning) {
                pauseTimer()
            } else {
                startTimer()
            }
        }

        binding.btnResetTimer.setOnClickListener {
            resetTimer()
        }
    }

    private fun setupModeSelectors() {
        binding.btnModeCoding.setOnClickListener { selectMode("Coding", "BUILD MY FUTURE — CODING", R.color.indigo_focus, "“Commit your work to the Lord, and your plans will be established.” (Prov 16:3)") }
        binding.btnModeTraining.setOnClickListener { selectMode("Training", "TRAIN MY BODY — ATHLETICISM", R.color.gold_primary, "“Every athlete exercises self-control in all things.” (1 Cor 9:25)") }
        binding.btnModePrayer.setOnClickListener { selectMode("Prayer", "PRAY & SEEK HIS KINGDOM", R.color.emerald_accent, "“Seek first the kingdom of God and his righteousness.” (Matt 6:33)") }
        binding.btnModeSleep.setOnClickListener { selectMode("Sleep", "REST & RESTORATION", R.color.text_secondary, "“In peace I will both lie down and sleep, for you alone, O Lord, make me dwell in safety.” (Psalm 4:8)") }
    }

    private fun selectMode(mode: String, label: String, colorRes: Int, quote: String) {
        currentMode = mode
        binding.tvFocusModeLabel.text = label
        binding.tvFocusModeLabel.setTextColor(ContextCompat.getColor(this, colorRes))
        binding.tvFocusQuote.text = quote

        // Update mode button styles
        val buttons = listOf(binding.btnModeCoding, binding.btnModeTraining, binding.btnModePrayer, binding.btnModeSleep)
        buttons.forEach { btn ->
            val isCurrent = btn.text.toString().equals(mode, ignoreCase = true)
            btn.setTextColor(ContextCompat.getColor(this, if (isCurrent) colorRes else R.color.text_secondary))
        }
    }

    private fun setupPresets() {
        binding.preset15.setOnClickListener { setDuration(15) }
        binding.preset25.setOnClickListener { setDuration(25) }
        binding.preset45.setOnClickListener { setDuration(45) }
        binding.preset60.setOnClickListener { setDuration(60) }
    }

    private fun setDuration(mins: Int) {
        if (isTimerRunning) pauseTimer()
        selectedMinutes = mins
        millisRemaining = mins * 60 * 1000L
        updateTimerDisplay(millisRemaining)

        // Highlight selected preset
        val presets = mapOf(15 to binding.preset15, 25 to binding.preset25, 45 to binding.preset45, 60 to binding.preset60)
        presets.forEach { (m, tv) ->
            if (m == mins) {
                tv.setBackgroundResource(R.drawable.button_gold)
                tv.setTextColor(ContextCompat.getColor(this, R.color.bg_dark))
            } else {
                tv.setBackgroundResource(R.drawable.card_background_elevated)
                tv.setTextColor(ContextCompat.getColor(this, R.color.text_secondary))
            }
        }
    }

    private fun startTimer() {
        PreferencesManager.getInstance(this).setActiveFocusSession(selectedMinutes, currentMode)
        countDownTimer = object : CountDownTimer(millisRemaining, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                millisRemaining = millisUntilFinished
                updateTimerDisplay(millisRemaining)
            }

            override fun onFinish() {
                isTimerRunning = false
                PreferencesManager.getInstance(this@FocusActivity).clearActiveFocusSession()
                binding.btnToggleTimer.text = "Session Complete!"
                binding.btnToggleTimer.setBackgroundResource(R.drawable.button_emerald)
                millisRemaining = selectedMinutes * 60 * 1000L
                updateTimerDisplay(millisRemaining)

                NyirabyoDbHelper.getInstance(this@FocusActivity).addFocusSession(
                    FocusSession(
                        sessionType = currentMode,
                        durationMinutes = selectedMinutes,
                        isCompleted = true
                    )
                )

                vibrateAlert()
                Toast.makeText(this@FocusActivity, "Glorious work! Focus session finished.", Toast.LENGTH_LONG).show()
            }
        }.start()

        isTimerRunning = true
        binding.btnToggleTimer.text = "Pause Session"
        binding.btnToggleTimer.setBackgroundResource(R.drawable.button_gold)
    }

    private fun pauseTimer() {
        countDownTimer?.cancel()
        isTimerRunning = false
        PreferencesManager.getInstance(this).clearActiveFocusSession()
        binding.btnToggleTimer.text = "Resume Session"
    }

    private fun resetTimer() {
        countDownTimer?.cancel()
        isTimerRunning = false
        PreferencesManager.getInstance(this).clearActiveFocusSession()
        millisRemaining = selectedMinutes * 60 * 1000L
        updateTimerDisplay(millisRemaining)
        binding.btnToggleTimer.text = "Start Session"
        binding.btnToggleTimer.setBackgroundResource(R.drawable.button_gold)
    }

    private fun updateTimerDisplay(millis: Long) {
        val totalSec = millis / 1000
        val mins = totalSec / 60
        val secs = totalSec % 60
        binding.tvTimerCountdown.text = "%02d:%02d".format(mins, secs)
    }

    private fun vibrateAlert() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vibratorManager = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                vibratorManager?.defaultVibrator?.vibrate(
                    VibrationEffect.createWaveform(longArrayOf(0, 300, 200, 300), -1)
                )
            } else {
                @Suppress("DEPRECATION")
                val vibrator = getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vibrator?.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 300, 200, 300), -1))
                } else {
                    @Suppress("DEPRECATION")
                    vibrator?.vibrate(500)
                }
            }
        } catch (_: Exception) {}
    }

    override fun onDestroy() {
        super.onDestroy()
        countDownTimer?.cancel()
    }
}
