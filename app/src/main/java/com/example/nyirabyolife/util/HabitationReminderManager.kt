package com.example.nyirabyolife.util

import android.app.Activity
import android.view.LayoutInflater
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import com.example.nyirabyolife.R
import com.example.nyirabyolife.data.local.PreferencesManager
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.random.Random

object HabitationReminderManager {

    data class HabitationPrompt(
        val icon: String,
        val title: String,
        val scriptureRef: String,
        val scripture: String,
        val message: String
    )

    private val prompts = listOf(
        HabitationPrompt(
            "💧",
            "Temple Care & Hydration",
            "1 Corinthians 6:19",
            "“Or do you not know that your body is a temple of the Holy Spirit within you?”",
            "Pause and drink a glass of fresh water. Release the tension in your shoulders and honor God with your physical body."
        ),
        HabitationPrompt(
            "🕊️",
            "Abiding in Christ's Presence",
            "John 15:5",
            "“I am the vine; you are the branches. If you remain in me and I in you, you will bear much fruit; apart from me you can do nothing.”",
            "Take 3 deep, slow breaths. Step out of the rush and remember: you are not walking alone today. Christ lives in you."
        ),
        HabitationPrompt(
            "🧠",
            "Mind Transformation Check",
            "Romans 12:2",
            "“Do not conform to the pattern of this world, but be transformed by the renewing of your mind. Then you will be able to test and approve what God’s will is—his good, pleasing and perfect will.”",
            "Ask yourself: is your screen time renewing your mind or scattering your soul? Choose kingdom focus."
        ),
        HabitationPrompt(
            "🛡️",
            "Guard Your Heart & Eyes",
            "Proverbs 4:23",
            "“Above all else, guard your heart, for everything you do flows from it.”",
            "Guard the doorway of your eyes. “Turn my eyes away from worthless things; preserve my life according to your word.” (Psalm 119:37)"
        ),
        HabitationPrompt(
            "🌱",
            "Habit Habitation Check",
            "Galatians 6:9",
            "“Let us not become weary in doing good, for at the proper time we will reap a harvest if we do not give up.”",
            "Small, faithful obedience every single day builds unshakeable character. Stand firm in your discipline."
        )
    )

    fun checkAndShowAutomaticReminders(activity: Activity) {
        if (activity.isFinishing || activity.isDestroyed) return
        val prefs = PreferencesManager.getInstance(activity)

        val cal = Calendar.getInstance()
        val hour = cal.get(Calendar.HOUR_OF_DAY)
        val todayStr = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())

        // 1. Digital Sunset / Bedtime Approaching Check (Between 21:00 and 22:00)
        if (hour in 21..21) {
            val lastWindDown = prefs.getLastWindDownDate()
            if (lastWindDown != todayStr) {
                prefs.setLastWindDownDate(todayStr)
                showBedtimeApproachingPopup(activity)
                return
            }
        }

        // 2. Periodic Mindful Habitation Check (Every 3 hours)
        val now = System.currentTimeMillis()
        val lastReminder = prefs.getLastReminderTime()
        val intervalMillis = 3 * 60 * 60 * 1000L // 3 Hours

        if (now - lastReminder > intervalMillis) {
            prefs.setLastReminderTime(now)
            val prompt = prompts[Random.nextInt(prompts.size)]
            showPromptDialog(activity, prompt)
        }
    }

    fun showManualHabitationReminder(activity: Activity) {
        if (activity.isFinishing || activity.isDestroyed) return
        val prompt = prompts[Random.nextInt(prompts.size)]
        showPromptDialog(activity, prompt)
    }

    private fun showPromptDialog(activity: Activity, prompt: HabitationPrompt) {
        val dialog = AlertDialog.Builder(activity, com.google.android.material.R.style.Theme_MaterialComponents_DayNight_Dialog_Alert)
            .setTitle("${prompt.icon} ${prompt.title}")
            .setMessage("${prompt.scripture}\n— ${prompt.scriptureRef}\n\n${prompt.message}")
            .setPositiveButton("Amen, I Abide 🕊️", null)
            .create()

        dialog.show()
    }

    private fun showBedtimeApproachingPopup(activity: Activity) {
        AlertDialog.Builder(activity, com.google.android.material.R.style.Theme_MaterialComponents_DayNight_Dialog_Alert)
            .setTitle("🌙 Digital Sunset: BedTime Approaching")
            .setMessage("“In peace I will both lie down and sleep; for you alone, O LORD, make me dwell in safety.” (Psalm 4:8)\n\nBedTime Total Lockdown begins at 22:00. Time to wind down your work, step away from screens, and prepare for peaceful rest.")
            .setPositiveButton("Prepare for Rest 🛌", null)
            .show()
    }
}

