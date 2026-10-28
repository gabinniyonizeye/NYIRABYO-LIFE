package com.example.nyirabyolife.ui.restriction

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.nyirabyolife.R
import com.example.nyirabyolife.data.local.PreferencesManager
import com.example.nyirabyolife.data.model.AppItem
import com.example.nyirabyolife.databinding.ActivityAppRestrictionsBinding
import com.example.nyirabyolife.databinding.ItemAppRestrictionBinding
import com.example.nyirabyolife.util.UsageStatsHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class AppRestrictionsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAppRestrictionsBinding
    private lateinit var prefs: PreferencesManager
    private lateinit var adapter: RestrictionAdapter
    private var allApps: List<AppItem> = emptyList()
    private var usageMap: Map<String, Int> = emptyMap()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAppRestrictionsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        prefs = PreferencesManager.getInstance(this)
        binding.btnBack.setOnClickListener { finish() }

        applyThemeColors()
        setupSwitches()
        setupRecyclerView()
        loadInstalledAppsAsync()

        binding.etSearchRestriction.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                adapter.filter(s?.toString() ?: "")
            }
            override fun afterTextChanged(s: Editable?) {}
        })
    }

    private fun setupSwitches() {
        binding.switchMasterBlocker.isChecked = prefs.isBlockerEnabled()
        binding.switchMasterBlocker.setOnCheckedChangeListener { _, isChecked ->
            prefs.setBlockerEnabled(isChecked)
            updateCount()
        }

        binding.switchSettingsGuard.isChecked = prefs.isSettingsProtectionEnabled()
        binding.switchSettingsGuard.setOnCheckedChangeListener { _, isChecked ->
            prefs.setSettingsProtectionEnabled(isChecked)
        }

        binding.switchLockScreen.isChecked = prefs.isLockScreenEnabled()
        binding.switchLockScreen.setOnCheckedChangeListener { _, isChecked ->
            prefs.setLockScreenEnabled(isChecked)
            val msg = if (isChecked) "Discipline Lock Screen enabled" else "Discipline Lock Screen disabled"
            Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
        }
    }

    private fun setupRecyclerView() {
        adapter = RestrictionAdapter(
            emptyList(),
            onToggle = { app, isRestricted ->
                prefs.toggleRestrictedApp(app.packageName)
                updateCount()
            },
            onTimerClick = { app ->
                showSetTimerDialog(app)
            }
        )
        binding.rvRestrictedApps.layoutManager = LinearLayoutManager(this)
        binding.rvRestrictedApps.adapter = adapter
    }

    private fun loadInstalledAppsAsync() {
        binding.pbLoadingRestrictions.visibility = View.VISIBLE
        binding.tvRestrictedCount.text = "Loading apps & usage..."

        lifecycleScope.launch(Dispatchers.IO) {
            val pm = packageManager
            val mainIntent = Intent(Intent.ACTION_MAIN, null).apply {
                addCategory(Intent.CATEGORY_LAUNCHER)
            }
            val resolveInfos = pm.queryIntentActivities(mainIntent, 0)
            val restrictedSet = prefs.getRestrictedApps()

            // Fetch usage from Android system
            usageMap = UsageStatsHelper.getAllTodayUsageMinutes(this@AppRestrictionsActivity)

            allApps = resolveInfos.mapNotNull { resolveInfo ->
                val pkg = resolveInfo.activityInfo.packageName
                if (pkg == packageName) return@mapNotNull null

                val label = resolveInfo.loadLabel(pm).toString()
                val icon = resolveInfo.loadIcon(pm)
                val isRestricted = restrictedSet.contains(pkg)

                AppItem(
                    label = label,
                    packageName = pkg,
                    className = resolveInfo.activityInfo.name,
                    icon = icon,
                    isFavorite = isRestricted
                )
            }.sortedBy { it.label.lowercase() }

            withContext(Dispatchers.Main) {
                binding.pbLoadingRestrictions.visibility = View.GONE
                adapter.updateData(allApps, usageMap)
                updateCount()
            }
        }
    }

    private fun showSetTimerDialog(app: AppItem) {
        val currentLimit = prefs.getAppDailyLimit(app.packageName)
        val options = arrayOf(
            "No Limit (Off)",
            "15 Minutes",
            "30 Minutes",
            "45 Minutes",
            "60 Minutes",
            "90 Minutes"
        )
        val limits = arrayOf(0, 15, 30, 45, 60, 90)

        val selectedIndex = limits.indexOfFirst { it == currentLimit }.let { if (it >= 0) it else 0 }

        AlertDialog.Builder(this, com.google.android.material.R.style.Theme_MaterialComponents_DayNight_Dialog_Alert)
            .setTitle("Daily Limit for ${app.label}")
            .setSingleChoiceItems(options, selectedIndex) { dialog, which ->
                val chosenLimit = limits[which]
                prefs.setAppDailyLimit(app.packageName, chosenLimit)
                adapter.notifyDataSetChanged()
                val msg = if (chosenLimit > 0) "Set ${chosenLimit}m daily limit for ${app.label}" else "Removed limit for ${app.label}"
                Toast.makeText(this@AppRestrictionsActivity, msg, Toast.LENGTH_SHORT).show()
                dialog.dismiss()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun updateCount() {
        val restrictedCount = prefs.getRestrictedApps().size
        binding.tvRestrictedCount.text = "$restrictedCount apps shielded • Settings Guard: ${if (prefs.isSettingsProtectionEnabled()) "ON" else "OFF"}"
    }

    private fun applyThemeColors() {
        val theme = com.example.nyirabyolife.util.ThemeHelper.getActiveThemeColors(this)
        binding.btnBack.background = com.example.nyirabyolife.util.ThemeHelper.createPillDrawable(this, theme)
        binding.cardMasterControls.background = com.example.nyirabyolife.util.ThemeHelper.createCardDrawable(this, theme, isElevated = true)
        binding.layoutSearchBar.background = com.example.nyirabyolife.util.ThemeHelper.createPillDrawable(this, theme)
        binding.tvRestrictedCount.setTextColor(theme.secondaryAccent)

        val thumbColors = android.content.res.ColorStateList(
            arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf()),
            intArrayOf(theme.primaryAccent, ContextCompat.getColor(this, R.color.text_tertiary))
        )
        binding.switchMasterBlocker.thumbTintList = thumbColors
        binding.switchSettingsGuard.thumbTintList = thumbColors
    }

    inner class RestrictionAdapter(
        private var list: List<AppItem>,
        private val onToggle: (AppItem, Boolean) -> Unit,
        private val onTimerClick: (AppItem) -> Unit
    ) : RecyclerView.Adapter<RestrictionAdapter.ViewHolder>() {

        private var filtered: MutableList<AppItem> = list.toMutableList()
        private var usageData: Map<String, Int> = emptyMap()

        fun updateData(newList: List<AppItem>, usage: Map<String, Int>) {
            list = newList
            usageData = usage
            filtered = newList.toMutableList()
            notifyDataSetChanged()
        }

        fun filter(query: String) {
            filtered = if (query.isBlank()) {
                list.toMutableList()
            } else {
                list.filter {
                    it.label.contains(query, ignoreCase = true) ||
                    it.packageName.contains(query, ignoreCase = true)
                }.toMutableList()
            }
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val itemBinding = ItemAppRestrictionBinding.inflate(
                LayoutInflater.from(parent.context), parent, false
            )
            return ViewHolder(itemBinding)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val theme = com.example.nyirabyolife.util.ThemeHelper.getActiveThemeColors(holder.itemView.context)
            holder.binding.root.background = com.example.nyirabyolife.util.ThemeHelper.createCardDrawable(holder.itemView.context, theme, isElevated = false)
            val app = filtered[position]
            val restrictedSet = prefs.getRestrictedApps()
            val isRestricted = restrictedSet.contains(app.packageName)
            val limitMinutes = prefs.getAppDailyLimit(app.packageName)

            // Combine system usage stats with internal live tracking
            val sysMinutes = usageData[app.packageName] ?: 0
            val internalMinutes = prefs.getAppUsageMinutesToday(app.packageName)
            val todayMinutes = maxOf(sysMinutes, internalMinutes)

            holder.binding.tvAppName.text = app.label
            holder.binding.tvAppPackage.text = app.packageName
            if (app.icon != null) {
                holder.binding.ivAppIcon.setImageDrawable(app.icon)
            } else {
                holder.binding.ivAppIcon.setImageResource(R.drawable.ic_shield)
            }

            // Usage status text
            if (limitMinutes > 0) {
                holder.binding.tvUsageToday.text = "Used today: ${todayMinutes}m / Limit: ${limitMinutes}m"
                if (todayMinutes >= limitMinutes) {
                    holder.binding.tvUsageToday.setTextColor(ContextCompat.getColor(this@AppRestrictionsActivity, R.color.rose_alert))
                } else {
                    holder.binding.tvUsageToday.setTextColor(theme.secondaryAccent)
                }
                holder.binding.btnUsageTimer.text = "${limitMinutes}m limit ⏱"
                holder.binding.btnUsageTimer.setTextColor(theme.primaryAccent)
            } else {
                holder.binding.tvUsageToday.text = "Used today: ${todayMinutes}m • No daily limit"
                holder.binding.tvUsageToday.setTextColor(ContextCompat.getColor(this@AppRestrictionsActivity, R.color.text_secondary))
                holder.binding.btnUsageTimer.text = "Set Timer ⏱"
                holder.binding.btnUsageTimer.setTextColor(ContextCompat.getColor(this@AppRestrictionsActivity, R.color.text_tertiary))
            }

            holder.binding.btnUsageTimer.setOnClickListener {
                onTimerClick(app)
            }

            holder.binding.switchRestrict.setOnCheckedChangeListener(null)
            holder.binding.switchRestrict.isChecked = isRestricted
            val itemThumbColors = android.content.res.ColorStateList(
                arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf()),
                intArrayOf(theme.primaryAccent, ContextCompat.getColor(this@AppRestrictionsActivity, R.color.text_tertiary))
            )
            holder.binding.switchRestrict.thumbTintList = itemThumbColors
            holder.binding.switchRestrict.setOnCheckedChangeListener { _, isChecked ->
                onToggle(app, isChecked)
            }
        }

        override fun getItemCount(): Int = filtered.size

        inner class ViewHolder(val binding: ItemAppRestrictionBinding) :
            RecyclerView.ViewHolder(binding.root)
    }
}
