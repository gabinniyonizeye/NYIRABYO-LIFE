package com.example.nyirabyolife.ui.permissions

import android.accessibilityservice.AccessibilityServiceInfo
import android.app.AppOpsManager
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Process
import android.provider.Settings
import android.view.accessibility.AccessibilityManager
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.example.nyirabyolife.R
import com.example.nyirabyolife.databinding.ActivityPermissionsHubBinding

class PermissionsHubActivity : AppCompatActivity() {

    private lateinit var binding: ActivityPermissionsHubBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPermissionsHubBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnBack.setOnClickListener { finish() }

        setupClicks()
    }

    override fun onResume() {
        super.onResume()
        refreshStatuses()
    }

    private fun setupClicks() {
        binding.cardPermLauncher.setOnClickListener {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val roleManager = getSystemService(android.app.role.RoleManager::class.java)
                if (roleManager != null && roleManager.isRoleAvailable(android.app.role.RoleManager.ROLE_HOME)) {
                    if (!roleManager.isRoleHeld(android.app.role.RoleManager.ROLE_HOME)) {
                        startActivity(roleManager.createRequestRoleIntent(android.app.role.RoleManager.ROLE_HOME))
                        return@setOnClickListener
                    }
                }
            }
            try {
                startActivity(Intent(Settings.ACTION_HOME_SETTINGS))
            } catch (_: Exception) {
                try {
                    startActivity(Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS))
                } catch (_: Exception) {
                    startActivity(Intent(Settings.ACTION_SETTINGS))
                }
            }
        }

        binding.cardPermAccessibility.setOnClickListener {
            try {
                Toast.makeText(this, "Find 'NYIRABYO Life' and turn it ON", Toast.LENGTH_LONG).show()
                startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            } catch (_: Exception) {
                startActivity(Intent(Settings.ACTION_SETTINGS))
            }
        }

        binding.cardPermUsage.setOnClickListener {
            try {
                startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
            } catch (_: Exception) {
                startActivity(Intent(Settings.ACTION_SETTINGS))
            }
        }

        binding.cardPermOverlay.setOnClickListener {
            try {
                val intent = Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:$packageName")
                )
                startActivity(intent)
            } catch (_: Exception) {
                startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION))
            }
        }

        binding.cardPermDnd.setOnClickListener {
            try {
                startActivity(Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS))
            } catch (_: Exception) {
                startActivity(Intent(Settings.ACTION_SETTINGS))
            }
        }
    }

    private fun refreshStatuses() {
        // 1. Default Launcher
        val isDefaultLauncher = checkIsDefaultLauncher()
        updateBadge(binding.badgeLauncher, isDefaultLauncher)

        // 2. Accessibility
        val isAccessibilityActive = checkIsAccessibilityActive()
        updateBadge(binding.badgeAccessibility, isAccessibilityActive)

        // 3. Usage Access
        val isUsageGranted = checkIsUsageGranted()
        updateBadge(binding.badgeUsage, isUsageGranted)

        // 4. Overlay
        val isOverlayGranted = Settings.canDrawOverlays(this)
        updateBadge(binding.badgeOverlay, isOverlayGranted)

        // 5. DND
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val isDndGranted = nm.isNotificationPolicyAccessGranted
        updateBadge(binding.badgeDnd, isDndGranted)
    }

    private fun checkIsDefaultLauncher(): Boolean {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        val resolve = packageManager.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)
        return resolve?.activityInfo?.packageName == packageName
    }

    private fun checkIsAccessibilityActive(): Boolean {
        val am = getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager
        val list = am.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
        return list.any { it.resolveInfo.serviceInfo.packageName == packageName }
    }

    private fun checkIsUsageGranted(): Boolean {
        val appOps = getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            appOps.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), packageName)
        } else {
            @Suppress("DEPRECATION")
            appOps.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), packageName)
        }
        return mode == AppOpsManager.MODE_ALLOWED
    }

    private fun updateBadge(tv: TextView, isGranted: Boolean) {
        if (isGranted) {
            tv.text = "Active ✓"
            tv.setTextColor(ContextCompat.getColor(this, R.color.emerald_accent))
        } else {
            tv.text = "Needs Action"
            tv.setTextColor(ContextCompat.getColor(this, R.color.gold_primary))
        }
    }
}
