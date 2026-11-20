package com.example.nyirabyolife.ui.godfirst

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.Build
import android.os.Bundle
import android.os.CountDownTimer
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.speech.tts.TextToSpeech
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
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin

class HolyMeditationActivity : AppCompatActivity(), TextToSpeech.OnInitListener {

    enum class PracticeType(
        val practiceId: String,
        val title: String,
        val purpose: String,
        val defaultMinutes: Int,
        val defaultAnchor: String,
        val initialPrompt: String,
        val openingVoiceScript: String
    ) {
        MORNING_BREATH(
            "morning_breath",
            "Morning Breath Awareness",
            "Build attention and begin the day with intention",
            3,
            "Breath (Nose, chest or belly)",
            "Feel one breath. When you notice your attention has wandered, gently begin again.",
            "Welcome to your morning practice. Settle comfortably into your seat, letting your hands rest and your feet be supported. Notice where you feel the breath moving in your body: your nose, your chest, or your belly. Let each breath move naturally. There is no need to make it deep or slow. When your attention wanders, simply whisper 'thinking', and begin again."
        ),
        GROUNDING_54321(
            "grounding_54321",
            "Grounding 5-4-3-2-1",
            "Reconnect with the present when your mind feels busy",
            2,
            "Senses & feet on floor",
            "There is no need to force calm. Look around slowly and notice what is here.",
            "Welcome to grounding. Take a comfortable, gentle breath, and feel the solid ground beneath your feet. There is no need to force calm. Step one: look around you slowly, and name five things you can see right now."
        ),
        OPEN_AWARENESS(
            "open_awareness",
            "Open Awareness (Inner Peace)",
            "Make room for what is here without needing to fix it",
            5,
            "Sitting posture & sounds",
            "For this moment, you can make room for what is here.",
            "Settle into a comfortable posture, and notice the support beneath you. For this practice, you don't need to fix or change anything. Simply allow sounds, sensations, and thoughts to come and go naturally, making room for whatever is here."
        ),
        COUNTING_BREATHS(
            "counting_breaths",
            "Counting Breaths (Focus)",
            "Practice returning your attention to one task",
            3,
            "Exhale count (1 to 5)",
            "Each time you begin again, you are practicing attention.",
            "Let's practice returning attention to one task. Settle into your seat. As each exhale naturally completes, silently count one. On the next exhale, count two, continuing up to five. If you lose count, simply begin again at one. Each time you begin again, you are strengthening attention."
        ),
        COMPASSION(
            "compassion",
            "Compassionate Phrases",
            "Respond to yourself with warmth and patience",
            3,
            "Compassionate phrases",
            "Silently repeat a phrase that feels natural, leaving space between repetitions.",
            "Settle into your seat, and let your body be supported. Silently repeat these gentle words with space between them: May I be kind to myself. May I have patience. May I find moments of ease."
        ),
        BODY_SCAN(
            "body_scan",
            "Gentle Body Scan (Sleep)",
            "Wind down and notice areas that might be holding tension",
            5,
            "Body sensations & Dim Mode",
            "There is nothing to achieve. Allow yourself to rest.",
            "Lie comfortably, and allow the bed to support your entire weight. There is nothing to achieve, and nowhere else you need to be. Bring gentle awareness down to your feet and toes, allowing any tension to soften."
        ),
        WALKING(
            "walking",
            "Mindful Walking (Movement)",
            "Practice attention when sitting still doesn't feel right",
            3,
            "Feet touching the ground",
            "Notice each step. When your mind wanders, gently return to the next step.",
            "Begin walking at a comfortable, natural pace with your eyes softly open. Notice the feeling of your feet lifting, moving through the air, and touching the ground. When your mind wanders, gently return to the very next step."
        ),
        SCRIPTURE(
            "scripture",
            "Sacred Scripture Contemplation",
            "Lectio Divina: Contemplating the Living Word & Peace",
            5,
            "Living Word & Presence",
            "Be still, and know that I am God. Rest in His lovingkindness.",
            "Be still, and know that I am God. In peace I will lie down and sleep, for You alone, Lord, make me dwell in safety. Savor His living Word and rest in Christ's peace."
        )
    }

