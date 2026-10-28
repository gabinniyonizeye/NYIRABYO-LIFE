package com.example.nyirabyolife.util

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import com.example.nyirabyolife.data.local.PreferencesManager

object ThemeHelper {

    enum class ThemeDesignStyle {
        CLASSIC_SANCTUARY,  // Holy Obsidian Gold
        GLASS_FUTURISTIC,   // Celestial Sanctuary
        ORGANIC_VINE,       // Living Vine
        MONASTIC_MINIMAL    // Royal Solitude
    }

    enum class MissionRowStyle {
        CLASSIC_CHECKBOX,   // Standard clean list with circular checks
        FLOATING_CAPSULE,   // Each task inside a floating glassmorphic pill card
        ORGANIC_BRANCH,     // Sprout / leaf bullets with living habit tracking
        MONASTIC_MINIMAL    // Quiet, distraction-free strike-through with amethyst focus dots
    }

    data class ThemeColors(
        val name: String,
        val style: ThemeDesignStyle,
        val primaryAccent: Int,
        val secondaryAccent: Int,
        val badgeBackground: Int,
        val rgb1: Triple<Int, Int, Int>,
        val rgb2: Triple<Int, Int, Int>,
        val rgb3: Triple<Int, Int, Int>,
        // Structural and Design elements
        val headerSubtitle: String,
        val clockSubtitle: String,
        val clockTextSizeSp: Float,
        val clockLetterSpacing: Float,
        val badgeText: String,
        val scriptureTextSizeSp: Float,
        val scriptureItalic: Boolean,
        val scriptureActionText: String,
        val missionsSubtitle: String,
        val missionRowStyle: MissionRowStyle
    )

    fun getActiveThemeColors(context: Context): ThemeColors {
        val theme = PreferencesManager.getInstance(context).getActiveTheme()
        return when (theme) {
            PreferencesManager.THEME_CELESTIAL_BLUE -> ThemeColors(
                name = "Celestial Sanctuary",
                style = ThemeDesignStyle.GLASS_FUTURISTIC,
                primaryAccent = Color.parseColor("#38BDF8"), // Sky Blue
                secondaryAccent = Color.parseColor("#60A5FA"),
                badgeBackground = Color.parseColor("#172554"),
                rgb1 = Triple(56, 189, 248),
                rgb2 = Triple(29, 78, 216),
                rgb3 = Triple(147, 197, 253),
                headerSubtitle = "Celestial Sanctuary • Walk in the Light ⚡",
                clockSubtitle = "CELESTIAL SANCTUARY • HEAVENLY VISION ⚡",
                clockTextSizeSp = 44f,
                clockLetterSpacing = 0.05f,
                badgeText = "CELESTIAL REVELATION • ILLUMINATE ⚡",
                scriptureTextSizeSp = 12.5f,
                scriptureItalic = false,
                scriptureActionText = "Enter Sanctuary 🕊️ →",
                missionsSubtitle = "SANCTUARY PROTOCOLS • ACTIVE HABITS ⚡",
                missionRowStyle = MissionRowStyle.FLOATING_CAPSULE
            )
            PreferencesManager.THEME_LIVING_VINE -> ThemeColors(
                name = "Living Vine",
                style = ThemeDesignStyle.ORGANIC_VINE,
                primaryAccent = Color.parseColor("#10B981"), // Holy Emerald
                secondaryAccent = Color.parseColor("#34D399"),
                badgeBackground = Color.parseColor("#132E25"),
                rgb1 = Triple(16, 185, 129),
                rgb2 = Triple(52, 211, 153),
                rgb3 = Triple(217, 119, 6),
                headerSubtitle = "Branch of the Living Vine • Bear Good Fruit 🌿",
                clockSubtitle = "LIVING BRANCH • GROWING IN CHRIST 🌿",
                clockTextSizeSp = 38f,
                clockLetterSpacing = 0.01f,
                badgeText = "THE LIVING WORD • FRUIT OF THE SPIRIT 🍃",
                scriptureTextSizeSp = 12f,
                scriptureItalic = false,
                scriptureActionText = "Nourish the Soul 🌿 →",
                missionsSubtitle = "DAILY BRANCH DISCIPLINE • LIFE IN CHRIST 🌿",
                missionRowStyle = MissionRowStyle.ORGANIC_BRANCH
            )
            PreferencesManager.THEME_ROYAL_AMETHYST -> ThemeColors(
                name = "Royal Solitude",
                style = ThemeDesignStyle.MONASTIC_MINIMAL,
                primaryAccent = Color.parseColor("#C084FC"), // Amethyst
                secondaryAccent = Color.parseColor("#F472B6"),
                badgeBackground = Color.parseColor("#2E1065"),
                rgb1 = Triple(168, 85, 247),
                rgb2 = Triple(99, 102, 241),
                rgb3 = Triple(245, 158, 11),
                headerSubtitle = "Sacred Solitude • Undivided Mind 🕊️",
                clockSubtitle = "SACRED SOLITUDE • BE STILL & KNOW 🕊️",
                clockTextSizeSp = 48f,
                clockLetterSpacing = -0.01f,
                badgeText = "MEDITATIO SACRA • SOLITUDE",
                scriptureTextSizeSp = 14f,
                scriptureItalic = true,
                scriptureActionText = "Silent Contemplation →",
                missionsSubtitle = "SOLITARY FOCUS • THE ESSENTIAL FEW 🕊️",
                missionRowStyle = MissionRowStyle.MONASTIC_MINIMAL
            )
            else -> ThemeColors(
                name = "Holy Obsidian Gold",
                style = ThemeDesignStyle.CLASSIC_SANCTUARY,
                primaryAccent = Color.parseColor("#E5B869"), // Gold
                secondaryAccent = Color.parseColor("#10B981"),
                badgeBackground = Color.parseColor("#2A2315"),
                rgb1 = Triple(212, 175, 55),
                rgb2 = Triple(58, 123, 213),
                rgb3 = Triple(0, 176, 116),
                headerSubtitle = "Holy Habitation • Live with Purpose 👑",
                clockSubtitle = "HOLY HABITATION • ABIDING IN CHRIST",
                clockTextSizeSp = 40f,
                clockLetterSpacing = 0.02f,
                badgeText = "HOLY HABITATION • BIBLE STUDY FOCUS",
                scriptureTextSizeSp = 12f,
                scriptureItalic = false,
                scriptureActionText = "Bible Study & Presence →",
                missionsSubtitle = "SYNCED WITH WEEKLY PLAN • HABIT REWIRING ⏰",
                missionRowStyle = MissionRowStyle.CLASSIC_CHECKBOX
            )
        }
    }

