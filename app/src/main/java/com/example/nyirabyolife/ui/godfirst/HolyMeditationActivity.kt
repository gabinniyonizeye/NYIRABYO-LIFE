package com.example.nyirabyolife.ui.godfirst

import android.content.Context
import android.os.Build
import android.os.Bundle
import android.os.CountDownTimer
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.example.nyirabyolife.R
import com.example.nyirabyolife.data.local.NyirabyoDbHelper
import com.example.nyirabyolife.data.model.FocusSession
import com.example.nyirabyolife.databinding.ActivityHolyMeditationBinding
import com.example.nyirabyolife.util.ThemeHelper

class HolyMeditationActivity : AppCompatActivity() {

    enum class MeditationMode(
        val title: String,
        val subtitle: String,
        val sessionKey: String
    ) {
        MORNING(
            "MORNING DAWN MEDITATION",
            "Awakening in God's Lovingkindness ☀️",
            "MEDITATION_MORNING"
        ),
        MIDDAY(
            "MIDDAY PEACE & HABITATION",
            "Centering in Christ's Stillness 🕊️",
            "MEDITATION_MIDDAY"
        ),
        EVENING(
            "EVENING SOLITUDE & REST",
            "Resting in Father's Protective Care 🌙",
            "MEDITATION_EVENING"
        ),
        WARFARE(
            "SPIRITUAL WARFARE & URGE RESET",
            "Casting Down Imaginations & Standing in Christ 🛡️",
            "MEDITATION_WARFARE"
        ),
        LECTIO(
            "LECTIO DIVINA: SACRED WORD",
            "Contemplating the Living Scriptures 📖",
            "MEDITATION_LECTIO"
        )
    }

    private data class MeditationVerse(val scripture: String, val reference: String)

    private lateinit var binding: ActivityHolyMeditationBinding
    private lateinit var dbHelper: NyirabyoDbHelper

    private var currentMode = MeditationMode.MORNING
    private var selectedDurationMinutes = 5
    private var totalMillis = 5 * 60 * 1000L
    private var millisRemaining = 5 * 60 * 1000L
    private var isTimerRunning = false
    private var countDownTimer: CountDownTimer? = null
    private var currentVerseIndex = 0