    private data class GroundingStep(
        val badge: String,
        val instruction: String,
        val nextLabel: String,
        val voiceScript: String
    )

    private val groundingSteps = listOf(
        GroundingStep("STEP 1 OF 6 • LOOK AROUND", "Look around and name 5 things you can see right now. 👁️", "Next Step (4 Things You Feel) ➔", "Step one: Look around slowly and name five things you can see. Notice their colors, textures, and shapes."),
        GroundingStep("STEP 2 OF 6 • PHYSICAL TOUCH", "Notice 4 things you can feel right now (clothes, chair, air on skin). ✋", "Next Step (3 Sounds) ➔", "Step two: Notice four things you can feel with your body right now. Perhaps your clothes, the chair beneath you, or the cool air on your skin."),
        GroundingStep("STEP 3 OF 6 • LISTEN CLOSELY", "Listen for 3 sounds around you (distant hum, clock, breathing). 👂", "Next Step (2 Smells) ➔", "Step three: Listen gently for three sounds around you. Let them rise and fall naturally without straining."),
        GroundingStep("STEP 4 OF 6 • SENSE SMELL", "Notice 2 things you can smell around you (fresh air, room scent). 👃", "Next Step (1 Taste) ➔", "Step four: Notice two things you can smell right now in your space."),
        GroundingStep("STEP 5 OF 6 • SENSE TASTE", "Notice 1 thing you can taste, or the resting sensation of your mouth. 👅", "Next Step (Ground Your Feet) ➔", "Step five: Notice one thing you can taste, or the simple resting sensation inside your mouth."),
        GroundingStep("STEP 6 OF 6 • SOLID SUPPORT", "Feel the solid ground beneath your feet and the support under your body. 🦶", "Complete Grounding 🕊️", "Step six: Feel the solid support beneath your body, and your feet resting firmly on the ground. You are present, grounded, and safe.")
    )

    private val compassionatePhrases = listOf(
        "“May I be kind to myself.”",
        "“May I have patience.”",
        "“May I find moments of ease.”",
        "“May I be gentle with my thoughts.”"
    )

    private val compassionateVoiceScripts = listOf(
        "May I be kind to myself.",
        "May I have patience with my journey.",
        "May I find moments of ease and peace.",
        "May I be gentle with my thoughts."
    )

    private val bodyScanRegions = listOf(
        Pair("Feet & Toes 🦶", "Bring gentle awareness down to your feet and toes. Invite any tension to soften and release."),
        Pair("Lower Legs & Knees 🦵", "Allow awareness to travel up through your lower legs, calves, and knees. Letting them feel heavy and relaxed."),
        Pair("Hips, Pelvis & Lower Back", "Notice your hips, pelvis, and lower back. Sinking gently into the support beneath you."),
        Pair("Belly & Natural Breathing", "Notice your belly. Let your breath rise and fall effortlessly, without any force."),
        Pair("Shoulders & Upper Back", "Feel your shoulders dropping away from your ears, releasing any burdens of the day."),
        Pair("Arms & Hands ✋", "Soften your hands, your fingers, and your arms, resting limp and peaceful."),
        Pair("Neck & Softened Jaw", "Notice your neck. Gently unclench your jaw and let your tongue rest loosely."),
        Pair("Eyes, Forehead & Face 😌", "Smooth the forehead, softening the eyes and face."),
        Pair("Entire Body at Peaceful Rest 🌙", "Allow your whole body to rest together in quiet, undisturbed peace.")
    )

