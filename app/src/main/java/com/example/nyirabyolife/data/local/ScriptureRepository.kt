package com.example.nyirabyolife.data.local

import com.example.nyirabyolife.data.model.ChristRevelation
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

    private val christRevelations = listOf(
        ChristRevelation(
            id = 1,
            title = "The Only True God & Jesus Christ",
            reference = "John 17:3",
            scripture = "And this is eternal life, that they know you, the only true God, and Jesus Christ whom you have sent.",
            revelation = "Eternal life is not merely a destination—it is a living, intimate knowledge of the Father through His beloved Son. When you seek Jesus, you touch the very heart of the Father.",
            prayer = "Heavenly Father, I desire to know You intimately. Lord Jesus Christ, reveal the Father's heart, wisdom, and righteousness to my soul today."
        ),
        ChristRevelation(
            id = 2,
            title = "The Radiance of God's Glory",
            reference = "Hebrews 1:3",
            scripture = "He is the radiance of the glory of God and the exact imprint of his nature, and he upholds the universe by the word of his power.",
            revelation = "Jesus is the uncreated light and exact representation of God Almighty. By His mighty Word, He sustains all creation—and He sustains your mind, discipline, and calling.",
            prayer = "Lord Jesus, radiance of the Father, illuminate my path. Uphold my thoughts and actions by Your powerful Word throughout this day."
        ),
        ChristRevelation(
            id = 3,
            title = "The Way, The Truth, and The Life",
            reference = "John 14:6",
            scripture = "Jesus said to him, 'I am the way, and the truth, and the life. No one comes to the Father except through me.'",
            revelation = "Jesus is not merely a teacher of truth; He IS Truth personified. Through His cross and resurrection, the veil is torn and we stand in the glorious presence of the Father.",
            prayer = "Lord Jesus, You are my Way in confusion, my Truth against every deceit, and my Life when I am weary. Lead me directly into the Father's presence."
        ),
        ChristRevelation(
            id = 4,
            title = "The Image of the Invisible God",
            reference = "Colossians 1:15-17",
            scripture = "He is the image of the invisible God, the firstborn of all creation. For by him all things were created... and in him all things hold together.",
            revelation = "Everything visible and invisible was created through Christ and for Christ. In Him, your future, your work, and your character are held together in divine purpose.",
            prayer = "Sovereign Christ, You hold all things together. Hold my focus, my energy, and my desires captive to Your holy love and divine purpose."
        ),
        ChristRevelation(
            id = 5,
            title = "The Father Revealed by the Son",
            reference = "Matthew 11:27-28",
            scripture = "No one knows the Father except the Son and anyone to whom the Son chooses to reveal him. Come to me, all who labor and are heavy laden, and I will give you rest.",
            revelation = "The Son delights to reveal the Father to the humble. In Christ, God is not distant or harsh; He is a loving Father who grants supernatural rest to those who turn to Him.",
            prayer = "Abba Father, thank You for sending Your Son to reveal Your everlasting love. Jesus, I come to You and receive Your rest and peace."
        ),
        ChristRevelation(
            id = 6,
            title = "The Word Became Flesh",
            reference = "John 1:1, 14",
            scripture = "In the beginning was the Word, and the Word was with God, and the Word was God... And the Word became flesh and dwelt among us, full of grace and truth.",
            revelation = "God did not remain distant in the heavens. The eternal Word took on flesh to walk in our midst, conquer sin and death, and fill our daily lives with overflowing grace and truth.",
            prayer = "Living Word, dwell richly in my heart. Let Your grace empower my discipline and Your truth sanctify my words and work."
        ),
        ChristRevelation(
            id = 7,
            title = "One with the Father",
            reference = "John 10:28-30",
            scripture = "I give them eternal life, and they will never perish, and no one will snatch them out of my hand. My Father, who has given them to me, is greater than all... I and the Father are one.",
            revelation = "Your spiritual security in Christ is unbreakable. You are held simultaneously in the hand of the Son and the hand of the Father. No temptation or darkness can snatch you away.",
            prayer = "Father, thank You that I am safe in Your almighty hand. Jesus, my Good Shepherd, I listen for Your voice and follow You with confidence."
        ),
        ChristRevelation(
            id = 8,
            title = "The Surpassing Worth of Knowing Christ",
            reference = "Philippians 3:8-10",
            scripture = "I count everything as loss because of the surpassing worth of knowing Christ Jesus my Lord... that I may know him and the power of his resurrection.",
            revelation = "All worldly achievements and fleeting digital impulses pale in comparison to the majestic treasure of knowing Jesus Christ and walking in the power of His resurrection.",
            prayer = "Lord Jesus, be my highest treasure. Count every distraction as worthless in my eyes compared to the surpassing glory of knowing You."
        ),
        ChristRevelation(
            id = 9,
            title = "The Spirit of Wisdom & Revelation",
            reference = "Ephesians 1:17",
            scripture = "That the God of our Lord Jesus Christ, the Father of glory, may give you the Spirit of wisdom and of revelation in the knowledge of him.",
            revelation = "Knowing God is a spiritual reality imparted by the Holy Spirit. As you read His Word, the eyes of your heart are enlightened to understand His great calling on your life.",
            prayer = "Father of glory, grant me the Spirit of wisdom and revelation. Open the eyes of my understanding to know Your Son deeper today than yesterday."
        ),
        ChristRevelation(
            id = 10,
            title = "The First, The Last, and The Living One",
            reference = "Revelation 1:17-18",
            scripture = "Fear not, I am the first and the last, and the living one. I died, and behold I am alive forevermore, and I have the keys of Death and Hades.",
            revelation = "Jesus Christ reigns supreme over time, eternity, life, and death. Because He is alive forevermore, you walk in His complete victory over all bondage and fear.",
            prayer = "Living Lord Jesus, Alpha and Omega, I bow before You in reverence. Fill me with Your resurrection boldness and power for this day."
        )
    )

    fun getVerseOfTheDay(): ScriptureVerse {
        val calendar = Calendar.getInstance()
        val dayOfYear = calendar.get(Calendar.DAY_OF_YEAR)
        val index = dayOfYear % verses.size
        return verses[index]
    }

    fun getAllVerses(): List<ScriptureVerse> = verses

    fun getChristRevelationOfTheDay(): ChristRevelation {
        val calendar = Calendar.getInstance()
        val dayOfYear = calendar.get(Calendar.DAY_OF_YEAR)
        val index = dayOfYear % christRevelations.size
        return christRevelations[index]
    }

    fun getAllChristRevelations(): List<ChristRevelation> = christRevelations
}

