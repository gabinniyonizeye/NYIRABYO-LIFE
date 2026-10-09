package com.example.nyirabyolife.ui.recovery

import android.content.Context
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.example.nyirabyolife.R
import com.example.nyirabyolife.data.local.NyirabyoDbHelper
import com.example.nyirabyolife.data.model.UrgeLog
import com.example.nyirabyolife.databinding.ActivityUrgeCheckinBinding

class UrgeCheckInActivity : AppCompatActivity() {

    private lateinit var binding: ActivityUrgeCheckinBinding
    private val handler = Handler(Looper.getMainLooper())
    private var breathPhase = 0 // 0: Inhale, 1: Hold, 2: Exhale, 3: Hold
    private var breathCount = 4
    private var isBreathingRunning = true

    private val breathRunnable = object : Runnable {
        override fun run() {
            if (!isBreathingRunning) return

            breathCount--
            if (breathCount <= 0) {
                breathCount = 4
                breathPhase = (breathPhase + 1) % 4
            }

            updateBreathUI()
            handler.postDelayed(this, 1000)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityUrgeCheckinBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnBack.setOnClickListener { finish() }

        startBreathing()

        binding.btnLogVictory.setOnClickListener {
            saveVictoryAndExit()
        }
    }

    private fun startBreathing() {
        updateBreathUI()
        handler.postDelayed(breathRunnable, 1000)
    }

    private fun updateBreathUI() {
        binding.tvBreathSeconds.text = breathCount.toString()
        when (breathPhase) {
            0 -> {
                binding.tvBreathInstruction.text = "INHALE DEEPLY (4s)"
                binding.tvBreathInstruction.setTextColor(ContextCompat.getColor(this, R.color.emerald_accent))
            }
            1 -> {
                binding.tvBreathInstruction.text = "HOLD AIR (4s)"
                binding.tvBreathInstruction.setTextColor(ContextCompat.getColor(this, R.color.gold_primary))
            }
            2 -> {
                binding.tvBreathInstruction.text = "EXHALE SLOWLY (4s)"
                binding.tvBreathInstruction.setTextColor(ContextCompat.getColor(this, R.color.indigo_focus))
            }
            3 -> {
                binding.tvBreathInstruction.text = "HOLD & PRAY (4s)"
                binding.tvBreathInstruction.setTextColor(ContextCompat.getColor(this, R.color.text_primary))
            }
        }
    }

    private fun saveVictoryAndExit() {
        val intensity = when (binding.rgIntensity.checkedRadioButtonId) {
            R.id.rb1 -> 1
            R.id.rb2 -> 2
            R.id.rb3 -> 3
            R.id.rb4 -> 4
            R.id.rb5 -> 5
            else -> 3
        }
        val trigger = binding.etTriggerContext.text.toString().trim()
        val thoughts = binding.etThoughts.text.toString().trim()

        val log = UrgeLog(
            intensity = intensity,
            triggerContext = trigger,
            thoughts = thoughts,
            usedResetPlan = true
        )

        NyirabyoDbHelper.getInstance(this).logUrge(log)

        vibrateFeedback()
        Toast.makeText(this, "Victory recorded! God is faithful: You overcame the urge.", Toast.LENGTH_LONG).show()
        finish()
    }

    private fun vibrateFeedback() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vibratorManager = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                vibratorManager?.defaultVibrator?.vibrate(
                    VibrationEffect.createOneShot(150, VibrationEffect.DEFAULT_AMPLITUDE)
                )
            } else {
                @Suppress("DEPRECATION")
                val vibrator = getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vibrator?.vibrate(VibrationEffect.createOneShot(150, VibrationEffect.DEFAULT_AMPLITUDE))
                } else {
                    @Suppress("DEPRECATION")
                    vibrator?.vibrate(150)
                }
            }
        } catch (_: Exception) {}
    }

    override fun onDestroy() {
        super.onDestroy()
        isBreathingRunning = false
        handler.removeCallbacks(breathRunnable)
    }
}
