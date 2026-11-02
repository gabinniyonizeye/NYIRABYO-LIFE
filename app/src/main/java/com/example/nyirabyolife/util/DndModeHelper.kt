package com.example.nyirabyolife.util

import android.app.NotificationManager
import android.content.Context
import android.media.AudioManager
import android.os.Build
import com.example.nyirabyolife.data.local.PreferencesManager

object DndModeHelper {

    fun isDndPermissionGranted(context: Context): Boolean {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return false
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            nm.isNotificationPolicyAccessGranted
        } else {
            true
        }
    }

    /**
     * Activates Do Not Disturb / Silence for focus or strict activities.
     *
     * Two-pronged approach for Android 14 compatibility:
     *  1. Set INTERRUPTION_FILTER_NONE (total DND) if permission is granted —
     *     this prevents the call UI / ringer from firing at the system level.
     *  2. Force ring and notification stream volumes to 0 regardless of DND
     *     permission — this silences the actual audio even if the system still
     *     routes the call through.
     *
     * All original values are saved and restored by [disableFocusDnd].
     */
    fun enableFocusDnd(context: Context, strictSilence: Boolean = false): Boolean {
        val prefs = PreferencesManager.getInstance(context)
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
        val am = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager

        // ── Save originals (only once; guard against double-call) ─────────────
        if (am != null) {
            if (prefs.getOriginalRingerMode() == -1) {
                prefs.saveOriginalRingerMode(am.ringerMode)
            }
            if (prefs.getOriginalRingVolume() == -1) {
                prefs.saveOriginalRingVolume(am.getStreamVolume(AudioManager.STREAM_RING))
            }
            if (prefs.getOriginalNotifVolume() == -1) {
                prefs.saveOriginalNotifVolume(am.getStreamVolume(AudioManager.STREAM_NOTIFICATION))
            }
        }

        // ── Strategy 1: INTERRUPTION_FILTER_NONE (requires DND permission) ────
        // On Android 14, INTERRUPTION_FILTER_NONE is the only reliable way to
        // fully prevent the incoming call screen / ringer from appearing.
        var policyChanged = false
        if (nm != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M
            && nm.isNotificationPolicyAccessGranted
        ) {
            if (prefs.getOriginalDndFilter() == -1) {
                prefs.saveOriginalDndFilter(nm.currentInterruptionFilter)
            }
            try {
                // Always use INTERRUPTION_FILTER_NONE — no exceptions for calls/stars
                nm.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_NONE)
                policyChanged = true
            } catch (_: Exception) {}
        }

        // ── Strategy 2: Zero the ring volume (works with or without DND perm) ─
        // Each call is in its own try-catch so a SecurityException on ringerMode
        // (requires DND perm on Android 6+) does NOT skip the volume zeroing.
        // FLAG_REMOVE_SOUND_AND_VIBRATE stops any ringtone already playing.
        if (am != null) {
            // Set RINGER_MODE_SILENT (needs DND permission; may fail — that's OK)
            try {
                am.ringerMode = AudioManager.RINGER_MODE_SILENT
            } catch (_: Exception) {
                // DND permission not granted; audio stream zeroing below handles silence
            }
            // Zero ring volume — always works, no special permission required
            try {
                am.setStreamVolume(
                    AudioManager.STREAM_RING,
                    0,
                    AudioManager.FLAG_REMOVE_SOUND_AND_VIBRATE
                )
            } catch (_: Exception) {}
            // Zero notification volume
            try {
                am.setStreamVolume(
                    AudioManager.STREAM_NOTIFICATION,
                    0,
                    0
                )
            } catch (_: Exception) {}
        }

        prefs.setFocusDndActive(true)
        return policyChanged
    }

    /**
     * Restores normal sound and original DND filter when the focus session ends.
     */
    fun disableFocusDnd(context: Context) {
        val prefs = PreferencesManager.getInstance(context)
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
        val am = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager

        // ── Restore stream volumes FIRST (before changing ringer mode) ─────────
        val origRing = prefs.getOriginalRingVolume()
        val origNotif = prefs.getOriginalNotifVolume()
        if (am != null) {
            try {
                if (origRing != -1) {
                    am.setStreamVolume(AudioManager.STREAM_RING, origRing, 0)
                }
            } catch (_: Exception) {}
            try {
                if (origNotif != -1) {
                    am.setStreamVolume(AudioManager.STREAM_NOTIFICATION, origNotif, 0)
                }
            } catch (_: Exception) {}
        }
        prefs.saveOriginalRingVolume(-1)
        prefs.saveOriginalNotifVolume(-1)

        // ── Restore interruption filter ────────────────────────────────────────
        val origFilter = prefs.getOriginalDndFilter()
        if (nm != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M
            && nm.isNotificationPolicyAccessGranted
        ) {
            try {
                val targetFilter = if (origFilter != -1) origFilter
                else NotificationManager.INTERRUPTION_FILTER_ALL
                nm.setInterruptionFilter(targetFilter)
            } catch (_: Exception) {}
        }
        prefs.saveOriginalDndFilter(-1)

        // ── Restore ringer mode ────────────────────────────────────────────────
        val origRinger = prefs.getOriginalRingerMode()
        if (am != null) {
            try {
                am.ringerMode = if (origRinger != -1) origRinger else AudioManager.RINGER_MODE_NORMAL
            } catch (_: Exception) {}
        }
        prefs.saveOriginalRingerMode(-1)

        prefs.setFocusDndActive(false)
    }
}
