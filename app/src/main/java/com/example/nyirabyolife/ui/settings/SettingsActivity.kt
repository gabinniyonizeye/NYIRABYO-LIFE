package com.example.nyirabyolife.ui.settings

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.example.nyirabyolife.R
import com.example.nyirabyolife.data.local.NyirabyoDbHelper
import com.example.nyirabyolife.data.local.PreferencesManager
import com.example.nyirabyolife.databinding.ActivitySettingsBinding
import java.io.File

class SettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySettingsBinding
    private lateinit var prefs: PreferencesManager
    private lateinit var dbHelper: NyirabyoDbHelper

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        prefs = PreferencesManager.getInstance(this)
        dbHelper = NyirabyoDbHelper.getInstance(this)

        binding.btnBack.setOnClickListener { finish() }

        binding.cardDefaultLauncher.setOnClickListener {
            openDefaultLauncherSettings()
        }

        binding.cardPermissionsHub.setOnClickListener {
            startActivity(Intent(this, com.example.nyirabyolife.ui.permissions.PermissionsHubActivity::class.java))
        }

        binding.cardAppRestrictions.setOnClickListener {
            startActivity(Intent(this, com.example.nyirabyolife.ui.restriction.AppRestrictionsActivity::class.java))
        }

        binding.cardLockMode.setOnClickListener {
            startActivity(Intent(this, com.example.nyirabyolife.ui.lock.DisciplineLockActivity::class.java))
        }

        binding.cardSecurity.setOnClickListener {
            promptChangePin()
        }

        binding.btnExportJson.setOnClickListener {
            exportData()
        }

        binding.btnClearRecoveryOnly.setOnClickListener {
            confirmClearRecovery()
        }

        binding.btnWipeAll.setOnClickListener {
            confirmWipeAll()
        }
    }

    private fun openDefaultLauncherSettings() {
        try {
            val intent = Intent(Settings.ACTION_HOME_SETTINGS)
            startActivity(intent)
        } catch (e: Exception) {
            try {
                val intent = Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS)
                startActivity(intent)
            } catch (ex: Exception) {
                Toast.makeText(this, "Please select NYIRABYO LIFE in Android Settings > Apps > Default apps", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun promptChangePin() {
        val input = EditText(this).apply {
            inputType = android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_VARIATION_PASSWORD
            hint = "Enter new 4-digit PIN"
            setPadding(48, 32, 48, 32)
            setTextColor(ContextCompat.getColor(this@SettingsActivity, R.color.text_primary))
            setHintTextColor(ContextCompat.getColor(this@SettingsActivity, R.color.text_tertiary))
        }

        AlertDialog.Builder(this, com.google.android.material.R.style.Theme_MaterialComponents_DayNight_Dialog_Alert)
            .setTitle("Configure Security PIN")
            .setMessage("Set a secret 4-digit PIN for the private recovery area:")
            .setView(input)
            .setPositiveButton("Save PIN") { _, _ ->
                val entered = input.text.toString().trim()
                if (entered.length >= 4) {
                    prefs.setPin(entered)
                    Toast.makeText(this, "Security PIN updated", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this, "PIN must have at least 4 digits", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun exportData() {
        try {
            val json = dbHelper.exportAllDataAsJson()
            val exportFile = File(cacheDir, "nyirabyo_life_export.json")
            exportFile.writeText(json)

            val uri = FileProvider.getUriForFile(this, "${packageName}.fileprovider", exportFile)
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "application/json"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(Intent.createChooser(shareIntent, "Export NYIRABYO Data"))
        } catch (e: Exception) {
            // Fallback: Copy to clipboard
            val clipboard = getSystemService(CLIPBOARD_SERVICE) as android.content.ClipboardManager
            val clip = android.content.ClipData.newPlainText("NYIRABYO Life Backup", dbHelper.exportAllDataAsJson())
            clipboard.setPrimaryClip(clip)
            Toast.makeText(this, "Export copied to clipboard (100% offline).", Toast.LENGTH_LONG).show()
        }
    }

    private fun confirmClearRecovery() {
        AlertDialog.Builder(this, com.google.android.material.R.style.Theme_MaterialComponents_DayNight_Dialog_Alert)
            .setTitle("Erase Recovery Records")
            .setMessage("Are you sure you want to permanently erase all private urge logs and trigger journal entries? This action cannot be undone.")
            .setPositiveButton("Erase Recovery") { _, _ ->
                dbHelper.clearRecoveryData()
                Toast.makeText(this, "Private recovery records have been permanently erased.", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun confirmWipeAll() {
        AlertDialog.Builder(this, com.google.android.material.R.style.Theme_MaterialComponents_DayNight_Dialog_Alert)
            .setTitle("Wipe All Data")
            .setMessage("WARNING: This will reset all daily missions, prayer intentions, gratitude entries, focus sessions, and recovery records. Are you completely sure?")
            .setPositiveButton("Wipe Everything") { _, _ ->
                dbHelper.wipeAllData()
                prefs.clearAllPreferences()
                Toast.makeText(this, "All application data wiped.", Toast.LENGTH_SHORT).show()
                finish()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
}
