package com.example.nyirabyolife.data.local

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.example.nyirabyolife.data.model.FocusSession
import com.example.nyirabyolife.data.model.GratitudeEntry
import com.example.nyirabyolife.data.model.Mission
import com.example.nyirabyolife.data.model.PrayerItem
import com.example.nyirabyolife.data.model.UrgeLog
import com.example.nyirabyolife.data.model.WeeklyPlanTask
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import org.json.JSONArray
import org.json.JSONObject

class NyirabyoDbHelper(context: Context) : SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

    companion object {
        const val DATABASE_NAME = "nyirabyo_life.db"
        const val DATABASE_VERSION = 3

        @Volatile
        private var instance: NyirabyoDbHelper? = null

        fun getInstance(context: Context): NyirabyoDbHelper {
            return instance ?: synchronized(this) {
                instance ?: NyirabyoDbHelper(context.applicationContext).also { instance = it }
            }
        }
    }

    override fun onOpen(db: SQLiteDatabase) {
        super.onOpen(db)
        ensureColumnsExist(db)
    }

    private fun ensureColumnsExist(db: SQLiteDatabase) {
        try { db.execSQL("ALTER TABLE weekly_plans ADD COLUMN task_mode TEXT DEFAULT 'NORMAL'") } catch (_: Exception) {}
        try { db.execSQL("ALTER TABLE weekly_plans ADD COLUMN is_daily INTEGER DEFAULT 0") } catch (_: Exception) {}
        try { db.execSQL("ALTER TABLE missions ADD COLUMN time_hour INTEGER NOT NULL DEFAULT 0") } catch (_: Exception) {}
        try { db.execSQL("ALTER TABLE missions ADD COLUMN time_minute INTEGER NOT NULL DEFAULT 0") } catch (_: Exception) {}
        try { db.execSQL("ALTER TABLE missions ADD COLUMN task_mode TEXT DEFAULT 'NORMAL'") } catch (_: Exception) {}
    }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("""
            CREATE TABLE missions (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                title TEXT NOT NULL,
                is_completed INTEGER NOT NULL DEFAULT 0,
                date TEXT NOT NULL,
                order_index INTEGER NOT NULL DEFAULT 0,
                time_hour INTEGER NOT NULL DEFAULT 0,
                time_minute INTEGER NOT NULL DEFAULT 0,
                task_mode TEXT DEFAULT 'NORMAL'
            )
        """.trimIndent())

        db.execSQL("""
            CREATE TABLE prayers (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                title TEXT NOT NULL,
                is_answered INTEGER NOT NULL DEFAULT 0,
                created_at INTEGER NOT NULL
            )
        """.trimIndent())

        db.execSQL("""
            CREATE TABLE gratitude (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                text TEXT NOT NULL,
                date TEXT NOT NULL,
                timestamp INTEGER NOT NULL
            )
        """.trimIndent())

        db.execSQL("""
            CREATE TABLE urge_logs (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                timestamp INTEGER NOT NULL,
                intensity INTEGER NOT NULL,
                trigger_context TEXT,
                thoughts TEXT,
                used_reset_plan INTEGER NOT NULL DEFAULT 1
            )
        """.trimIndent())

        db.execSQL("""
            CREATE TABLE focus_sessions (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                session_type TEXT NOT NULL,
                duration_minutes INTEGER NOT NULL,
                timestamp INTEGER NOT NULL,
                is_completed INTEGER NOT NULL DEFAULT 1
            )
        """.trimIndent())

        db.execSQL("""
            CREATE TABLE IF NOT EXISTS weekly_plans (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                title TEXT NOT NULL,
                day_of_week INTEGER NOT NULL,
                time_hour INTEGER NOT NULL,
                time_minute INTEGER NOT NULL,
                is_alarm_enabled INTEGER NOT NULL DEFAULT 1,
                is_completed INTEGER NOT NULL DEFAULT 0,
                week_of_year INTEGER NOT NULL,
                year INTEGER NOT NULL,
                created_at INTEGER NOT NULL,
                task_mode TEXT DEFAULT 'NORMAL',
                is_daily INTEGER DEFAULT 0
            )
        """.trimIndent())
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        ensureColumnsExist(db)
    }

    // --- MISSIONS ---
    fun getMissionsForDate(date: String): List<Mission> {
        val list = mutableListOf<Mission>()
        val db = readableDatabase
        val cursor = db.query(
            "missions",
            null,
            "date = ?",
            arrayOf(date),
            null,
            null,
            "time_hour ASC, time_minute ASC, order_index ASC"
        )
        cursor.use {
            while (it.moveToNext()) {
                val id = it.getLong(it.getColumnIndexOrThrow("id"))
                val title = it.getString(it.getColumnIndexOrThrow("title"))
                val completed = it.getInt(it.getColumnIndexOrThrow("is_completed")) == 1
                val order = it.getInt(it.getColumnIndexOrThrow("order_index"))
                val hIdx = it.getColumnIndex("time_hour")
                val hour = if (hIdx != -1) it.getInt(hIdx) else 0
                val mIdx = it.getColumnIndex("time_minute")
                val min = if (mIdx != -1) it.getInt(mIdx) else 0
                val modeIdx = it.getColumnIndex("task_mode")
                val mode = if (modeIdx != -1) it.getString(modeIdx) ?: "NORMAL" else "NORMAL"
                list.add(Mission(id = id, title = title, isCompleted = completed, date = date, orderIndex = order, timeHour = hour, timeMinute = min, taskMode = mode))
            }
        }
        return list
    }

    fun getMissionById(id: Long): Mission? {
        val db = readableDatabase
        val cursor = db.query("missions", null, "id = ?", arrayOf(id.toString()), null, null, null)
        cursor.use {
            if (it.moveToFirst()) {
                val mId = it.getLong(it.getColumnIndexOrThrow("id"))
                val title = it.getString(it.getColumnIndexOrThrow("title"))
                val completed = it.getInt(it.getColumnIndexOrThrow("is_completed")) == 1
                val date = it.getString(it.getColumnIndexOrThrow("date"))
                val order = it.getInt(it.getColumnIndexOrThrow("order_index"))
                val hIdx = it.getColumnIndex("time_hour")
                val hour = if (hIdx != -1) it.getInt(hIdx) else 0
                val mIdx = it.getColumnIndex("time_minute")
                val min = if (mIdx != -1) it.getInt(mIdx) else 0
                val modeIdx = it.getColumnIndex("task_mode")
                val mode = if (modeIdx != -1) it.getString(modeIdx) ?: "NORMAL" else "NORMAL"
                return Mission(id = mId, title = title, isCompleted = completed, date = date, orderIndex = order, timeHour = hour, timeMinute = min, taskMode = mode)
            }
        }
        return null
    }

    fun ensureFoundationTasksForDay(dayOfWeek: Int, weekOfYear: Int, year: Int) {
        val tasks = getWeeklyTasksForDay(dayOfWeek, weekOfYear, year)
        val titles = tasks.map { it.title.trim().lowercase() }.toSet()

        if (!titles.any { it.contains("wake up") }) {
            addWeeklyTask(
                WeeklyPlanTask(
                    title = "Wake Up at 7:00",
                    dayOfWeek = dayOfWeek,
                    timeHour = 7,
                    timeMinute = 0,
                    isAlarmEnabled = true,
                    weekOfYear = weekOfYear,
                    year = year,
                    taskMode = "NORMAL",
                    isDaily = true
                )
            )
        }

        if (!titles.any { it.contains("pray") || it.contains("scripture") }) {
            addWeeklyTask(
                WeeklyPlanTask(
                    title = "Pray & Scripture at 7:10",
                    dayOfWeek = dayOfWeek,
                    timeHour = 7,
                    timeMinute = 10,
                    isAlarmEnabled = true,
                    weekOfYear = weekOfYear,
                    year = year,
                    taskMode = "MEDITATION",
                    isDaily = true
                )
            )
        }

        if (!titles.any { it.contains("bedtime") }) {
            addWeeklyTask(
                WeeklyPlanTask(
                    title = "BedTime 22:00",
                    dayOfWeek = dayOfWeek,
                    timeHour = 22,
                    timeMinute = 0,
                    isAlarmEnabled = true,
                    weekOfYear = weekOfYear,
                    year = year,
                    taskMode = "BEDTIME",
                    isDaily = true
                )
            )
        }
    }

    fun getSynchronizedDailyMissions(date: String, dayOfWeek: Int, weekOfYear: Int, year: Int): List<Mission> {
        ensureDefaultSundayWeeklyPlanIfEmpty(weekOfYear, year)
        ensureFoundationTasksForDay(dayOfWeek, weekOfYear, year)

        // Strict rule: arrange tasks chronologically by time (first to come first on the list)
        val weeklyTasks = getWeeklyTasksForDay(dayOfWeek, weekOfYear, year)
            .sortedWith(compareBy({ it.timeHour }, { it.timeMinute }))

        val db = writableDatabase

        // Synchronize and remove legacy collisions
        db.beginTransaction()
        try {
            val existingMissions = getMissionsForDate(date)
            val completedMap = existingMissions.associate { it.title.trim().lowercase() to it.isCompleted }

            // Clear legacy stale rows for today so no collisions exist
            db.delete("missions", "date = ?", arrayOf(date))

            weeklyTasks.forEachIndexed { index, task ->
                val wasCompleted = completedMap[task.title.trim().lowercase()] ?: task.isCompleted
                val values = ContentValues().apply {
                    put("title", task.title)
                    put("is_completed", if (wasCompleted) 1 else 0)
                    put("date", date)
                    put("order_index", index)
                    put("time_hour", task.timeHour)
                    put("time_minute", task.timeMinute)
                    put("task_mode", task.taskMode)
                }
                db.insert("missions", null, values)
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }

        return getMissionsForDate(date)
    }

    fun ensureDefaultMissionsIfEmpty(date: String): List<Mission> {
        val existing = getMissionsForDate(date)
        if (existing.isNotEmpty()) return existing

        val defaults = listOf(
            "Pray and read Scripture",
            "Build my future — coding",
            "Train my body — basketball"
        )
        val db = writableDatabase
        db.beginTransaction()
        try {
            defaults.forEachIndexed { index, title ->
                val values = ContentValues().apply {
                    put("title", title)
                    put("is_completed", 0)
                    put("date", date)
                    put("order_index", index)
                }
                db.insert("missions", null, values)
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
        return getMissionsForDate(date)
    }

    fun updateMissionStatus(id: Long, completed: Boolean) {
        val db = writableDatabase
        val values = ContentValues().apply {
            put("is_completed", if (completed) 1 else 0)
        }
        db.update("missions", values, "id = ?", arrayOf(id.toString()))

        // Synchronize with weekly_plans table for today if matching
        val mission = getMissionById(id)
        if (mission != null) {
            val cal = Calendar.getInstance()
            val dayOfWeek = cal.get(Calendar.DAY_OF_WEEK)
            val weekOfYear = cal.get(Calendar.WEEK_OF_YEAR)
            val year = cal.get(Calendar.YEAR)

            val weeklyTasks = getWeeklyTasksForDay(dayOfWeek, weekOfYear, year)
            val match = weeklyTasks.find { it.title.trim().equals(mission.title.trim(), ignoreCase = true) }
                ?: weeklyTasks.getOrNull(mission.orderIndex)

            if (match != null && match.isCompleted != completed) {
                val tValues = ContentValues().apply {
                    put("is_completed", if (completed) 1 else 0)
                }
                db.update("weekly_plans", tValues, "id = ?", arrayOf(match.id.toString()))
            }
        }
    }

    fun updateMissionTitle(id: Long, newTitle: String) {
        val db = writableDatabase
        val values = ContentValues().apply {
            put("title", newTitle)
        }
        db.update("missions", values, "id = ?", arrayOf(id.toString()))
    }

    fun saveCustomMission(date: String, title: String, orderIndex: Int, timeHour: Int = 0, timeMinute: Int = 0, taskMode: String = "NORMAL"): Long {
        val db = writableDatabase
        val values = ContentValues().apply {
            put("title", title)
            put("is_completed", 0)
            put("date", date)
            put("order_index", orderIndex)
            put("time_hour", timeHour)
            put("time_minute", timeMinute)
            put("task_mode", taskMode)
        }
        return db.insert("missions", null, values)
    }

    // --- PRAYERS ---
    fun getPrayers(): List<PrayerItem> {
        val list = mutableListOf<PrayerItem>()
        val db = readableDatabase
        val cursor = db.query("prayers", null, null, null, null, null, "is_answered ASC, created_at DESC")
        cursor.use {
            while (it.moveToNext()) {
                val id = it.getLong(it.getColumnIndexOrThrow("id"))
                val title = it.getString(it.getColumnIndexOrThrow("title"))
                val answered = it.getInt(it.getColumnIndexOrThrow("is_answered")) == 1
                val createdAt = it.getLong(it.getColumnIndexOrThrow("created_at"))
                list.add(PrayerItem(id, title, answered, createdAt))
            }
        }
        return list
    }

    fun addPrayer(title: String): Long {
        val db = writableDatabase
        val values = ContentValues().apply {
            put("title", title)
            put("is_answered", 0)
            put("created_at", System.currentTimeMillis())
        }
        return db.insert("prayers", null, values)
    }

    fun togglePrayerAnswered(id: Long, isAnswered: Boolean) {
        val db = writableDatabase
        val values = ContentValues().apply {
            put("is_answered", if (isAnswered) 1 else 0)
        }
        db.update("prayers", values, "id = ?", arrayOf(id.toString()))
    }

    fun deletePrayer(id: Long) {
        writableDatabase.delete("prayers", "id = ?", arrayOf(id.toString()))
    }

    // --- GRATITUDE ---
    fun getGratitudeEntries(): List<GratitudeEntry> {
        val list = mutableListOf<GratitudeEntry>()
        val db = readableDatabase
        val cursor = db.query("gratitude", null, null, null, null, null, "timestamp DESC")
        cursor.use {
            while (it.moveToNext()) {
                val id = it.getLong(it.getColumnIndexOrThrow("id"))
                val text = it.getString(it.getColumnIndexOrThrow("text"))
                val date = it.getString(it.getColumnIndexOrThrow("date"))
                val timestamp = it.getLong(it.getColumnIndexOrThrow("timestamp"))
                list.add(GratitudeEntry(id, text, date, timestamp))
            }
        }
        return list
    }

    fun addGratitude(text: String, date: String): Long {
        val db = writableDatabase
        val values = ContentValues().apply {
            put("text", text)
            put("date", date)
            put("timestamp", System.currentTimeMillis())
        }
        return db.insert("gratitude", null, values)
    }

    // --- RECOVERY & URGE LOGS ---
    fun logUrge(log: UrgeLog): Long {
        val db = writableDatabase
        val values = ContentValues().apply {
            put("timestamp", log.timestamp)
            put("intensity", log.intensity)
            put("trigger_context", log.triggerContext)
            put("thoughts", log.thoughts)
            put("used_reset_plan", if (log.usedResetPlan) 1 else 0)
        }
        return db.insert("urge_logs", null, values)
    }

    fun getUrgeLogs(): List<UrgeLog> {
        val list = mutableListOf<UrgeLog>()
        val db = readableDatabase
        val cursor = db.query("urge_logs", null, null, null, null, null, "timestamp DESC")
        cursor.use {
            while (it.moveToNext()) {
                val id = it.getLong(it.getColumnIndexOrThrow("id"))
                val timestamp = it.getLong(it.getColumnIndexOrThrow("timestamp"))
                val intensity = it.getInt(it.getColumnIndexOrThrow("intensity"))
                val context = it.getString(it.getColumnIndexOrThrow("trigger_context")) ?: ""
                val thoughts = it.getString(it.getColumnIndexOrThrow("thoughts")) ?: ""
                val usedReset = it.getInt(it.getColumnIndexOrThrow("used_reset_plan")) == 1
                list.add(UrgeLog(id, timestamp, intensity, context, thoughts, usedReset))
            }
        }
        return list
    }

    fun clearRecoveryData() {
        val db = writableDatabase
        db.delete("urge_logs", null, null)
    }

    // --- FOCUS SESSIONS ---
    fun addFocusSession(session: FocusSession): Long {
        val db = writableDatabase
        val values = ContentValues().apply {
            put("session_type", session.sessionType)
            put("duration_minutes", session.durationMinutes)
            put("timestamp", session.timestamp)
            put("is_completed", if (session.isCompleted) 1 else 0)
        }
        return db.insert("focus_sessions", null, values)
    }

    fun getFocusSessions(): List<FocusSession> {
        val list = mutableListOf<FocusSession>()
        val db = readableDatabase
        val cursor = db.query("focus_sessions", null, null, null, null, null, "timestamp DESC")
        cursor.use {
            while (it.moveToNext()) {
                val id = it.getLong(it.getColumnIndexOrThrow("id"))
                val type = it.getString(it.getColumnIndexOrThrow("session_type"))
                val duration = it.getInt(it.getColumnIndexOrThrow("duration_minutes"))
                val timestamp = it.getLong(it.getColumnIndexOrThrow("timestamp"))
                val completed = it.getInt(it.getColumnIndexOrThrow("is_completed")) == 1
                list.add(FocusSession(id, type, duration, timestamp, completed))
            }
        }
        return list
    }

    // --- EXPORT & PRIVACY ---
    fun exportAllDataAsJson(): String {
        val root = JSONObject()
        root.put("app", "NYIRABYO LIFE")
        root.put("export_timestamp", System.currentTimeMillis())

        // Export gratitude
        val gratitudeArray = JSONArray()
        getGratitudeEntries().forEach {
            gratitudeArray.put(JSONObject().apply {
                put("date", it.date)
                put("text", it.text)
                put("timestamp", it.timestamp)
            })
        }
        root.put("gratitude_entries", gratitudeArray)

        // Export prayers
        val prayersArray = JSONArray()
        getPrayers().forEach {
            prayersArray.put(JSONObject().apply {
                put("title", it.title)
                put("is_answered", it.isAnswered)
                put("created_at", it.createdAt)
            })
        }
        root.put("prayers", prayersArray)

        // Export focus sessions
        val focusArray = JSONArray()
        getFocusSessions().forEach {
            focusArray.put(JSONObject().apply {
                put("type", it.sessionType)
                put("duration_minutes", it.durationMinutes)
                put("timestamp", it.timestamp)
            })
        }
        root.put("focus_sessions", focusArray)

        // Export urge logs (anonymously / securely)
        val recoveryArray = JSONArray()
        getUrgeLogs().forEach {
            recoveryArray.put(JSONObject().apply {
                put("intensity", it.intensity)
                put("trigger", it.triggerContext)
                put("timestamp", it.timestamp)
            })
        }
        root.put("urge_logs", recoveryArray)

        return root.toString(2)
    }

    fun wipeAllData() {
        val db = writableDatabase
        db.delete("missions", null, null)
        db.delete("prayers", null, null)
        db.delete("gratitude", null, null)
        db.delete("urge_logs", null, null)
        db.delete("focus_sessions", null, null)
        db.delete("weekly_plans", null, null)
    }

    // --- WEEKLY PLANS WITH STRONG ALARM COLLABORATION ---
    fun getWeeklyTasksForWeek(weekOfYear: Int, year: Int): List<WeeklyPlanTask> {
        val list = mutableListOf<WeeklyPlanTask>()
        val db = readableDatabase
        val cursor = db.query(
            "weekly_plans",
            null,
            "week_of_year = ? AND year = ?",
            arrayOf(weekOfYear.toString(), year.toString()),
            null,
            null,
            "day_of_week ASC, time_hour ASC, time_minute ASC"
        )
        cursor.use {
            while (it.moveToNext()) {
                list.add(cursorToWeeklyTask(it))
            }
        }
        return list
    }

    fun getWeeklyTasksForDay(dayOfWeek: Int, weekOfYear: Int, year: Int): List<WeeklyPlanTask> {
        val list = mutableListOf<WeeklyPlanTask>()
        val db = readableDatabase
        val cursor = db.query(
            "weekly_plans",
            null,
            "day_of_week = ? AND week_of_year = ? AND year = ?",
            arrayOf(dayOfWeek.toString(), weekOfYear.toString(), year.toString()),
            null,
            null,
            "time_hour ASC, time_minute ASC"
        )
        cursor.use {
            while (it.moveToNext()) {
                list.add(cursorToWeeklyTask(it))
            }
        }
        return list
    }

    fun getWeeklyTaskById(id: Long): WeeklyPlanTask? {
        val db = readableDatabase
        val cursor = db.query("weekly_plans", null, "id = ?", arrayOf(id.toString()), null, null, null)
        cursor.use {
            if (it.moveToFirst()) {
                return cursorToWeeklyTask(it)
            }
        }
        return null
    }

    fun addWeeklyTask(task: WeeklyPlanTask): Long {
        val db = writableDatabase
        val values = ContentValues().apply {
            put("title", task.title)
            put("day_of_week", task.dayOfWeek)
            put("time_hour", task.timeHour)
            put("time_minute", task.timeMinute)
            put("is_alarm_enabled", if (task.isAlarmEnabled) 1 else 0)
            put("is_completed", if (task.isCompleted) 1 else 0)
            put("week_of_year", task.weekOfYear)
            put("year", task.year)
            put("created_at", System.currentTimeMillis())
            put("task_mode", task.taskMode)
            put("is_daily", if (task.isDaily) 1 else 0)
        }
        val newId = db.insert("weekly_plans", null, values)

        // If added for today, synchronize into daily missions
        val cal = Calendar.getInstance()
        if (task.dayOfWeek == cal.get(Calendar.DAY_OF_WEEK) &&
            task.weekOfYear == cal.get(Calendar.WEEK_OF_YEAR) &&
            task.year == cal.get(Calendar.YEAR)) {
            val todayDate = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
            val existing = getMissionsForDate(todayDate)
            saveCustomMission(
                date = todayDate,
                title = task.title,
                orderIndex = existing.size,
                timeHour = task.timeHour,
                timeMinute = task.timeMinute,
                taskMode = task.taskMode
            )
        }
        return newId
    }

    fun addDailyRecurringTask(task: WeeklyPlanTask): List<Long> {
        val ids = mutableListOf<Long>()
        for (day in Calendar.SUNDAY..Calendar.SATURDAY) {
            val dailyTask = task.copy(dayOfWeek = day, isDaily = true)
            ids.add(addWeeklyTask(dailyTask))
        }
        return ids
    }

    fun updateWeeklyTaskStatus(id: Long, isCompleted: Boolean) {
        val db = writableDatabase
        val values = ContentValues().apply {
            put("is_completed", if (isCompleted) 1 else 0)
        }
        db.update("weekly_plans", values, "id = ?", arrayOf(id.toString()))

        // Synchronize with missions table for today
        val task = getWeeklyTaskById(id)
        if (task != null) {
            val cal = Calendar.getInstance()
            if (task.dayOfWeek == cal.get(Calendar.DAY_OF_WEEK) &&
                task.weekOfYear == cal.get(Calendar.WEEK_OF_YEAR) &&
                task.year == cal.get(Calendar.YEAR)) {
                val todayDate = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
                val missions = getMissionsForDate(todayDate)
                val match = missions.find { it.title.trim().equals(task.title.trim(), ignoreCase = true) }
                if (match != null && match.isCompleted != isCompleted) {
                    val mValues = ContentValues().apply {
                        put("is_completed", if (isCompleted) 1 else 0)
                    }
                    db.update("missions", mValues, "id = ?", arrayOf(match.id.toString()))
                }
            }
        }
    }

    fun updateWeeklyTaskTitle(id: Long, newTitle: String) {
        val db = writableDatabase
        val values = ContentValues().apply {
            put("title", newTitle)
        }
        db.update("weekly_plans", values, "id = ?", arrayOf(id.toString()))
    }

    fun toggleWeeklyTaskAlarm(id: Long, isAlarmEnabled: Boolean) {
        val db = writableDatabase
        val values = ContentValues().apply {
            put("is_alarm_enabled", if (isAlarmEnabled) 1 else 0)
        }
        db.update("weekly_plans", values, "id = ?", arrayOf(id.toString()))
    }

    fun deleteWeeklyTask(id: Long) {
        val task = getWeeklyTaskById(id)
        writableDatabase.delete("weekly_plans", "id = ?", arrayOf(id.toString()))

        if (task != null) {
            val cal = Calendar.getInstance()
            if (task.dayOfWeek == cal.get(Calendar.DAY_OF_WEEK) &&
                task.weekOfYear == cal.get(Calendar.WEEK_OF_YEAR) &&
                task.year == cal.get(Calendar.YEAR)) {
                val todayDate = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
                writableDatabase.delete("missions", "date = ? AND title = ?", arrayOf(todayDate, task.title))
            }
        }
    }

    private fun cursorToWeeklyTask(cursor: android.database.Cursor): WeeklyPlanTask {
        val modeIdx = cursor.getColumnIndex("task_mode")
        val taskMode = if (modeIdx != -1) cursor.getString(modeIdx) ?: "NORMAL" else "NORMAL"
        val dailyIdx = cursor.getColumnIndex("is_daily")
        val isDaily = if (dailyIdx != -1) cursor.getInt(dailyIdx) == 1 else false

        return WeeklyPlanTask(
            id = cursor.getLong(cursor.getColumnIndexOrThrow("id")),
            title = cursor.getString(cursor.getColumnIndexOrThrow("title")),
            dayOfWeek = cursor.getInt(cursor.getColumnIndexOrThrow("day_of_week")),
            timeHour = cursor.getInt(cursor.getColumnIndexOrThrow("time_hour")),
            timeMinute = cursor.getInt(cursor.getColumnIndexOrThrow("time_minute")),
            isAlarmEnabled = cursor.getInt(cursor.getColumnIndexOrThrow("is_alarm_enabled")) == 1,
            isCompleted = cursor.getInt(cursor.getColumnIndexOrThrow("is_completed")) == 1,
            weekOfYear = cursor.getInt(cursor.getColumnIndexOrThrow("week_of_year")),
            year = cursor.getInt(cursor.getColumnIndexOrThrow("year")),
            createdAt = cursor.getLong(cursor.getColumnIndexOrThrow("created_at")),
            taskMode = taskMode,
            isDaily = isDaily
        )
    }

    fun ensureDefaultSundayWeeklyPlanIfEmpty(weekOfYear: Int, year: Int): List<WeeklyPlanTask> {
        val existing = getWeeklyTasksForWeek(weekOfYear, year)
        if (existing.isNotEmpty()) {
            // Also ensure foundational tasks exist for every day of the week
            for (day in Calendar.SUNDAY..Calendar.SATURDAY) {
                ensureFoundationTasksForDay(day, weekOfYear, year)
            }
            return getWeeklyTasksForWeek(weekOfYear, year)
        }

        // 1. Seed 3 foundational tasks for all 7 days
        for (day in Calendar.SUNDAY..Calendar.SATURDAY) {
            ensureFoundationTasksForDay(day, weekOfYear, year)
        }

        // 2. Add daytime work / study / rest presets
        val defaults = listOf(
            // SUNDAY
            WeeklyPlanTask(title = "Sunday Service & Deep Prayer with God", dayOfWeek = Calendar.SUNDAY, timeHour = 9, timeMinute = 0, isAlarmEnabled = true, weekOfYear = weekOfYear, year = year, taskMode = "MEDITATION"),
            WeeklyPlanTask(title = "Sunday Weekly Review & Goal Setting with God", dayOfWeek = Calendar.SUNDAY, timeHour = 16, timeMinute = 0, isAlarmEnabled = true, weekOfYear = weekOfYear, year = year, taskMode = "STUDY_FOCUS"),

            // MONDAY
            WeeklyPlanTask(title = "Deep Coding Work: High Focus Session", dayOfWeek = Calendar.MONDAY, timeHour = 9, timeMinute = 30, isAlarmEnabled = true, weekOfYear = weekOfYear, year = year, taskMode = "STUDY_FOCUS"),
            WeeklyPlanTask(title = "Physical Training: Basketball & Fitness", dayOfWeek = Calendar.MONDAY, timeHour = 17, timeMinute = 30, isAlarmEnabled = true, weekOfYear = weekOfYear, year = year, taskMode = "NORMAL"),

            // TUESDAY
            WeeklyPlanTask(title = "Coding & System Architecture Mastery", dayOfWeek = Calendar.TUESDAY, timeHour = 9, timeMinute = 30, isAlarmEnabled = true, weekOfYear = weekOfYear, year = year, taskMode = "STUDY_FOCUS"),

            // WEDNESDAY
            WeeklyPlanTask(title = "Mid-Week Basketball & Athletic Conditioning", dayOfWeek = Calendar.WEDNESDAY, timeHour = 17, timeMinute = 30, isAlarmEnabled = true, weekOfYear = weekOfYear, year = year, taskMode = "NORMAL"),

            // THURSDAY
            WeeklyPlanTask(title = "Deep Work: Project Milestones & Craft", dayOfWeek = Calendar.THURSDAY, timeHour = 9, timeMinute = 30, isAlarmEnabled = true, weekOfYear = weekOfYear, year = year, taskMode = "STUDY_FOCUS"),

            // FRIDAY
            WeeklyPlanTask(title = "Weekly Accomplishments Review & Solitude", dayOfWeek = Calendar.FRIDAY, timeHour = 18, timeMinute = 0, isAlarmEnabled = true, weekOfYear = weekOfYear, year = year, taskMode = "MEDITATION"),

            // SATURDAY
            WeeklyPlanTask(title = "Sabbath Rest, Scripture Reading & Renewal", dayOfWeek = Calendar.SATURDAY, timeHour = 10, timeMinute = 0, isAlarmEnabled = true, weekOfYear = weekOfYear, year = year, taskMode = "MEDITATION")
        )

        defaults.forEach { addWeeklyTask(it) }
        return getWeeklyTasksForWeek(weekOfYear, year)
    }
}

