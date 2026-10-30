package com.example.ml

import android.content.Context
import android.util.Log
import com.example.data.PersonNicknameEntity
import com.example.data.RadarDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlin.random.Random

enum class HumorLevel(val label: String, val description: String) {
    MILD("Mild Humor", "Friendly, whimsical and polite playful titles"),
    EXTRA_SILLY("Extra Silly", "Absurd, high-energy superhero and cartoon titles")
}

data class HumorSettings(
    val isEnabled: Boolean = true,
    val humorLevel: HumorLevel = HumorLevel.EXTRA_SILLY,
    val intervalSeconds: Int = 12, // Repeat interval: 10, 12, 15, or 20s
    val language: String = "English"
)

data class GeneratedCommentary(
    val nickname: String,
    val announcement: String,
    val fullSpokenWarning: String
)

class HumorCommentaryEngine(
    private val context: Context,
    private val database: RadarDatabase
) {
    companion object {
        private const val TAG = "HumorCommentaryEngine"

        // Fresh, harmless prefixes suitable for all ages
        private val INTROS = listOf(
            "Attention!",
            "Warning!",
            "Alert!",
            "Hold the phone!",
            "Look alive!",
            "Sound the alarm!",
            "Incoming!",
            "Heads up!",
            "Break out the confetti!",
            "Brace yourselves!",
            "Code red!",
            "Breaking news!"
        )

        // Action/arrival phrases
        private val ARRIVAL_PHRASES = listOf(
            "has entered the scene!",
            "has arrived!",
            "is approaching the perimeter!",
            "has been detected!",
            "is on the loose!",
            "has materialized!",
            "is officially in the building!",
            "has made a grand entrance!",
            "is right in front of the lens!"
        )

        // EXTRA SILLY Titles (harmless, fictional characters, funny roles, absurd objects)
        private val EXTRA_SILLY_TITLES = listOf(
            "The Pocket Ninja",
            "The Professional Snack Thief",
            "The Walking Wi-Fi Router",
            "The Legendary Couch Potato",
            "The Minister of Chaos",
            "The Sleepy Panda",
            "The Stealth Slipper",
            "The Tactical Hamster",
            "The Master of Microwaves",
            "The Human Lag Spike",
            "The Blanket Burrito",
            "The Grand Duke of Lost Keys",
            "The Midnight Fridge Raider",
            "The Autocorrect Destroyer",
            "The Caffeine Crusader",
            "The Glitch in the Matrix",
            "The Penguin in Disguise",
            "The International Detective of Snacks",
            "The Sock Monster Whisperer",
            "The Captain of Cat Naps",
            "The Sovereign of Sarcasm",
            "The Majestic Sloth",
            "The Bubble Wrap Popper Champion",
            "The Overlord of Online Shopping",
            "The Chief Procrastination Officer",
            "The Sneaky Raccoon",
            "The Disco Llama",
            "The Supreme Ruler of Leftovers",
            "The Agent 00-Snack",
            "The Unstoppable Yawn Machine"
        )

        // MILD Humor Titles (warm, polite, charmingly playful)
        private val MILD_TITLES = listOf(
            "The Chief Joy Officer",
            "The Master Chef of Fun",
            "The Legendary Coffee Connoisseur",
            "The Smile Ambassador",
            "The Supreme Sunshine Specialist",
            "The Neighborhood Superstar",
            "The Early Bird Legend",
            "The Team Motivation Master",
            "The World Champion High-Fiver",
            "The Official Good Vibes Inspector",
            "The Designated Fun Coordinator",
            "The Keeper of Secret Recipes",
            "The Master of Gentle Humor",
            "The Golden Heart Specialist",
            "The Certified Day-Maker"
        )
    }

    private val dao = database.personDao()

    private val _settings = MutableStateFlow(HumorSettings())
    val settings: StateFlow<HumorSettings> = _settings.asStateFlow()

    // Cache of recent nickname ID per personId to strictly avoid repeating the same one twice in a row
    private val lastUsedNicknameIdPerPerson = mutableMapOf<Long, Long>()

    fun updateSettings(newSettings: HumorSettings) {
        _settings.value = newSettings
    }

    fun setEnabled(enabled: Boolean) {
        _settings.value = _settings.value.copy(isEnabled = enabled)
    }

    fun setHumorLevel(level: HumorLevel) {
        _settings.value = _settings.value.copy(humorLevel = level)
    }

    fun setIntervalSeconds(seconds: Int) {
        _settings.value = _settings.value.copy(intervalSeconds = seconds.coerceIn(8, 30))
    }

    /**
     * Retrieves or generates a fresh, distinct humorous announcement for the recognized person.
     * Uses the person's unique database ID to ensure different people get different jokes.
     */
    suspend fun getNextCommentaryForPerson(
        personId: Long,
        personName: String
    ): GeneratedCommentary = withContext(Dispatchers.IO) {
        val currentSettings = _settings.value
        if (!currentSettings.isEnabled) {
            return@withContext GeneratedCommentary(
                nickname = personName,
                announcement = "Target identified: $personName",
                fullSpokenWarning = "DANGER! DANGER! Target identified: $personName"
            )
        }

        // 1. Ensure this person has a pool of distinct nicknames saved in the database
        var pool = dao.getNicknamesListForPerson(personId)
        if (pool.isEmpty()) {
            generateInitialPoolForPerson(personId, personName, currentSettings.humorLevel)
            pool = dao.getNicknamesListForPerson(personId)
        }

        // 2. Select a candidate avoiding the most recently used nickname for this person
        val lastUsedId = lastUsedNicknameIdPerPerson[personId]
        val candidate = if (pool.size > 1 && lastUsedId != null) {
            val eligible = pool.filter { it.id != lastUsedId }
            eligible.minByOrNull { it.timesUsed } ?: eligible.random()
        } else {
            pool.minByOrNull { it.timesUsed } ?: pool.firstOrNull()
        }

        val selectedNickname: String
        val selectedAnnouncement: String

        if (candidate != null) {
            selectedNickname = candidate.nickname
            selectedAnnouncement = candidate.announcement
            lastUsedNicknameIdPerPerson[personId] = candidate.id
            dao.updateNicknameUsage(candidate.id, candidate.timesUsed + 1, System.currentTimeMillis())
        } else {
            // Instant fallback if pool is somehow empty
            val fallback = createSingleNickname(personId, personName, currentSettings.humorLevel, 0)
            selectedNickname = fallback.first
            selectedAnnouncement = fallback.second
        }

        GeneratedCommentary(
            nickname = selectedNickname,
            announcement = selectedAnnouncement,
            fullSpokenWarning = "DANGER! DANGER! $selectedAnnouncement"
        )
    }

    /**
     * Seeds a distinct, personalized pool of creative nicknames for a registered profile.
     * Uses a hash of the personId and name to guarantee variety between different people.
     */
    suspend fun generateInitialPoolForPerson(
        personId: Long,
        personName: String,
        level: HumorLevel
    ) = withContext(Dispatchers.IO) {
        val titlesSource = if (level == HumorLevel.EXTRA_SILLY) EXTRA_SILLY_TITLES else MILD_TITLES

        // Seeded shuffle based on personId ensures Person 1 and Person 2 get completely different subsets!
        val seed = personId * 31L + personName.hashCode()
        val random = Random(seed)
        val shuffledTitles = titlesSource.shuffled(random)

        val nicknamesToInsert = mutableListOf<PersonNicknameEntity>()
        val count = minOf(6, shuffledTitles.size)

        for (i in 0 until count) {
            val title = shuffledTitles[i]
            val intro = INTROS[random.nextInt(INTROS.size)]
            val arrival = ARRIVAL_PHRASES[random.nextInt(ARRIVAL_PHRASES.size)]
            val announcement = "$intro $title $arrival"

            nicknamesToInsert.add(
                PersonNicknameEntity(
                    personId = personId,
                    nickname = title,
                    announcement = announcement,
                    timesUsed = 0,
                    lastUsedAt = 0L,
                    isCustom = false
                )
            )
        }

        dao.insertNicknames(nicknamesToInsert)
        Log.i(TAG, "Generated ${nicknamesToInsert.size} distinct nicknames for profile $personName (ID: $personId)")
    }

    /**
     * Generates a single dynamic nickname pair (Title, Announcement).
     */
    private fun createSingleNickname(
        personId: Long,
        personName: String,
        level: HumorLevel,
        indexOffset: Int
    ): Pair<String, String> {
        val titles = if (level == HumorLevel.EXTRA_SILLY) EXTRA_SILLY_TITLES else MILD_TITLES
        val intro = INTROS.random()
        val arrival = ARRIVAL_PHRASES.random()
        val title = titles.random()
        return Pair(title, "$intro $title $arrival")
    }

    /**
     * Adds a custom user-defined nickname to a profile.
     */
    suspend fun addCustomNickname(
        personId: Long,
        nickname: String
    ): Long = withContext(Dispatchers.IO) {
        val cleanNickname = nickname.trim()
        val intro = INTROS.random()
        val arrival = ARRIVAL_PHRASES.random()
        val announcement = "$intro $cleanNickname $arrival"

        dao.insertNickname(
            PersonNicknameEntity(
                personId = personId,
                nickname = cleanNickname,
                announcement = announcement,
                timesUsed = 0,
                lastUsedAt = 0L,
                isCustom = true
            )
        )
    }

    /**
     * Clears nicknames for a specific person and regenerates a fresh, varied pool.
     */
    suspend fun regenerateNicknamesForPerson(
        personId: Long,
        personName: String
    ) = withContext(Dispatchers.IO) {
        dao.deleteNicknamesForPerson(personId)
        lastUsedNicknameIdPerPerson.remove(personId)
        generateInitialPoolForPerson(personId, personName, _settings.value.humorLevel)
    }

    /**
     * Global clear of all stored nicknames.
     */
    suspend fun clearAllNicknames() = withContext(Dispatchers.IO) {
        dao.clearAllNicknames()
        lastUsedNicknameIdPerPerson.clear()
    }
}
