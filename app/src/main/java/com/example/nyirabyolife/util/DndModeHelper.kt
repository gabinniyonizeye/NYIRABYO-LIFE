package com.example.nyirabyolife.util

import android.app.NotificationManager
import android.content.Context
import android.media.AudioManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import com.example.nyirabyolife.data.local.PreferencesManager

object DndModeHelper {

    // ── Heartbeat to re-enforce silence (the OS can revert ringer mode) ───────
    private val handler = Handler(Looper.getMainLooper())
    private var enforcementRunnable: Runnable? = null

    fun isDndPermissionGranted(context: Context): Boolean {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return false
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            nm.isNotificationPolicyAccessGranted
        } else {
            true
        }
    }

    /**
     * Activates Do Not Disturb / Silence for focus activities.
     *
     * IMPORTANT — Android OS (and the Accessibility Service calling
     * killBackgroundProcesses) can cause the AudioService / TelecomManager to
     * reset the ringer mode back to NORMAL within seconds of us setting SILENT.
     * To counter this, we start a repeating 3-second heartbeat that
     * continuously re-enforces the silent state for the duration of the session.
     *
     * Two-pronged approach:
     *  1. INTERRUPTION_FILTER_NONE (total DND) — if DND permission is granted.
     *  2. RINGER_MODE_SILENT + zero STREAM_RING/STREAM_NOTIFICATION volumes.
     *     Note: setRingerMode(SILENT) is silently downgraded to VIBRATE by Android
     *     when DND permission is NOT granted, so it is guarded behind the check.
     *
     * All original values are saved and restored by [disableFocusDnd].
     */
    fun enableFocusDnd(context: Context, strictSilence: Boolean = false): Boolean {
        val appContext = context.applicationContext
        val prefs = PreferencesManager.getInstance(appContext)
        val nm = appContext.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
        val am = appContext.getSystemService(Context.AUDIO_SERVICE) as? AudioManager

        // ── Save originals once (guard against double-call) ───────────────────
        if (am != null) {
            if (prefs.getOriginalRingerMode() == -1)
                prefs.saveOriginalRingerMode(am.ringerMode)
            if (prefs.getOriginalRingVolume() == -1)
                prefs.saveOriginalRingVolume(am.getStreamVolume(AudioManager.STREAM_RING))
            if (prefs.getOriginalNotifVolume() == -1)
                prefs.saveOriginalNotifVolume(am.getStreamVolume(AudioManager.STREAM_NOTIFICATION))
        }

        // ── Apply silence now ─────────────────────────────────────────────────
        applyFocusSilence(appContext, nm, am)

        // ── Start heartbeat to re-enforce every 3 s ───────────────────────────
        stopEnforcementHeartbeat()
        val runnable = object : Runnable {
            override fun run() {
                if (PreferencesManager.getInstance(appContext).isFocusDndActive()) {
                    applyFocusSilence(
                        appContext,
                        appContext.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager,
                        appContext.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
                    )
                    handler.postDelayed(this, 3000L)
                }
            }
        }
        enforcementRunnable = runnable
        handler.postDelayed(runnable, 3000L)

        prefs.setFocusDndActive(true)
        return isDndPermissionGranted(appContext)
    }

    /**
     * The actual silence enforcement, called once on start and then every 3 s.
     *
     * CRITICAL: Do NOT call both setInterruptionFilter() and setRingerMode().
     * Calling setRingerMode() triggers ZenModeHelper.onSetRingerModeInternal()
     * which immediately re-applies the previous ZenConfig, overriding our
     * INTERRUPTION_FILTER_NONE. They fight each other.
     *
     * Rule: if DND permission is granted → setInterruptionFilter(NONE) only, return.
     *       if NOT granted              → zero stream volumes only (stops audio, not vibration).
     */
    private fun applyFocusSilence(
        context: Context,
        nm: NotificationManager?,
        am: AudioManager?
    ) {
        val dndGranted = isDndPermissionGranted(context)

        // Strategy 1: INTERRUPTION_FILTER_NONE — handles everything at system level.
        // Return immediately after; calling setRingerMode after this overrides the filter.
        if (nm != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && dndGranted) {
            val prefs = PreferencesManager.getInstance(context)
            if (prefs.getOriginalDndFilter() == -1) {
                prefs.saveOriginalDndFilter(nm.currentInterruptionFilter)
            }
            try {
                nm.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_NONE)
                return   // ← early return: FILTER_NONE already blocks calls + vibration
            } catch (_: Exception) {}
        }

        // Strategy 2 (fallback — no DND permission): zero stream volumes to at least
        // suppress audio. Vibration cannot be stopped without DND permission on Android 6+.
        if (am != null) {
            try { am.setStreamVolume(AudioManager.STREAM_RING, 0, 0) } catch (_: Exception) {}
            try { am.setStreamVolume(AudioManager.STREAM_NOTIFICATION, 0, 0) } catch (_: Exception) {}
        }
    }

    private fun stopEnforcementHeartbeat() {
        enforcementRunnable?.let { handler.removeCallbacks(it) }
        enforcementRunnable = null
    }

    /**
     * Restores normal sound and original DND filter when the focus session ends.
     */
    fun disableFocusDnd(context: Context) {
        val appContext = context.applicationContext
        val prefs = PreferencesManager.getInstance(appContext)
        val nm = appContext.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
        val am = appContext.getSystemService(Context.AUDIO_SERVICE) as? AudioManager

        // Stop the heartbeat FIRST so it doesn't fight the restore
        stopEnforcementHeartbeat()
        prefs.setFocusDndActive(false)

        // ── Restore stream volumes (before ringer mode) ───────────────────────
        val origRing = prefs.getOriginalRingVolume()
        val origNotif = prefs.getOriginalNotifVolume()
        if (am != null) {
            try { if (origRing != -1) am.setStreamVolume(AudioManager.STREAM_RING, origRing, 0) } catch (_: Exception) {}
            try { if (origNotif != -1) am.setStreamVolume(AudioManager.STREAM_NOTIFICATION, origNotif, 0) } catch (_: Exception) {}
        }
        prefs.saveOriginalRingVolume(-1)
        prefs.saveOriginalNotifVolume(-1)

        // ── Restore interruption filter ───────────────────────────────────────
        val origFilter = prefs.getOriginalDndFilter()
        if (nm != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M
            && nm.isNotificationPolicyAccessGranted
        ) {
            try {
                nm.setInterruptionFilter(
                    if (origFilter != -1) origFilter else NotificationManager.INTERRUPTION_FILTER_ALL
                )
            } catch (_: Exception) {}
        }
        prefs.saveOriginalDndFilter(-1)

        // ── Restore ringer mode ───────────────────────────────────────────────
        val origRinger = prefs.getOriginalRingerMode()
        if (am != null) {
            try {
                am.ringerMode = if (origRinger != -1) origRinger else AudioManager.RINGER_MODE_NORMAL
            } catch (_: Exception) {}
        }
        prefs.saveOriginalRingerMode(-1)
    }
}

