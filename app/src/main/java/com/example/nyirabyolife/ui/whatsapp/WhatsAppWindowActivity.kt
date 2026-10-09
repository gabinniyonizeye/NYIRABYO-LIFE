package com.example.nyirabyolife.ui.whatsapp

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.example.nyirabyolife.R
import com.example.nyirabyolife.data.local.PreferencesManager
import com.example.nyirabyolife.databinding.ActivityWhatsappWindowBinding

class WhatsAppWindowActivity : AppCompatActivity() {

    private lateinit var binding: ActivityWhatsappWindowBinding
    private lateinit var prefs: PreferencesManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityWhatsappWindowBinding.inflate(layoutInflater)
        setContentView(binding.root)

        prefs = PreferencesManager.getInstance(this)
        binding.btnBack.setOnClickListener { finish() }

        updateStatusUI()

        binding.btnOpenWhatsApp.setOnClickListener {
            handleWhatsAppLaunch()
        }

        binding.cardWellbeingSettings.setOnClickListener {
            try {
                startActivity(Intent(Settings.ACTION_SETTINGS))
            } catch (e: Exception) {
                Toast.makeText(this, "Could not open Android Settings", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun updateStatusUI() {
        val isOpen = prefs.isWhatsAppWindowOpen()
        if (isOpen) {
            binding.tvWindowStatusBadge.text = "WINDOW CURRENTLY ACTIVE"
            binding.tvWindowStatusBadge.setTextColor(ContextCompat.getColor(this, R.color.emerald_accent))
            binding.ivStatusDot.setColorFilter(ContextCompat.getColor(this, R.color.emerald_accent))
            binding.btnOpenWhatsApp.text = "Open WhatsApp (Active Window)"
            binding.btnOpenWhatsApp.setBackgroundResource(R.drawable.button_emerald)
        } else {
            binding.tvWindowStatusBadge.text = "WINDOW CURRENTLY CLOSED"
            binding.tvWindowStatusBadge.setTextColor(ContextCompat.getColor(this, R.color.gold_primary))
            binding.ivStatusDot.setColorFilter(ContextCompat.getColor(this, R.color.gold_primary))
            binding.btnOpenWhatsApp.text = "Open WhatsApp with Mindful Pause"
            binding.btnOpenWhatsApp.setBackgroundResource(R.drawable.button_gold)
            binding.btnOpenWhatsApp.setTextColor(ContextCompat.getColor(this, R.color.bg_dark))
        }

        binding.tvWindowScheduleDetails.text = prefs.getWhatsAppWindowDescription()
    }

    private fun handleWhatsAppLaunch() {
        if (prefs.isWhatsAppWindowOpen()) {
            launchWhatsAppDirectly()
        } else {
            // Prompt mindful pause
            AlertDialog.Builder(this, com.google.android.material.R.style.Theme_MaterialComponents_DayNight_Dialog_Alert)
                .setTitle("Mindful Communication Pause")
                .setMessage("You are outside your scheduled communication period.\n\nTake a breath. Is this deliberate communication, or an unconscious distraction?\n\nDiscipline means choosing what you want most over what you want now.")
                .setPositiveButton("Proceed Intentionally") { _, _ ->
                    launchWhatsAppDirectly()
                }
                .setNegativeButton("Protect Focus (Cancel)", null)
                .show()
        }
    }

    private fun launchWhatsAppDirectly() {
        val packages = listOf("com.whatsapp", "com.whatsapp.w4b")
        for (pkg in packages) {
            val launchIntent = packageManager.getLaunchIntentForPackage(pkg)
            if (launchIntent != null) {
                startActivity(launchIntent)
                finish()
                return
            }
        }
        Toast.makeText(this, "WhatsApp is not installed on this device.", Toast.LENGTH_SHORT).show()
    }
}
