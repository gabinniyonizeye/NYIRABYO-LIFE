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
import com.example.nyirabyolife.ui.lock.DisciplineLockActivity
import com.example.nyirabyolife.ui.shield.BlockShieldActivity

class NyirabyoGuardianService : AccessibilityService() {

    private lateinit var prefs: PreferencesManager
    private var screenLockReceiver: ScreenLockReceiver? = null
    private var currentForegroundPackage: String? = null
    private val handler = Handler(Looper.getMainLooper())
    private val usageCheckIntervalMs = 5000L // Heartbeat every 5 seconds

    private val usageTrackerRunnable = object : Runnable {
        override fun run() {
            val pkg = currentForegroundPackage
            val myPkg = applicationContext.packageName

            // If Escape Room is active, keep checking lockdown
            if (prefs.isEscapeRoomActive()) {
                if (pkg != null && pkg != myPkg && !isSystemPhoneDialer(pkg)) {
                    triggerEscapeRoomLockdown()
                }
            } else if (pkg != null && pkg != myPkg && !isSystemHomeOrDialer(pkg)) {
                // Accumulate usage seconds for foreground app
                prefs.addAppUsageSeconds(pkg, 5)

                if (prefs.isAppUsageLimitExceeded(pkg) && !prefs.isPackageTemporarilyUnlocked(pkg)) {
                    triggerShield(pkg, "USAGE_LIMIT_EXCEEDED")
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
        if (event == null || event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            return
        }

        val targetPkg = event.packageName?.toString() ?: return
        val myPkg = applicationContext.packageName

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
        if (prefs.isEscapeRoomActive()) {
            // ONLY phone calls & dialer are permitted!
            if (isSystemPhoneDialer(targetPkg)) {
                return // Allowed to take phone call or use dialer
            }
            if (targetPkg != myPkg) {
                // Instantly shut down the unauthorized app and return to lock screen
                killAppProcesses(targetPkg)
                triggerEscapeRoomLockdown()
                return
            }
        }

        if (targetPkg == myPkg) return

        // 2. SETTINGS GUARD:
        if (targetPkg == "com.android.settings" || targetPkg.startsWith("com.android.settings.")) {
            if (prefs.isSettingsProtectionEnabled() && !prefs.isPackageTemporarilyUnlocked("com.android.settings")) {
                triggerShield("com.android.settings", "SETTINGS_GUARD")
                return
            }
        }

        // 3. DAILY USAGE LIMIT EXCEEDED:
        if (prefs.isAppUsageLimitExceeded(targetPkg) && !prefs.isPackageTemporarilyUnlocked(targetPkg)) {
            triggerShield(targetPkg, "USAGE_LIMIT_EXCEEDED")
            return
        }

        // 4. RESTRICTED APP / FOCUS MODE / WHATSAPP WINDOWS:
        if (prefs.isPackageBlocked(targetPkg, myPkg)) {
            val reason = prefs.getBlockReason(targetPkg)
            triggerShield(targetPkg, reason)
        }
    }

    private fun killAppProcesses(packageName: String) {
        try {
            val am = getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
            am?.killBackgroundProcesses(packageName)
        } catch (_: Exception) {}
    }

    private fun triggerEscapeRoomLockdown() {
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
            "com.samsung.android.incallui"
        )
        return phoneDialers.contains(pkg)
    }

    private fun isExemptedPhoneService(pkg: String): Boolean {
        val myPkg = applicationContext.packageName
        if (pkg == myPkg) return true
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
            "com.google.android.gms",
            "com.google.android.gsf",
            "com.google.android.packageinstaller",
            "com.android.packageinstaller"
        )
        return allowedServices.contains(pkg) || pkg.startsWith("com.android.internal")
    }

    private fun isSystemHomeOrDialer(pkg: String): Boolean {
        return isExemptedPhoneService(pkg)
    }

    private fun triggerShield(packageName: String, reason: String) {
        vibrateShieldAlert()

        val intent = Intent(this, BlockShieldActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
            addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
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
