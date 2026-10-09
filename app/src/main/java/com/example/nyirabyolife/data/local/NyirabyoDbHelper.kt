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
import org.json.JSONArray
import org.json.JSONObject

class NyirabyoDbHelper(context: Context) : SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

    companion object {
        const val DATABASE_NAME = "nyirabyo_life.db"
        const val DATABASE_VERSION = 1

        @Volatile
        private var instance: NyirabyoDbHelper? = null

        fun getInstance(context: Context): NyirabyoDbHelper {
            return instance ?: synchronized(this) {
                instance ?: NyirabyoDbHelper(context.applicationContext).also { instance = it }
            }
        }
    }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("""
            CREATE TABLE missions (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                title TEXT NOT NULL,
                is_completed INTEGER NOT NULL DEFAULT 0,
                date TEXT NOT NULL,
                order_index INTEGER NOT NULL DEFAULT 0
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
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        // Migration strategy when schema evolves
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
            "order_index ASC"
        )
        cursor.use {
            while (it.moveToNext()) {
                val id = it.getLong(it.getColumnIndexOrThrow("id"))
                val title = it.getString(it.getColumnIndexOrThrow("title"))
                val completed = it.getInt(it.getColumnIndexOrThrow("is_completed")) == 1
                val order = it.getInt(it.getColumnIndexOrThrow("order_index"))
                list.add(Mission(id = id, title = title, isCompleted = completed, date = date, orderIndex = order))
            }
        }
        return list
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
    }

    fun updateMissionTitle(id: Long, newTitle: String) {
        val db = writableDatabase
        val values = ContentValues().apply {
            put("title", newTitle)
        }
        db.update("missions", values, "id = ?", arrayOf(id.toString()))
    }

    fun saveCustomMission(date: String, title: String, orderIndex: Int): Long {
        val db = writableDatabase
        val values = ContentValues().apply {
            put("title", title)
            put("is_completed", 0)
            put("date", date)
            put("order_index", orderIndex)
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
    }
}
