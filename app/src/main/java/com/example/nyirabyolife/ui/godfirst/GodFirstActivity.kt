package com.example.nyirabyolife.ui.godfirst

import android.graphics.Paint
import android.os.Bundle
import android.view.LayoutInflater
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.example.nyirabyolife.R
import com.example.nyirabyolife.data.local.NyirabyoDbHelper
import com.example.nyirabyolife.data.local.ScriptureRepository
import com.example.nyirabyolife.data.model.ChristRevelation
import com.example.nyirabyolife.databinding.ActivityGodFirstBinding
import com.example.nyirabyolife.databinding.ItemPrayerBinding
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class GodFirstActivity : AppCompatActivity() {

    private lateinit var binding: ActivityGodFirstBinding
    private lateinit var dbHelper: NyirabyoDbHelper
    private var allRevelations: List<ChristRevelation> = emptyList()
    private var currentRevelationIndex = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityGodFirstBinding.inflate(layoutInflater)
        setContentView(binding.root)

        dbHelper = NyirabyoDbHelper.getInstance(this)
        binding.btnBack.setOnClickListener { finish() }

        setupSacredPresenceAnime()
        setupChristRevelations()
        setupPrayers()
        setupGratitude()
    }

    private fun setupSacredPresenceAnime() {
        // Connect the breathing anime animation to live prayer prompts
        binding.spiritualAuraView.onPhaseChangeListener = { phase ->
            runOnUiThread {
                binding.tvBreathPhase.text = phase.text
                binding.tvBreathPrayer.text = phase.subText
            }
        }
    }

    private fun setupChristRevelations() {
        allRevelations = ScriptureRepository.getAllChristRevelations()
        val today = ScriptureRepository.getChristRevelationOfTheDay()
        currentRevelationIndex = allRevelations.indexOfFirst { it.id == today.id }.coerceAtLeast(0)
        displayRevelation(allRevelations[currentRevelationIndex])

        binding.btnCycleRevelation.setOnClickListener {
            currentRevelationIndex = (currentRevelationIndex + 1) % allRevelations.size
            displayRevelation(allRevelations[currentRevelationIndex])
        }
    }

    private fun displayRevelation(revelation: ChristRevelation) {
        binding.tvRevelationTitle.text = revelation.title
        binding.tvFullVerse.text = "“${revelation.scripture}”"
        binding.tvFullRef.text = revelation.reference
        binding.tvRevelationInsight.text = revelation.revelation
        binding.tvPrayerInsight.text = "“${revelation.prayer}”"
    }

    private fun setupPrayers() {
        refreshPrayers()

        binding.btnAddPrayer.setOnClickListener {
            val title = binding.etNewPrayer.text.toString().trim()
            if (title.isNotEmpty()) {
                dbHelper.addPrayer(title)
                binding.etNewPrayer.text?.clear()
                refreshPrayers()
            } else {
                Toast.makeText(this, "Please enter a prayer intention", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun refreshPrayers() {
        binding.layoutPrayerList.removeAllViews()
        val prayers = dbHelper.getPrayers()
        val inflater = LayoutInflater.from(this)

        prayers.forEach { prayer ->
            val prayerBinding = ItemPrayerBinding.inflate(inflater, binding.layoutPrayerList, false)
            prayerBinding.tvPrayerTitle.text = prayer.title

            if (prayer.isAnswered) {
                prayerBinding.ivPrayerCheck.setImageResource(R.drawable.ic_check_circle)
                prayerBinding.ivPrayerCheck.setColorFilter(ContextCompat.getColor(this, R.color.emerald_accent))
                prayerBinding.tvPrayerTitle.paintFlags = prayerBinding.tvPrayerTitle.paintFlags or Paint.STRIKE_THRU_TEXT_FLAG
                prayerBinding.tvPrayerTitle.setTextColor(ContextCompat.getColor(this, R.color.text_tertiary))
            } else {
                prayerBinding.ivPrayerCheck.setImageResource(R.drawable.ic_circle_outline)
                prayerBinding.ivPrayerCheck.setColorFilter(ContextCompat.getColor(this, R.color.text_tertiary))
                prayerBinding.tvPrayerTitle.paintFlags = prayerBinding.tvPrayerTitle.paintFlags and Paint.STRIKE_THRU_TEXT_FLAG.inv()
                prayerBinding.tvPrayerTitle.setTextColor(ContextCompat.getColor(this, R.color.text_primary))
            }

            prayerBinding.ivPrayerCheck.setOnClickListener {
                dbHelper.togglePrayerAnswered(prayer.id, !prayer.isAnswered)
                refreshPrayers()
            }

            prayerBinding.ivDeletePrayer.setOnClickListener {
                dbHelper.deletePrayer(prayer.id)
                refreshPrayers()
            }

            binding.layoutPrayerList.addView(prayerBinding.root)
        }
    }

    private fun setupGratitude() {
        refreshGratitude()

        binding.btnSaveGratitude.setOnClickListener {
            val text = binding.etGratitude.text.toString().trim()
            if (text.isNotEmpty()) {
                val todayStr = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
                dbHelper.addGratitude(text, todayStr)
                binding.etGratitude.text?.clear()
                Toast.makeText(this, "Gratitude saved. Give thanks to the Lord!", Toast.LENGTH_SHORT).show()
                refreshGratitude()
            }
        }
    }

    private fun refreshGratitude() {
        binding.layoutGratitudeList.removeAllViews()
        val entries = dbHelper.getGratitudeEntries()

        entries.take(5).forEach { entry ->
            val textView = android.widget.TextView(this).apply {
                text = "• ${entry.text}"
                setTextColor(ContextCompat.getColor(context, R.color.text_secondary))
                textSize = 13f
                setPadding(0, 8, 0, 8)
            }
            binding.layoutGratitudeList.addView(textView)
        }
    }
}