    private val versesByMode = mapOf(
        MeditationMode.MORNING to listOf(
            MeditationVerse(
                "“Let the morning bring me word of your unfailing love, for I have put my trust in you. Show me the way I should go, for to you I entrust my life.”",
                "Psalm 143:8"
            ),
            MeditationVerse(
                "“The steadfast love of the LORD never ceases; his mercies never come to an end; they are new every morning; great is your faithfulness.”",
                "Lamentations 3:22-23"
            ),
            MeditationVerse(
                "“In the morning, LORD, you hear my voice; in the morning I lay my requests before you and wait expectantly.”",
                "Psalm 5:3"
            ),
            MeditationVerse(
                "“Awake, my soul! Awake, harp and lyre! I will awaken the dawn. I will give thanks to you, LORD, among the nations.”",
                "Psalm 57:8-9"
            )
        ),
        MeditationMode.MIDDAY to listOf(
            MeditationVerse(
                "“Peace I leave with you; my peace I give to you. I do not give to you as the world gives. Do not let your hearts be troubled and do not be afraid.”",
                "John 14:27"
            ),
            MeditationVerse(
                "“Be still, and know that I am God! I will be honored by every nation. I will be honored throughout the world.”",
                "Psalm 46:10"
            ),
            MeditationVerse(
                "“Remain in me, as I also remain in you. No branch can bear fruit by itself; it must remain in the vine. Neither can you bear fruit unless you remain in me.”",
                "John 15:4"
            ),
            MeditationVerse(
                "“Let the peace of Christ rule in your hearts, since as members of one body you were called to peace. And be thankful.”",
                "Colossians 3:15"
            )
        ),
        MeditationMode.EVENING to listOf(
            MeditationVerse(
                "“In peace I will lie down and sleep, for you alone, LORD, make me dwell in safety.”",
                "Psalm 4:8"
            ),
            MeditationVerse(
                "“Whoever dwells in the shelter of the Most High will rest in the shadow of the Almighty. I will say of the LORD, ‘He is my refuge and my fortress, my God, in whom I trust.’”",
                "Psalm 91:1-2"
            ),
            MeditationVerse(
                "“Come to me, all you who are weary and burdened, and I will give you rest. Take my yoke upon you and learn from me, for I am gentle and humble in heart.”",
                "Matthew 11:28-29"
            ),
            MeditationVerse(
                "“When you lie down, you will not be afraid; when you lie down, your sleep will be sweet.”",
                "Proverbs 3:24"
            )
        ),
        MeditationMode.WARFARE to listOf(
            MeditationVerse(
                "“The weapons we fight with are not the weapons of the world. On the contrary, they have divine power to demolish strongholds... and we take captive every thought to make it obedient to Christ.”",
                "2 Corinthians 10:4-5"
            ),
            MeditationVerse(
                "“Submit yourselves, then, to God. Resist the devil, and he will flee from you. Come near to God and he will come near to you.”",
                "James 4:7-8"
            ),
            MeditationVerse(
                "“No temptation has overtaken you except what is common to mankind. And God is faithful; he will not let you be tempted beyond what you can bear.”",
                "1 Corinthians 10:13"
            ),
            MeditationVerse(
                "“Put on the full armor of God, so that you can take your stand against the devil’s schemes.”",
                "Ephesians 6:11"
            )
        ),
        MeditationMode.LECTIO to listOf(
            MeditationVerse(
                "“Keep this Book of the Law always on your lips; meditate on it day and night, so that you may be careful to do everything written in it. Then you will be prosperous and successful.”",
                "Joshua 1:8"
            ),
            MeditationVerse(
                "“Blessed is the one whose delight is in the law of the LORD, and who meditates on his law day and night. That person is like a tree planted by streams of water.”",
                "Psalm 1:2-3"
            ),
            MeditationVerse(
                "“Let the message of Christ dwell among you richly as you teach and admonish one another with all wisdom through psalms, hymns, and songs from the Spirit.”",
                "Colossians 3:16"
            ),
            MeditationVerse(
                "“Your word is a lamp for my feet, a light on my path.”",
                "Psalm 119:105"
            )
        )
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityHolyMeditationBinding.inflate(layoutInflater)
        setContentView(binding.root)

        dbHelper = NyirabyoDbHelper.getInstance(this)

        binding.btnBack.setOnClickListener { finish() }

        val themeColors = ThemeHelper.getActiveThemeColors(this)
        binding.tvMeditationHeaderTitle.setTextColor(themeColors.primaryAccent)
        binding.btnNextVerse.setTextColor(themeColors.secondaryAccent)
        val startPill = ThemeHelper.createPillDrawable(this, themeColors)
        startPill.setColor(themeColors.primaryAccent)
        binding.btnStartMeditation.background = startPill
        binding.btnStartMeditation.setTextColor(ContextCompat.getColor(this, R.color.bg_dark))
        binding.cardScriptureMeditation.background = ThemeHelper.createCardDrawable(this, themeColors, isElevated = true)

        setupModeTabs()
        setupDurationPills()
        switchMode(MeditationMode.MORNING)

        binding.btnNextVerse.setOnClickListener {
            val list = versesByMode[currentMode] ?: emptyList()
            if (list.isNotEmpty()) {
                currentVerseIndex = (currentVerseIndex + 1) % list.size
                showCurrentVerse()
            }
        }

        binding.btnStartMeditation.setOnClickListener {
            if (isTimerRunning) {
                pauseMeditation()
            } else {
                startMeditation()
            }
        }
    }

    private fun setupModeTabs() {
        val tabMap = listOf(
            Pair(binding.tabModeMorning, MeditationMode.MORNING),
            Pair(binding.tabModeMidday, MeditationMode.MIDDAY),
            Pair(binding.tabModeEvening, MeditationMode.EVENING),
            Pair(binding.tabModeWarfare, MeditationMode.WARFARE),
            Pair(binding.tabModeLectio, MeditationMode.LECTIO)
        )

        for ((view, mode) in tabMap) {
            view.setOnClickListener {
                if (currentMode != mode) {
                    if (isTimerRunning) pauseMeditation()
                    switchMode(mode)
                }
            }
        }
    }

