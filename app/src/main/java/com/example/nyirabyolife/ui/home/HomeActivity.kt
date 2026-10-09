package com.example.nyirabyolife.ui.home

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
import com.example.nyirabyolife.ui.whatsapp.WhatsAppWindowActivity
import java.text.SimpleDateFormat
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
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        setIntent(intent)
        refreshAllData()
    }

    private fun refreshAllData() {
        loadVerseOfTheDay()
        loadMissions()
        loadFavoriteAppsDock()
        updateEscapeRoomStatus()
        updateToolsAndWhatsAppBanner()
        updateSocialLimitsUI()
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

    // --- SLIDE 1: DAILY WALK & MISSIONS ---
    private fun bindSlideDaily(dailyBinding: PageHomeDailyBinding) {
        slideDailyBinding = dailyBinding

        dailyBinding.cardTodayWord.setOnClickListener {
            startActivity(Intent(this, GodFirstActivity::class.java))
        }

        dailyBinding.btnEditMissions.setOnClickListener {
            promptEditMissions()
        }

        loadVerseOfTheDay()
        loadMissions()
    }

    private fun loadVerseOfTheDay() {
        val b = slideDailyBinding ?: return
        val verse = ScriptureRepository.getVerseOfTheDay()
        b.tvVerseText.text = "“${verse.text}”"
        b.tvVerseRef.text = verse.reference
    }

    private fun getTodayDateString(): String {
        return SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
    }

    private fun loadMissions() {
        val b = slideDailyBinding ?: return
        val today = getTodayDateString()
        currentMissions = dbHelper.ensureDefaultMissionsIfEmpty(today)

        val completedCount = currentMissions.count { it.isCompleted }
        b.tvMissionCounter.text = "$completedCount of ${currentMissions.size} complete"
        b.progressBarMissions.progress = completedCount

        if (currentMissions.isNotEmpty()) {
            bindMissionRow(currentMissions[0], b.tvMissionTitle1, b.ivCheck1, b.rowMission1)
        }
        if (currentMissions.size > 1) {
            bindMissionRow(currentMissions[1], b.tvMissionTitle2, b.ivCheck2, b.rowMission2)
        }
        if (currentMissions.size > 2) {
            bindMissionRow(currentMissions[2], b.tvMissionTitle3, b.ivCheck3, b.rowMission3)
        }
    }

    private fun bindMissionRow(
        mission: Mission,
        tvTitle: TextView,
        ivCheck: ImageView,
        rowLayout: LinearLayout
    ) {
        tvTitle.text = mission.title

        if (mission.isCompleted) {
            ivCheck.setImageResource(R.drawable.ic_check_circle)
            ivCheck.setColorFilter(ContextCompat.getColor(this, R.color.emerald_accent))
            tvTitle.paintFlags = tvTitle.paintFlags or Paint.STRIKE_THRU_TEXT_FLAG
            tvTitle.setTextColor(ContextCompat.getColor(this, R.color.text_tertiary))
        } else {
            ivCheck.setImageResource(R.drawable.ic_circle_outline)
            ivCheck.setColorFilter(ContextCompat.getColor(this, R.color.text_tertiary))
            tvTitle.paintFlags = tvTitle.paintFlags and Paint.STRIKE_THRU_TEXT_FLAG.inv()
            tvTitle.setTextColor(ContextCompat.getColor(this, R.color.text_primary))
        }

        rowLayout.setOnClickListener {
            val newStatus = !mission.isCompleted
            dbHelper.updateMissionStatus(mission.id, newStatus)
            loadMissions()
        }
    }

    private fun promptEditMissions() {
        if (currentMissions.size < 3) return

        val view = LayoutInflater.from(this).inflate(R.layout.dialog_edit_missions, null)
        val et1 = view.findViewById<EditText>(R.id.etMission1).apply { setText(currentMissions[0].title) }
        val et2 = view.findViewById<EditText>(R.id.etMission2).apply { setText(currentMissions[1].title) }
        val et3 = view.findViewById<EditText>(R.id.etMission3).apply { setText(currentMissions[2].title) }

        AlertDialog.Builder(this, com.google.android.material.R.style.Theme_MaterialComponents_DayNight_Dialog_Alert)
            .setTitle("Set Today's 3 Priorities")
            .setView(view)
            .setPositiveButton("Save Priorities") { _, _ ->
                val t1 = et1.text.toString().trim()
                val t2 = et2.text.toString().trim()
                val t3 = et3.text.toString().trim()

                if (t1.isNotEmpty()) dbHelper.updateMissionTitle(currentMissions[0].id, t1)
                if (t2.isNotEmpty()) dbHelper.updateMissionTitle(currentMissions[1].id, t2)
                if (t3.isNotEmpty()) dbHelper.updateMissionTitle(currentMissions[2].id, t3)

                loadMissions()
                Toast.makeText(this, "Daily priorities updated", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    // --- SLIDE 2: TOOLS & DISCIPLINE ---
    private fun bindSlideTools(toolsBinding: PageHomeToolsBinding) {
        slideToolsBinding = toolsBinding

        toolsBinding.btnToolFaith.setOnClickListener {
            startActivity(Intent(this, GodFirstActivity::class.java))
        }

        toolsBinding.btnToolFocus.setOnClickListener {
            startActivity(Intent(this, FocusActivity::class.java))
        }

        toolsBinding.btnToolReset.setOnClickListener {
            recoveryAuthManager.authenticate()
        }

        // Tapping Social Limits smoothly slides to Slide 3 (Digital Boundaries)
        toolsBinding.btnToolSocial.setOnClickListener {
            binding.homeViewPager.setCurrentItem(2, true)
        }

        toolsBinding.cardWhatsAppBanner.setOnClickListener {
            launchWhatsAppDirectly()
        }

        toolsBinding.btnLaunchWhatsAppBanner.setOnClickListener {
            launchWhatsAppDirectly()
        }

        toolsBinding.btnEscapeRoomBlock.setOnClickListener {
            promptEscapeRoomActivation()
        }

        updateToolsAndWhatsAppBanner()
        updateEscapeRoomStatus()
    }

    private fun updateToolsAndWhatsAppBanner() {
        val b = slideToolsBinding ?: return

        // WhatsApp daytime status
        val isWhatsAppOpen = prefs.isWhatsAppWindowOpen()
        if (isWhatsAppOpen) {
            b.tvWhatsAppBannerStatus.text = "Open 08:00 – 20:00 • Window Active"
            b.tvWhatsAppBannerStatus.setTextColor(ContextCompat.getColor(this, R.color.emerald_accent))
            b.ivWhatsAppBannerIcon.setColorFilter(ContextCompat.getColor(this, R.color.emerald_accent))
            b.btnLaunchWhatsAppBanner.text = "Open →"
            b.btnLaunchWhatsAppBanner.setTextColor(ContextCompat.getColor(this, R.color.emerald_accent))
        } else {
            b.tvWhatsAppBannerStatus.text = "Closed (Outside 08:00 – 20:00) • Mindful Pause"
            b.tvWhatsAppBannerStatus.setTextColor(ContextCompat.getColor(this, R.color.text_tertiary))
            b.ivWhatsAppBannerIcon.setColorFilter(ContextCompat.getColor(this, R.color.text_tertiary))
            b.btnLaunchWhatsAppBanner.text = "Mindful →"
            b.btnLaunchWhatsAppBanner.setTextColor(ContextCompat.getColor(this, R.color.gold_primary))
        }

        // Social Limits indicator text on card
        val usedInsta = prefs.getAppUsageMinutesToday("com.instagram.android")
        val usedTikTok = prefs.getAppUsageMinutesToday("com.zhiliaoapp.musically")
        val totalSocialUsed = usedInsta + usedTikTok
        b.tvSocialStatus.text = "Insta & TikTok • ${totalSocialUsed}m used (30m cap)"
    }

    // --- SLIDE 3: DIGITAL BOUNDARIES (INSTAGRAM & TIKTOK 30M BUDGET) ---
    private fun bindSlideSocial(socialBinding: PageHomeSocialBinding) {
        slideSocialBinding = socialBinding

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

        socialBinding.btnLaunchWhatsApp.setOnClickListener {
            launchWhatsAppDirectly()
        }
        socialBinding.cardWhatsAppSchedule.setOnClickListener {
            startActivity(Intent(this, WhatsAppWindowActivity::class.java))
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

        // 3. WHATSAPP (08:00 - 20:00 schedule)
        val isWaOpen = prefs.isWhatsAppWindowOpen()
        if (isWaOpen) {
            b.tvWhatsAppScheduleBadge.text = "ACTIVE (08:00 – 20:00)"
            b.tvWhatsAppScheduleBadge.setTextColor(ContextCompat.getColor(this, R.color.emerald_accent))
            b.tvWhatsAppScheduleNote.text = "Open for daytime communication (8 AM – 8 PM). Enjoy intentional connection."
            b.btnLaunchWhatsApp.text = "Open WhatsApp"
            b.btnLaunchWhatsApp.setBackgroundResource(R.drawable.button_emerald)
        } else {
            b.tvWhatsAppScheduleBadge.text = "CLOSED (OUTSIDE HOURS)"
            b.tvWhatsAppScheduleBadge.setTextColor(ContextCompat.getColor(this, R.color.gold_primary))
            b.tvWhatsAppScheduleNote.text = "Outside active daytime hours (08:00 – 20:00). Mindful pause protects rest."
            b.btnLaunchWhatsApp.text = "Mindful Pause"
            b.btnLaunchWhatsApp.setBackgroundResource(R.drawable.button_gold)
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

    private fun launchWhatsAppDirectly() {
        if (!prefs.isWhatsAppWindowOpen()) {
            startActivity(Intent(this, WhatsAppWindowActivity::class.java))
            return
        }
        val packages = listOf("com.whatsapp", "com.whatsapp.w4b")
        launchAppByPackage(packages, "WhatsApp")
    }

    private fun launchAppByPackage(packages: List<String>, appName: String) {
        val pm = packageManager
        for (pkg in packages) {
            val intent = pm.getLaunchIntentForPackage(pkg)
            if (intent != null) {
                startActivity(intent)
                return
            }
        }
        Toast.makeText(this, "$appName is not installed on this device.", Toast.LENGTH_SHORT).show()
    }

    // --- FAVORITES DOCK ---
    private fun loadFavoriteAppsDock() {
        binding.layoutFavorites.removeAllViews()
        val pinnedSet = prefs.getPinnedApps()
        val pm = packageManager

        if (pinnedSet.isEmpty()) {
            binding.layoutFavorites.visibility = View.GONE
            return
        }

        binding.layoutFavorites.visibility = View.VISIBLE

        pinnedSet.take(5).forEach { pkg ->
            try {
                val icon = pm.getApplicationIcon(pkg)
                val iv = ImageView(this).apply {
                    layoutParams = LinearLayout.LayoutParams(96, 96).apply {
                        setMargins(14, 0, 14, 0)
                    }
                    setImageDrawable(icon)
                    contentDescription = pkg
                    setPadding(8, 8, 8, 8)
                    setOnClickListener {
                        pm.getLaunchIntentForPackage(pkg)?.let { startActivity(it) }
                    }
                }
                binding.layoutFavorites.addView(iv)
            } catch (_: Exception) {}
        }
    }

    // --- ESCAPE ROOM LOCKDOWN ---
    private fun promptEscapeRoomActivation() {
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
