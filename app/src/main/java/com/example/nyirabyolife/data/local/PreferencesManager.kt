package com.example.nyirabyolife.data.local

import android.content.Context
import android.content.SharedPreferences
import java.security.MessageDigest
import java.util.Calendar

class PreferencesManager(context: Context) {

    private val appContext: Context = context.applicationContext
    private val prefs: SharedPreferences =
        context.getSharedPreferences("nyirabyo_prefs", Context.MODE_PRIVATE)

    companion object {
        private const val KEY_PIN_HASH = "key_pin_hash"
        private const val KEY_BIOMETRIC_ENABLED = "key_biometric_enabled"
        private const val KEY_CLEAN_START_TIME = "key_clean_start_time"
        private const val KEY_WHATSAPP_START_HOUR = "key_wa_start_h"
        private const val KEY_WHATSAPP_START_MIN = "key_wa_start_m"
        private const val KEY_WHATSAPP_END_HOUR = "key_wa_end_h"
        private const val KEY_WHATSAPP_END_MIN = "key_wa_end_m"
        private const val KEY_PINNED_APPS = "key_pinned_apps"
        private const val KEY_RESTRICTED_APPS = "key_restricted_apps"
        private const val KEY_BLOCKER_ENABLED = "key_blocker_enabled"
        private const val KEY_SETTINGS_PROTECTION_ENABLED = "key_settings_protection_enabled"
        private const val KEY_ACTIVE_FOCUS_UNTIL = "key_active_focus_until"
        private const val KEY_ACTIVE_FOCUS_MODE = "key_active_focus_mode"
        private const val KEY_LOCK_SCREEN_ENABLED = "key_lock_screen_enabled"
        private const val KEY_SETTINGS_PIN_LOCK = "key_settings_pin_lock"
        private const val KEY_TEMP_UNLOCK_PKG = "key_temp_unlock_pkg"
        private const val KEY_TEMP_UNLOCK_UNTIL = "key_temp_unlock_until"
        private const val KEY_APP_USAGE_PREFIX = "usage_sec_"
        private const val KEY_APP_LIMIT_PREFIX = "limit_min_"
        private const val KEY_USAGE_TRACK_DATE = "usage_track_date"
        private const val KEY_ESCAPE_ROOM_UNTIL = "key_escape_room_until"
        private const val KEY_ACTIVE_THEME = "key_active_theme"
        private const val KEY_LAST_REMINDER_TIME = "key_last_reminder_time"
        private const val KEY_LAST_WINDDOWN_DATE = "key_last_winddown_date"

        const val THEME_HOLY_GOLD = "GOLD_OBSIDIAN"
        const val THEME_CELESTIAL_BLUE = "CELESTIAL_BLUE"
        const val THEME_LIVING_VINE = "LIVING_VINE"
        const val THEME_ROYAL_AMETHYST = "ROYAL_AMETHYST"

        val INSTAGRAM_PACKAGES = setOf(
            "com.instagram.android",
            "com.instagram.lite"
        )
        val TIKTOK_PACKAGES = setOf(
            "com.zhiliaoapp.musically",
            "com.ss.android.ugc.trill",
            "com.zhiliaoapp.musically.go"
        )
        val SOCIAL_RESTRICTED_PACKAGES = INSTAGRAM_PACKAGES + TIKTOK_PACKAGES

        @Volatile
        private var instance: PreferencesManager? = null

        fun getInstance(context: Context): PreferencesManager {
            return instance ?: synchronized(this) {
                instance ?: PreferencesManager(context.applicationContext).also { instance = it }
            }
        }
    }

    // --- PIN & SECURITY ---
    fun hasPin(): Boolean {
        return prefs.getString(KEY_PIN_HASH, null) != null
    }

    fun setPin(pin: String) {
        val hash = hashPin(pin)
        prefs.edit().putString(KEY_PIN_HASH, hash).apply()
    }

    fun verifyPin(pin: String): Boolean {
        val storedHash = prefs.getString(KEY_PIN_HASH, null) ?: return false
        return storedHash == hashPin(pin)
    }

