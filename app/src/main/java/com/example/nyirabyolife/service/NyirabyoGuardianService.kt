package com.example.nyirabyolife.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.accessibility.AccessibilityEvent
import com.example.nyirabyolife.data.local.PreferencesManager
import com.example.nyirabyolife.receiver.ScreenLockReceiver
import com.example.nyirabyolife.ui.home.HomeActivity
import com.example.nyirabyolife.ui.lock.DisciplineLockActivity
import com.example.nyirabyolife.ui.shield.BlockShieldActivity

class NyirabyoGuardianService : AccessibilityService() {

    private lateinit var prefs: PreferencesManager
    private var screenLockReceiver: ScreenLockReceiver? = null
    private var currentForegroundPackage: String? = null
    private var currentForegroundActivity: String? = null
    private val handler = Handler(Looper.getMainLooper())
    private val usageCheckIntervalMs = 2000L // Heartbeat every 2 seconds

    // Debounce: prevent rapid re-triggering of BlockShieldActivity for the same package
    private var lastShieldPkg: String? = null
    private var lastShieldTimeMs: Long = 0L
    private val shieldDebounceMs = 1800L  // Don't re-show shield for same pkg within 1.8 s

    // Debounce: prevent rapid re-triggering of Escape Room lock screen
    private var lastEscapeRoomLockTimeMs: Long = 0L

    private val usageTrackerRunnable = object : Runnable {
        override fun run() {
            val pkg = currentForegroundPackage
            val myPkg = applicationContext.packageName

            val powerManager = getSystemService(Context.POWER_SERVICE) as? android.os.PowerManager
            val isScreenOn = powerManager?.isInteractive ?: true
            if (!isScreenOn) {
                // Device screen is OFF / sleeping. Never run watchdog or wake device!
                handler.postDelayed(this, usageCheckIntervalMs)
                return
            }

            // ISOLATE NYIRABYO LIFE & SYSTEM ESSENTIALS:
            // Never block, kill, or shield our own launcher, phone dialers, keyboards, or alarm clock
            if (pkg == null || pkg == myPkg || isSystemPhoneDialer(pkg) || isKeyboardOrIme(pkg) || isDeskClock(pkg) || isSystemUiOrFramework(pkg)) {
                handler.postDelayed(this, usageCheckIntervalMs)
                return
            }

            // If Escape Room is active — heartbeat watchdog.
            // Force back to lock screen if ANY third-party or non-dialer app is active.
            if (prefs.isEscapeRoomActive()) {
                triggerEscapeRoomLockdown()
            } else if (prefs.isBedtimeLockdownActive()) {
                // Heartbeat watchdog for BedTime Total Lockdown (22:00 – 05:00)
                killAppProcesses(pkg)
                triggerShield(pkg, "BEDTIME_LOCKDOWN")
            } else {
                // Accumulate usage seconds for foreground app
                prefs.addAppUsageSeconds(pkg, 2)

                if (prefs.isAppUsageLimitExceeded(pkg) && !prefs.isPackageTemporarilyUnlocked(pkg)) {
                    triggerShield(pkg, "USAGE_LIMIT_EXCEEDED")
                } else if (prefs.isPackageBlocked(pkg, myPkg)) {
                    val reason = prefs.getBlockReason(pkg)
                    triggerShield(pkg, reason)
                }
            }

            handler.postDelayed(this, usageCheckIntervalMs)
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        prefs = PreferencesManager.getInstance(this)

        // Dynamic service configuration to intercept all windows on modern Android
        val info = serviceInfo ?: AccessibilityServiceInfo()
        info.eventTypes = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED or AccessibilityEvent.TYPE_WINDOWS_CHANGED
        info.feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
        info.flags = AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS or
                AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS or
                AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
        info.packageNames = null // Monitor all packages across the system
        serviceInfo = info

        // Register receiver for SCREEN_OFF to trigger Discipline Lock Screen
        try {
            screenLockReceiver = ScreenLockReceiver()
            val filter = IntentFilter(Intent.ACTION_SCREEN_OFF)
            registerReceiver(screenLockReceiver, filter)
        } catch (_: Exception) {}

        // Start usage tracker & lockdown watchdog
        handler.postDelayed(usageTrackerRunnable, usageCheckIntervalMs)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null || (event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED && event.eventType != AccessibilityEvent.TYPE_WINDOWS_CHANGED)) {
            return
        }

        val targetPkg = event.packageName?.toString() ?: return
        val myPkg = applicationContext.packageName

        // Never intercept, block, or kill keyboard / IME windows
        if (isKeyboardOrIme(targetPkg)) {
            return
        }

        val className = event.className?.toString() ?: ""
        if (className.contains("Activity") || className.contains("ui.")) {
            currentForegroundActivity = className
        }

        val previousPkg = currentForegroundPackage
        currentForegroundPackage = targetPkg

        // Aggressive shutdown when apps are minimized:
        // No other app allowed to keep running in background when minimized, except:
        // Launcher, calls/phone, messages, and core phone services.
        // Social media apps are shut down immediately upon minimize.
        if (previousPkg != null && previousPkg != targetPkg && !isExemptedPhoneService(previousPkg)) {
            killAppProcesses(previousPkg)
        }

        // 1. ESCAPE ROOM STRICT 1-HOUR LOCKDOWN CHECK:
        // TOTAL lockdown — ONLY DisciplineLockActivity + phone/emergency dialer allowed.
        // Settings, browser, our own home screen — ALL blocked and redirected to lock screen.
        if (prefs.isEscapeRoomActive()) {
            // Phone/emergency dialer: always pass through
            if (isSystemPhoneDialer(targetPkg)) return

            // Our own app: ONLY the DisciplineLockActivity and UrgeCheckIn are allowed.
            if (targetPkg == myPkg) {
                val isBlockedScreen = className.contains("HomeActivity") ||
                                      className.contains("AppDrawerActivity") ||
                                      className.contains("SettingsActivity") ||
                                      className.contains("WeeklyPlanActivity") ||
                                      className.contains("FocusActivity") ||
                                      className.contains("GodFirstActivity")
                if (isBlockedScreen) {
                    triggerEscapeRoomLockdown()
                }
                return
            }

            // Every other app (including Settings) — kill it and show lock screen
            killAppProcesses(targetPkg)
            triggerEscapeRoomLockdown()
            return
        }

        // 2. BEDTIME TOTAL LOCKDOWN CHECK (22:00 – 05:00):
        // STRICT TOTAL LOCKDOWN: Only phone calls/dialer, keyboard, and clock/alarms are allowed.
        // Google Search/Weather, Play Store, System Settings, WhatsApp, and all other apps are BLOCKED & KILLED!
        if (prefs.isBedtimeLockdownActive()) {
            if (isSystemPhoneDialer(targetPkg) || isKeyboardOrIme(targetPkg) || isDeskClock(targetPkg) || isSystemUiOrFramework(targetPkg)) {
                return
            }

            if (targetPkg == myPkg) {
                val isBlockedScreen = className.contains("SettingsActivity") ||
                                      className.contains("AppDrawerActivity") ||
                                      className.contains("AppRestrictionsActivity")
                if (isBlockedScreen) {
                    val homeIntent = Intent(this, HomeActivity::class.java).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                    }
                    startActivity(homeIntent)
                }
                return
            }

            // Every other app (System Settings, Play Store, Weather, Google Search, WhatsApp, etc.):
            killAppProcesses(targetPkg)
            triggerShield(targetPkg, "BEDTIME_LOCKDOWN")
            return
        }

