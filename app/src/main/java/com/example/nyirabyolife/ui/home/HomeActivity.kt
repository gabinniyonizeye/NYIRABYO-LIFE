package com.example.nyirabyolife.ui.home

import android.content.Context
import android.content.Intent
import android.graphics.Paint
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import com.example.nyirabyolife.R
import com.example.nyirabyolife.data.local.NyirabyoDbHelper
import com.example.nyirabyolife.data.local.PreferencesManager
import com.example.nyirabyolife.data.local.ScriptureRepository
import com.example.nyirabyolife.data.model.Mission
import com.example.nyirabyolife.data.model.ShoppingSession
import com.example.nyirabyolife.data.model.ShoppingSessionItem
import com.example.nyirabyolife.data.model.TodoItem
import com.example.nyirabyolife.databinding.ActivityHomeBinding
import com.example.nyirabyolife.databinding.ItemHomeTodoBinding
import com.example.nyirabyolife.databinding.PageHomeDailyBinding
import com.example.nyirabyolife.databinding.PageHomeSocialBinding
import com.example.nyirabyolife.databinding.PageHomeTodoBinding
import com.example.nyirabyolife.databinding.PageHomeToolsBinding
import com.example.nyirabyolife.security.RecoveryAuthManager
import com.example.nyirabyolife.ui.drawer.AppDrawerActivity
import com.example.nyirabyolife.ui.focus.FocusActivity
import com.example.nyirabyolife.ui.godfirst.GodFirstActivity
import com.example.nyirabyolife.ui.recovery.RecoveryActivity
import com.example.nyirabyolife.ui.restriction.AppRestrictionsActivity
import com.example.nyirabyolife.ui.settings.SettingsActivity
import com.example.nyirabyolife.ui.shield.BlockShieldActivity
import com.example.nyirabyolife.ui.weekly.WeeklyPlanActivity
import com.example.nyirabyolife.util.TaskAlarmScheduler
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class HomeActivity : AppCompatActivity() {

    private lateinit var binding: ActivityHomeBinding
    private lateinit var dbHelper: NyirabyoDbHelper
    private lateinit var prefs: PreferencesManager
    private lateinit var recoveryAuthManager: RecoveryAuthManager
    private var currentMissions: List<Mission> = emptyList()

    // Page bindings cached for live updates
    private var slideDailyBinding: PageHomeDailyBinding? = null
    private var slideTodoBinding: PageHomeTodoBinding? = null
    private var slideToolsBinding: PageHomeToolsBinding? = null
    private var slideSocialBinding: PageHomeSocialBinding? = null

    // Todo filtering and category selection state
    private var currentTodoCategoryFilter: String? = null
    private var selectedNewTodoCategory: String = "GENERAL"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityHomeBinding.inflate(layoutInflater)
        setContentView(binding.root)

        dbHelper = NyirabyoDbHelper.getInstance(this)
        prefs = PreferencesManager.getInstance(this)

        // One-time fresh reset for user to define custom schedule from scratch
        if (!prefs.hasPerformedOneTimeTaskWipe()) {
            com.example.nyirabyolife.util.TaskAlarmScheduler.cancelAllAlarms(this)
            dbHelper.resetAllTasks()
            prefs.setOneTimeTaskWipePerformed(true)
            prefs.setTasksCustomizedByUser(true)
        }

        // Ensure permanent 5:00 AM wakeup and 22:00 bedtime are seeded and scheduled
        val cal = Calendar.getInstance()
        val week = cal.get(Calendar.WEEK_OF_YEAR)
        val year = cal.get(Calendar.YEAR)
        dbHelper.ensurePermanentWakeupAndBedtime(week, year)
        com.example.nyirabyolife.util.TaskAlarmScheduler.schedulePermanentAlarms(this)

        recoveryAuthManager = RecoveryAuthManager(
            activity = this,
            onSuccess = {
                val intent = Intent(this, RecoveryActivity::class.java)
                startActivity(intent)
            },
            onFailed = { _ -> }
        )

        // As a Home Launcher, Back press stays on Home (or returns to Slide 1)
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (binding.homeViewPager.currentItem != 0) {
                    binding.homeViewPager.setCurrentItem(0, true)
                }
            }
        })

        setupViewPager()
        setupTopHeaderAndDock()
    }

    override fun onResume() {
        super.onResume()
        refreshAllData()
        com.example.nyirabyolife.util.TaskAlarmScheduler.schedulePermanentAlarms(this)
        com.example.nyirabyolife.util.HabitationReminderManager.checkAndShowAutomaticReminders(this)
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        setIntent(intent)
        refreshAllData()
    }

    private fun refreshAllData() {
        applyThemeColors()
        loadVerseOfTheDay()
        loadMissions()
        loadWeeklyPlans()
        loadTodos()
        loadFavoriteAppsDock()
        updateEscapeRoomStatus()
        updateSocialLimitsUI()
        slideToolsBinding?.let { loadMyAppsRow(it) }
    }

    private fun setupViewPager() {
        val adapter = HomeSlideAdapter()
        binding.homeViewPager.adapter = adapter
        binding.homeViewPager.offscreenPageLimit = 3 // Keeps all 4 slides active

        binding.homeViewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                super.onPageSelected(position)
                updateIndicatorDots(position)
            }
        })

        // Dot indicator clicks
        binding.dot1.setOnClickListener { binding.homeViewPager.setCurrentItem(0, true) }
        binding.dot2.setOnClickListener { binding.homeViewPager.setCurrentItem(1, true) }
        binding.dot3.setOnClickListener { binding.homeViewPager.setCurrentItem(2, true) }
        binding.dot4.setOnClickListener { binding.homeViewPager.setCurrentItem(3, true) }
    }

    private fun updateIndicatorDots(position: Int) {
        val dots = listOf(binding.dot1, binding.dot2, binding.dot3, binding.dot4)
        val density = resources.displayMetrics.density
        for (i in dots.indices) {
            val dot = dots[i]
            val params = dot.layoutParams
            if (i == position) {
                dot.setBackgroundResource(R.drawable.indicator_dot_active)
                params.width = (18 * density).toInt()
                params.height = (6 * density).toInt()
            } else {
                dot.setBackgroundResource(R.drawable.indicator_dot_inactive)
                params.width = (6 * density).toInt()
                params.height = (6 * density).toInt()
            }
            dot.layoutParams = params
        }
    }

    private fun setupTopHeaderAndDock() {
        binding.btnTheme.setOnClickListener {
            showThemePickerDialog()
        }

        binding.btnMeditate.setOnClickListener {
            startActivity(Intent(this, com.example.nyirabyolife.ui.godfirst.HolyMeditationActivity::class.java))
        }

        binding.btnLockMode.setOnClickListener {
            startActivity(Intent(this, com.example.nyirabyolife.ui.lock.DisciplineLockActivity::class.java))
        }

        binding.btnSettings.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }

        binding.btnOpenAppDrawer.setOnClickListener {
            startActivity(Intent(this, AppDrawerActivity::class.java))
        }

        // Swipe Up to open App Drawer gesture
        val gestureDetector = android.view.GestureDetector(this, object : android.view.GestureDetector.SimpleOnGestureListener() {
            override fun onFling(
                e1: android.view.MotionEvent?,
                e2: android.view.MotionEvent,
                velocityX: Float,
                velocityY: Float
            ): Boolean {
                if (e1 != null && (e1.y - e2.y > 120) && Math.abs(velocityY) > 100) {
                    startActivity(Intent(this@HomeActivity, AppDrawerActivity::class.java))
                    return true
                }
                return false
            }
        })
        binding.rootHome.setOnTouchListener { _, event ->
            gestureDetector.onTouchEvent(event)
        }
    }

    private fun showThemePickerDialog() {
        val themes = listOf(
            Pair("👑 Holy Obsidian Gold (Majestic Classical Sanctuary)", PreferencesManager.THEME_HOLY_GOLD),
            Pair("🕊️ Celestial Sanctuary (Modern Glassmorphic Cyber-Zen)", PreferencesManager.THEME_CELESTIAL_BLUE),
            Pair("🌿 Living Vine (Organic Leaf & Habit Growth)", PreferencesManager.THEME_LIVING_VINE),
            Pair("🍇 Royal Solitude (Monastic Minimalist & Sacred Focus)", PreferencesManager.THEME_ROYAL_AMETHYST)
        )
        val currentTheme = prefs.getActiveTheme()
        val currentIndex = themes.indexOfFirst { it.second == currentTheme }.coerceAtLeast(0)

        val labels = themes.map { it.first }.toTypedArray()
        AlertDialog.Builder(this, com.google.android.material.R.style.Theme_MaterialComponents_DayNight_Dialog_Alert)
            .setTitle("Select Habitation Design & Theme")
            .setSingleChoiceItems(labels, currentIndex) { dialog, which ->
                val selectedTheme = themes[which].second
                prefs.setActiveTheme(selectedTheme)
                applyThemeColors()
                binding.ambientGlowView.invalidate()
                loadMissions()
                loadVerseOfTheDay()
                Toast.makeText(this, "Design updated: ${themes[which].first}", Toast.LENGTH_SHORT).show()
                dialog.dismiss()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun applyThemeColors() {
        val themeColors = com.example.nyirabyolife.util.ThemeHelper.getActiveThemeColors(this)

        // 1. Root & Dock Design
        binding.tvHeaderTitle.setTextColor(themeColors.primaryAccent)
        binding.tvHeaderSubtitle.text = themeColors.headerSubtitle
        binding.btnMeditate.setTextColor(themeColors.primaryAccent)
        binding.btnLockMode.setColorFilter(themeColors.primaryAccent)

        binding.bottomDock.background = com.example.nyirabyolife.util.ThemeHelper.createDockDrawable(this, themeColors)
        binding.btnOpenAppDrawer.background = com.example.nyirabyolife.util.ThemeHelper.createPillDrawable(this, themeColors)
        binding.btnTheme.background = com.example.nyirabyolife.util.ThemeHelper.createPillDrawable(this, themeColors)
        binding.btnMeditate.background = com.example.nyirabyolife.util.ThemeHelper.createPillDrawable(this, themeColors)
        binding.btnLockMode.background = com.example.nyirabyolife.util.ThemeHelper.createPillDrawable(this, themeColors)
        binding.btnSettings.background = com.example.nyirabyolife.util.ThemeHelper.createPillDrawable(this, themeColors)

        // 2. Slide 1 (Daily Walk & Missions) Structural Design
        val b = slideDailyBinding
        if (b != null) {
            b.tvSacredHabitationBanner.text = themeColors.clockSubtitle
            b.tvSacredHabitationBanner.setTextColor(themeColors.primaryAccent)
            b.textClockTime.textSize = themeColors.clockTextSizeSp
            b.textClockTime.letterSpacing = themeColors.clockLetterSpacing

            b.cardTodayWord.background = com.example.nyirabyolife.util.ThemeHelper.createCardDrawable(this, themeColors, isElevated = true)
            b.cardTodayMission.background = com.example.nyirabyolife.util.ThemeHelper.createCardDrawable(this, themeColors, isElevated = false)

            b.lblWordBadge.text = themeColors.badgeText
            b.lblWordBadge.setTextColor(themeColors.primaryAccent)
            b.ivSparkleGod.setColorFilter(themeColors.primaryAccent)
            b.tvVerseRef.setTextColor(themeColors.primaryAccent)
            b.tvReflectPrompt.text = themeColors.scriptureActionText
            b.tvReflectPrompt.setTextColor(themeColors.secondaryAccent)

            b.tvVerseText.textSize = themeColors.scriptureTextSizeSp
            if (themeColors.scriptureItalic) {
                b.tvVerseText.setTypeface(null, android.graphics.Typeface.ITALIC)
            } else {
                b.tvVerseText.setTypeface(null, android.graphics.Typeface.NORMAL)
            }

            b.tvMissionSubtitle.text = themeColors.missionsSubtitle
            b.tvMissionSubtitle.setTextColor(themeColors.primaryAccent)
            b.btnSanctuaryCheckIn.setTextColor(themeColors.primaryAccent)
            b.btnOpenWeeklyPlan.setTextColor(themeColors.secondaryAccent)
            b.tvMissionCounter.setTextColor(themeColors.secondaryAccent)
            b.progressBarMissions.progressTintList = android.content.res.ColorStateList.valueOf(themeColors.secondaryAccent)
        }

        // 3. Slide 2 (Personal TODO & Goals) Structural Design
        val td = slideTodoBinding
        if (td != null) {
            td.tvTodoHeaderSubtitle.setTextColor(themeColors.primaryAccent)
            td.tvTodoSummaryTitle.setTextColor(themeColors.primaryAccent)
            td.tvMarketBudgetTitle.setTextColor(themeColors.primaryAccent)
            td.cardTodoSummary.background = com.example.nyirabyolife.util.ThemeHelper.createCardDrawable(this, themeColors, isElevated = false)
            td.cardTodoInput.background = com.example.nyirabyolife.util.ThemeHelper.createCardDrawable(this, themeColors, isElevated = true)
            td.cardMarketBudget.background = com.example.nyirabyolife.util.ThemeHelper.createCardDrawable(this, themeColors, isElevated = true)
            td.pbTodoProgress.progressTintList = android.content.res.ColorStateList.valueOf(themeColors.primaryAccent)
            td.btnAddTodo.setColorFilter(themeColors.primaryAccent)
            td.btnFinishMarketTrip.setTextColor(themeColors.primaryAccent)
            updateTodoFilterChips()
            updateNewTodoCategoryChips()
        }

        // 4. Slide 3 (Tools & Habit Windows) Structural Design
        val t = slideToolsBinding
        if (t != null) {
            t.btnEscapeRoomBlock.background = com.example.nyirabyolife.util.ThemeHelper.createCardDrawable(this, themeColors, isElevated = true)
            t.btnToolFaith.background = com.example.nyirabyolife.util.ThemeHelper.createCardDrawable(this, themeColors, isElevated = true)
            t.btnToolFocus.background = com.example.nyirabyolife.util.ThemeHelper.createCardDrawable(this, themeColors, isElevated = true)
            t.btnToolReset.background = com.example.nyirabyolife.util.ThemeHelper.createCardDrawable(this, themeColors, isElevated = true)
            t.btnToolBible.background = com.example.nyirabyolife.util.ThemeHelper.createCardDrawable(this, themeColors, isElevated = true)
            t.btnManageMyApps.setTextColor(themeColors.primaryAccent)
        }
        // 5. Slide 4 (Digital Boundaries) Structural Design
        val s = slideSocialBinding
        if (s != null) {
            s.cardInstagramLimit.background = com.example.nyirabyolife.util.ThemeHelper.createCardDrawable(this, themeColors, isElevated = true)
            s.cardTikTokLimit.background = com.example.nyirabyolife.util.ThemeHelper.createCardDrawable(this, themeColors, isElevated = true)
            s.btnManageAllRestrictions.background = com.example.nyirabyolife.util.ThemeHelper.createCardDrawable(this, themeColors, isElevated = false)
        }
    }

    // --- SLIDE 1: DAILY WALK & MISSIONS ---
    private fun bindSlideDaily(dailyBinding: PageHomeDailyBinding) {
        slideDailyBinding = dailyBinding
        applyThemeColors()

        dailyBinding.cardTodayWord.setOnClickListener {
            startActivity(Intent(this, GodFirstActivity::class.java))
        }

        dailyBinding.btnEditMissions.setOnClickListener {
            promptEditMissions()
        }

        dailyBinding.btnOpenWeeklyPlan.setOnClickListener {
            startActivity(Intent(this, WeeklyPlanActivity::class.java))
        }

        dailyBinding.btnSanctuaryCheckIn.setOnClickListener {
            com.example.nyirabyolife.util.HabitationReminderManager.showManualHabitationReminder(this)
        }

        loadVerseOfTheDay()
        loadMissions()
        loadWeeklyPlans()
    }

    private fun loadVerseOfTheDay() {
        val b = slideDailyBinding ?: return
        val themeColors = com.example.nyirabyolife.util.ThemeHelper.getActiveThemeColors(this)
        val revelation = ScriptureRepository.getChristRevelationOfTheDay()
        b.tvRevelationTitle.text = revelation.title
        b.tvVerseText.text = "“${revelation.scripture}”"
        b.tvVerseRef.text = revelation.reference
        b.tvVerseRef.setTextColor(themeColors.primaryAccent)
        b.lblWordBadge.setTextColor(themeColors.primaryAccent)
        b.ivSparkleGod.setColorFilter(themeColors.primaryAccent)
        b.tvReflectPrompt.setTextColor(themeColors.secondaryAccent)
    }

    private fun loadWeeklyPlans() {
        val cal = Calendar.getInstance()
        val week = cal.get(Calendar.WEEK_OF_YEAR)
        val year = cal.get(Calendar.YEAR)
        dbHelper.ensureDefaultSundayWeeklyPlanIfEmpty(week, year)
    }

    private fun getTodayDateString(): String {
        return SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
    }

    private fun loadMissions() {
        val b = slideDailyBinding ?: return
        val themeColors = com.example.nyirabyolife.util.ThemeHelper.getActiveThemeColors(this)
        val today = getTodayDateString()
        val cal = Calendar.getInstance()
        val dayOfWeek = cal.get(Calendar.DAY_OF_WEEK)
        val week = cal.get(Calendar.WEEK_OF_YEAR)
        val year = cal.get(Calendar.YEAR)

        currentMissions = dbHelper.getSynchronizedDailyMissions(today, dayOfWeek, week, year)

        val completedCount = currentMissions.count { it.isCompleted }
        b.tvMissionCounter.text = "$completedCount of ${currentMissions.size} complete"
        b.tvMissionCounter.setTextColor(themeColors.secondaryAccent)
        b.btnOpenWeeklyPlan.setTextColor(themeColors.secondaryAccent)
        b.btnSanctuaryCheckIn.setTextColor(themeColors.primaryAccent)
        b.tvMissionSubtitle.setTextColor(themeColors.primaryAccent)
        b.progressBarMissions.max = if (currentMissions.isEmpty()) 1 else currentMissions.size
        b.progressBarMissions.progress = completedCount
        b.progressBarMissions.progressTintList = android.content.res.ColorStateList.valueOf(themeColors.secondaryAccent)

        b.layoutMissionsContainer.removeAllViews()
        val weeklyTasks = dbHelper.getWeeklyTasksForDay(dayOfWeek, week, year)

        if (currentMissions.isEmpty()) {
            val emptyTv = TextView(this).apply {
                text = "No priorities set today. Tap below to plan your day."
                setTextColor(ContextCompat.getColor(context, R.color.text_tertiary))
                textSize = 13f
                setPadding(0, 8, 0, 8)
            }
            b.layoutMissionsContainer.addView(emptyTv)
            return
        }

        val density = resources.displayMetrics.density
        for (mission in currentMissions) {
            val matchingWeekly = weeklyTasks.find {
                it.title.trim().equals(mission.title.trim(), ignoreCase = true)
            } ?: weeklyTasks.getOrNull(mission.orderIndex)

            val rowView = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = android.view.Gravity.CENTER_VERTICAL
                when (themeColors.missionRowStyle) {
                    com.example.nyirabyolife.util.ThemeHelper.MissionRowStyle.FLOATING_CAPSULE -> {
                        background = com.example.nyirabyolife.util.ThemeHelper.createTaskCapsuleDrawable(this@HomeActivity, themeColors, mission.isCompleted)
                        setPadding((14 * density).toInt(), (10 * density).toInt(), (14 * density).toInt(), (10 * density).toInt())
                        val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                            setMargins(0, (3 * density).toInt(), 0, (3 * density).toInt())
                        }
                        layoutParams = lp
                    }
                    com.example.nyirabyolife.util.ThemeHelper.MissionRowStyle.ORGANIC_BRANCH -> {
                        setPadding((6 * density).toInt(), (8 * density).toInt(), (6 * density).toInt(), (8 * density).toInt())
                        foreground = ContextCompat.getDrawable(context, android.R.drawable.list_selector_background)
                    }
                    com.example.nyirabyolife.util.ThemeHelper.MissionRowStyle.MONASTIC_MINIMAL -> {
                        setPadding(0, (7 * density).toInt(), 0, (7 * density).toInt())
                        foreground = ContextCompat.getDrawable(context, android.R.drawable.list_selector_background)
                    }
                    com.example.nyirabyolife.util.ThemeHelper.MissionRowStyle.CLASSIC_CHECKBOX -> {
                        setPadding(0, (10 * density).toInt(), 0, (10 * density).toInt())
                        foreground = ContextCompat.getDrawable(context, android.R.drawable.list_selector_background)
                    }
                }
            }

            val checkSize = when (themeColors.missionRowStyle) {
                com.example.nyirabyolife.util.ThemeHelper.MissionRowStyle.MONASTIC_MINIMAL -> (18 * density).toInt()
                com.example.nyirabyolife.util.ThemeHelper.MissionRowStyle.FLOATING_CAPSULE -> (20 * density).toInt()
                else -> (22 * density).toInt()
            }

            val ivCheck = ImageView(this).apply {
                layoutParams = LinearLayout.LayoutParams(checkSize, checkSize)
                if (mission.isCompleted) {
                    setImageResource(R.drawable.ic_check_circle)
                    setColorFilter(themeColors.secondaryAccent)
                } else {
                    setImageResource(R.drawable.ic_circle_outline)
                    setColorFilter(ContextCompat.getColor(context, R.color.text_tertiary))
                }
            }

            val detailsLayout = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                    marginStart = (14 * density).toInt()
                }
            }

            val rawTime = if (matchingWeekly != null) {
                "${matchingWeekly.formattedTimeRange}${if (matchingWeekly.isAlarmEnabled) " ⏰" else ""}"
            } else if (mission.timeHour > 0 || mission.timeMinute > 0 || mission.title.contains("7:00")) {
                mission.formattedTimeRange
            } else null

            val displayTime = if (rawTime != null) {
                when (themeColors.missionRowStyle) {
                    com.example.nyirabyolife.util.ThemeHelper.MissionRowStyle.ORGANIC_BRANCH -> if (mission.isCompleted) "🌿 $rawTime" else "🌱 $rawTime"
                    com.example.nyirabyolife.util.ThemeHelper.MissionRowStyle.FLOATING_CAPSULE -> "⚡ $rawTime"
                    else -> rawTime
                }
            } else null

            if (displayTime != null) {
                val timeRow = LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = android.view.Gravity.CENTER_VERTICAL
                }
                val tvTime = TextView(this).apply {
                    text = displayTime
                    setTextColor(themeColors.primaryAccent)
                    textSize = 11f
                    paintFlags = paintFlags or Paint.FAKE_BOLD_TEXT_FLAG
                    letterSpacing = 0.06f
                }
                timeRow.addView(tvTime)

                val badgeText = mission.modeBadgeText ?: matchingWeekly?.modeBadgeText
                if (badgeText != null) {
                    val tvBadge = TextView(this).apply {
                        text = " • $badgeText"
                        setTextColor(themeColors.secondaryAccent)
                        textSize = 10f
                    }
                    timeRow.addView(tvBadge)
                }
                detailsLayout.addView(timeRow)
            }

            val tvTitle = TextView(this).apply {
                text = mission.title
                textSize = when (themeColors.missionRowStyle) {
                    com.example.nyirabyolife.util.ThemeHelper.MissionRowStyle.MONASTIC_MINIMAL -> 15f
                    com.example.nyirabyolife.util.ThemeHelper.MissionRowStyle.FLOATING_CAPSULE -> 13.5f
                    else -> 14f
                }
                if (mission.isCompleted) {
                    paintFlags = paintFlags or Paint.STRIKE_THRU_TEXT_FLAG
                    setTextColor(ContextCompat.getColor(context, R.color.text_tertiary))
                } else {
                    paintFlags = paintFlags and Paint.STRIKE_THRU_TEXT_FLAG.inv()
                    setTextColor(ContextCompat.getColor(context, R.color.text_primary))
                }
            }
            detailsLayout.addView(tvTitle)

            rowView.addView(ivCheck)
            rowView.addView(detailsLayout)

            val effectiveDur = matchingWeekly?.durationMinutes ?: mission.durationMinutes
            val isFocusable = (((mission.taskMode in listOf("STUDY_FOCUS", "SILENT_DND", "MEDITATION", "BEDTIME")) ||
                    (matchingWeekly?.taskMode in listOf("STUDY_FOCUS", "SILENT_DND", "MEDITATION", "BEDTIME")))) && effectiveDur > 0

            if (isFocusable && !mission.isCompleted) {
                val ivPlay = ImageView(this).apply {
                    val playSize = (24 * density).toInt()
                    layoutParams = LinearLayout.LayoutParams(playSize, playSize).apply {
                        marginStart = (8 * density).toInt()
                    }
                    setImageResource(R.drawable.ic_play)
                    setColorFilter(themeColors.primaryAccent)
                    setOnClickListener {
                        val task = matchingWeekly
                        val intent = Intent(this@HomeActivity, com.example.nyirabyolife.ui.focus.ScheduledFocusActivity::class.java).apply {
                            putExtra(com.example.nyirabyolife.ui.focus.ScheduledFocusActivity.EXTRA_TASK_ID, task?.id ?: mission.id)
                            putExtra(com.example.nyirabyolife.ui.focus.ScheduledFocusActivity.EXTRA_TASK_TITLE, task?.title ?: mission.title)
                            putExtra(com.example.nyirabyolife.ui.focus.ScheduledFocusActivity.EXTRA_TASK_MODE, task?.taskMode ?: mission.taskMode)
                            putExtra(com.example.nyirabyolife.ui.focus.ScheduledFocusActivity.EXTRA_TASK_DURATION_MINUTES, task?.durationMinutes ?: mission.durationMinutes)
                            putExtra(com.example.nyirabyolife.ui.focus.ScheduledFocusActivity.EXTRA_START_HOUR, task?.timeHour ?: mission.timeHour)
                            putExtra(com.example.nyirabyolife.ui.focus.ScheduledFocusActivity.EXTRA_START_MINUTE, task?.timeMinute ?: mission.timeMinute)
                        }
                        startActivity(intent)
                    }
                }
                rowView.addView(ivPlay)
            }

            rowView.setOnClickListener {
                val newStatus = !mission.isCompleted
                dbHelper.updateMissionStatus(mission.id, newStatus)
                if (matchingWeekly != null) {
                    if (newStatus) {
                        TaskAlarmScheduler.cancelTaskAlarm(this, matchingWeekly.id)
                    } else if (matchingWeekly.isAlarmEnabled) {
                        TaskAlarmScheduler.scheduleTaskAlarm(this, matchingWeekly.copy(isCompleted = false))
                    }
                }
                loadMissions()
                loadWeeklyPlans()
            }

            rowView.setOnLongClickListener {
                startActivity(Intent(this, com.example.nyirabyolife.ui.weekly.WeeklyPlanActivity::class.java))
                true
            }

            b.layoutMissionsContainer.addView(rowView)
        }
    }

    private fun promptEditMissions() {
        val view = LayoutInflater.from(this).inflate(R.layout.dialog_edit_missions, null)
        val et1 = view.findViewById<EditText>(R.id.etMission1).apply {
            setText(currentMissions.getOrNull(0)?.title ?: "")
        }
        val et2 = view.findViewById<EditText>(R.id.etMission2).apply {
            setText(currentMissions.getOrNull(1)?.title ?: "")
        }
        val et3 = view.findViewById<EditText>(R.id.etMission3).apply {
            setText(currentMissions.getOrNull(2)?.title ?: "")
        }

        AlertDialog.Builder(this, com.google.android.material.R.style.Theme_MaterialComponents_DayNight_Dialog_Alert)
            .setTitle("Set Today's Priorities")
            .setView(view)
            .setPositiveButton("Save & Sync") { _, _ ->
                val titles = listOf(
                    et1.text.toString().trim(),
                    et2.text.toString().trim(),
                    et3.text.toString().trim()
                ).filter { it.isNotEmpty() }

                val cal = Calendar.getInstance()
                val todayDate = getTodayDateString()
                val dayOfWeek = cal.get(Calendar.DAY_OF_WEEK)
                val week = cal.get(Calendar.WEEK_OF_YEAR)
                val year = cal.get(Calendar.YEAR)

                val weeklyTasks = dbHelper.getWeeklyTasksForDay(dayOfWeek, week, year)

                titles.forEachIndexed { index, newTitle ->
                    if (index < currentMissions.size) {
                        dbHelper.updateMissionTitle(currentMissions[index].id, newTitle)
                    } else {
                        dbHelper.saveCustomMission(todayDate, newTitle, index)
                    }
                    if (index < weeklyTasks.size) {
                        dbHelper.updateWeeklyTaskTitle(weeklyTasks[index].id, newTitle)
                    }
                }

                loadMissions()
                loadWeeklyPlans()
                Toast.makeText(this, "Priorities synchronized with Weekly Plan", Toast.LENGTH_SHORT).show()
            }
            .setNeutralButton("Open Weekly Plan") { _, _ ->
                startActivity(Intent(this, WeeklyPlanActivity::class.java))
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    // --- SLIDE 2: PERSONAL TODO & ACHIEVEMENT TRACKER ---
    private fun bindSlideTodo(todoBinding: PageHomeTodoBinding) {
        slideTodoBinding = todoBinding
        applyThemeColors()

        // Quick add button
        todoBinding.btnAddTodo.setOnClickListener {
            addNewTodoFromInput()
        }

        // IME action on edit text
        todoBinding.etTodoTitle.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == android.view.inputmethod.EditorInfo.IME_ACTION_DONE ||
                actionId == android.view.inputmethod.EditorInfo.IME_ACTION_GO) {
                addNewTodoFromInput()
                true
            } else {
                false
            }
        }

        // Category selection chips for adding items
        todoBinding.chipCategoryGeneral.setOnClickListener {
            selectedNewTodoCategory = "GENERAL"
            updateNewTodoCategoryChips()
        }
        todoBinding.chipCategoryMarket.setOnClickListener {
            selectedNewTodoCategory = "MARKET"
            updateNewTodoCategoryChips()
        }
        todoBinding.chipCategoryProject.setOnClickListener {
            selectedNewTodoCategory = "PROJECT"
            updateNewTodoCategoryChips()
        }
        todoBinding.chipCategoryGoal.setOnClickListener {
            selectedNewTodoCategory = "GOAL"
            updateNewTodoCategoryChips()
        }
        updateNewTodoCategoryChips()

        // Filter chips
        todoBinding.filterAll.setOnClickListener {
            currentTodoCategoryFilter = null
            updateTodoFilterChips()
            loadTodos()
        }
        todoBinding.filterMarket.setOnClickListener {
            currentTodoCategoryFilter = "MARKET"
            updateTodoFilterChips()
            loadTodos()
        }
        todoBinding.filterProject.setOnClickListener {
            currentTodoCategoryFilter = "PROJECT"
            updateTodoFilterChips()
            loadTodos()
        }
        todoBinding.filterGoal.setOnClickListener {
            currentTodoCategoryFilter = "GOAL"
            updateTodoFilterChips()
            loadTodos()
        }
        todoBinding.filterGeneral.setOnClickListener {
            currentTodoCategoryFilter = "GENERAL"
            updateTodoFilterChips()
            loadTodos()
        }
        updateTodoFilterChips()

        // Clear completed button
        todoBinding.btnClearCompletedTodos.setOnClickListener {
            val stats = dbHelper.getTodoStats()
            if (stats.first == 0) {
                Toast.makeText(this, "No completed items to clear.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            AlertDialog.Builder(this, com.google.android.material.R.style.Theme_MaterialComponents_DayNight_Dialog_Alert)
                .setTitle("Clear Completed Todos")
                .setMessage("Remove all ${stats.first} completed items from your list?")
                .setPositiveButton("Clear") { _, _ ->
                    dbHelper.clearCompletedTodos()
                    loadTodos()
                    Toast.makeText(this, "Cleared completed items.", Toast.LENGTH_SHORT).show()
                }
                .setNegativeButton("Cancel", null)
                .show()
        }

        // Currency tap to change
        todoBinding.tvMarketCurrency.setOnClickListener {
            showCurrencyPicker()
        }

        // View Shopping Trip History
        todoBinding.btnViewShoppingHistory.setOnClickListener {
            showShoppingHistoryDialog()
        }

        // Finish & Save Shopping Trip to History
        todoBinding.btnFinishMarketTrip.setOnClickListener {
            promptFinishShoppingTrip()
        }

        loadTodos()
    }

    private fun showCurrencyPicker() {
        val currencies = arrayOf("RWF 🇷🇼", "USD 🇺🇸", "EUR 🇪🇺", "KES 🇰🇪", "UGX 🇺🇬", "TZS 🇹🇿")
        val currencyCodes = arrayOf("RWF", "USD", "EUR", "KES", "UGX", "TZS")
        val currentCurrency = slideTodoBinding?.tvMarketCurrency?.text?.toString() ?: "RWF"
        val currentIdx = currencyCodes.indexOfFirst { it == currentCurrency }.coerceAtLeast(0)

        AlertDialog.Builder(this, com.google.android.material.R.style.Theme_MaterialComponents_DayNight_Dialog_Alert)
            .setTitle("Select Currency")
            .setSingleChoiceItems(currencies, currentIdx) { dialog, which ->
                val selected = currencyCodes[which]
                slideTodoBinding?.tvMarketCurrency?.text = selected
                dialog.dismiss()
                loadTodos()
            }
            .show()
    }

    private fun addNewTodoFromInput() {
        val b = slideTodoBinding ?: return
        val title = b.etTodoTitle.text.toString().trim()
        if (title.isEmpty()) {
            Toast.makeText(this, "Please enter a todo or item name.", Toast.LENGTH_SHORT).show()
            return
        }

        val currency = b.tvMarketCurrency.text?.toString()?.takeIf { it.isNotBlank() } ?: "RWF"

        val todo = TodoItem(
            title = title,
            category = selectedNewTodoCategory,
            isCompleted = false,
            currency = currency
        )
        dbHelper.addTodo(todo)
        b.etTodoTitle.text.clear()

        // Hide soft keyboard
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as? android.view.inputmethod.InputMethodManager
        imm?.hideSoftInputFromWindow(b.etTodoTitle.windowToken, 0)

        loadTodos()
    }

    private fun updateNewTodoCategoryChips() {
        val b = slideTodoBinding ?: return
        val themeColors = com.example.nyirabyolife.util.ThemeHelper.getActiveThemeColors(this)

        val chips = listOf(
            Triple(b.chipCategoryGeneral, "GENERAL", "📋 Todo"),
            Triple(b.chipCategoryMarket, "MARKET", "🛒 Market"),
            Triple(b.chipCategoryProject, "PROJECT", "🎨 Project"),
            Triple(b.chipCategoryGoal, "GOAL", "🎯 Goal")
        )

        for ((chip, cat, _) in chips) {
            if (cat.equals(selectedNewTodoCategory, ignoreCase = true)) {
                chip.setTextColor(themeColors.primaryAccent)
                chip.setBackgroundResource(R.drawable.pill_background)
            } else {
                chip.setTextColor(ContextCompat.getColor(this, R.color.text_secondary))
                chip.setBackgroundResource(R.drawable.card_background)
            }
        }
    }

    private fun updateTodoFilterChips() {
        val b = slideTodoBinding ?: return
        val themeColors = com.example.nyirabyolife.util.ThemeHelper.getActiveThemeColors(this)

        val filters = listOf(
            Pair(b.filterAll, null),
            Pair(b.filterMarket, "MARKET"),
            Pair(b.filterProject, "PROJECT"),
            Pair(b.filterGoal, "GOAL"),
            Pair(b.filterGeneral, "GENERAL")
        )

        for ((filterView, cat) in filters) {
            val isSelected = (cat == null && currentTodoCategoryFilter == null) ||
                             (cat != null && cat.equals(currentTodoCategoryFilter, ignoreCase = true))
            if (isSelected) {
                filterView.setTextColor(themeColors.primaryAccent)
                filterView.setBackgroundResource(R.drawable.pill_background)
            } else {
                filterView.setTextColor(ContextCompat.getColor(this, R.color.text_secondary))
                filterView.setBackgroundResource(R.drawable.card_background)
            }
        }
    }

    private fun formatPrice(amount: Double, currency: String): String {
        return if (amount == 0.0) "-" else {
            val formatted = if (amount == kotlin.math.floor(amount)) {
                android.text.format.Formatter.formatShortFileSize(this, amount.toLong())
                    .let { "${amount.toLong()}" }
            } else {
                "%.0f".format(amount)
            }
            "$currency $formatted"
        }
    }

    private fun updateMarketBudgetCard(todos: List<com.example.nyirabyolife.data.model.TodoItem>) {
        val b = slideTodoBinding ?: return
        val themeColors = com.example.nyirabyolife.util.ThemeHelper.getActiveThemeColors(this)
        val filter = currentTodoCategoryFilter

        // Show Market Budget card whenever market filter is selected or there are market items with prices
        val marketItems = if (filter == "MARKET") todos
                          else dbHelper.getAllTodos("MARKET")
        val pricedItems = marketItems.filter { it.hasFinancialData }

        val currency = b.tvMarketCurrency.text?.toString()?.takeIf { it.isNotBlank() } ?: "RWF"

        if (pricedItems.isEmpty() && filter != "MARKET") {
            b.cardMarketBudget.visibility = View.GONE
            return
        }

        b.cardMarketBudget.visibility = View.VISIBLE

        val totalBudget = pricedItems.sumOf { it.totalPlanned }
        val pricedWithActual = pricedItems.filter { it.actualPrice > 0.0 }
        val totalSpent = pricedWithActual.sumOf { it.totalActual }
        val totalSaved = pricedItems.filter { it.hasSavings }.sumOf { it.priceDifference }
        val totalOverspent = pricedItems.filter { it.isOverBudget }.sumOf { -it.priceDifference }
        val netResult = totalSaved - totalOverspent

        // Budget summary numbers
        b.tvMarketTotalBudget.text = if (totalBudget > 0) "%.0f".format(totalBudget) else "—"
        b.tvMarketTotalSpent.text = if (totalSpent > 0) "%.0f".format(totalSpent) else "—"

        if (netResult > 0) {
            b.tvMarketNetLabel.text = "SAVED 💚"
            b.tvMarketNetAmount.text = "%.0f".format(netResult)
            b.tvMarketNetAmount.setTextColor(android.graphics.Color.parseColor("#10B981"))
        } else if (netResult < 0) {
            b.tvMarketNetLabel.text = "OVER 🔴"
            b.tvMarketNetAmount.text = "%.0f".format(-netResult)
            b.tvMarketNetAmount.setTextColor(android.graphics.Color.parseColor("#F87171"))
        } else {
            b.tvMarketNetLabel.text = "BALANCE"
            b.tvMarketNetAmount.text = "0"
            b.tvMarketNetAmount.setTextColor(ContextCompat.getColor(this, R.color.text_secondary))
        }

        // Budget usage progress bar
        val budgetLimitStr = b.etTotalBudgetTarget.text?.toString()?.trim()
        val budgetLimit = budgetLimitStr?.toDoubleOrNull() ?: totalBudget
        val budgetProgress = if (budgetLimit > 0) ((totalSpent / budgetLimit) * 100).toInt().coerceIn(0, 100) else 0
        b.pbMarketBudget.progress = budgetProgress

        if (budgetLimit > 0 && totalSpent > 0) {
            val isOver = totalSpent > budgetLimit
            b.tvBudgetLimitStatus.visibility = View.VISIBLE
            if (isOver) {
                val overAmount = "%.0f".format(totalSpent - budgetLimit)
                b.tvBudgetLimitStatus.text = "Over by $overAmount ⚠️"
                b.tvBudgetLimitStatus.setTextColor(android.graphics.Color.parseColor("#F87171"))
                b.pbMarketBudget.progressTintList =
                    android.content.res.ColorStateList.valueOf(android.graphics.Color.parseColor("#F87171"))
            } else {
                val remaining = "%.0f".format(budgetLimit - totalSpent)
                b.tvBudgetLimitStatus.text = "$remaining remaining ✓"
                b.tvBudgetLimitStatus.setTextColor(android.graphics.Color.parseColor("#10B981"))
                b.pbMarketBudget.progressTintList =
                    android.content.res.ColorStateList.valueOf(themeColors.primaryAccent)
            }
        } else {
            b.tvBudgetLimitStatus.visibility = View.GONE
        }

        b.tvMarketCurrency.text = currency
    }

    private fun loadTodos() {
        val b = slideTodoBinding ?: return
        val themeColors = com.example.nyirabyolife.util.ThemeHelper.getActiveThemeColors(this)
        val todos = dbHelper.getAllTodos(currentTodoCategoryFilter)
        val (completed, total) = dbHelper.getTodoStats()

        // Update progress summary
        val percent = if (total > 0) ((completed * 100f) / total).toInt() else 0
        b.tvTodoProgressStats.text = "$completed of $total achieved ($percent%)"
        b.pbTodoProgress.progress = percent

        // Update market budget card
        updateMarketBudgetCard(todos)

        b.layoutTodoList.removeAllViews()

        if (todos.isEmpty()) {
            b.layoutEmptyTodo.visibility = View.VISIBLE
            val filterName = when (currentTodoCategoryFilter?.uppercase()) {
                "MARKET" -> "Market"
                "PROJECT" -> "Projects"
                "GOAL" -> "Goals"
                "GENERAL" -> "Todo"
                else -> null
            }
            b.tvEmptyTodoTitle.text = if (filterName != null) "No items in $filterName" else "No items yet"
            return
        }

        b.layoutEmptyTodo.visibility = View.GONE

        val currency = b.tvMarketCurrency.text?.toString()?.takeIf { it.isNotBlank() } ?: "RWF"
        val inflater = LayoutInflater.from(this)
        for (todo in todos) {
            val itemBinding = ItemHomeTodoBinding.inflate(inflater, b.layoutTodoList, false)

            itemBinding.tvTodoTitle.text = todo.title
            itemBinding.tvTodoCategoryBadge.text = todo.categoryBadgeText
            itemBinding.tvTodoCategoryBadge.setTextColor(themeColors.primaryAccent)

            // Quantity badge
            if (todo.quantity > 1) {
                itemBinding.tvTodoQuantityBadge.text = "×${todo.quantity}"
                itemBinding.tvTodoQuantityBadge.visibility = View.VISIBLE
            } else {
                itemBinding.tvTodoQuantityBadge.visibility = View.GONE
            }

            if (todo.note.isNotBlank()) {
                itemBinding.tvTodoNote.text = todo.note
                itemBinding.tvTodoNote.visibility = View.VISIBLE
            } else {
                itemBinding.tvTodoNote.visibility = View.GONE
            }

            // Financial price row
            if (todo.hasFinancialData) {
                itemBinding.layoutPriceRow.visibility = View.VISIBLE
                val cur = todo.currency.ifBlank { currency }

                itemBinding.tvTodoPlannedPrice.text = if (todo.plannedPrice > 0)
                    "$cur ${"%.0f".format(todo.plannedPrice)}" else "—"

                itemBinding.tvTodoActualPrice.text = if (todo.actualPrice > 0)
                    "$cur ${"%.0f".format(todo.actualPrice)}" else "—"

                // Price difference badge
                when {
                    todo.hasSavings -> {
                        itemBinding.tvTodoPriceDiff.visibility = View.VISIBLE
                        itemBinding.tvTodoPriceDiff.text = "💚 Saved ${"%.0f".format(todo.priceDifference)}"
                        itemBinding.tvTodoPriceDiff.setTextColor(android.graphics.Color.parseColor("#10B981"))
                    }
                    todo.isOverBudget -> {
                        itemBinding.tvTodoPriceDiff.visibility = View.VISIBLE
                        itemBinding.tvTodoPriceDiff.text = "🔴 +${"%.0f".format(-todo.priceDifference)} over"
                        itemBinding.tvTodoPriceDiff.setTextColor(android.graphics.Color.parseColor("#F87171"))
                    }
                    else -> itemBinding.tvTodoPriceDiff.visibility = View.GONE
                }
            } else {
                itemBinding.layoutPriceRow.visibility = View.GONE
            }

            if (todo.isCompleted) {
                itemBinding.ivTodoCheck.setImageResource(R.drawable.ic_check_circle)
                itemBinding.ivTodoCheck.setColorFilter(themeColors.primaryAccent)
                itemBinding.tvTodoTitle.paintFlags = itemBinding.tvTodoTitle.paintFlags or Paint.STRIKE_THRU_TEXT_FLAG
                itemBinding.tvTodoTitle.setTextColor(ContextCompat.getColor(this, R.color.text_tertiary))
            } else {
                itemBinding.ivTodoCheck.setImageResource(R.drawable.ic_circle_outline)
                itemBinding.ivTodoCheck.setColorFilter(ContextCompat.getColor(this, R.color.text_tertiary))
                itemBinding.tvTodoTitle.paintFlags = itemBinding.tvTodoTitle.paintFlags and Paint.STRIKE_THRU_TEXT_FLAG.inv()
                itemBinding.tvTodoTitle.setTextColor(ContextCompat.getColor(this, R.color.text_primary))
            }

            // Toggle completion
            itemBinding.ivTodoCheck.setOnClickListener {
                dbHelper.updateTodoStatus(todo.id, !todo.isCompleted)
                loadTodos()
            }

            // Quick Delete
            itemBinding.btnDeleteTodo.setOnClickListener {
                dbHelper.deleteTodo(todo.id)
                loadTodos()
                Toast.makeText(this, "Removed '${todo.title}'", Toast.LENGTH_SHORT).show()
            }

            // Tap on item to edit (with financial fields)
            itemBinding.root.setOnClickListener {
                showEditTodoDialog(todo)
            }

            b.layoutTodoList.addView(itemBinding.root)
        }
    }

    private fun showEditTodoDialog(todo: TodoItem) {
        val density = resources.displayMetrics.density
        val p = (18 * density).toInt()
        val pSm = (10 * density).toInt()
        val pCard = (12 * density).toInt()
        val mTop = (10 * density).toInt()

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(p, pSm, p, (8 * density).toInt())
        }

        fun makeEditText(initialValue: String, hintText: String, inputType: Int = android.text.InputType.TYPE_CLASS_TEXT): EditText {
            return EditText(this).apply {
                setText(initialValue)
                hint = hintText
                this.inputType = inputType
                setTextColor(ContextCompat.getColor(this@HomeActivity, R.color.text_primary))
                setHintTextColor(ContextCompat.getColor(this@HomeActivity, R.color.text_tertiary))
                setBackgroundResource(R.drawable.card_background)
                setPadding(pCard, pSm, pCard, pSm)
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { topMargin = mTop }
            }
        }

        // Title
        val etTitle = makeEditText(todo.title, "Title / Item name")
        layout.addView(etTitle)

        // Note
        val etNote = makeEditText(todo.note, "Notes, details, or specs (optional)")
        layout.addView(etNote)

        // Category picker
        val categories = listOf("GENERAL", "MARKET", "PROJECT", "GOAL")
        val categoryLabels = arrayOf("📋 Todo / General", "🛒 Market / Shopping", "🎨 Project / Work", "🎯 Goal / Achievement")
        var selectedCatIndex = categories.indexOfFirst { it.equals(todo.category, ignoreCase = true) }.coerceAtLeast(0)

        val catButton = TextView(this).apply {
            text = "Category: ${categoryLabels[selectedCatIndex]}"
            setTextColor(ContextCompat.getColor(this@HomeActivity, R.color.gold_primary))
            textSize = 12f
            setPadding(pCard, pSm, pCard, pSm)
            setBackgroundResource(R.drawable.pill_background)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = mTop }
            setOnClickListener {
                AlertDialog.Builder(this@HomeActivity, com.google.android.material.R.style.Theme_MaterialComponents_DayNight_Dialog_Alert)
                    .setTitle("Select Category")
                    .setSingleChoiceItems(categoryLabels, selectedCatIndex) { d, which ->
                        selectedCatIndex = which
                        this.text = "Category: ${categoryLabels[which]}"
                        d.dismiss()
                    }
                    .show()
            }
        }
        layout.addView(catButton)

        // ── Financial fields section ──
        val divLabel = TextView(this).apply {
            text = "── PRICE & BUDGET TRACKING (optional) ──"
            setTextColor(ContextCompat.getColor(this@HomeActivity, R.color.text_tertiary))
            textSize = 10f
            gravity = android.view.Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = (14 * density).toInt(); bottomMargin = (4 * density).toInt() }
        }
        layout.addView(divLabel)

        // Previous price lookup from history
        val previousPurchase = dbHelper.findPreviousPriceForItem(todo.title)
        if (previousPurchase != null) {
            val tvPrevPrice = TextView(this).apply {
                val cur = todo.currency.ifBlank { "RWF" }
                text = "💡 Previous price: $cur ${"%.0f".format(previousPurchase.first)} (${previousPurchase.second})\nTap to use as planned budget"
                setTextColor(ContextCompat.getColor(this@HomeActivity, R.color.gold_primary))
                textSize = 10.5f
                setPadding(pCard, (6 * density).toInt(), pCard, (6 * density).toInt())
                setBackgroundResource(R.drawable.pill_background)
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { topMargin = (4 * density).toInt(); bottomMargin = (4 * density).toInt() }
                setOnClickListener {
                    Toast.makeText(this@HomeActivity, "Applied previous price: %.0f".format(previousPurchase.first), Toast.LENGTH_SHORT).show()
                }
            }
            layout.addView(tvPrevPrice)
        }

        // Budget (planned price per unit)
        val etPlanned = makeEditText(
            if (todo.plannedPrice > 0) "%.0f".format(todo.plannedPrice) else "",
            "Budget / Planned price per unit",
            android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL
        )
        layout.addView(etPlanned)

        // Connect tap on previous price to set the planned price
        if (previousPurchase != null) {
            val prevView = layout.getChildAt(layout.childCount - 2) as? TextView
            prevView?.setOnClickListener {
                etPlanned.setText("%.0f".format(previousPurchase.first))
                Toast.makeText(this@HomeActivity, "Applied previous price: %.0f".format(previousPurchase.first), Toast.LENGTH_SHORT).show()
            }
        }

        // Actual price found / paid
        val etActual = makeEditText(
            if (todo.actualPrice > 0) "%.0f".format(todo.actualPrice) else "",
            "Actual price found / paid",
            android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL
        )
        layout.addView(etActual)

        // Quantity
        val etQty = makeEditText(
            if (todo.quantity > 1) todo.quantity.toString() else "",
            "Quantity (default: 1)",
            android.text.InputType.TYPE_CLASS_NUMBER
        )
        layout.addView(etQty)

        // Live savings preview hint
        val tvSavingsPreview = TextView(this).apply {
            setTextColor(ContextCompat.getColor(this@HomeActivity, R.color.text_secondary))
            textSize = 11f
            gravity = android.view.Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = (8 * density).toInt() }
        }
        layout.addView(tvSavingsPreview)

        // Live preview calculation as user types
        val updatePreview = {
            val planned = etPlanned.text.toString().toDoubleOrNull() ?: 0.0
            val actual = etActual.text.toString().toDoubleOrNull() ?: 0.0
            val qty = etQty.text.toString().toIntOrNull()?.coerceAtLeast(1) ?: 1
            val cur = todo.currency.ifBlank { "RWF" }
            if (planned > 0 && actual > 0) {
                val diff = (planned - actual) * qty
                if (diff > 0) {
                    tvSavingsPreview.text = "💚 You save $cur ${"%.0f".format(diff)} on this item"
                    tvSavingsPreview.setTextColor(android.graphics.Color.parseColor("#10B981"))
                } else if (diff < 0) {
                    tvSavingsPreview.text = "🔴 $cur ${"%.0f".format(-diff)} over budget on this item"
                    tvSavingsPreview.setTextColor(android.graphics.Color.parseColor("#F87171"))
                } else {
                    tvSavingsPreview.text = "✓ Exactly on budget"
                    tvSavingsPreview.setTextColor(ContextCompat.getColor(this@HomeActivity, R.color.text_secondary))
                }
            } else {
                tvSavingsPreview.text = ""
            }
        }

        val watcher = object : android.text.TextWatcher {
            override fun afterTextChanged(s: android.text.Editable?) { updatePreview() }
            override fun beforeTextChanged(s: CharSequence?, st: Int, c: Int, a: Int) {}
            override fun onTextChanged(s: CharSequence?, st: Int, b: Int, c: Int) {}
        }
        etPlanned.addTextChangedListener(watcher)
        etActual.addTextChangedListener(watcher)
        etQty.addTextChangedListener(watcher)
        updatePreview()

        AlertDialog.Builder(this, com.google.android.material.R.style.Theme_MaterialComponents_DayNight_Dialog_Alert)
            .setTitle("Edit Item")
            .setView(layout)
            .setPositiveButton("Save") { _, _ ->
                val newTitle = etTitle.text.toString().trim()
                if (newTitle.isNotEmpty()) {
                    val updated = todo.copy(
                        title = newTitle,
                        note = etNote.text.toString().trim(),
                        category = categories[selectedCatIndex],
                        plannedPrice = etPlanned.text.toString().toDoubleOrNull() ?: 0.0,
                        actualPrice = etActual.text.toString().toDoubleOrNull() ?: 0.0,
                        quantity = etQty.text.toString().toIntOrNull()?.coerceAtLeast(1) ?: 1
                    )
                    dbHelper.updateTodo(updated)
                    loadTodos()
                    Toast.makeText(this, "Item updated.", Toast.LENGTH_SHORT).show()
                }
            }
            .setNeutralButton("Delete") { _, _ ->
                dbHelper.deleteTodo(todo.id)
                loadTodos()
                Toast.makeText(this, "Item deleted.", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun promptFinishShoppingTrip() {
        val b = slideTodoBinding ?: return
        val marketTodos = dbHelper.getAllTodos("MARKET")
        if (marketTodos.isEmpty()) {
            Toast.makeText(this, "No items in your Market list to save.", Toast.LENGTH_SHORT).show()
            return
        }

        val currency = b.tvMarketCurrency.text?.toString()?.takeIf { it.isNotBlank() } ?: "RWF"
        val totalBudget = marketTodos.sumOf { it.totalPlanned }
        val totalSpent = marketTodos.filter { it.actualPrice > 0.0 }.sumOf { it.totalActual }
        val netSaved = marketTodos.filter { it.hasSavings }.sumOf { it.priceDifference } -
                       marketTodos.filter { it.isOverBudget }.sumOf { -it.priceDifference }

        val dateFormat = SimpleDateFormat("EEEE, MMM d, yyyy", Locale.getDefault())
        val defaultName = "Market Trip - ${SimpleDateFormat("MMM d", Locale.getDefault()).format(Date())}"
        val density = resources.displayMetrics.density

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding((18 * density).toInt(), (10 * density).toInt(), (18 * density).toInt(), (10 * density).toInt())
        }

        // Trip Name input
        val etName = EditText(this).apply {
            setText(defaultName)
            hint = "Trip name (e.g. Nyabugogo Market, Supermarket)"
            setTextColor(ContextCompat.getColor(this@HomeActivity, R.color.text_primary))
            setHintTextColor(ContextCompat.getColor(this@HomeActivity, R.color.text_tertiary))
            setBackgroundResource(R.drawable.card_background)
            setPadding((12 * density).toInt(), (10 * density).toInt(), (12 * density).toInt(), (10 * density).toInt())
        }
        layout.addView(etName)

        // Summary Preview
        val tvSummary = TextView(this).apply {
            val netStr = if (netSaved > 0) "💚 Saved $currency ${"%.0f".format(netSaved)}"
                         else if (netSaved < 0) "🔴 Over budget by $currency ${"%.0f".format(-netSaved)}"
                         else "✓ On budget"
            text = "• Items to record: ${marketTodos.size}\n• Total Budget: $currency ${"%.0f".format(totalBudget)}\n• Total Spent: $currency ${"%.0f".format(totalSpent)}\n• Net Result: $netStr"
            setTextColor(ContextCompat.getColor(this@HomeActivity, R.color.text_secondary))
            textSize = 12f
            setLineSpacing(4f, 1f)
            setPadding(0, (10 * density).toInt(), 0, (10 * density).toInt())
        }
        layout.addView(tvSummary)

        // Checkbox: Remove completed/purchased items
        val cbClearPurchased = android.widget.CheckBox(this).apply {
            text = "Clear purchased items from active list after saving"
            isChecked = true
            setTextColor(ContextCompat.getColor(this@HomeActivity, R.color.text_secondary))
            textSize = 12f
        }
        layout.addView(cbClearPurchased)

        AlertDialog.Builder(this, com.google.android.material.R.style.Theme_MaterialComponents_DayNight_Dialog_Alert)
            .setTitle("📦 Finish & Save Shopping Trip")
            .setView(layout)
            .setPositiveButton("Save to History") { _, _ ->
                val tripName = etName.text.toString().trim().ifBlank { defaultName }
                val dateLabel = dateFormat.format(Date())

                val jsonArr = org.json.JSONArray()
                for (item in marketTodos) {
                    val obj = org.json.JSONObject().apply {
                        put("title", item.title)
                        put("plannedPrice", item.plannedPrice)
                        put("actualPrice", item.actualPrice)
                        put("quantity", item.quantity)
                        put("note", item.note)
                    }
                    jsonArr.put(obj)
                }

                val session = ShoppingSession(
                    name = tripName,
                    dateLabel = dateLabel,
                    createdAt = System.currentTimeMillis(),
                    totalBudget = totalBudget,
                    totalSpent = totalSpent,
                    netResult = netSaved,
                    currency = currency,
                    itemCount = marketTodos.size,
                    itemsJson = jsonArr.toString()
                )
                dbHelper.saveShoppingSession(session)

                if (cbClearPurchased.isChecked) {
                    for (item in marketTodos) {
                        if (item.isCompleted || item.actualPrice > 0.0) {
                            dbHelper.deleteTodo(item.id)
                        }
                    }
                }

                loadTodos()
                Toast.makeText(this, "Trip '$tripName' archived to history! 📜", Toast.LENGTH_LONG).show()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showShoppingHistoryDialog() {
        val sessions = dbHelper.getAllShoppingSessions()
        if (sessions.isEmpty()) {
            AlertDialog.Builder(this, com.google.android.material.R.style.Theme_MaterialComponents_DayNight_Dialog_Alert)
                .setTitle("📜 Shopping Trip History")
                .setMessage("No archived trips yet.\n\nWhen you finish buying items at the market, tap '📦 Finish & Save' to record your trip, prices, and savings for future reference.")
                .setPositiveButton("OK", null)
                .show()
            return
        }

        val density = resources.displayMetrics.density
        val scrollView = androidx.core.widget.NestedScrollView(this).apply {
            isFillViewport = true
        }

        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding((16 * density).toInt(), (10 * density).toInt(), (16 * density).toInt(), (16 * density).toInt())
        }
        scrollView.addView(container)

        val themeColors = com.example.nyirabyolife.util.ThemeHelper.getActiveThemeColors(this)
        var dialog: AlertDialog? = null

        fun renderSessionList() {
            container.removeAllViews()
            val currentList = dbHelper.getAllShoppingSessions()
            if (currentList.isEmpty()) {
                dialog?.dismiss()
                Toast.makeText(this, "All history records cleared.", Toast.LENGTH_SHORT).show()
                return
            }

            for (session in currentList) {
                val card = LinearLayout(this).apply {
                    orientation = LinearLayout.VERTICAL
                    background = com.example.nyirabyolife.util.ThemeHelper.createCardDrawable(this@HomeActivity, themeColors, isElevated = true)
                    setPadding((14 * density).toInt(), (12 * density).toInt(), (14 * density).toInt(), (12 * density).toInt())
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    ).apply { bottomMargin = (10 * density).toInt() }
                }

                // Header row: Title + Delete button
                val headerRow = LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = android.view.Gravity.CENTER_VERTICAL
                }

                val titleCol = LinearLayout(this).apply {
                    orientation = LinearLayout.VERTICAL
                    layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                }

                val tvName = TextView(this).apply {
                    text = session.name
                    textSize = 14f
                    setTypeface(null, android.graphics.Typeface.BOLD)
                    setTextColor(ContextCompat.getColor(this@HomeActivity, R.color.text_primary))
                }
                titleCol.addView(tvName)

                val tvDate = TextView(this).apply {
                    text = session.dateLabel
                    textSize = 10f
                    setTextColor(ContextCompat.getColor(this@HomeActivity, R.color.text_tertiary))
                    setPadding(0, (2 * density).toInt(), 0, 0)
                }
                titleCol.addView(tvDate)
                headerRow.addView(titleCol)

                val btnDelete = ImageView(this).apply {
                    setImageResource(R.drawable.ic_close)
                    setColorFilter(ContextCompat.getColor(this@HomeActivity, R.color.text_tertiary))
                    setPadding((6 * density).toInt(), (6 * density).toInt(), (6 * density).toInt(), (6 * density).toInt())
                    layoutParams = LinearLayout.LayoutParams((30 * density).toInt(), (30 * density).toInt())
                    setOnClickListener {
                        AlertDialog.Builder(this@HomeActivity, com.google.android.material.R.style.Theme_MaterialComponents_DayNight_Dialog_Alert)
                            .setTitle("Delete Trip Record")
                            .setMessage("Remove '${session.name}' from history?")
                            .setPositiveButton("Delete") { _, _ ->
                                dbHelper.deleteShoppingSession(session.id)
                                renderSessionList()
                            }
                            .setNegativeButton("Cancel", null)
                            .show()
                    }
                }
                headerRow.addView(btnDelete)
                card.addView(headerRow)

                // Stats row
                val statsRow = LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL
                    setPadding(0, (8 * density).toInt(), 0, 0)
                    gravity = android.view.Gravity.CENTER_VERTICAL
                }

                val cur = session.currency
                val tvSpent = TextView(this).apply {
                    text = "${session.itemCount} items • Spent: $cur ${"%.0f".format(session.totalSpent)}"
                    textSize = 12f
                    setTextColor(ContextCompat.getColor(this@HomeActivity, R.color.text_secondary))
                    layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                }
                statsRow.addView(tvSpent)

                val tvNet = TextView(this).apply {
                    textSize = 11f
                    setTypeface(null, android.graphics.Typeface.BOLD)
                    setPadding((8 * density).toInt(), (3 * density).toInt(), (8 * density).toInt(), (3 * density).toInt())
                    setBackgroundResource(R.drawable.pill_background)
                    if (session.netResult > 0) {
                        text = "💚 Saved ${"%.0f".format(session.netResult)}"
                        setTextColor(android.graphics.Color.parseColor("#10B981"))
                    } else if (session.netResult < 0) {
                        text = "🔴 +${"%.0f".format(-session.netResult)} over"
                        setTextColor(android.graphics.Color.parseColor("#F87171"))
                    } else {
                        text = "✓ On budget"
                        setTextColor(ContextCompat.getColor(this@HomeActivity, R.color.text_secondary))
                    }
                }
                statsRow.addView(tvNet)
                card.addView(statsRow)

                // Tap to view receipt breakdown
                card.setOnClickListener {
                    showTripReceiptDialog(session)
                }

                container.addView(card)
            }
        }

        renderSessionList()

        dialog = AlertDialog.Builder(this, com.google.android.material.R.style.Theme_MaterialComponents_DayNight_Dialog_Alert)
            .setTitle("📜 Shopping Trip History")
            .setView(scrollView)
            .setPositiveButton("Close", null)
            .create()
        dialog?.show()
    }

    private fun showTripReceiptDialog(session: ShoppingSession) {
        val items = session.parseItems()
        val density = resources.displayMetrics.density
        val cur = session.currency

        val scroll = androidx.core.widget.NestedScrollView(this).apply {
            isFillViewport = true
        }

        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding((16 * density).toInt(), (12 * density).toInt(), (16 * density).toInt(), (16 * density).toInt())
        }
        scroll.addView(container)

        // Summary banner
        val tvBanner = TextView(this).apply {
            text = "${session.dateLabel}\nTotal Planned: $cur ${"%.0f".format(session.totalBudget)} | Spent: $cur ${"%.0f".format(session.totalSpent)}"
            textSize = 12f
            setTextColor(ContextCompat.getColor(this@HomeActivity, R.color.gold_primary))
            setPadding(0, 0, 0, (12 * density).toInt())
        }
        container.addView(tvBanner)

        // Table headers
        val headerTable = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, 0, 0, (6 * density).toInt())
        }
        val thItem = TextView(this).apply {
            text = "ITEM"
            textSize = 10f
            setTypeface(null, android.graphics.Typeface.BOLD)
            setTextColor(ContextCompat.getColor(this@HomeActivity, R.color.text_tertiary))
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.5f)
        }
        val thPlan = TextView(this).apply {
            text = "PLANNED"
            textSize = 10f
            gravity = android.view.Gravity.END
            setTypeface(null, android.graphics.Typeface.BOLD)
            setTextColor(ContextCompat.getColor(this@HomeActivity, R.color.text_tertiary))
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        val thActual = TextView(this).apply {
            text = "ACTUAL"
            textSize = 10f
            gravity = android.view.Gravity.END
            setTypeface(null, android.graphics.Typeface.BOLD)
            setTextColor(ContextCompat.getColor(this@HomeActivity, R.color.text_tertiary))
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        headerTable.addView(thItem)
        headerTable.addView(thPlan)
        headerTable.addView(thActual)
        container.addView(headerTable)

        // Divider line
        val div = View(this).apply {
            setBackgroundColor(ContextCompat.getColor(this@HomeActivity, R.color.border_subtle))
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, (1 * density).toInt().coerceAtLeast(1)).apply {
                bottomMargin = (8 * density).toInt()
            }
        }
        container.addView(div)

        for (item in items) {
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(0, (4 * density).toInt(), 0, (4 * density).toInt())
                gravity = android.view.Gravity.CENTER_VERTICAL
            }

            val tvTitle = TextView(this).apply {
                val qtyStr = if (item.quantity > 1) " (${item.quantity}×)" else ""
                text = "${item.title}$qtyStr"
                textSize = 13f
                setTextColor(ContextCompat.getColor(this@HomeActivity, R.color.text_primary))
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.5f)
            }

            val tvPlan = TextView(this).apply {
                text = if (item.plannedPrice > 0) "%.0f".format(item.totalPlanned) else "—"
                textSize = 12f
                gravity = android.view.Gravity.END
                setTextColor(ContextCompat.getColor(this@HomeActivity, R.color.text_secondary))
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            }

            val tvAct = TextView(this).apply {
                text = if (item.actualPrice > 0) "%.0f".format(item.totalActual) else "—"
                textSize = 12f
                gravity = android.view.Gravity.END
                setTypeface(null, android.graphics.Typeface.BOLD)
                if (item.hasSavings) {
                    setTextColor(android.graphics.Color.parseColor("#10B981"))
                } else if (item.isOverBudget) {
                    setTextColor(android.graphics.Color.parseColor("#F87171"))
                } else {
                    setTextColor(ContextCompat.getColor(this@HomeActivity, R.color.text_primary))
                }
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            }

            row.addView(tvTitle)
            row.addView(tvPlan)
            row.addView(tvAct)
            container.addView(row)
        }

        AlertDialog.Builder(this, com.google.android.material.R.style.Theme_MaterialComponents_DayNight_Dialog_Alert)
            .setTitle(session.name)
            .setView(scroll)
            .setPositiveButton("Close", null)
            .show()
    }

    // --- SLIDE 3: TOOLS & DISCIPLINE ---
    private fun bindSlideTools(toolsBinding: PageHomeToolsBinding) {
        slideToolsBinding = toolsBinding
        applyThemeColors()

        toolsBinding.btnToolFaith.setOnClickListener {
            startActivity(Intent(this, GodFirstActivity::class.java))
        }

        toolsBinding.btnToolFocus.setOnClickListener {
            startActivity(Intent(this, FocusActivity::class.java))
        }

        toolsBinding.btnToolReset.setOnClickListener {
            recoveryAuthManager.authenticate()
        }

        // Open BIBILIYA NTAGATIFU app directly
        toolsBinding.btnToolBible.setOnClickListener {
            val biblePkg = "com.bibiliya.ntagatifu"
            val bibleIntent = packageManager.getLaunchIntentForPackage(biblePkg)
            if (bibleIntent != null) {
                startActivity(bibleIntent)
            } else {
                // App not installed — open Play Store to download it
                try {
                    startActivity(Intent(Intent.ACTION_VIEW,
                        android.net.Uri.parse("market://details?id=$biblePkg")))
                } catch (_: Exception) {
                    startActivity(Intent(Intent.ACTION_VIEW,
                        android.net.Uri.parse("https://play.google.com/store/apps/details?id=$biblePkg")))
                }
            }
        }

        // ＋ Add button — pick an app and pin it to My Apps row
        toolsBinding.btnManageMyApps.setOnClickListener {
            showAddMyAppPicker()
        }

        toolsBinding.btnEscapeRoomBlock.setOnClickListener {
            promptEscapeRoomActivation()
        }

        loadMyAppsRow(toolsBinding)
        updateEscapeRoomStatus()
    }

    /** Populates the MY APPS horizontal row from pinned apps prefs */
    private fun loadMyAppsRow(toolsBinding: PageHomeToolsBinding) {
        val row = toolsBinding.layoutMyAppsRow
        val emptyHint = toolsBinding.tvMyAppsEmpty
        row.removeAllViews()

        val pinnedSet = prefs.getPinnedApps()
        val pm = packageManager
        val density = resources.displayMetrics.density
        val iconSize = (56 * density).toInt()
        val iconMargin = (10 * density).toInt()
        val themeColors = com.example.nyirabyolife.util.ThemeHelper.getActiveThemeColors(this)

        if (pinnedSet.isEmpty()) {
            emptyHint.visibility = View.VISIBLE
            return
        }
        emptyHint.visibility = View.GONE

        pinnedSet.forEach { pkg ->
            try {
                val appInfo = pm.getApplicationInfo(pkg, 0)
                val icon = pm.getApplicationIcon(appInfo)
                val label = pm.getApplicationLabel(appInfo).toString()

                val cell = LinearLayout(this).apply {
                    orientation = LinearLayout.VERTICAL
                    gravity = android.view.Gravity.CENTER_HORIZONTAL
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    ).apply { setMargins(iconMargin, 0, iconMargin, 0) }
                }

                // App icon
                val iv = ImageView(this).apply {
                    layoutParams = LinearLayout.LayoutParams(iconSize, iconSize)
                    setImageDrawable(icon)
                    background = com.example.nyirabyolife.util.ThemeHelper.createCardDrawable(
                        this@HomeActivity, themeColors, isElevated = false
                    )
                    setPadding(
                        (6 * density).toInt(), (6 * density).toInt(),
                        (6 * density).toInt(), (6 * density).toInt()
                    )
                    contentDescription = label
                    setOnClickListener { launchOrBlockApp(pkg) }
                    setOnLongClickListener {
                        confirmRemoveMyApp(pkg, label)
                        true
                    }
                }

                // App label
                val tv = TextView(this).apply {
                    text = label.take(10)
                    textSize = 10f
                    setTextColor(ContextCompat.getColor(this@HomeActivity, R.color.text_tertiary))
                    gravity = android.view.Gravity.CENTER
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    ).apply { topMargin = (4 * density).toInt() }
                }

                cell.addView(iv)
                cell.addView(tv)
                row.addView(cell)
            } catch (_: Exception) { /* app not installed */ }
        }
    }

    /** Launches an app, or shows the block shield if restricted */
    private fun launchOrBlockApp(pkg: String) {
        if (prefs.isPackageBlocked(pkg, packageName)) {
            startActivity(Intent(this, BlockShieldActivity::class.java).apply {
                putExtra(BlockShieldActivity.EXTRA_PACKAGE_NAME, pkg)
                putExtra(BlockShieldActivity.EXTRA_BLOCK_REASON, prefs.getBlockReason(pkg))
            })
            return
        }
        packageManager.getLaunchIntentForPackage(pkg)?.let { startActivity(it) }
            ?: Toast.makeText(this, "App not found", Toast.LENGTH_SHORT).show()
    }

    /** Show a list of installed apps — user picks one to pin */
    private fun showAddMyAppPicker() {
        val pm = packageManager
        val mainIntent = Intent(Intent.ACTION_MAIN).apply { addCategory(Intent.CATEGORY_LAUNCHER) }
        val apps = pm.queryIntentActivities(mainIntent, 0)
            .filter { it.activityInfo.packageName != packageName }
            .sortedBy { pm.getApplicationLabel(it.activityInfo.applicationInfo).toString() }

        val labels = apps.map { pm.getApplicationLabel(it.activityInfo.applicationInfo).toString() }.toTypedArray()

        AlertDialog.Builder(this, com.google.android.material.R.style.Theme_MaterialComponents_DayNight_Dialog_Alert)
            .setTitle("Pin an App to My Apps")
            .setItems(labels) { _, idx ->
                val pkg = apps[idx].activityInfo.packageName
                prefs.togglePinnedApp(pkg)
                slideToolsBinding?.let { loadMyAppsRow(it) }
                Toast.makeText(this, "\"${labels[idx]}\" added to My Apps", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    /** Long-press confirmation to remove a pinned app */
    private fun confirmRemoveMyApp(pkg: String, label: String) {
        AlertDialog.Builder(this, com.google.android.material.R.style.Theme_MaterialComponents_DayNight_Dialog_Alert)
            .setTitle("Remove \"$label\"?")
            .setMessage("Remove this app from My Apps? You can always add it back.")
            .setPositiveButton("Remove") { _, _ ->
                prefs.togglePinnedApp(pkg)
                slideToolsBinding?.let { loadMyAppsRow(it) }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    // --- SLIDE 3: DIGITAL BOUNDARIES (INSTAGRAM & TIKTOK 30M BUDGET) ---
    private fun bindSlideSocial(socialBinding: PageHomeSocialBinding) {
        slideSocialBinding = socialBinding
        applyThemeColors()

        socialBinding.btnLaunchInstagram.setOnClickListener {
            launchInstagram()
        }
        socialBinding.cardInstagramLimit.setOnClickListener {
            launchInstagram()
        }

        socialBinding.btnLaunchTikTok.setOnClickListener {
            launchTikTok()
        }
        socialBinding.cardTikTokLimit.setOnClickListener {
            launchTikTok()
        }

        socialBinding.btnManageAllRestrictions.setOnClickListener {
            startActivity(Intent(this, AppRestrictionsActivity::class.java))
        }

        updateSocialLimitsUI()
    }

    private fun updateSocialLimitsUI() {
        val b = slideSocialBinding ?: return

        // 1. INSTAGRAM (30m budget)
        val instaLimit = prefs.getAppDailyLimit("com.instagram.android") // defaults to 30
        val instaUsed = prefs.getAppUsageMinutesToday("com.instagram.android")
        b.pbInstagramUsage.max = instaLimit
        b.pbInstagramUsage.progress = minOf(instaUsed, instaLimit)

        if (instaUsed >= instaLimit) {
            b.tvInstagramUsage.text = "${instaUsed}m used today • 30m daily limit reached"
            b.tvInstagramUsage.setTextColor(ContextCompat.getColor(this, R.color.rose_alert))
            b.btnLaunchInstagram.text = "Limit Reached"
            b.btnLaunchInstagram.setBackgroundResource(R.drawable.pill_background)
            b.btnLaunchInstagram.setTextColor(ContextCompat.getColor(this, R.color.rose_alert))
        } else {
            val remaining = instaLimit - instaUsed
            b.tvInstagramUsage.text = "${instaUsed}m used today • ${remaining}m remaining"
            b.tvInstagramUsage.setTextColor(ContextCompat.getColor(this, R.color.text_secondary))
            b.btnLaunchInstagram.text = "Open Instagram"
            b.btnLaunchInstagram.setBackgroundResource(R.drawable.button_gold)
            b.btnLaunchInstagram.setTextColor(ContextCompat.getColor(this, R.color.bg_dark))
        }

        // 2. TIKTOK (30m budget)
        val tikTokLimit = prefs.getAppDailyLimit("com.zhiliaoapp.musically") // defaults to 30
        val tikTokUsed = prefs.getAppUsageMinutesToday("com.zhiliaoapp.musically")
        b.pbTikTokUsage.max = tikTokLimit
        b.pbTikTokUsage.progress = minOf(tikTokUsed, tikTokLimit)

        if (tikTokUsed >= tikTokLimit) {
            b.tvTikTokUsage.text = "${tikTokUsed}m used today • 30m daily limit reached"
            b.tvTikTokUsage.setTextColor(ContextCompat.getColor(this, R.color.rose_alert))
            b.btnLaunchTikTok.text = "Limit Reached"
            b.btnLaunchTikTok.setBackgroundResource(R.drawable.pill_background)
            b.btnLaunchTikTok.setTextColor(ContextCompat.getColor(this, R.color.rose_alert))
        } else {
            val remaining = tikTokLimit - tikTokUsed
            b.tvTikTokUsage.text = "${tikTokUsed}m used today • ${remaining}m remaining"
            b.tvTikTokUsage.setTextColor(ContextCompat.getColor(this, R.color.text_secondary))
            b.btnLaunchTikTok.text = "Open TikTok"
            b.btnLaunchTikTok.setBackgroundResource(R.drawable.button_gold)
            b.btnLaunchTikTok.setTextColor(ContextCompat.getColor(this, R.color.bg_dark))
        }
    }

    private fun launchInstagram() {
        val instaUsed = prefs.getAppUsageMinutesToday("com.instagram.android")
        val instaLimit = prefs.getAppDailyLimit("com.instagram.android")
        if (instaUsed >= instaLimit) {
            Toast.makeText(this, "Daily 30-minute allowance reached for Instagram.", Toast.LENGTH_SHORT).show()
        }
        val packages = listOf("com.instagram.android", "com.instagram.lite")
        launchAppByPackage(packages, "Instagram")
    }

    private fun launchTikTok() {
        val tikTokUsed = prefs.getAppUsageMinutesToday("com.zhiliaoapp.musically")
        val tikTokLimit = prefs.getAppDailyLimit("com.zhiliaoapp.musically")
        if (tikTokUsed >= tikTokLimit) {
            Toast.makeText(this, "Daily 30-minute allowance reached for TikTok.", Toast.LENGTH_SHORT).show()
        }
        val packages = listOf("com.zhiliaoapp.musically", "com.ss.android.ugc.trill", "com.zhiliaoapp.musically.go")
        launchAppByPackage(packages, "TikTok")
    }

    private fun launchAppByPackage(packages: List<String>, appName: String) {
        val pm = packageManager
        for (pkg in packages) {
            if (prefs.isPackageBlocked(pkg, packageName)) {
                // All blocked apps — including WhatsApp — go straight to BlockShield. No bypass.
                val intent = Intent(this, BlockShieldActivity::class.java).apply {
                    putExtra(BlockShieldActivity.EXTRA_PACKAGE_NAME, pkg)
                    putExtra(BlockShieldActivity.EXTRA_BLOCK_REASON, prefs.getBlockReason(pkg))
                }
                startActivity(intent)
                return
            }
            val intent = pm.getLaunchIntentForPackage(pkg)
            if (intent != null) {
                startActivity(intent)
                return
            }
        }
        Toast.makeText(this, "$appName is not installed on this device.", Toast.LENGTH_SHORT).show()
    }

    // --- DOCK: Always shows Phone only ---
    private fun loadFavoriteAppsDock() {
        binding.layoutFavorites.removeAllViews()
        val pm = packageManager
        val density = resources.displayMetrics.density
        val themeColors = com.example.nyirabyolife.util.ThemeHelper.getActiveThemeColors(this)

        // Only the Phone (dialer) app lives in the dock above "All Applications"
        val phonePkg = "com.google.android.dialer"
        try {
            val icon = pm.getApplicationIcon(phonePkg)

            val iconSize = (44 * density).toInt() // compact — fits without scroll
            val iv = ImageView(this).apply {
                layoutParams = LinearLayout.LayoutParams(iconSize, iconSize)
                setImageDrawable(icon)
                background = com.example.nyirabyolife.util.ThemeHelper.createCardDrawable(
                    this@HomeActivity, themeColors, isElevated = false
                )
                setPadding(
                    (6 * density).toInt(), (6 * density).toInt(),
                    (6 * density).toInt(), (6 * density).toInt()
                )
                contentDescription = "Phone"
                setOnClickListener {
                    pm.getLaunchIntentForPackage(phonePkg)?.let { startActivity(it) }
                }
            }

            binding.layoutFavorites.addView(iv)
            binding.layoutFavorites.visibility = View.VISIBLE
        } catch (_: Exception) {
            binding.layoutFavorites.visibility = View.GONE
        }
    }

    // --- ESCAPE ROOM LOCKDOWN ---
    private fun isAccessibilityServiceEnabled(): Boolean {
        val am = getSystemService(Context.ACCESSIBILITY_SERVICE) as? android.view.accessibility.AccessibilityManager ?: return false
        val list = am.getEnabledAccessibilityServiceList(android.accessibilityservice.AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
        for (info in list) {
            if (info.resolveInfo.serviceInfo.packageName == packageName) {
                return true
            }
        }
        return false
    }

    private fun promptEscapeRoomActivation() {
        if (!isAccessibilityServiceEnabled()) {
            AlertDialog.Builder(this, com.google.android.material.R.style.Theme_MaterialComponents_DayNight_Dialog_Alert)
                .setTitle("Guardian Permission Needed")
                .setMessage("To enforce the Strict 1-Hour Lockdown and block all apps, NYIRABYO Life needs Accessibility Permission enabled in system settings.\n\nOpen Settings now to turn it ON?")
                .setPositiveButton("Open Settings") { _, _ ->
                    try {
                        startActivity(Intent(android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS))
                    } catch (_: Exception) {}
                }
                .setNegativeButton("Cancel", null)
                .show()
            return
        }

        if (prefs.isEscapeRoomActive()) {
            val remMins = (prefs.getEscapeRoomRemainingSeconds() / 60) + 1
            Toast.makeText(this, "Escape Room already active ($remMins mins remaining).", Toast.LENGTH_SHORT).show()
            startActivity(Intent(this, com.example.nyirabyolife.ui.lock.DisciplineLockActivity::class.java).apply {
                putExtra(com.example.nyirabyolife.ui.lock.DisciplineLockActivity.EXTRA_IS_ESCAPE_ROOM, true)
            })
            return
        }

        AlertDialog.Builder(this, com.google.android.material.R.style.Theme_MaterialComponents_DayNight_Dialog_Alert)
            .setTitle("Strict 1-Hour Block (Escape Room)")
            .setMessage("This will lock down your phone completely for 60 minutes.\n\n• All apps and settings will be blocked\n• Only incoming phone calls can be answered\n• Designed to break all urges immediately\n\nEnter the Escape Room now?")
            .setPositiveButton("Activate 1-Hour Block") { _, _ ->
                prefs.startEscapeRoomLockdown(60)
                Toast.makeText(this, "Escape Room Activated. Stay strong.", Toast.LENGTH_LONG).show()
                val intent = Intent(this, com.example.nyirabyolife.ui.lock.DisciplineLockActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
                    putExtra(com.example.nyirabyolife.ui.lock.DisciplineLockActivity.EXTRA_IS_ESCAPE_ROOM, true)
                }
                startActivity(intent)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun updateEscapeRoomStatus() {
        val b = slideToolsBinding ?: return
        if (prefs.isEscapeRoomActive()) {
            val remMins = (prefs.getEscapeRoomRemainingSeconds() / 60) + 1
            b.tvEscapeRoomStatus.text = "Escape Room Active ($remMins min remaining • Strict Lock)"
            b.tvEscapeRoomStatus.setTextColor(ContextCompat.getColor(this, R.color.rose_alert))
        } else {
            b.tvEscapeRoomStatus.text = "Locks phone for 60 min • Incoming calls allowed"
            b.tvEscapeRoomStatus.setTextColor(ContextCompat.getColor(this, R.color.text_secondary))
        }
    }

    // --- VIEWPAGER2 ADAPTER ---
    private inner class HomeSlideAdapter : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

        override fun getItemCount(): Int = 4

        override fun getItemViewType(position: Int): Int = position

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
            val inflater = LayoutInflater.from(parent.context)
            return when (viewType) {
                0 -> {
                    val dailyBinding = PageHomeDailyBinding.inflate(inflater, parent, false)
                    DailyViewHolder(dailyBinding)
                }
                1 -> {
                    val todoBinding = PageHomeTodoBinding.inflate(inflater, parent, false)
                    TodoViewHolder(todoBinding)
                }
                2 -> {
                    val toolsBinding = PageHomeToolsBinding.inflate(inflater, parent, false)
                    ToolsViewHolder(toolsBinding)
                }
                else -> {
                    val socialBinding = PageHomeSocialBinding.inflate(inflater, parent, false)
                    SocialViewHolder(socialBinding)
                }
            }
        }

        override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
            when (holder) {
                is DailyViewHolder -> bindSlideDaily(holder.binding)
                is TodoViewHolder -> bindSlideTodo(holder.binding)
                is ToolsViewHolder -> bindSlideTools(holder.binding)
                is SocialViewHolder -> bindSlideSocial(holder.binding)
            }
        }
    }

    private class DailyViewHolder(val binding: PageHomeDailyBinding) : RecyclerView.ViewHolder(binding.root)
    private class TodoViewHolder(val binding: PageHomeTodoBinding) : RecyclerView.ViewHolder(binding.root)
    private class ToolsViewHolder(val binding: PageHomeToolsBinding) : RecyclerView.ViewHolder(binding.root)
    private class SocialViewHolder(val binding: PageHomeSocialBinding) : RecyclerView.ViewHolder(binding.root)
}
