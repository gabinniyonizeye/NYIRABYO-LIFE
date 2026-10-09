package com.example.nyirabyolife.ui.drawer

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.nyirabyolife.data.local.PreferencesManager
import com.example.nyirabyolife.data.model.AppItem
import com.example.nyirabyolife.databinding.ActivityAppDrawerBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class AppDrawerActivity : AppCompatActivity() {

    companion object {
        // In-memory cache for ultra-fast, zero-lag opening
        private var cachedApps: List<AppItem>? = null
    }

    private lateinit var binding: ActivityAppDrawerBinding
    private lateinit var adapter: AppDrawerAdapter
    private lateinit var prefs: PreferencesManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAppDrawerBinding.inflate(layoutInflater)
        setContentView(binding.root)

        prefs = PreferencesManager.getInstance(this)

        binding.btnBack.setOnClickListener { finish() }

        setupRecyclerView()
        loadInstalledAppsAsync()

        binding.etSearchApp.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                adapter.filter(s?.toString() ?: "")
            }
            override fun afterTextChanged(s: Editable?) {}
        })
    }

    private fun setupRecyclerView() {
        adapter = AppDrawerAdapter(
            emptyList(),
            onAppClick = { app -> launchApp(app) },
            onPinClick = { app ->
                prefs.togglePinnedApp(app.packageName)
                val msg = if (app.isFavorite) "Pinned to favorites" else "Unpinned from favorites"
                Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
            }
        )
        binding.rvApps.layoutManager = LinearLayoutManager(this)
        binding.rvApps.adapter = adapter
    }

    private fun loadInstalledAppsAsync() {
        // If cache is ready, show it instantly!
        val cached = cachedApps
        if (cached != null) {
            val pinnedSet = prefs.getPinnedApps()
            val updated = cached.map { it.copy(isFavorite = pinnedSet.contains(it.packageName)) }
            adapter.updateApps(updated)
            binding.tvAppCount.text = "${updated.size} Installed Applications"
            binding.pbLoadingApps.visibility = View.GONE
            return
        }

        binding.pbLoadingApps.visibility = View.VISIBLE
        binding.tvAppCount.text = "Loading applications..."

        lifecycleScope.launch(Dispatchers.IO) {
            val pm = packageManager
            val mainIntent = Intent(Intent.ACTION_MAIN, null).apply {
                addCategory(Intent.CATEGORY_LAUNCHER)
            }
            val resolveInfos = pm.queryIntentActivities(mainIntent, 0)
            val pinnedSet = prefs.getPinnedApps()

            val appList = resolveInfos.mapNotNull { resolveInfo ->
                val pkg = resolveInfo.activityInfo.packageName
                if (pkg == packageName) return@mapNotNull null

                val label = resolveInfo.loadLabel(pm).toString()
                val icon = resolveInfo.loadIcon(pm)
                val className = resolveInfo.activityInfo.name
                val isFavorite = pinnedSet.contains(pkg)

                AppItem(
                    label = label,
                    packageName = pkg,
                    className = className,
                    icon = icon,
                    isFavorite = isFavorite
                )
            }.sortedBy { it.label.lowercase() }

            cachedApps = appList

            withContext(Dispatchers.Main) {
                binding.pbLoadingApps.visibility = View.GONE
                binding.tvAppCount.text = "${appList.size} Installed Applications"
                adapter.updateApps(appList)
            }
        }
    }

    private fun launchApp(app: AppItem) {
        try {
            val launchIntent = packageManager.getLaunchIntentForPackage(app.packageName)
            if (launchIntent != null) {
                startActivity(launchIntent)
                finish()
            } else {
                Toast.makeText(this, "Cannot launch ${app.label}", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Toast.makeText(this, "Error launching app: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
}