    private fun switchMode(mode: MeditationMode) {
        currentMode = mode
        currentVerseIndex = 0

        // Highlight active tab
        val tabMap = listOf(
            Pair(binding.tabModeMorning, MeditationMode.MORNING),
            Pair(binding.tabModeMidday, MeditationMode.MIDDAY),
            Pair(binding.tabModeEvening, MeditationMode.EVENING),
            Pair(binding.tabModeWarfare, MeditationMode.WARFARE),
            Pair(binding.tabModeLectio, MeditationMode.LECTIO)
        )

        val themeColors = ThemeHelper.getActiveThemeColors(this)
        for ((view, m) in tabMap) {
            if (m == mode) {
                view.setTextColor(themeColors.primaryAccent)
                view.setBackgroundResource(R.drawable.card_background_elevated)
            } else {
                view.setTextColor(ContextCompat.getColor(this, R.color.text_secondary))
                view.setBackgroundResource(R.drawable.pill_background)
            }
        }

        binding.tvMeditationHeaderTitle.text = mode.title
        binding.tvMeditationHeaderSubtitle.text = mode.subtitle

        showCurrentVerse()
        updateBreathPhase(millisRemaining)
    }

    private fun showCurrentVerse() {
        val list = versesByMode[currentMode] ?: emptyList()
        val themeColors = ThemeHelper.getActiveThemeColors(this)
        if (list.isNotEmpty()) {
            val v = list[currentVerseIndex.coerceIn(0, list.size - 1)]
            binding.tvMeditationVerse.text = v.scripture
            binding.tvMeditationRef.text = v.reference
            binding.tvMeditationRef.setTextColor(themeColors.primaryAccent)
        }
    }

    private fun setupDurationPills() {
        val pills = listOf(
            Triple(binding.btnDur3, 3, 3 * 60 * 1000L),
            Triple(binding.btnDur5, 5, 5 * 60 * 1000L),
            Triple(binding.btnDur10, 10, 10 * 60 * 1000L),
            Triple(binding.btnDur15, 15, 15 * 60 * 1000L)
        )

        for ((view, mins, millis) in pills) {
            view.setOnClickListener {
                if (isTimerRunning) pauseMeditation()

                selectedDurationMinutes = mins
                totalMillis = millis
                millisRemaining = millis
                updateTimerDisplay(millisRemaining)

                val themeColors = ThemeHelper.getActiveThemeColors(this@HolyMeditationActivity)
                for ((v, m, _) in pills) {
                    if (m == mins) {
                        v.setTextColor(themeColors.primaryAccent)
                        v.setBackgroundResource(R.drawable.card_background_elevated)
                    } else {
                        v.setTextColor(ContextCompat.getColor(this@HolyMeditationActivity, R.color.text_secondary))
                        v.setBackgroundResource(R.drawable.pill_background)
                    }
                }
            }
        }
    }

    private fun startMeditation() {
        val themeColors = ThemeHelper.getActiveThemeColors(this)
        isTimerRunning = true
        binding.btnStartMeditation.text = "Pause Meditation ⏸️"
        binding.btnStartMeditation.setBackgroundColor(ContextCompat.getColor(this, R.color.bg_card_elevated))
        binding.btnStartMeditation.setTextColor(themeColors.primaryAccent)

        countDownTimer?.cancel()
        countDownTimer = object : CountDownTimer(millisRemaining, 1000L) {
            override fun onTick(msUntilFinished: Long) {
                millisRemaining = msUntilFinished
                updateTimerDisplay(msUntilFinished)
                updateBreathPhase(msUntilFinished)
            }

            override fun onFinish() {
                completeMeditation()
            }
        }.start()
    }