    private data class ScriptureVerse(val scripture: String, val reference: String, val voiceScript: String)
    private val scriptureVerses = listOf(
        ScriptureVerse("“Let the morning bring me word of your unfailing love, for I have put my trust in you. Show me the way I should go, for to you I entrust my life.”", "Psalm 143:8", "Let the morning bring me word of your unfailing love, for I have put my trust in you. Show me the way I should go, for to you I entrust my life. Psalm 143:8"),
        ScriptureVerse("“Peace I leave with you; my peace I give to you. I do not give to you as the world gives. Do not let your hearts be troubled and do not be afraid.”", "John 14:27", "Peace I leave with you; my peace I give to you. Do not let your hearts be troubled, and do not be afraid. John 14:27"),
        ScriptureVerse("“Be still, and know that I am God! I will be honored by every nation. I will be honored throughout the world.”", "Psalm 46:10", "Be still, and know that I am God. Psalm 46:10"),
        ScriptureVerse("“Come to me, all you who are weary and burdened, and I will give you rest. Take my yoke upon you and learn from me, for I am gentle and humble in heart.”", "Matthew 11:28-29", "Come to me, all you who are weary and burdened, and I will give you rest. Matthew 11:28"),
        ScriptureVerse("“In peace I will lie down and sleep, for you alone, LORD, make me dwell in safety.”", "Psalm 4:8", "In peace I will lie down and sleep, for you alone, Lord, make me dwell in safety. Psalm 4:8"),
        ScriptureVerse("“The steadfast love of the LORD never ceases; his mercies never come to an end; they are new every morning.”", "Lamentations 3:22-23", "The steadfast love of the Lord never ceases; His mercies are new every morning. Great is your faithfulness.")
    )

    private lateinit var binding: ActivityHolyMeditationBinding
    private lateinit var dbHelper: NyirabyoDbHelper

    // TextToSpeech Engine
    private var tts: TextToSpeech? = null
    private var isTtsReady = false
    private var isVoiceEnabled = true

    // Ambient Soundscape Engine (432 Hz Healing Drone & Gentle Rain)
    private val ambientEngine = AmbientSoundEngine()

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
    private var lastSpokenSecond = -1
    private var selectedPreMood = "😌 Calm"
    private var selectedPostMood = "😌 Calm"
    private var lastRecordedSessionId: Long = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityHolyMeditationBinding.inflate(layoutInflater)
        setContentView(binding.root)

        dbHelper = NyirabyoDbHelper.getInstance(this)

        // Initialize Native Speech Engine for Headphone/Eyes-closed Guided Meditation
        tts = TextToSpeech(this, this)

        setupTheme()
        setupTopTabs()
        setupTodayLaunchers()
        setupPracticesLaunchers()
        setupSessionPlayerControls()
        setupPostSessionControls()
        refreshJournalStats()