    /**
     * Dynamically creates a card background Drawable conforming to the theme's structural style.
     */
    fun createCardDrawable(context: Context, theme: ThemeColors, isElevated: Boolean = false): GradientDrawable {
        val density = context.resources.displayMetrics.density
        val drawable = GradientDrawable()
        drawable.shape = GradientDrawable.RECTANGLE

        when (theme.style) {
            ThemeDesignStyle.CLASSIC_SANCTUARY -> {
                val fill = if (isElevated) Color.parseColor("#221D15") else Color.parseColor("#1A150F")
                drawable.setColor(fill)
                drawable.setStroke((1.2f * density).toInt().coerceAtLeast(1), Color.parseColor("#4DE5B869"))
                drawable.cornerRadius = 18f * density
            }
            ThemeDesignStyle.GLASS_FUTURISTIC -> {
                val fill = if (isElevated) Color.parseColor("#16233B") else Color.parseColor("#10192A")
                drawable.setColor(fill)
                drawable.setStroke((1.5f * density).toInt().coerceAtLeast(2), Color.parseColor("#5938BDF8"))
                drawable.cornerRadius = 24f * density
            }
            ThemeDesignStyle.ORGANIC_VINE -> {
                val fill = if (isElevated) Color.parseColor("#10231B") else Color.parseColor("#0B1A13")
                drawable.setColor(fill)
                drawable.setStroke((1.5f * density).toInt().coerceAtLeast(2), Color.parseColor("#5910B981"))
                // Asymmetric organic leaf/seed silhouette
                val rBig = 28f * density
                val rSmall = 8f * density
                drawable.cornerRadii = floatArrayOf(rBig, rBig, rSmall, rSmall, rBig, rBig, rSmall, rSmall)
            }
            ThemeDesignStyle.MONASTIC_MINIMAL -> {
                val fill = if (isElevated) Color.parseColor("#190D26") else Color.parseColor("#13091F")
                drawable.setColor(fill)
                drawable.setStroke((1f * density).toInt().coerceAtLeast(1), Color.parseColor("#4DC084FC"))
                drawable.cornerRadius = 12f * density
            }
        }
        return drawable
    }

