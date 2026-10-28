package com.example.nyirabyolife.ui.alarm

import android.app.KeyguardManager
import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.WindowManager
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.nyirabyolife.data.local.NyirabyoDbHelper
import com.example.nyirabyolife.data.local.ScriptureRepository
import com.example.nyirabyolife.databinding.ActivityTaskAlarmBinding
import com.example.nyirabyolife.util.TaskAlarmScheduler

class TaskAlarmActivity : AppCompatActivity() {

    private lateinit var binding: ActivityTaskAlarmBinding
    private var mediaPlayer: MediaPlayer? = null
    private var vibrator: Vibrator? = null

    private var taskId: Long = -1L
    private var taskTitle: String = ""
    private var taskTime: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        showOverLockScreen()
        super.onCreate(savedInstanceState)
        binding = ActivityTaskAlarmBinding.inflate(layoutInflater)
        setContentView(binding.root)

        taskId = intent.getLongExtra(TaskAlarmScheduler.EXTRA_TASK_ID, -1L)
        taskTitle = intent.getStringExtra(TaskAlarmScheduler.EXTRA_TASK_TITLE) ?: "Scheduled Weekly Task"
        taskTime = intent.getStringExtra(TaskAlarmScheduler.EXTRA_TASK_TIME) ?: ""

        setupUI()
        startAlarmSoundAndVibration()
    }

    private fun showOverLockScreen() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
            val keyguardManager = getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager
            keyguardManager?.requestDismissKeyguard(this, null)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                        WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD or
                        WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                        WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
            )
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    private fun setupUI() {
        binding.tvTaskTitle.text = taskTitle
        if (taskTime.isNotEmpty()) {
            binding.tvTaskScheduledTime.text = "Scheduled for $taskTime"
        }

        // Inspiring Christ-centered scripture anchor
        val verse = ScriptureRepository.getVerseOfTheDay()
        binding.tvScriptureMotivation.text = "“${verse.text}” — ${verse.reference}"

        binding.btnCompleteTask.setOnClickListener {
            stopAlarm()
            if (taskId != -1L) {
                NyirabyoDbHelper.getInstance(this).updateWeeklyTaskStatus(taskId, true)
                TaskAlarmScheduler.cancelTaskAlarm(this, taskId)
            }
            Toast.makeText(this, "Praise God! Task marked completed.", Toast.LENGTH_SHORT).show()
            finish()
        }

        binding.btnSnoozeAlarm.setOnClickListener {
            stopAlarm()
            TaskAlarmScheduler.scheduleSnooze(this, taskId, taskTitle, taskTime, 10)
            Toast.makeText(this, "Alarm snoozed for 10 minutes.", Toast.LENGTH_SHORT).show()
            finish()
        }

        binding.btnDismissAlarm.setOnClickListener {
            stopAlarm()
            finish()
        }
    }

    private fun startAlarmSoundAndVibration() {
        // 1. Play Loud Alarm Ringtone in a continuous loop
        try {
            val alertUri: Uri = RingtoneManager.getActualDefaultRingtoneUri(this, RingtoneManager.TYPE_ALARM)
                ?: RingtoneManager.getActualDefaultRingtoneUri(this, RingtoneManager.TYPE_RINGTONE)
                ?: android.provider.Settings.System.DEFAULT_ALARM_ALERT_URI

            mediaPlayer = MediaPlayer().apply {
                setDataSource(applicationContext, alertUri)
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .setLegacyStreamType(AudioManager.STREAM_ALARM)
                        .build()
                )
                isLooping = true
                prepare()
                start()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // 2. Continuous Strong Vibration
        try {
            vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vm = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                vm?.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            }

            val pattern = longArrayOf(0, 1000, 500, 1000, 500)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator?.vibrate(VibrationEffect.createWaveform(pattern, 0))
            } else {
                @Suppress("DEPRECATION")
                vibrator?.vibrate(pattern, 0)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun stopAlarm() {
        try {
            mediaPlayer?.stop()
            mediaPlayer?.release()
            mediaPlayer = null
        } catch (_: Exception) {}

        try {
            vibrator?.cancel()
            vibrator = null
        } catch (_: Exception) {}
    }

    override fun onDestroy() {
        super.onDestroy()
        stopAlarm()
    }
}
