package com.example.nyirabyolife.data.model

import android.graphics.drawable.Drawable

data class Mission(
    val id: Long = 0,
    val title: String,
    val isCompleted: Boolean = false,
    val date: String,
    val orderIndex: Int = 0,
    val timeHour: Int = 0,
    val timeMinute: Int = 0,
    val taskMode: String = "NORMAL",
    val durationMinutes: Int = 0
) {
    val formattedTime: String
        get() = "%02d:%02d".format(timeHour, timeMinute)

    val endHour: Int
        get() = (timeHour + (timeMinute + durationMinutes) / 60) % 24

    val endMinute: Int
        get() = (timeMinute + durationMinutes) % 60

    val formattedEndTime: String
        get() = "%02d:%02d".format(endHour, endMinute)

    val formattedDuration: String
        get() {
            val h = durationMinutes / 60
            val m = durationMinutes % 60
            return when {
                h > 0 && m > 0 -> "${h}h ${m}m"
                h > 0 -> "${h}h"
                else -> "${m}m"
            }
        }

    val formattedTimeRange: String
        get() = if (durationMinutes > 0) "$formattedTime - $formattedEndTime ($formattedDuration)" else "$formattedTime • Direct Alarm"

    val modeBadgeText: String?
        get() = when (taskMode) {
            "STUDY_FOCUS" -> "Study Focus 🎯"
            "SILENT_DND" -> "Silent 🔕"
            "MEDITATION" -> "Meditation 🕊️"
            "BEDTIME" -> "BedTime 🌙"
            else -> null
        }
}

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
    val createdAt: Long = System.currentTimeMillis(),
    val taskMode: String = "NORMAL", // "NORMAL", "STUDY_FOCUS", "SILENT_DND", "MEDITATION", "BEDTIME"
    val isDaily: Boolean = false,
    val durationMinutes: Int = 0
) {
    val formattedTime: String
        get() = "%02d:%02d".format(timeHour, timeMinute)

    val endHour: Int
        get() = (timeHour + (timeMinute + durationMinutes) / 60) % 24

    val endMinute: Int
        get() = (timeMinute + durationMinutes) % 60

    val formattedEndTime: String
        get() = "%02d:%02d".format(endHour, endMinute)

    val formattedDuration: String
        get() {
            val h = durationMinutes / 60
            val m = durationMinutes % 60
            return when {
                h > 0 && m > 0 -> "${h}h ${m}m"
                h > 0 -> "${h}h"
                else -> "${m}m"
            }
        }

    val formattedTimeRange: String
        get() = if (durationMinutes > 0) "$formattedTime - $formattedEndTime ($formattedDuration)" else "$formattedTime • Direct Alarm"

    val modeBadgeText: String?
        get() = when (taskMode) {
            "STUDY_FOCUS" -> "Study Focus 🎯"
            "SILENT_DND" -> "Silent 🔕"
            "MEDITATION" -> "Meditation 🕊️"
            "BEDTIME" -> "BedTime 🌙"
            else -> null
        }
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

data class TodoItem(
    val id: Long = 0,
    val title: String,
    val isCompleted: Boolean = false,
    val category: String = "GENERAL", // "GENERAL", "MARKET", "PROJECT", "GOAL"
    val note: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val completedAt: Long = 0L,
    // Financial tracking fields (for MARKET, PROJECT, GOAL budget tracking)
    val plannedPrice: Double = 0.0,  // budget / expected price
    val actualPrice: Double = 0.0,   // real price found / spent
    val quantity: Int = 1,           // quantity / units
    val currency: String = "RWF",    // currency symbol (RWF, USD, etc.)
    // Grouping & Time horizon fields
    val groupName: String = "",      // e.g. "My Office", "Market", "Graphics"
    val targetType: String = "NONE", // "NONE", "DAY", "DAYS", "WEEK", "WEEKS", "MONTH"
    val targetValue: Int = 0,        // e.g. 3 (days), 2 (weeks)
    val deadlineTimestamp: Long = 0L // epoch millis when task is target-due
) {
    val categoryBadgeText: String
        get() = when (category.uppercase()) {
            "MARKET", "SHOPPING" -> "🛒 Market"
            "PROJECT", "GRAPHICS", "WORK" -> "🎨 Project"
            "GOAL", "ACHIEVEMENT" -> "🎯 Goal"
            else -> "📋 Todo"
        }

    val groupBadgeText: String?
        get() = if (groupName.isNotBlank()) "📁 $groupName" else null

    val timeframeBadgeText: String?
        get() {
            if (deadlineTimestamp <= 0L && targetType == "NONE") return null
            val now = System.currentTimeMillis()
            val diffMs = deadlineTimestamp - now
            val diffHours = diffMs / (1000 * 60 * 60)
            val diffDays = diffHours / 24

            return when {
                isCompleted -> "✓ Done"
                diffMs < 0 -> {
                    val overdueDays = kotlin.math.abs(diffDays).coerceAtLeast(1)
                    "⚠️ ${overdueDays}d overdue"
                }
                diffHours <= 24 -> "⏳ Today"
                diffDays == 1L -> "⏳ Tomorrow"
                diffDays < 7 -> "⏳ ${diffDays}d left"
                diffDays < 30 -> {
                    val weeks = (diffDays / 7).coerceAtLeast(1)
                    "⏳ ${weeks}w left"
                }
                else -> {
                    val months = (diffDays / 30).coerceAtLeast(1)
                    "⏳ ${months}mo left"
                }
            }
        }

    /** True if this item has any financial data entered */
    val hasFinancialData: Boolean
        get() = plannedPrice > 0.0 || actualPrice > 0.0

    /** Total planned cost (planned × qty) */
    val totalPlanned: Double get() = plannedPrice * quantity

    /** Total actual cost (actual × qty) */
    val totalActual: Double get() = if (actualPrice > 0.0) actualPrice * quantity else totalPlanned

    /** Positive = money saved, Negative = money lost/overspent */
    val priceDifference: Double get() = totalPlanned - totalActual

    /** Whether this item saved money (actual < planned, both set) */
    val hasSavings: Boolean get() = plannedPrice > 0.0 && actualPrice > 0.0 && actualPrice < plannedPrice

    /** Whether this item cost more than planned */
    val isOverBudget: Boolean get() = plannedPrice > 0.0 && actualPrice > 0.0 && actualPrice > plannedPrice
}

data class ShoppingSessionItem(
    val title: String,
    val plannedPrice: Double,
    val actualPrice: Double,
    val quantity: Int = 1,
    val note: String = ""
) {
    val totalPlanned: Double get() = plannedPrice * quantity
    val totalActual: Double get() = if (actualPrice > 0.0) actualPrice * quantity else totalPlanned
    val priceDifference: Double get() = totalPlanned - totalActual
    val hasSavings: Boolean get() = plannedPrice > 0.0 && actualPrice > 0.0 && actualPrice < plannedPrice
    val isOverBudget: Boolean get() = plannedPrice > 0.0 && actualPrice > 0.0 && actualPrice > plannedPrice
}

data class ShoppingSession(
    val id: Long = 0,
    val name: String,
    val dateLabel: String,
    val createdAt: Long = System.currentTimeMillis(),
    val totalBudget: Double,
    val totalSpent: Double,
    val netResult: Double,
    val currency: String = "RWF",
    val itemCount: Int,
    val itemsJson: String = "[]"
) {
    fun parseItems(): List<ShoppingSessionItem> {
        val list = mutableListOf<ShoppingSessionItem>()
        try {
            val arr = org.json.JSONArray(itemsJson)
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                list.add(
                    ShoppingSessionItem(
                        title = obj.optString("title", ""),
                        plannedPrice = obj.optDouble("plannedPrice", 0.0),
                        actualPrice = obj.optDouble("actualPrice", 0.0),
                        quantity = obj.optInt("quantity", 1),
                        note = obj.optString("note", "")
                    )
                )
            }
        } catch (_: Exception) {}
        return list
    }
}