    /**
     * Dynamically creates a button / pill Drawable based on the theme's design style.
     */
    fun createPillDrawable(context: Context, theme: ThemeColors): GradientDrawable {
        val density = context.resources.displayMetrics.density
        val drawable = GradientDrawable()
        drawable.shape = GradientDrawable.RECTANGLE

        when (theme.style) {
            ThemeDesignStyle.CLASSIC_SANCTUARY -> {
                drawable.setColor(Color.parseColor("#262015"))
                drawable.setStroke((1f * density).toInt().coerceAtLeast(1), Color.parseColor("#66E5B869"))
                drawable.cornerRadius = 999f
            }
            ThemeDesignStyle.GLASS_FUTURISTIC -> {
                drawable.setColor(Color.parseColor("#182742"))
                drawable.setStroke((1.5f * density).toInt().coerceAtLeast(1), Color.parseColor("#6638BDF8"))
                drawable.cornerRadius = 999f
            }
            ThemeDesignStyle.ORGANIC_VINE -> {
                drawable.setColor(Color.parseColor("#12291E"))
                drawable.setStroke((1.2f * density).toInt().coerceAtLeast(1), Color.parseColor("#6610B981"))
                val r1 = 20f * density
                val r2 = 6f * density
                drawable.cornerRadii = floatArrayOf(r1, r1, r2, r2, r1, r1, r2, r2)
            }
            ThemeDesignStyle.MONASTIC_MINIMAL -> {
                drawable.setColor(Color.parseColor("#211233"))
                drawable.setStroke((1f * density).toInt().coerceAtLeast(1), Color.parseColor("#66C084FC"))
                drawable.cornerRadius = 8f * density
            }
        }
        return drawable
    }

    /**
     * Dynamically creates the bottom dock container Drawable based on theme design.
     */
    fun createDockDrawable(context: Context, theme: ThemeColors): GradientDrawable {
        val density = context.resources.displayMetrics.density
        val drawable = GradientDrawable()
        drawable.shape = GradientDrawable.RECTANGLE

        when (theme.style) {
            ThemeDesignStyle.CLASSIC_SANCTUARY -> {
                drawable.setColor(Color.parseColor("#19140D"))
                drawable.setStroke((1f * density).toInt().coerceAtLeast(1), Color.parseColor("#33E5B869"))
                drawable.cornerRadius = 24f * density
            }
            ThemeDesignStyle.GLASS_FUTURISTIC -> {
                drawable.setColor(Color.parseColor("#0E1624"))
                drawable.setStroke((1.5f * density).toInt().coerceAtLeast(2), Color.parseColor("#4D38BDF8"))
                drawable.cornerRadius = 32f * density
            }
            ThemeDesignStyle.ORGANIC_VINE -> {
                drawable.setColor(Color.parseColor("#091610"))
                drawable.setStroke((1.2f * density).toInt().coerceAtLeast(1), Color.parseColor("#3310B981"))
                val rBig = 28f * density
                val rSmall = 12f * density
                drawable.cornerRadii = floatArrayOf(rBig, rBig, rSmall, rSmall, rBig, rBig, rSmall, rSmall)
            }
            ThemeDesignStyle.MONASTIC_MINIMAL -> {
                drawable.setColor(Color.parseColor("#100717"))
                drawable.setStroke((1f * density).toInt().coerceAtLeast(1), Color.parseColor("#33C084FC"))
                drawable.cornerRadius = 14f * density
            }
        }
        return drawable
    }

    /**
     * Creates a floating capsule background for mission rows when in GLASS_FUTURISTIC theme.
     */
    fun createTaskCapsuleDrawable(context: Context, theme: ThemeColors, isCompleted: Boolean): GradientDrawable? {
        if (theme.style != ThemeDesignStyle.GLASS_FUTURISTIC) return null
        val density = context.resources.displayMetrics.density
        val drawable = GradientDrawable()
        drawable.shape = GradientDrawable.RECTANGLE
        if (isCompleted) {
            drawable.setColor(Color.parseColor("#0F1A2A"))
            drawable.setStroke((1f * density).toInt().coerceAtLeast(1), Color.parseColor("#2638BDF8"))
        } else {
            drawable.setColor(Color.parseColor("#152238"))
            drawable.setStroke((1.2f * density).toInt().coerceAtLeast(1), Color.parseColor("#4D38BDF8"))
        }
        drawable.cornerRadius = 14f * density
        return drawable
    }
}
