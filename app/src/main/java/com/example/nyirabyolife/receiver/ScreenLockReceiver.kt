package com.example.nyirabyolife.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.nyirabyolife.data.local.PreferencesManager
import com.example.nyirabyolife.ui.lock.DisciplineLockActivity

class ScreenLockReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_SCREEN_OFF) {
            val prefs = PreferencesManager.getInstance(context)
            if (prefs.isLockScreenEnabled()) {
                val lockIntent = Intent(context, DisciplineLockActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
                    addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION)
                }
                context.startActivity(lockIntent)
            }
        }
    }
}
