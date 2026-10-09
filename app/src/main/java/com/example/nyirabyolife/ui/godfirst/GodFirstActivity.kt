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
import com.example.nyirabyolife.data.model.ScriptureVerse
import com.example.nyirabyolife.databinding.ActivityGodFirstBinding
import com.example.nyirabyolife.databinding.ItemPrayerBinding
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class GodFirstActivity : AppCompatActivity() {

    private lateinit var binding: ActivityGodFirstBinding
    private lateinit var dbHelper: NyirabyoDbHelper
    private var allVerses: List<ScriptureVerse> = emptyList()
    private var currentVerseIndex = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityGodFirstBinding.inflate(layoutInflater)
        setContentView(binding.root)

        dbHelper = NyirabyoDbHelper.getInstance(this)
        binding.btnBack.setOnClickListener { finish() }

        setupScripture()
        setupPrayers()
        setupGratitude()
    }

    private fun setupScripture() {
        allVerses = ScriptureRepository.getAllVerses()
        val today = ScriptureRepository.getVerseOfTheDay()
        currentVerseIndex = allVerses.indexOfFirst { it.id == today.id }.coerceAtLeast(0)
        displayVerse(allVerses[currentVerseIndex])

        binding.btnCycleVerse.setOnClickListener {
            currentVerseIndex = (currentVerseIndex + 1) % allVerses.size
            displayVerse(allVerses[currentVerseIndex])
        }
    }

    private fun displayVerse(verse: ScriptureVerse) {
        binding.tvThemeBadge.text = verse.theme.uppercase()
        binding.tvFullVerse.text = "“${verse.text}”"
        binding.tvFullRef.text = verse.reference
        binding.tvReflectionPrompt.text = verse.reflectionPrompt
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
