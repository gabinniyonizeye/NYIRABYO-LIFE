package com.example.nyirabyolife

import com.example.nyirabyolife.data.local.ScriptureRepository
import com.example.nyirabyolife.data.model.FocusSession
import com.example.nyirabyolife.data.model.Mission
import com.example.nyirabyolife.data.model.PrayerItem
import com.example.nyirabyolife.data.model.UrgeLog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NyirabyoCoreTest {

    @Test
    fun scriptureRepository_containsVerses() {
        val verses = ScriptureRepository.getAllVerses()
        assertTrue("Scripture repository should contain curated verses", verses.isNotEmpty())
        verses.forEach { verse ->
            assertTrue("Verse text must not be blank", verse.text.isNotBlank())
            assertTrue("Verse reference must not be blank", verse.reference.isNotBlank())
            assertTrue("Verse reflection prompt must not be blank", verse.reflectionPrompt.isNotBlank())
        }
    }

    @Test
    fun scriptureRepository_verseOfTheDayReturnsValidVerse() {
        val todayVerse = ScriptureRepository.getVerseOfTheDay()
        assertNotNull(todayVerse)
        assertTrue(todayVerse.reference.isNotBlank())
    }

    @Test
    fun mission_completionStateAndOrdering() {
        val mission1 = Mission(id = 1, title = "Pray and read Scripture", isCompleted = false, date = "2026-10-09", orderIndex = 0)
        val mission2 = Mission(id = 2, title = "Build my future — coding", isCompleted = true, date = "2026-10-09", orderIndex = 1)
        val mission3 = Mission(id = 3, title = "Train my body — basketball", isCompleted = false, date = "2026-10-09", orderIndex = 2)

        val missions = listOf(mission1, mission2, mission3)
        assertEquals(3, missions.size)
        assertEquals(1, missions.count { it.isCompleted })
        assertEquals("Build my future — coding", missions[1].title)
    }

    @Test
    fun prayerItem_defaultsAndToggle() {
        val prayer = PrayerItem(id = 1, title = "Strength and clarity in coding")
        assertFalse(prayer.isAnswered)
        val answeredPrayer = prayer.copy(isAnswered = true)
        assertTrue(answeredPrayer.isAnswered)
    }

    @Test
    fun urgeLog_victoryTracking() {
        val log = UrgeLog(
            intensity = 4,
            triggerContext = "Late night fatigue",
            thoughts = "Felt urge to browse mindless media, practiced box breathing and prayed.",
            usedResetPlan = true
        )
        assertEquals(4, log.intensity)
        assertTrue(log.usedResetPlan)
        assertEquals("Late night fatigue", log.triggerContext)
    }

    @Test
    fun focusSession_validTypes() {
        val session = FocusSession(
            sessionType = "Coding",
            durationMinutes = 25,
            isCompleted = true
        )
        assertEquals("Coding", session.sessionType)
        assertEquals(25, session.durationMinutes)
        assertTrue(session.isCompleted)
    }

    @Test
    fun socialPackages_identification() {
        assertTrue(com.example.nyirabyolife.data.local.PreferencesManager.INSTAGRAM_PACKAGES.contains("com.instagram.android"))
        assertTrue(com.example.nyirabyolife.data.local.PreferencesManager.INSTAGRAM_PACKAGES.contains("com.instagram.lite"))
        assertTrue(com.example.nyirabyolife.data.local.PreferencesManager.TIKTOK_PACKAGES.contains("com.zhiliaoapp.musically"))
        assertTrue(com.example.nyirabyolife.data.local.PreferencesManager.TIKTOK_PACKAGES.contains("com.ss.android.ugc.trill"))
        assertTrue(com.example.nyirabyolife.data.local.PreferencesManager.SOCIAL_RESTRICTED_PACKAGES.contains("com.instagram.android"))
        assertTrue(com.example.nyirabyolife.data.local.PreferencesManager.SOCIAL_RESTRICTED_PACKAGES.contains("com.zhiliaoapp.musically"))
    }

    @Test
    fun whatsAppDaytimeWindow_rangeCalculation() {
        val startMinutes = 8 * 60 // 08:00 = 480
        val endMinutes = 20 * 60  // 20:00 = 1200

        // In window
        assertTrue(480 in startMinutes..endMinutes)  // 08:00
        assertTrue(720 in startMinutes..endMinutes)  // 12:00
        assertTrue(1199 in startMinutes..endMinutes) // 19:59
        assertTrue(1200 in startMinutes..endMinutes) // 20:00

        // Outside window (late night or early morning)
        assertFalse(479 in startMinutes..endMinutes)  // 07:59
        assertFalse(1201 in startMinutes..endMinutes) // 20:01
        assertFalse(1380 in startMinutes..endMinutes) // 23:00
        assertFalse(60 in startMinutes..endMinutes)   // 01:00
    }
}
