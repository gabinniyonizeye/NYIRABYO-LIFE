package com.example.nyirabyolife.ui.godfirst

import android.animation.ValueAnimator
import android.content.Context
import android.os.Build
import android.os.Bundle
import android.os.CountDownTimer
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.LayoutInflater
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.example.nyirabyolife.R
import com.example.nyirabyolife.data.local.NyirabyoDbHelper
import com.example.nyirabyolife.data.model.MeditationSessionRecord
import com.example.nyirabyolife.databinding.ActivityHolyMeditationBinding
import com.example.nyirabyolife.databinding.ItemMeditationSessionBinding
import com.example.nyirabyolife.util.ThemeHelper
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class HolyMeditationActivity : AppCompatActivity() {

    enum class PracticeType(
        val practiceId: String,
        val title: String,
        val purpose: String,
        val defaultMinutes: Int,
        val defaultAnchor: String,
        val initialPrompt: String
    ) {
        MORNING_BREATH(
            "morning_breath",
            "Morning Breath Awareness",
            "Build attention and begin the day with intention",
            3,
            "Breath (Nose, chest or belly)",
            "Feel one breath. When you notice your attention has wandered, gently begin again."
        ),
        GROUNDING_54321(
            "grounding_54321",
            "Grounding 5-4-3-2-1",
            "Reconnect with the present when your mind feels busy",
            2,
            "Senses & feet on floor",
            "There is no need to force calm. Look around slowly and notice what is here."
        ),
        OPEN_AWARENESS(
            "open_awareness",
            "Open Awareness (Inner Peace)",
            "Make room for what is here without needing to fix it",
            5,
            "Sitting posture & sounds",
            "For this moment, you can make room for what is here."
        ),
        COUNTING_BREATHS(
            "counting_breaths",
            "Counting Breaths (Focus)",
            "Practice returning your attention to one task",
            3,
            "Exhale count (1 to 5)",
            "Each time you begin again, you are practicing attention."
        ),
        COMPASSION(
            "compassion",
            "Compassionate Phrases",
            "Respond to yourself with warmth and patience",
            3,
            "Compassionate phrases",
            "Silently repeat a phrase that feels natural, leaving space between repetitions."
        ),
        BODY_SCAN(
            "body_scan",
            "Gentle Body Scan (Sleep)",
            "Wind down and notice areas that might be holding tension",
            5,
            "Body sensations & Dim Mode",
            "There is nothing to achieve. Allow yourself to rest."
        ),
        WALKING(
            "walking",
            "Mindful Walking (Movement)",
            "Practice attention when sitting still doesn't feel right",
            3,
            "Feet touching the ground",
            "Notice each step. When your mind wanders, gently return to the next step."
        ),
        SCRIPTURE(
            "scripture",
            "Sacred Scripture Contemplation",
            "Lectio Divina: Contemplating the Living Word & Peace",
            5,
            "Living Word & Presence",
            "Be still, and know that I am God. Rest in His lovingkindness."
        )
    }

    private data class GroundingStep(
        val badge: String,
        val instruction: String,
        val nextLabel: String
    )

    private val groundingSteps = listOf(
        GroundingStep("STEP 1 OF 6 • LOOK AROUND", "Look around and name 5 things you can see right now. 👁️", "Next Step (4 Things You Feel) ➔"),
        GroundingStep("STEP 2 OF 6 • PHYSICAL TOUCH", "Notice 4 things you can feel right now (clothes, chair, air on skin). ✋", "Next Step (3 Sounds) ➔"),
        GroundingStep("STEP 3 OF 6 • LISTEN CLOSELY", "Listen for 3 sounds around you (distant hum, clock, breathing). 👂", "Next Step (2 Smells) ➔"),
        GroundingStep("STEP 4 OF 6 • SENSE SMELL", "Notice 2 things you can smell around you (fresh air, room scent). 👃", "Next Step (1 Taste) ➔"),
        GroundingStep("STEP 5 OF 6 • SENSE TASTE", "Notice 1 thing you can taste, or the resting sensation of your mouth. 👅", "Next Step (Ground Your Feet) ➔"),
        GroundingStep("STEP 6 OF 6 • SOLID SUPPORT", "Feel the solid ground beneath your feet and the support under your body. 🦶", "Complete Grounding 🕊️")
    )

    private val compassionatePhrases = listOf(
        "“May I be kind to myself.”",
        "“May I have patience.”",
        "“May I find moments of ease.”",
        "“May I be gentle with my thoughts.”"
    )

    private val bodyScanRegions = listOf(
        "Feet & Toes 🦶",
        "Lower Legs & Knees 🦵",
        "Hips, Pelvis & Lower Back",
        "Belly & Natural Breathing",
        "Shoulders & Upper Back",
        "Arms & Hands ✋",
        "Neck & Softened Jaw",
        "Eyes, Forehead & Face 😌",
        "Entire Body at Peaceful Rest 🌙"
    )

    private data class ScriptureVerse(val scripture: String, val reference: String)
    private val scriptureVerses = listOf(
        ScriptureVerse("“Let the morning bring me word of your unfailing love, for I have put my trust in you. Show me the way I should go, for to you I entrust my life.”", "Psalm 143:8"),
        ScriptureVerse("“Peace I leave with you; my peace I give to you. I do not give to you as the world gives. Do not let your hearts be troubled and do not be afraid.”", "John 14:27"),
        ScriptureVerse("“Be still, and know that I am God! I will be honored by every nation. I will be honored throughout the world.”", "Psalm 46:10"),
        ScriptureVerse("“Come to me, all you who are weary and burdened, and I will give you rest. Take my yoke upon you and learn from me, for I am gentle and humble in heart.”", "Matthew 11:28-29"),
        ScriptureVerse("“In peace I will lie down and sleep, for you alone, LORD, make me dwell in safety.”", "Psalm 4:8"),
        ScriptureVerse("“The steadfast love of the LORD never ceases; his mercies never come to an end; they are new every morning.”", "Lamentations 3:22-23"),
        ScriptureVerse("“Remain in me, as I also remain in you. No branch can bear fruit by itself; it must remain in the vine.”", "John 15:4")
    )

    private lateinit var binding: ActivityHolyMeditationBinding
    private lateinit var dbHelper: NyirabyoDbHelper

    private var currentPractice = PracticeType.MORNING_BREATH
    private var totalMillis = 3 * 60 * 1000L
    private var millisRemaining = 3 * 60 * 1000L
    private var isTimerRunning = false
    private var countDownTimer: CountDownTimer? = null

    // Specialized Practice States
    private var groundingStepIndex = 0
    private var breathCount = 1
    private var compassionateIndex = 0
    private var bodyScanIndex = 0
    private var scriptureIndex = 0
    private var isDimMode = false
    private var selectedPreMood = "😌 Calm"
    private var selectedPostMood = "😌 Calm"
    private var lastRecordedSessionId: Long = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityHolyMeditationBinding.inflate(layoutInflater)
        setContentView(binding.root)

        dbHelper = NyirabyoDbHelper.getInstance(this)

        setupTheme()
        setupTopTabs()
        setupTodayLaunchers()
        setupPracticesLaunchers()
        setupSessionPlayerControls()
        setupPostSessionControls()
        refreshJournalStats()

        binding.btnBack.setOnClickListener { finish() }
    }

    private fun setupTheme() {
        val themeColors = ThemeHelper.getActiveThemeColors(this)
        binding.tvMeditationHeaderTitle.setTextColor(themeColors.primaryAccent)
        binding.tvMeditationHeaderSubtitle.setTextColor(ContextCompat.getColor(this, R.color.text_tertiary))
    }

    // ================= TOP TABS NAVIGATION =================
    private fun setupTopTabs() {
        val tabs = listOf(
            Triple(binding.tabToday, binding.layoutTodayTab, "TODAY"),
            Triple(binding.tabPractices, binding.layoutPracticesTab, "PRACTICES"),
            Triple(binding.tabLearn, binding.layoutLearnTab, "LEARN"),
            Triple(binding.tabJournal, binding.layoutJournalTab, "JOURNAL")
        )

        for ((tabView, _, _) in tabs) {
            tabView.setOnClickListener {
                switchTab(tabView)
            }
        }
    }

    private fun switchTab(activeTab: TextView) {
        val themeColors = ThemeHelper.getActiveThemeColors(this)
        val tabs = listOf(
            Pair(binding.tabToday, binding.layoutTodayTab),
            Pair(binding.tabPractices, binding.layoutPracticesTab),
            Pair(binding.tabLearn, binding.layoutLearnTab),
            Pair(binding.tabJournal, binding.layoutJournalTab)
        )

        for ((tabView, container) in tabs) {
            if (tabView == activeTab) {
                tabView.setTextColor(themeColors.primaryAccent)
                tabView.setBackgroundResource(R.drawable.card_background_elevated)
                container.visibility = View.VISIBLE
            } else {
                tabView.setTextColor(ContextCompat.getColor(this, R.color.text_secondary))
                tabView.setBackgroundResource(R.drawable.pill_background)
                container.visibility = View.GONE
            }
        }

        if (activeTab == binding.tabJournal) {
            refreshJournalStats()
        }
    }

    // ================= TODAY TAB LAUNCHERS =================
    private fun setupTodayLaunchers() {
        binding.btnStartMorning.setOnClickListener {
            launchSession(PracticeType.MORNING_BREATH, durationMinutes = 3)
        }
        binding.btnStartAfternoon.setOnClickListener {
            launchSession(PracticeType.GROUNDING_54321, durationMinutes = 2)
        }
        binding.btnStartSleep.setOnClickListener {
            launchSession(PracticeType.BODY_SCAN, durationMinutes = 5, startWithDim = true)
        }
    }

    // ================= PRACTICES TAB LAUNCHERS =================
    private fun setupPracticesLaunchers() {
        binding.btnLaunchBreath.setOnClickListener { launchSession(PracticeType.MORNING_BREATH, 3) }
        binding.btnLaunchGrounding.setOnClickListener { launchSession(PracticeType.GROUNDING_54321, 2) }
        binding.btnLaunchOpenAwareness.setOnClickListener { launchSession(PracticeType.OPEN_AWARENESS, 5) }
        binding.btnLaunchCounting.setOnClickListener { launchSession(PracticeType.COUNTING_BREATHS, 3) }
        binding.btnLaunchCompassion.setOnClickListener { launchSession(PracticeType.COMPASSION, 3) }
        binding.btnLaunchBodyScan.setOnClickListener { launchSession(PracticeType.BODY_SCAN, 5, startWithDim = true) }
        binding.btnLaunchWalking.setOnClickListener { launchSession(PracticeType.WALKING, 3) }
        binding.btnLaunchScripture.setOnClickListener { launchSession(PracticeType.SCRIPTURE, 5) }
    }

    // ================= SESSION PLAYER ORCHESTRATION =================
    private fun launchSession(practice: PracticeType, durationMinutes: Int, startWithDim: Boolean = false) {
        currentPractice = practice
        totalMillis = durationMinutes * 60 * 1000L
        millisRemaining = totalMillis
        groundingStepIndex = 0
        breathCount = 1
        compassionateIndex = 0
        bodyScanIndex = 0
        scriptureIndex = 0
        isDimMode = startWithDim
        updateDimOverlay()

        binding.tvPlayerTitle.text = practice.title.uppercase(Locale.getDefault())
        binding.tvPlayerPurpose.text = practice.purpose
        binding.tvPlayerGuidance.text = practice.initialPrompt
        binding.tvPlayerAnchorNotice.text = "Anchor: ${practice.defaultAnchor} • Whisper 'thinking' if mind wanders"
        updateTimerDisplay(millisRemaining)

        // Configure Specialized UI Modules
        binding.moduleGrounding.visibility = if (practice == PracticeType.GROUNDING_54321) View.VISIBLE else View.GONE
        binding.moduleCounting.visibility = if (practice == PracticeType.COUNTING_BREATHS) View.VISIBLE else View.GONE
        binding.moduleScripture.visibility = if (practice == PracticeType.SCRIPTURE) View.VISIBLE else View.GONE

        if (practice == PracticeType.GROUNDING_54321) {
            updateGroundingStepUI()
        } else if (practice == PracticeType.COUNTING_BREATHS) {
            binding.btnCountTap.text = "Exhaled: Count (1 / 5) 🎯"
        } else if (practice == PracticeType.SCRIPTURE) {
            updateScriptureUI()
        }

        // Show player, hide main scroll
        binding.scrollMainContent.visibility = View.GONE
        binding.scrollTabs.visibility = View.GONE
        binding.headerMeditation.visibility = View.GONE
        binding.layoutSessionPlayer.visibility = View.VISIBLE

        startSessionTimer()
    }

    private fun startSessionTimer() {
        isTimerRunning = true
        binding.btnPlayerPause.text = "Pause ⏸️"

        countDownTimer?.cancel()
        countDownTimer = object : CountDownTimer(millisRemaining, 1000L) {
            override fun onTick(msUntilFinished: Long) {
                millisRemaining = msUntilFinished
                updateTimerDisplay(msUntilFinished)
                updateDynamicGuidance(msUntilFinished)
            }

            override fun onFinish() {
                completeSession()
            }
        }.start()
    }

    private fun pauseSessionTimer() {
        isTimerRunning = false
        countDownTimer?.cancel()
        binding.btnPlayerPause.text = "Resume ▶️"
        binding.tvPlayerGuidance.text = "Practice Paused • Rest in Grace"
    }

    private fun updateTimerDisplay(millis: Long) {
        val totalSecs = (millis / 1000).toInt()
        val mins = totalSecs / 60
        val secs = totalSecs % 60
        binding.tvPlayerTimer.text = "%02d:%02d".format(mins, secs)
    }

    private fun updateDynamicGuidance(millisRemaining: Long) {
        val elapsedSec = ((totalMillis - millisRemaining) / 1000).toInt()

        when (currentPractice) {
            PracticeType.MORNING_BREATH -> {
                val cycleSec = elapsedSec % 16
                binding.tvPlayerGuidance.text = when {
                    cycleSec < 5 -> "Feel the breath entering naturally..."
                    cycleSec < 7 -> "Rest with the breath..."
                    cycleSec < 12 -> "Feel the gentle exhale..."
                    else -> "When your mind wanders, gently whisper 'thinking' and return."
                }
            }
            PracticeType.COMPASSION -> {
                val phraseIdx = (elapsedSec / 15) % compassionatePhrases.size
                binding.tvPlayerGuidance.text = compassionatePhrases[phraseIdx]
            }
            PracticeType.BODY_SCAN -> {
                val regionIdx = (elapsedSec / 35).coerceIn(0, bodyScanRegions.size - 1)
                binding.tvPlayerGuidance.text = "Bring gentle awareness to: ${bodyScanRegions[regionIdx]}"
                binding.tvPlayerAnchorNotice.text = "Notice whatever sensation is there without forcing change."
            }
            PracticeType.OPEN_AWARENESS -> {
                val cycle = (elapsedSec / 20) % 3
                binding.tvPlayerGuidance.text = when (cycle) {
                    0 -> "Notice sounds and sensations coming and going..."
                    1 -> "When something catches attention: silently name 'sound', 'thought', or 'feeling'."
                    else -> "For this moment, make room for whatever is here."
                }
            }
            PracticeType.WALKING -> {
                val stepCycle = elapsedSec % 12
                binding.tvPlayerGuidance.text = if (stepCycle < 6) {
                    "Notice feet lifting, moving, and touching the ground."
                } else {
                    "When your mind wanders, gently return to the next step."
                }
            }
            else -> {}
        }
    }

    // Specialized Grounding Steps
    private fun updateGroundingStepUI() {
        if (groundingStepIndex in groundingSteps.indices) {
            val step = groundingSteps[groundingStepIndex]
            binding.tvGroundingStepBadge.text = step.badge
            binding.tvGroundingInstruction.text = step.instruction
            binding.btnNextGroundingStep.text = step.nextLabel
        }
    }

    // Specialized Scripture UI
    private fun updateScriptureUI() {
        if (scriptureVerses.isNotEmpty()) {
            val v = scriptureVerses[scriptureIndex.coerceIn(0, scriptureVerses.size - 1)]
            binding.tvPlayerScriptureVerse.text = v.scripture
            binding.tvPlayerScriptureRef.text = v.reference
        }
    }

    // Session Player Controls
    private fun setupSessionPlayerControls() {
        binding.btnPlayerPause.setOnClickListener {
            if (isTimerRunning) {
                pauseSessionTimer()
            } else {
                startSessionTimer()
            }
        }

        binding.btnPlayerFinish.setOnClickListener {
            completeSession()
        }

        binding.btnPlayerExit.setOnClickListener {
            exitPlayerToSanctuary()
        }

        // Emergency Grounding button
        binding.btnEmergencyGround.setOnClickListener {
            pauseSessionTimer()
            Toast.makeText(
                this,
                "Take a pause. Open your eyes, look around, and feel your feet on the floor. There is no rush 🌿",
                Toast.LENGTH_LONG
            ).show()
            exitPlayerToSanctuary()
        }

        // Grounding Next Step Button
        binding.btnNextGroundingStep.setOnClickListener {
            groundingStepIndex++
            if (groundingStepIndex < groundingSteps.size) {
                updateGroundingStepUI()
            } else {
                completeSession()
            }
        }

        // Counting Breaths Counter
        binding.btnCountTap.setOnClickListener {
            breathCount++
            if (breathCount > 5) breathCount = 1
            binding.btnCountTap.text = "Exhaled: Count ($breathCount / 5) 🎯"
            binding.tvPlayerGuidance.text = "Exhale counted: $breathCount"
            vibrateSubtleTick()
        }

        binding.btnCountReset.setOnClickListener {
            breathCount = 1
            binding.btnCountTap.text = "Exhaled: Count (1 / 5) 🎯"
            binding.tvPlayerGuidance.text = "Reset to 1 • Returning IS the practice"
        }

        // Scripture next verse
        binding.btnPlayerNextVerse.setOnClickListener {
            scriptureIndex = (scriptureIndex + 1) % scriptureVerses.size
            updateScriptureUI()
        }

        // Dim Mode Toggle
        binding.btnDimToggle.setOnClickListener {
            isDimMode = !isDimMode
            updateDimOverlay()
        }

        // Anchor Switchers
        binding.btnAnchorBreath.setOnClickListener {
            binding.tvPlayerAnchorNotice.text = "Anchor: Breath in nose/chest • Return gently when wandering"
            highlightAnchor(binding.btnAnchorBreath)
        }
        binding.btnAnchorSound.setOnClickListener {
            binding.tvPlayerAnchorNotice.text = "Anchor: Ambient room sounds • Let them rise and fall"
            highlightAnchor(binding.btnAnchorSound)
        }
        binding.btnAnchorFeet.setOnClickListener {
            binding.tvPlayerAnchorNotice.text = "Anchor: Sensation of feet on the floor • Grounded & supported"
            highlightAnchor(binding.btnAnchorFeet)
        }
    }

    private fun highlightAnchor(activeView: TextView) {
        val themeColors = ThemeHelper.getActiveThemeColors(this)
        val anchors = listOf(binding.btnAnchorBreath, binding.btnAnchorSound, binding.btnAnchorFeet)
        for (v in anchors) {
            if (v == activeView) {
                v.setTextColor(themeColors.primaryAccent)
                v.setBackgroundResource(R.drawable.card_background_elevated)
            } else {
                v.setTextColor(ContextCompat.getColor(this, R.color.text_secondary))
                v.setBackgroundResource(R.drawable.pill_background)
            }
        }
    }

    private fun updateDimOverlay() {
        binding.dimOverlayView.visibility = if (isDimMode) View.VISIBLE else View.GONE
        binding.btnDimToggle.setTextColor(
            if (isDimMode) ContextCompat.getColor(this, R.color.gold_primary)
            else ContextCompat.getColor(this, R.color.text_secondary)
        )
    }

    private fun completeSession() {
        countDownTimer?.cancel()
        isTimerRunning = false

        val durationMinutes = ((totalMillis - millisRemaining) / 1000 / 60).toInt().coerceAtLeast(1)

        // Save session record
        val record = MeditationSessionRecord(
            practiceId = currentPractice.practiceId,
            practiceName = currentPractice.title,
            durationMinutes = durationMinutes,
            timestamp = System.currentTimeMillis(),
            note = "",
            preMood = selectedPreMood,
            postMood = selectedPostMood,
            isCompleted = true
        )
        lastRecordedSessionId = dbHelper.addMeditationRecord(record)

        if (!isDimMode) {
            vibratePeaceChime()
        }

        // Show Reflection Modal
        binding.tvPostSessionCompletedSummary.text = "Well done practicing attention today with ${currentPractice.title} (${durationMinutes}m)."
        binding.layoutSessionPlayer.visibility = View.GONE
        binding.layoutPostSessionModal.visibility = View.VISIBLE
    }

    private fun exitPlayerToSanctuary() {
        countDownTimer?.cancel()
        isTimerRunning = false
        binding.layoutSessionPlayer.visibility = View.GONE
        binding.layoutPostSessionModal.visibility = View.GONE
        binding.scrollMainContent.visibility = View.VISIBLE
        binding.scrollTabs.visibility = View.VISIBLE
        binding.headerMeditation.visibility = View.VISIBLE
        refreshJournalStats()
    }

    // ================= POST-SESSION REFLECTION MODAL =================
    private fun setupPostSessionControls() {
        val moods = listOf(
            Pair(binding.moodCalm, "😌 Calm"),
            Pair(binding.moodGrounded, "🌿 Grounded"),
            Pair(binding.moodPeaceful, "🕊️ Peaceful"),
            Pair(binding.moodTired, "🥱 Sleepy"),
            Pair(binding.moodNeutral, "😐 Neutral")
        )

        for ((view, mood) in moods) {
            view.setOnClickListener {
                selectedPostMood = mood
                for ((v, _) in moods) {
                    if (v == view) {
                        v.setTextColor(ContextCompat.getColor(this, R.color.gold_primary))
                        v.setBackgroundResource(R.drawable.card_background_elevated)
                    } else {
                        v.setTextColor(ContextCompat.getColor(this, R.color.text_secondary))
                        v.setBackgroundResource(R.drawable.pill_background)
                    }
                }
            }
        }

        binding.btnSaveReflection.setOnClickListener {
            val note = binding.etReflectionNote.text.toString().trim()
            if (lastRecordedSessionId > 0 && note.isNotEmpty()) {
                dbHelper.updateMeditationNote(lastRecordedSessionId, note, selectedPostMood)
            }
            binding.etReflectionNote.text.clear()
            exitPlayerToSanctuary()
            Toast.makeText(this, "🕊️ Session saved to My Practice. Walk in peace.", Toast.LENGTH_SHORT).show()
        }
    }

    // ================= MY PRACTICE JOURNAL STATS =================
    private fun refreshJournalStats() {
        val daysThisWeek = dbHelper.getMeditationDaysThisWeek()
        val totalMinutes = dbHelper.getTotalMeditationMinutes()

        binding.tvDaysPracticed.text = "$daysThisWeek / 7 Days"
        binding.tvTotalMindfulMinutes.text = "$totalMinutes Mins"

        val records = dbHelper.getMeditationRecords(30)
        binding.layoutCompletedSessionsList.removeAllViews()

        if (records.isEmpty()) {
            binding.tvEmptyJournal.visibility = View.VISIBLE
            binding.layoutCompletedSessionsList.visibility = View.GONE
        } else {
            binding.tvEmptyJournal.visibility = View.GONE
            binding.layoutCompletedSessionsList.visibility = View.VISIBLE

            val dateFormat = SimpleDateFormat("MMM d • h:mm a", Locale.getDefault())
            for (rec in records) {
                val itemBinding = ItemMeditationSessionBinding.inflate(
                    LayoutInflater.from(this),
                    binding.layoutCompletedSessionsList,
                    false
                )
                itemBinding.tvItemPracticeTitle.text = rec.practiceName
                itemBinding.tvItemDuration.text = "${rec.durationMinutes} min"
                itemBinding.tvItemTimestamp.text = dateFormat.format(Date(rec.timestamp))
                itemBinding.tvItemMood.text = if (rec.postMood.isNotEmpty()) rec.postMood else "🕊️ Completed"

                if (rec.note.isNotEmpty()) {
                    itemBinding.tvItemNote.visibility = View.VISIBLE
                    itemBinding.tvItemNote.text = "“${rec.note}”"
                } else {
                    itemBinding.tvItemNote.visibility = View.GONE
                }

                binding.layoutCompletedSessionsList.addView(itemBinding.root)
            }
        }
    }

    private fun vibratePeaceChime() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vm = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                vm?.defaultVibrator?.vibrate(
                    VibrationEffect.createWaveform(longArrayOf(0, 150, 100, 250), -1)
                )
            } else {
                @Suppress("DEPRECATION")
                val v = getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
                @Suppress("DEPRECATION")
                v?.vibrate(longArrayOf(0, 150, 100, 250), -1)
            }
        } catch (_: Exception) {}
    }

    private fun vibrateSubtleTick() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val v = getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
                v?.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_TICK))
            }
        } catch (_: Exception) {}
    }

    override fun onDestroy() {
        super.onDestroy()
        countDownTimer?.cancel()
    }
}