        if (targetPkg == myPkg) return

        // 3. SETTINGS GUARD:
        if (targetPkg == "com.android.settings" || targetPkg.startsWith("com.android.settings.")) {
            if (prefs.isSettingsProtectionEnabled() && !prefs.isPackageTemporarilyUnlocked("com.android.settings")) {
                triggerShield("com.android.settings", "SETTINGS_GUARD")
                return
            }
        }

        // 4. DAILY USAGE LIMIT EXCEEDED:
        if (prefs.isAppUsageLimitExceeded(targetPkg) && !prefs.isPackageTemporarilyUnlocked(targetPkg)) {
            triggerShield(targetPkg, "USAGE_LIMIT_EXCEEDED")
            return
        }

        // 5. RESTRICTED APP / FOCUS MODE / SOCIAL WINDOWS:
        if (prefs.isPackageBlocked(targetPkg, myPkg)) {
            val reason = prefs.getBlockReason(targetPkg)
            triggerShield(targetPkg, reason)
        }
    }

    private fun killAppProcesses(packageName: String) {
        val myPkg = applicationContext.packageName
        if (packageName == myPkg) return
        if (isKeyboardOrIme(packageName)) return
        try {
            val am = getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
            am?.killBackgroundProcesses(packageName)
        } catch (_: Exception) {}
    }

    private fun triggerEscapeRoomLockdown() {
        val now = System.currentTimeMillis()
        if (now - lastEscapeRoomLockTimeMs < 1200L) {
            return
        }
        lastEscapeRoomLockTimeMs = now

        vibrateShieldAlert()

        val lockIntent = Intent(this, DisciplineLockActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
            addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION)
            putExtra(DisciplineLockActivity.EXTRA_IS_ESCAPE_ROOM, true)
        }
        startActivity(lockIntent)
    }

    private fun isSystemPhoneDialer(pkg: String): Boolean {
        val phoneDialers = listOf(
            "com.google.android.dialer",
            "com.android.dialer",
            "com.android.phone",
            "com.android.incallui",
            "com.google.android.incallui",
            "com.samsung.android.incallui",
            "com.android.server.telecom",
            "com.google.android.emergency",
            "com.android.emergency"
        )
        return phoneDialers.contains(pkg)
    }

    private fun isExemptedPhoneService(pkg: String): Boolean {
        val myPkg = applicationContext.packageName
        if (pkg == myPkg) return true
        if (isKeyboardOrIme(pkg)) return true
        if (isSystemPhoneDialer(pkg)) return true

        val allowedServices = listOf(
            "android",
            "com.android.systemui",
            "com.google.android.apps.messaging",
            "com.android.mms",
            "com.google.android.contacts",
            "com.android.contacts",
            "com.google.android.deskclock",
            "com.android.deskclock",
            "com.google.android.inputmethod.latin",
            "com.android.inputmethod.latin",
            "com.samsung.android.honeyboard",
            "com.touchtype.swiftkey",
            "com.microsoft.ime.swiftkey",
            "com.google.android.gms",
            "com.google.android.gsf",
            "com.google.android.packageinstaller",
            "com.android.packageinstaller",
            // Scripture: always allowed, never killed
            "com.bibiliya.ntagatifu"
        )
        return allowedServices.contains(pkg) || pkg.startsWith("com.android.internal")
    }

    private fun isKeyboardOrIme(pkg: String): Boolean {
        if (!::prefs.isInitialized) {
            prefs = PreferencesManager.getInstance(applicationContext)
        }
        return prefs.isKeyboardOrIme(pkg)
    }

    private fun isSystemHomeOrDialer(pkg: String): Boolean {
        return isExemptedPhoneService(pkg)
    }

    private fun isDeskClock(pkg: String): Boolean {
        return pkg == "com.google.android.deskclock" || pkg == "com.android.deskclock"
    }

    private fun isSystemUiOrFramework(pkg: String): Boolean {
        return pkg == "android" || pkg == "com.android.systemui" || pkg.startsWith("com.android.internal")
    }

    private fun triggerShield(packageName: String, reason: String) {
        val myPkg = applicationContext.packageName
        // ABSOLUTE ISOLATION: Never ever show BlockShieldActivity for NYIRABYO Life itself!
        if (packageName == myPkg) {
            return
        }

        // Debounce: if the shield is already showing for the same package, don't re-trigger
        val now = System.currentTimeMillis()
        if (packageName == lastShieldPkg && (now - lastShieldTimeMs) < shieldDebounceMs) {
            return
        }
        lastShieldPkg = packageName
        lastShieldTimeMs = now

        // Kill the app process immediately so it cannot remain running underneath
        killAppProcesses(packageName)

        // Dismiss any open system popups, search overlays, or status bar shades
        try {
            @Suppress("DEPRECATION")
            sendBroadcast(Intent(Intent.ACTION_CLOSE_SYSTEM_DIALOGS))
        } catch (_: Exception) {}

        // Single gentle vibration — no repeated jolts
        vibrateShieldAlert()

        val intent = Intent(this, BlockShieldActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)   // Reuse existing instance if on top
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
            addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION) // No jarring slide animation
            putExtra(BlockShieldActivity.EXTRA_PACKAGE_NAME, packageName)
            putExtra(BlockShieldActivity.EXTRA_BLOCK_REASON, reason)
        }
        startActivity(intent)
    }

    private fun vibrateShieldAlert() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vibratorManager = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                vibratorManager?.defaultVibrator?.vibrate(
                    VibrationEffect.createOneShot(80, VibrationEffect.DEFAULT_AMPLITUDE)
                )
            } else {
                @Suppress("DEPRECATION")
                val vibrator = getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vibrator?.vibrate(VibrationEffect.createOneShot(80, VibrationEffect.DEFAULT_AMPLITUDE))
                } else {
                    @Suppress("DEPRECATION")
                    vibrator?.vibrate(80)
                }
            }
        } catch (_: Exception) {}
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacks(usageTrackerRunnable)
        screenLockReceiver?.let {
            try {
                unregisterReceiver(it)
            } catch (_: Exception) {}
        }
    }

    override fun onInterrupt() {
        // Accessibility service interrupted
    }
}