    private fun pauseMeditation() {
        val themeColors = ThemeHelper.getActiveThemeColors(this)
        isTimerRunning = false
        countDownTimer?.cancel()
        binding.btnStartMeditation.text = "Resume Holy Meditation 🕊️"
        binding.btnStartMeditation.setBackgroundColor(themeColors.primaryAccent)
        binding.btnStartMeditation.setTextColor(ContextCompat.getColor(this, R.color.bg_dark))
        binding.tvBreathPrompt.text = "Meditation Paused • Rest in Grace"
    }

    private fun completeMeditation() {
        val themeColors = ThemeHelper.getActiveThemeColors(this)
        isTimerRunning = false
        millisRemaining = totalMillis
        updateTimerDisplay(millisRemaining)

        binding.btnStartMeditation.text = "Begin Holy Meditation 🕊️"
        binding.btnStartMeditation.setBackgroundColor(themeColors.primaryAccent)
        binding.btnStartMeditation.setTextColor(ContextCompat.getColor(this, R.color.bg_dark))
        binding.tvBreathPrompt.text = "Amen • Abiding in His Peace"

        // Log session in focus sessions database
        dbHelper.addFocusSession(
            FocusSession(
                sessionType = currentMode.sessionKey,
                durationMinutes = selectedDurationMinutes,
                timestamp = System.currentTimeMillis(),
                isCompleted = true
            )
        )

        vibratePeaceChime()
        Toast.makeText(this, "🕊️ ${currentMode.title} Complete. Walk in Christ's peace.", Toast.LENGTH_LONG).show()
    }

    private fun updateTimerDisplay(millis: Long) {
        val totalSecs = (millis / 1000).toInt()
        val mins = totalSecs / 60
        val secs = totalSecs % 60
        binding.tvTimerDisplay.text = "%02d:%02d".format(mins, secs)
    }

    private fun updateBreathPhase(millis: Long) {
        val elapsedSec = ((totalMillis - millis) / 1000).toInt()
        val cycleSec = elapsedSec % 18 // 18s breath cycle

        val prompt = when (currentMode) {
            MeditationMode.MORNING -> when {
                cycleSec < 6 -> "Inhale Awakening Grace ☀️ (6s)"
                cycleSec < 9 -> "Hold & Align Mind with Purpose ⚓ (3s)"
                cycleSec < 15 -> "Exhale Sluggishness & Doubts 🕊️ (6s)"
                else -> "Rest in His Morning Mercies 👑 (3s)"
            }
            MeditationMode.MIDDAY -> when {
                cycleSec < 6 -> "Inhale Christ's Stillness & Peace 🕊️ (6s)"
                cycleSec < 9 -> "Hold & Abide in the Vine ⚓ (3s)"
                cycleSec < 15 -> "Exhale Daytime Stress & Noise 🌿 (6s)"
                else -> "Rest in the Living Sanctuary 👑 (3s)"
            }
            MeditationMode.EVENING -> when {
                cycleSec < 6 -> "Inhale Shalom Peace & Safety 🌙 (6s)"
                cycleSec < 9 -> "Hold in Father's Protective Wings ⚓ (3s)"
                cycleSec < 15 -> "Exhale the Burdens of the Day 🕊️ (6s)"
                else -> "Sleep in Christ's Embracing Love 👑 (3s)"
            }
            MeditationMode.WARFARE -> when {
                cycleSec < 6 -> "Inhale the Holy Spirit & Authority 🛡️ (6s)"
                cycleSec < 9 -> "Hold & Cast Down Intrusive Thoughts ⚓ (3s)"
                cycleSec < 15 -> "Exhale Temptation, Urges & Fear 🔥 (6s)"
                else -> "Stand Victorious in Christ Jesus 👑 (3s)"
            }
            MeditationMode.LECTIO -> when {
                cycleSec < 6 -> "Inhale the Living Breath of the Word 📖 (6s)"
                cycleSec < 9 -> "Hold & Savor the Scripture ⚓ (3s)"
                cycleSec < 15 -> "Exhale Worldly Distractions 🕊️ (6s)"
                else -> "Let the Word Take Deep Root 👑 (3s)"
            }
        }
        binding.tvBreathPrompt.text = prompt
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

    override fun onDestroy() {
        super.onDestroy()
        countDownTimer?.cancel()
    }
}
