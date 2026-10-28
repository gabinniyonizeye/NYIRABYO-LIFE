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
import com.example.nyirabyolife.databinding.ActivityHomeBinding
import com.example.nyirabyolife.databinding.PageHomeDailyBinding
import com.example.nyirabyolife.databinding.PageHomeSocialBinding
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
    private var slideToolsBinding: PageHomeToolsBinding? = null
    private var slideSocialBinding: PageHomeSocialBinding? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityHomeBinding.inflate(layoutInflater)
        setContentView(binding.root)

        dbHelper = NyirabyoDbHelper.getInstance(this)
        prefs = PreferencesManager.getInstance(this)

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
        loadFavoriteAppsDock()
        updateEscapeRoomStatus()
        updateSocialLimitsUI()
        slideToolsBinding?.let { loadMyAppsRow(it) }
    }

    private fun setupViewPager() {
        val adapter = HomeSlideAdapter()
        binding.homeViewPager.adapter = adapter
        binding.homeViewPager.offscreenPageLimit = 2 // Keeps all 3 slides active

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
    }

    private fun updateIndicatorDots(position: Int) {
        val dots = listOf(binding.dot1, binding.dot2, binding.dot3)
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

        // 3. Slide 2 (Tools & Habit Windows) Structural Design
        val t = slideToolsBinding
        if (t != null) {
            t.btnEscapeRoomBlock.background = com.example.nyirabyolife.util.ThemeHelper.createCardDrawable(this, themeColors, isElevated = true)
            t.btnToolFaith.background = com.example.nyirabyolife.util.ThemeHelper.createCardDrawable(this, themeColors, isElevated = true)
            t.btnToolFocus.background = com.example.nyirabyolife.util.ThemeHelper.createCardDrawable(this, themeColors, isElevated = true)
            t.btnToolReset.background = com.example.nyirabyolife.util.ThemeHelper.createCardDrawable(this, themeColors, isElevated = true)
            t.btnToolBible.background = com.example.nyirabyolife.util.ThemeHelper.createCardDrawable(this, themeColors, isElevated = true)
            t.btnManageMyApps.setTextColor(themeColors.primaryAccent)
        }
        // 4. Slide 3 (Digital Boundaries) Structural Design
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
                "${matchingWeekly.formattedTime}${if (matchingWeekly.isAlarmEnabled) " ⏰" else ""}"
            } else if (mission.timeHour > 0 || mission.timeMinute > 0 || mission.title.contains("7:00")) {
                mission.formattedTime
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

    // --- SLIDE 2: TOOLS & DISCIPLINE ---
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

        override fun getItemCount(): Int = 3

        override fun getItemViewType(position: Int): Int = position

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
            val inflater = LayoutInflater.from(parent.context)
            return when (viewType) {
                0 -> {
                    val dailyBinding = PageHomeDailyBinding.inflate(inflater, parent, false)
                    DailyViewHolder(dailyBinding)
                }
                1 -> {
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
                is ToolsViewHolder -> bindSlideTools(holder.binding)
                is SocialViewHolder -> bindSlideSocial(holder.binding)
            }
        }
    }

    private class DailyViewHolder(val binding: PageHomeDailyBinding) : RecyclerView.ViewHolder(binding.root)
    private class ToolsViewHolder(val binding: PageHomeToolsBinding) : RecyclerView.ViewHolder(binding.root)
    private class SocialViewHolder(val binding: PageHomeSocialBinding) : RecyclerView.ViewHolder(binding.root)
}
