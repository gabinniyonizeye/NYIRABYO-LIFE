package com.example.nyirabyolife.ui.drawer

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.example.nyirabyolife.R
import com.example.nyirabyolife.data.model.AppItem
import com.example.nyirabyolife.databinding.ItemAppBinding
import com.example.nyirabyolife.util.ThemeHelper

class AppDrawerAdapter(
    private var allApps: List<AppItem>,
    private val onAppClick: (AppItem) -> Unit,
    private val onPinClick: (AppItem) -> Unit
) : RecyclerView.Adapter<AppDrawerAdapter.AppViewHolder>() {

    private var filteredList: MutableList<AppItem> = allApps.toMutableList()

    fun updateApps(newApps: List<AppItem>) {
        allApps = newApps
        filteredList = newApps.toMutableList()
        notifyDataSetChanged()
    }

    fun filter(query: String) {
        filteredList = if (query.isBlank()) {
            allApps.toMutableList()
        } else {
            allApps.filter {
                it.label.contains(query, ignoreCase = true) ||
                it.packageName.contains(query, ignoreCase = true)
            }.toMutableList()
        }
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AppViewHolder {
        val binding = ItemAppBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return AppViewHolder(binding)
    }

    override fun onBindViewHolder(holder: AppViewHolder, position: Int) {
        holder.bind(filteredList[position])
    }

    override fun getItemCount(): Int = filteredList.size

    inner class AppViewHolder(private val binding: ItemAppBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(app: AppItem) {
            binding.tvAppName.text = app.label
            binding.tvAppPackage.text = app.packageName
            if (app.icon != null) {
                binding.ivAppIcon.setImageDrawable(app.icon)
            } else {
                binding.ivAppIcon.setImageResource(R.drawable.ic_apps)
            }

            val theme = ThemeHelper.getActiveThemeColors(binding.root.context)
            val pinColor = if (app.isFavorite) theme.primaryAccent else ContextCompat.getColor(binding.root.context, R.color.text_tertiary)
            binding.ivPinFavorite.setColorFilter(pinColor)

            binding.root.setOnClickListener { onAppClick(app) }
            binding.ivPinFavorite.setOnClickListener {
                app.isFavorite = !app.isFavorite
                val newColor = if (app.isFavorite) theme.primaryAccent else ContextCompat.getColor(binding.root.context, R.color.text_tertiary)
                binding.ivPinFavorite.setColorFilter(newColor)
                onPinClick(app)
            }
        }
    }
}