        binding.btnBack.setOnClickListener { finish() }
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val result = tts?.setLanguage(Locale.US)
            if (result != TextToSpeech.LANG_MISSING_DATA && result != TextToSpeech.LANG_NOT_SUPPORTED) {
                isTtsReady = true
                // Calming meditation cadence: slower rate, grounded soothing pitch
                tts?.setSpeechRate(0.82f)
                tts?.setPitch(0.95f)
            }
        }
    }

    private fun speakGuidance(text: String, queueMode: Int = TextToSpeech.QUEUE_FLUSH) {
        if (!isVoiceEnabled || !isTtsReady) return
        try {
            val params = Bundle().apply {
                putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, 0.95f)
            }
            tts?.speak(text, queueMode, params, "GUIDE_${System.currentTimeMillis()}")
        } catch (_: Exception) {}
    }

    private fun stopGuidanceVoice() {
        try {
            tts?.stop()
        } catch (_: Exception) {}
    }

    /**
     * Synthesizes and plays a resonant Tibetan singing bowl chime in real-time.
     * Pure mathematical acoustics with fundamental + warm harmonics and exponential decay envelope.
     */
    private fun playMeditationChime(frequency: Double = 432.0, durationMs: Int = 2200) {
        Thread {
            try {
                val sampleRate = 44100
                val numSamples = (sampleRate * (durationMs / 1000.0)).toInt()
                val buffer = ShortArray(numSamples)
                for (i in 0 until numSamples) {
                    val t = i.toDouble() / sampleRate
                    val decay = exp(-2.8 * t / (durationMs / 1000.0))
                    val sample = (sin(2.0 * PI * frequency * t) * 0.65 +
                                  sin(2.0 * PI * (frequency * 1.5) * t) * 0.22 +
                                  sin(2.0 * PI * (frequency * 2.76) * t) * 0.13) * decay
                    buffer[i] = (sample * Short.MAX_VALUE * 0.40).toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
                }

                val track = AudioTrack.Builder()
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_MEDIA)
                            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                            .build()
                    )
                    .setAudioFormat(
                        AudioFormat.Builder()
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .setSampleRate(sampleRate)
                            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                            .build()
                    )
                    .setBufferSizeInBytes(buffer.size * 2)
                    .setTransferMode(AudioTrack.MODE_STATIC)
                    .build()

                track.write(buffer, 0, buffer.size)
                track.play()
                Thread.sleep(durationMs.toLong() + 150)
                track.stop()
                track.release()
            } catch (_: Exception) {}
        }.start()
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
        lastSpokenSecond = -1
        isDimMode = startWithDim
        updateDimOverlay()

        binding.tvPlayerTitle.text = practice.title.uppercase(Locale.getDefault())
        binding.tvPlayerPurpose.text = practice.purpose
        binding.tvPlayerGuidance.text = practice.initialPrompt
        binding.tvPlayerAnchorNotice.text = "Anchor: ${practice.defaultAnchor} • Whisper 'thinking' if mind wanders"
        updateTimerDisplay(millisRemaining)
        updateVoiceButtonUI()
        updateAmbientButtonUI()

        // Configure Specialized UI Modules
        binding.moduleGrounding.visibility = if (practice == PracticeType.GROUNDING_54321) View.VISIBLE else View.GONE
        binding.moduleCounting.visibility = if (practice == PracticeType.COUNTING_BREATHS) View.VISIBLE else View.GONE
        binding.moduleScripture.visibility = if (practice == PracticeType.SCRIPTURE) View.VISIBLE else View.GONE

        if (practice == PracticeType.GROUNDING_54321) {
            updateGroundingStepUI(speak = false)
        } else if (practice == PracticeType.COUNTING_BREATHS) {
            binding.btnCountTap.text = "Exhaled: Count (1 / 5) 🎯"
        } else if (practice == PracticeType.SCRIPTURE) {
            updateScriptureUI(speak = false)
        }

        // Show player, hide main scroll
        binding.scrollMainContent.visibility = View.GONE
        binding.scrollTabs.visibility = View.GONE
        binding.headerMeditation.visibility = View.GONE
        binding.layoutSessionPlayer.visibility = View.VISIBLE

        // Start Bell Chime (432 Hz) & Voice Script
        playMeditationChime(432.0, 2500)
        speakGuidance(practice.openingVoiceScript)
        ambientEngine.start()

        startSessionTimer()
    }

    private fun startSessionTimer() {
        isTimerRunning = true
        binding.btnPlayerPause.text = "Pause ⏸️"
        ambientEngine.resume()

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
        stopGuidanceVoice()
        ambientEngine.pause()
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
        if (elapsedSec == lastSpokenSecond) return
        lastSpokenSecond = elapsedSec

        when (currentPractice) {
            PracticeType.GROUNDING_54321 -> {
                // Auto-advance grounding step every 20 seconds for eyes-closed/headphone mode
                val stepTarget = (elapsedSec / 20).coerceIn(0, groundingSteps.size - 1)
                if (stepTarget != groundingStepIndex) {
                    groundingStepIndex = stepTarget
                    updateGroundingStepUI(speak = true)
                }
            }
            PracticeType.BODY_SCAN -> {
                val regionIdx = (elapsedSec / 33).coerceIn(0, bodyScanRegions.size - 1)
                if (regionIdx != bodyScanIndex) {
                    bodyScanIndex = regionIdx
                    val (regionName, voiceScript) = bodyScanRegions[regionIdx]
                    binding.tvPlayerGuidance.text = "Awareness to: $regionName"
                    binding.tvPlayerAnchorNotice.text = "Notice sensation without forcing any change."
                    playMeditationChime(396.0, 1800)
                    speakGuidance(voiceScript)
                }
            }
            PracticeType.COMPASSION -> {
                val phraseIdx = (elapsedSec / 18).coerceIn(0, compassionatePhrases.size - 1)
                if (phraseIdx != compassionateIndex) {
                    compassionateIndex = phraseIdx
                    binding.tvPlayerGuidance.text = compassionatePhrases[phraseIdx]
                    playMeditationChime(528.0, 1500)
                    speakGuidance(compassionateVoiceScripts[phraseIdx])
                }
            }
            PracticeType.MORNING_BREATH -> {
                val cycleSec = elapsedSec % 16
                binding.tvPlayerGuidance.text = when {
                    cycleSec < 5 -> "Feel the breath entering naturally..."
                    cycleSec < 7 -> "Rest with the breath..."
                    cycleSec < 12 -> "Feel the gentle exhale..."
                    else -> "When your mind wanders, gently whisper 'thinking' and return."
                }
                if (elapsedSec == 60) {
                    speakGuidance("When you notice your mind has wandered to thoughts or planning, gently acknowledge it as thinking, and return your attention to the next breath.")
                } else if (elapsedSec == 120) {
                    speakGuidance("Feel one breath. Savor this moment of presence.")
                }
            }
            PracticeType.OPEN_AWARENESS -> {
                val cycle = (elapsedSec / 20) % 3
                binding.tvPlayerGuidance.text = when (cycle) {
                    0 -> "Notice sounds and sensations coming and going..."
                    1 -> "When something catches attention: silently name 'sound', 'thought', or 'feeling'."
                    else -> "For this moment, make room for whatever is here."
                }
                if (elapsedSec == 60) {
                    speakGuidance("If a sound, sensation, or thought catches your attention, silently name it: sound, thought, or feeling, then return to the feeling of sitting.")
                }
            }
            PracticeType.WALKING -> {
                val stepCycle = elapsedSec % 12
                binding.tvPlayerGuidance.text = if (stepCycle < 6) {
                    "Notice feet lifting, moving, and touching the ground."
                } else {
                    "When your mind wanders, gently return to the next step."
                }
                if (elapsedSec == 60) {
                    speakGuidance("Stay present with each step. Notice your feet lifting, moving, and touching the ground.")
                }
            }
            else -> {}
        }
    }

    // Specialized Grounding Steps with Voice & Chime
    private fun updateGroundingStepUI(speak: Boolean = true) {
        if (groundingStepIndex in groundingSteps.indices) {
            val step = groundingSteps[groundingStepIndex]
            binding.tvGroundingStepBadge.text = step.badge
            binding.tvGroundingInstruction.text = step.instruction
            binding.btnNextGroundingStep.text = step.nextLabel

            if (speak) {
                playMeditationChime(528.0, 1600)
                speakGuidance(step.voiceScript)
            }
        }
    }

    // Specialized Scripture UI
    private fun updateScriptureUI(speak: Boolean = true) {
        if (scriptureVerses.isNotEmpty()) {
            val v = scriptureVerses[scriptureIndex.coerceIn(0, scriptureVerses.size - 1)]
            binding.tvPlayerScriptureVerse.text = v.scripture
            binding.tvPlayerScriptureRef.text = v.reference
            if (speak) {
                playMeditationChime(432.0, 2000)
                speakGuidance(v.voiceScript)
            }
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

        // Voice Guidance Toggle
        binding.btnVoiceToggle.setOnClickListener {
            isVoiceEnabled = !isVoiceEnabled
            if (!isVoiceEnabled) {
                stopGuidanceVoice()
            } else {
                speakGuidance("Voice guidance enabled.")
            }
            updateVoiceButtonUI()
        }

        // Emergency Grounding button
        binding.btnEmergencyGround.setOnClickListener {
            pauseSessionTimer()
            stopGuidanceVoice()
            speakGuidance("Take a gentle pause. Open your eyes, look around, and feel your feet firmly on the ground. There is no rush.")
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
                updateGroundingStepUI(speak = true)
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
            if (isVoiceEnabled) {
                speakGuidance("$breathCount")
            }
        }

        binding.btnCountReset.setOnClickListener {
            breathCount = 1
            binding.btnCountTap.text = "Exhaled: Count (1 / 5) 🎯"
            binding.tvPlayerGuidance.text = "Reset to 1 • Returning IS the practice"
            if (isVoiceEnabled) {
                speakGuidance("One. Beginning again.")
            }
        }

        // Scripture next verse
        binding.btnPlayerNextVerse.setOnClickListener {
            scriptureIndex = (scriptureIndex + 1) % scriptureVerses.size
            updateScriptureUI(speak = true)
        }

        // Dim Mode Toggle
        binding.btnDimToggle.setOnClickListener {
            isDimMode = !isDimMode
            updateDimOverlay()
        }

        // Ambient Sound Toggle (432 Hz Drone / Gentle Rain / Mute)
        binding.btnAmbientToggle.setOnClickListener {
            val newMode = ambientEngine.toggleNextMode()
            updateAmbientButtonUI()
            speakGuidance("Background sound: ${newMode.shortLabel}")
        }

        // Anchor Switchers
        binding.btnAnchorBreath.setOnClickListener {
            binding.tvPlayerAnchorNotice.text = "Anchor: Breath in nose/chest • Return gently when wandering"
            highlightAnchor(binding.btnAnchorBreath)
            speakGuidance("Anchor switched to natural breath.")
        }
        binding.btnAnchorSound.setOnClickListener {
            binding.tvPlayerAnchorNotice.text = "Anchor: Ambient room sounds • Let them rise and fall"
            highlightAnchor(binding.btnAnchorSound)
            speakGuidance("Anchor switched to sounds around you.")
        }
        binding.btnAnchorFeet.setOnClickListener {
            binding.tvPlayerAnchorNotice.text = "Anchor: Sensation of feet on the floor • Grounded & supported"
            highlightAnchor(binding.btnAnchorFeet)
            speakGuidance("Anchor switched to feet on the floor.")
        }
    }

    private fun updateVoiceButtonUI() {
        if (isVoiceEnabled) {
            binding.btnVoiceToggle.text = "🔊 Voice: ON"
            binding.btnVoiceToggle.setTextColor(ContextCompat.getColor(this, R.color.emerald_accent))
        } else {
            binding.btnVoiceToggle.text = "🔇 Voice: OFF"
            binding.btnVoiceToggle.setTextColor(ContextCompat.getColor(this, R.color.text_secondary))
        }
    }

    private fun updateAmbientButtonUI() {
        val mode = ambientEngine.getMode()
        val themeColors = ThemeHelper.getActiveThemeColors(this)
        binding.btnAmbientToggle.text = mode.label
        if (mode != AmbientSoundEngine.AmbientMode.OFF) {
            binding.btnAmbientToggle.setTextColor(themeColors.primaryAccent)
            binding.btnAmbientToggle.setBackgroundResource(R.drawable.card_background_elevated)
        } else {
            binding.btnAmbientToggle.setTextColor(ContextCompat.getColor(this, R.color.text_secondary))
            binding.btnAmbientToggle.setBackgroundResource(R.drawable.pill_background)
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
        stopGuidanceVoice()
        ambientEngine.stop()

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
            playMeditationChime(528.0, 3000)
            speakGuidance("Your practice is complete. Walk in peace.")
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
        stopGuidanceVoice()
        ambientEngine.stop()
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
        tts?.stop()
        tts?.shutdown()
        ambientEngine.stop()
    }
}
