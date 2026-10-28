package com.example.nyirabyolife.data.model

import android.graphics.drawable.Drawable

data class Mission(
    val id: Long = 0,
    val title: String,
    val isCompleted: Boolean = false,
    val date: String,
    val orderIndex: Int = 0
)

data class ScriptureVerse(
    val id: Int,
    val text: String,
    val reference: String,
    val theme: String,
    val reflectionPrompt: String
)

data class WeeklyPlanTask(
    val id: Long = 0,
    val title: String,
    val dayOfWeek: Int, // Calendar.SUNDAY (1) through Calendar.SATURDAY (7)
    val timeHour: Int, // 0..23
    val timeMinute: Int, // 0..59
    val isAlarmEnabled: Boolean = true,
    val isCompleted: Boolean = false,
    val weekOfYear: Int,
    val year: Int,
    val createdAt: Long = System.currentTimeMillis()
) {
    val formattedTime: String
        get() = "%02d:%02d".format(timeHour, timeMinute)
}

data class ChristRevelation(
    val id: Int,
    val title: String,
    val reference: String,
    val scripture: String,
    val revelation: String,
    val prayer: String
)


data class PrayerItem(
    val id: Long = 0,
    val title: String,
    val isAnswered: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
)

data class GratitudeEntry(
    val id: Long = 0,
    val text: String,
    val date: String,
    val timestamp: Long = System.currentTimeMillis()
)

data class UrgeLog(
    val id: Long = 0,
    val timestamp: Long = System.currentTimeMillis(),
    val intensity: Int = 3, // 1 to 5
    val triggerContext: String = "",
    val thoughts: String = "",
    val usedResetPlan: Boolean = true
)

data class FocusSession(
    val id: Long = 0,
    val sessionType: String, // STUDY, PRAYER, TRAINING, SLEEP
    val durationMinutes: Int,
    val timestamp: Long = System.currentTimeMillis(),
    val isCompleted: Boolean = true
)

data class AppItem(
    val label: String,
    val packageName: String,
    val className: String,
    var icon: Drawable? = null,
    var isFavorite: Boolean = false
)
