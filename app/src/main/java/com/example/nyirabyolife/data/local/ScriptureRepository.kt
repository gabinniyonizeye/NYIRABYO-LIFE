package com.example.nyirabyolife.data.local

import com.example.nyirabyolife.data.model.ScriptureVerse
import java.util.Calendar

object ScriptureRepository {

    private val verses = listOf(
        ScriptureVerse(
            id = 1,
            text = "Seek first the kingdom of God and his righteousness, and all these things will be added to you.",
            reference = "Matthew 6:33",
            theme = "Priorities & Purpose",
            reflectionPrompt = "What is the single most important action in your day that aligns with God's purpose?"
        ),
        ScriptureVerse(
            id = 2,
            text = "For God gave us a spirit not of fear but of power and love and self-control.",
            reference = "2 Timothy 1:7",
            theme = "Discipline & Strength",
            reflectionPrompt = "Where in your daily routine can you exercise self-control today instead of following an impulse?"
        ),
        ScriptureVerse(
            id = 3,
            text = "I can do all things through Christ who strengthens me.",
            reference = "Philippians 4:13",
            theme = "Perseverance",
            reflectionPrompt = "What challenging task (coding, training, or habit) will you conquer through His strength?"
        ),
        ScriptureVerse(
            id = 4,
            text = "No temptation has overtaken you that is not common to man. God is faithful, and he will not let you be tempted beyond your ability, but with the temptation he will also provide the way of escape.",
            reference = "1 Corinthians 10:13",
            theme = "Recovery & Deliverance",
            reflectionPrompt = "Remember that the way of escape is always ready: step back, breathe, and pray."
        ),
        ScriptureVerse(
            id = 5,
            text = "Whatever you do, work heartily, as for the Lord and not for men.",
            reference = "Colossians 3:23",
            theme = "Excellence & Work",
            reflectionPrompt = "How will you bring full excellence and dedication to your craft and training today?"
        ),
        ScriptureVerse(
            id = 6,
            text = "The steadfast love of the Lord never ceases; his mercies never come to an end; they are new every morning; great is your faithfulness.",
            reference = "Lamentations 3:22-23",
            theme = "Grace & Renewal",
            reflectionPrompt = "Yesterday is gone. Today is a fresh slate granted by God's infinite mercy."
        ),
        ScriptureVerse(
            id = 7,
            text = "Do you not know that in a race all the runners run, but only one receives the prize? So run that you may obtain it. Every athlete exercises self-control in all things.",
            reference = "1 Corinthians 9:24-25",
            theme = "The Athlete's Mindset",
            reflectionPrompt = "Train your body and your mind like an athlete striving for an eternal prize."
        ),
        ScriptureVerse(
            id = 8,
            text = "Trust in the Lord with all your heart, and do not lean on your own understanding. In all your ways acknowledge him, and he will make straight your paths.",
            reference = "Proverbs 3:5-6",
            theme = "Direction & Faith",
            reflectionPrompt = "Surrender anxious thoughts about the future into God's capable hands right now."
        ),
        ScriptureVerse(
            id = 9,
            text = "Be strong and courageous. Do not be frightened, and do not be dismayed, for the Lord your God is with you wherever you go.",
            reference = "Joshua 1:9",
            theme = "Courage",
            reflectionPrompt = "Stand tall in your identity as a man of faith and purposeful action."
        ),
        ScriptureVerse(
            id = 10,
            text = "Create in me a clean heart, O God, and renew a right spirit within me.",
            reference = "Psalm 51:10",
            theme = "Purity & Restoration",
            reflectionPrompt = "Ask God for an honest, pure heart free from hidden compromises."
        )
    )

    fun getVerseOfTheDay(): ScriptureVerse {
        val calendar = Calendar.getInstance()
        val dayOfYear = calendar.get(Calendar.DAY_OF_YEAR)
        val index = dayOfYear % verses.size
        return verses[index]
    }

    fun getAllVerses(): List<ScriptureVerse> = verses
}