    private fun hashPin(pin: String): String {
        val md = MessageDigest.getInstance("SHA-256")
        val bytes = md.digest(pin.toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
    }

    fun isBiometricEnabled(): Boolean {
        return prefs.getBoolean(KEY_BIOMETRIC_ENABLED, true)
    }

    fun setBiometricEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_BIOMETRIC_ENABLED, enabled).apply()
    }

    // --- RECOVERY / CLEAN STREAK ---
    fun getCleanStartTime(): Long {
        var start = prefs.getLong(KEY_CLEAN_START_TIME, 0L)
        if (start == 0L) {
            start = System.currentTimeMillis()
            prefs.edit().putLong(KEY_CLEAN_START_TIME, start).apply()
        }
        return start
    }

    fun resetCleanStreak() {
        prefs.edit().putLong(KEY_CLEAN_START_TIME, System.currentTimeMillis()).apply()
    }

    fun getCleanDays(): Int {
        val start = getCleanStartTime()
        val diffMillis = System.currentTimeMillis() - start
        if (diffMillis < 0) return 0
        return (diffMillis / (1000 * 60 * 60 * 24)).toInt()
    }

    // --- INSTAGRAM & TIKTOK SOCIAL RESTRICTION HELPERS ---
    fun isInstagram(packageName: String): Boolean {
        return INSTAGRAM_PACKAGES.contains(packageName)
    }

    fun isTikTok(packageName: String): Boolean {
        return TIKTOK_PACKAGES.contains(packageName)
    }

    fun isInstagramOrTikTok(packageName: String): Boolean {
        return SOCIAL_RESTRICTED_PACKAGES.contains(packageName)
    }

    // --- PINNED APPS ---
    fun getPinnedApps(): Set<String> {
        return prefs.getStringSet(KEY_PINNED_APPS, emptySet()) ?: emptySet()
    }

    fun togglePinnedApp(packageName: String) {
        val current = getPinnedApps().toMutableSet()
        if (current.contains(packageName)) {
            current.remove(packageName)
        } else {
            current.add(packageName)
        }
        prefs.edit().putStringSet(KEY_PINNED_APPS, current).apply()
    }

    // --- RESTRICTED & BLOCKED APPS ---
    fun isBlockerEnabled(): Boolean {
        return prefs.getBoolean(KEY_BLOCKER_ENABLED, true)
    }

    fun setBlockerEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_BLOCKER_ENABLED, enabled).apply()
    }

    fun isSettingsProtectionEnabled(): Boolean {
        return prefs.getBoolean(KEY_SETTINGS_PROTECTION_ENABLED, true)
    }

    fun setSettingsProtectionEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_SETTINGS_PROTECTION_ENABLED, enabled).apply()
    }

    fun isSettingsPinLockRequired(): Boolean {
        return prefs.getBoolean(KEY_SETTINGS_PIN_LOCK, true)
    }

    fun setSettingsPinLockRequired(required: Boolean) {
        prefs.edit().putBoolean(KEY_SETTINGS_PIN_LOCK, required).apply()
    }

    fun getRestrictedApps(): Set<String> {
        return prefs.getStringSet(KEY_RESTRICTED_APPS, emptySet()) ?: emptySet()
    }

    fun toggleRestrictedApp(packageName: String) {
        val current = getRestrictedApps().toMutableSet()
        if (current.contains(packageName)) {
            current.remove(packageName)
        } else {
            current.add(packageName)
        }
        prefs.edit().putStringSet(KEY_RESTRICTED_APPS, current).apply()
    }

    fun setRestrictedApps(apps: Set<String>) {
        prefs.edit().putStringSet(KEY_RESTRICTED_APPS, apps).apply()
    }

    // --- USAGE TIMERS & DAILY APP LIMITS ---
    private fun getTodayUsageKey(): String {
        val cal = Calendar.getInstance()
        return "%04d%02d%02d".format(
            cal.get(Calendar.YEAR),
            cal.get(Calendar.MONTH) + 1,
            cal.get(Calendar.DAY_OF_MONTH)
        )
    }

    private fun checkAndResetDailyUsage() {
        val today = getTodayUsageKey()
        val lastDate = prefs.getString(KEY_USAGE_TRACK_DATE, "")
        if (lastDate != today) {
            // New day! Clear previous day's tracked seconds
            val editor = prefs.edit()
            prefs.all.keys.filter { it.startsWith(KEY_APP_USAGE_PREFIX) }.forEach { key ->
                editor.remove(key)
            }
            editor.putString(KEY_USAGE_TRACK_DATE, today)
            editor.apply()
        }
    }

    fun setAppDailyLimit(packageName: String, limitMinutes: Int) {
        if (limitMinutes <= 0) {
            prefs.edit().remove(KEY_APP_LIMIT_PREFIX + packageName).apply()
        } else {
            prefs.edit().putInt(KEY_APP_LIMIT_PREFIX + packageName, limitMinutes).apply()
        }
    }

    fun getAppDailyLimit(packageName: String): Int {
        val defaultLimit = if (isInstagramOrTikTok(packageName)) 30 else 0
        return prefs.getInt(KEY_APP_LIMIT_PREFIX + packageName, defaultLimit)
    }

    fun getAppUsageSecondsToday(packageName: String): Long {
        checkAndResetDailyUsage()
        return prefs.getLong(KEY_APP_USAGE_PREFIX + packageName, 0L)
    }

    fun getAppUsageMinutesToday(packageName: String): Int {
        val internalMinutes = (getAppUsageSecondsToday(packageName) / 60).toInt()
        val sysMinutes = com.example.nyirabyolife.util.UsageStatsHelper.getTodayForegroundMinutes(appContext, packageName)
        return maxOf(internalMinutes, sysMinutes)
    }

    fun addAppUsageSeconds(packageName: String, seconds: Long) {
        checkAndResetDailyUsage()
        val current = prefs.getLong(KEY_APP_USAGE_PREFIX + packageName, 0L)
        prefs.edit().putLong(KEY_APP_USAGE_PREFIX + packageName, current + seconds).apply()
    }

    fun isAppUsageLimitExceeded(packageName: String): Boolean {
        val limitMinutes = getAppDailyLimit(packageName)
        if (limitMinutes <= 0) return false
        val usedMinutes = getAppUsageMinutesToday(packageName)
        return usedMinutes >= limitMinutes
    }

    fun getRemainingMinutesToday(packageName: String): Int {
        val limitMinutes = getAppDailyLimit(packageName)
        if (limitMinutes <= 0) return -1
        val usedMinutes = getAppUsageMinutesToday(packageName)
        return maxOf(0, limitMinutes - usedMinutes)
    }

    // --- FOCUS MODE ACTIVE BLOCKING ---
    fun setActiveFocusSession(durationMinutes: Int, mode: String) {
        val until = System.currentTimeMillis() + (durationMinutes * 60 * 1000L)
        prefs.edit()
            .putLong(KEY_ACTIVE_FOCUS_UNTIL, until)
            .putString(KEY_ACTIVE_FOCUS_MODE, mode)
            .apply()
    }

    fun clearActiveFocusSession() {
        prefs.edit()
            .remove(KEY_ACTIVE_FOCUS_UNTIL)
            .remove(KEY_ACTIVE_FOCUS_MODE)
            .apply()
    }

    fun isFocusSessionActive(): Boolean {
        val until = prefs.getLong(KEY_ACTIVE_FOCUS_UNTIL, 0L)
        return until > System.currentTimeMillis()
    }

    fun getActiveFocusMode(): String {
        return prefs.getString(KEY_ACTIVE_FOCUS_MODE, "Deep Focus") ?: "Deep Focus"
    }

    fun getFocusRemainingSeconds(): Int {
        val until = prefs.getLong(KEY_ACTIVE_FOCUS_UNTIL, 0L)
        val remaining = (until - System.currentTimeMillis()) / 1000
        return if (remaining > 0) remaining.toInt() else 0
    }

    // --- TEMPORARY UNLOCK (e.g. 2 minutes intentional access) ---
    fun grantTemporaryUnlock(packageName: String, minutes: Int = 2) {
        val until = System.currentTimeMillis() + (minutes * 60 * 1000L)
        prefs.edit()
            .putString(KEY_TEMP_UNLOCK_PKG, packageName)
            .putLong(KEY_TEMP_UNLOCK_UNTIL, until)
            .apply()
    }

    fun isPackageTemporarilyUnlocked(packageName: String): Boolean {
        val pkg = prefs.getString(KEY_TEMP_UNLOCK_PKG, null) ?: return false
        val until = prefs.getLong(KEY_TEMP_UNLOCK_UNTIL, 0L)
        return pkg == packageName && until > System.currentTimeMillis()
    }

    // --- BEDTIME & SOCIAL MEDIA WINDOWS ---
    fun isBedtimeLockdownActive(): Boolean {
        val cal = Calendar.getInstance()
        val hour = cal.get(Calendar.HOUR_OF_DAY)
        // 22:00 (10 PM) until 05:00 (5 AM)
        return hour >= 22 || hour < 5
    }

    fun isSocialMediaWindowOpen(): Boolean {
        val cal = Calendar.getInstance()
        val hour = cal.get(Calendar.HOUR_OF_DAY)
        // Window open from 07:00 up to 20:00 (i.e. hour in 7..19)
        return hour in 7..19
    }

    // --- CORE LOGIC: IS A PACKAGE CURRENTLY BLOCKED? ---
    fun isPackageBlocked(pkg: String, myPackageName: String): Boolean {
        // Never block launcher itself
        if (pkg == myPackageName) return false

        // Essential emergency & system apps: NEVER block under any restriction
        val essentialAllowed = listOf(
            // Phone / calls
            "com.google.android.dialer",
            "com.android.dialer",
            "com.android.phone",
            "com.android.incallui",
            "com.google.android.incallui",
            // Contacts
            "com.google.android.contacts",
            "com.android.contacts",
            // Clock / alarm
            "com.google.android.deskclock",
            "com.android.deskclock",
            // System Settings — always accessible (guarded by PIN, not hard-blocked)
            "com.android.settings",
            // Messages / SMS — emergency communication
            "com.google.android.apps.messaging",
            "com.android.mms",
            // Google Play Store — app updates
            "com.android.vending",
            // Scripture & Bible: always allowed, never blocked
            "com.bibiliya.ntagatifu"
        )
        if (essentialAllowed.contains(pkg)) return false

        // Also never block any core Android system package
        if (pkg == "android" || pkg.startsWith("com.android.internal") ||
            pkg == "com.android.systemui" || pkg == "com.google.android.gms") return false

        // 1. BedTime Total Lockdown (22:00 - 05:00): blocks social/distraction apps only.
        //    System apps, Settings, Phone, Messages are exempt (handled above).
        if (isBedtimeLockdownActive()) {
            return true
        }

        // If temporarily unlocked intentionally, allow it (outside bedtime)
        if (isPackageTemporarilyUnlocked(pkg)) return false

        // 2. Social Media Window — WhatsApp is now treated same as social media.
        //    Blocked before 07:00 and after 20:00 alongside all other social apps.
        if (isSocialMediaOrDistraction(pkg)) {
            if (!isSocialMediaWindowOpen()) {
                return true
            }
        }

        // 4. Daily Usage Limit Exceeded (e.g. 30-min budget for Instagram/TikTok)
        if (isAppUsageLimitExceeded(pkg)) {
            return true
        }

        // 5. Active Focus Session — blocks all social/distraction apps (WhatsApp now included)
        if (isFocusSessionActive()) {
            val restricted = getRestrictedApps()
            if (restricted.contains(pkg) || isSocialMediaOrDistraction(pkg)) {
                return true
            }
        }

        // 6. Block Restricted Apps
        if (isBlockerEnabled()) {
            val restricted = getRestrictedApps()
            if (restricted.contains(pkg) && !isInstagramOrTikTok(pkg)) {
                return true
            }
        }

        return false
    }

    fun getBlockReason(pkg: String): String {
        return when {
            isBedtimeLockdownActive() -> "BEDTIME_LOCKDOWN"
            isSocialMediaOrDistraction(pkg) && !isSocialMediaWindowOpen() -> "SOCIAL_MEDIA_WINDOW_CLOSED"
            isAppUsageLimitExceeded(pkg) -> "USAGE_LIMIT_EXCEEDED"
            isFocusSessionActive() -> "FOCUS_SESSION_ACTIVE"
            pkg == "com.android.settings" -> "SETTINGS_GUARD"
            else -> "DISTRACTION_RESTRICTED"
        }
    }

    // --- DISCIPLINE LOCK SCREEN ---
    fun isLockScreenEnabled(): Boolean {
        return prefs.getBoolean(KEY_LOCK_SCREEN_ENABLED, true)
    }

    fun setLockScreenEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_LOCK_SCREEN_ENABLED, enabled).apply()
    }

    // --- ESCAPE ROOM: 1-HOUR STRICT TOTAL LOCKDOWN ---
    fun startEscapeRoomLockdown(durationMinutes: Int = 60) {
        val until = System.currentTimeMillis() + (durationMinutes * 60 * 1000L)
        prefs.edit().putLong(KEY_ESCAPE_ROOM_UNTIL, until).apply()
    }

    fun isEscapeRoomActive(): Boolean {
        val until = prefs.getLong(KEY_ESCAPE_ROOM_UNTIL, 0L)
        return until > System.currentTimeMillis()
    }

    fun getEscapeRoomRemainingSeconds(): Long {
        val until = prefs.getLong(KEY_ESCAPE_ROOM_UNTIL, 0L)
        val diff = (until - System.currentTimeMillis()) / 1000
        return if (diff > 0) diff else 0L
    }

    fun clearEscapeRoomLockdown() {
        prefs.edit().remove(KEY_ESCAPE_ROOM_UNTIL).apply()
    }

    // Identifies social media and high-distraction apps
    fun isSocialMediaOrDistraction(pkg: String): Boolean {
        val knownSocials = setOf(
            // Messaging / social (treated same as social media — no special window)
            "com.whatsapp",
            "com.whatsapp.w4b",
            "com.instagram.android",
            "com.facebook.katana",
            "com.facebook.orca",
            "com.twitter.android",
            "com.zhiliaoapp.musically", // TikTok
            "com.ss.android.ugc.trill",
            "com.snapchat.android",
            "org.telegram.messenger",
            "com.reddit.frontpage",
            "com.google.android.youtube",
            "tv.twitch.android.app",
            "com.pinterest",
            "com.netflix.mediaclient"
        )
        if (knownSocials.contains(pkg)) return true
        if (getRestrictedApps().contains(pkg)) return true
        return false
    }

    // --- THEMES ---
    fun getActiveTheme(): String {
        return prefs.getString(KEY_ACTIVE_THEME, THEME_HOLY_GOLD) ?: THEME_HOLY_GOLD
    }

    fun setActiveTheme(theme: String) {
        prefs.edit().putString(KEY_ACTIVE_THEME, theme).apply()
    }

    // --- MINDFUL HABITATION REMINDERS ---
    fun getLastReminderTime(): Long {
        return prefs.getLong(KEY_LAST_REMINDER_TIME, 0L)
    }

    fun setLastReminderTime(time: Long) {
        prefs.edit().putLong(KEY_LAST_REMINDER_TIME, time).apply()
    }

    fun getLastWindDownDate(): String {
        return prefs.getString(KEY_LAST_WINDDOWN_DATE, "") ?: ""
    }

    fun setLastWindDownDate(date: String) {
        prefs.edit().putString(KEY_LAST_WINDDOWN_DATE, date).apply()
    }

    fun clearAllPreferences() {
        prefs.edit().clear().apply()
    }
}
