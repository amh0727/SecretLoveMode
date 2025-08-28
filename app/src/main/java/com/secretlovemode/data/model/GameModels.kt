package com.secretlovemode.data.model

import kotlinx.serialization.Serializable
import java.io.Serializable as JavaSerializable



// Game state data class
data class GameState(
    val characterName: String,
    val characterPersona: String,
    val playerName: String = "", // Added playerName field
    val affinity: Int = 50, // Affinity (0-100)
    val conversationCount: Int = 0, // Conversation count
    val currentScenarioId: String = "CHAPTER_1_START", // Current scenario ID
    val currentSeason: Season = Season.SPRING, // Current season
    val responsesInSeason: Int = 0, // Number of responses in current season
    val seasonChangeThreshold: Int = 3, // Number of responses to change season
    val confessionInput: String? = null, // 최종 고백 메시지 저장
    val conversationHistory: List<ChatMessage> = emptyList(), // 대화 기록
    val confessionKeyword: String? = null, // Variable to receive user input flag // Add "requiresUserInput": true to scenario json
    val sectionSummaries: Map<String, String> = emptyMap(), // 섹션별 요약 저장
    val keyInputValues: Map<String, String> = emptyMap(), // key_input 값들 저장
    val sectionDialogues: Map<String, List<SectionDialogue>> = emptyMap(), // 섹션별 전체 대화 저장
    val confessionJudgmentReason: String? = null // AI 고백 판정 이유 저장
) : JavaSerializable {
    fun getAffinityDescription(): String {
        return when {
            affinity >= 80 -> "最高の関係"
            affinity >= 60 -> "かなり親密"
            affinity >= 40 -> "好意的"
            affinity >= 20 -> "普通"
            else -> "険悪"
        }
    }
}

// Character information data class
data class Character(
    val id: String,
    val characterName: String,
    val characterPersona: String,
    val modelFileName: String,
    val scenarioFileName: String
) : JavaSerializable

// Chat message data class
data class ChatMessage(
    val role: String,
    val message: String
) {
    companion object {
        const val ROLE_USER = "user"
        const val ROLE_MODEL = "model"
        const val ROLE_SYSTEM = "system"
    }
}

/**
 * Character dialogue part of AI response (inner monologue + actual dialogue)
 * Add @Serializable annotation for JSON parsing.
 */
@Serializable
data class CharacterResponse(
    val inner_monologue: String,
    val spoken_response: String
)

/**
 * Initial turn result data class
 * Removes duplicate definition from CharacterAi.kt and uses this class.
 */
data class InitialTurnResult(
    val fullInitialResponse: CharacterResponse,
    val firstPlayerOptions: List<String>
)

/**
 * Player turn result data class
 * Unifies name to TurnResult and removes duplicate definition from CharacterAi.kt.
 */
data class TurnResult(
    val fullCharacterResponse: CharacterResponse,
    val updatedAffinity: Int,
    val nextPlayerOptions: List<String>
)

enum class Season {
    SPRING, SUMMER, AUTUMN, WINTER
}

/**
 * Data class representing option choices in scenario JSON files
 */
@Serializable
data class ScenarioOption(
    val text: String,
    val next: String,
    // affectionChange is determined by SLM, so it's not used here,
    // but can be kept or removed if necessary.
    val affectionChange: Int? = null
)

/**
 * Data class representing each step in scenario JSON files (message or choice)
 */
@Serializable
data class ScenarioNode(
    val type: String,
    val speaker: String,
    val text: String,
    val images: String? = null,
    val options: List<ScenarioOption>? = null,
    val requiresUserInput: Boolean = false, // Added for input type
    val keyInput: String? = null, // Added for key input trigger
    val placeholder: String? = null // Added for text_input type
)

/**
 * Data class representing the entire structure of session*.json files
 */
@Serializable
data class ScenarioFile(
    val title: String,
    val scenarios: List<ScenarioNode>
)

/**
 * Data class for storing section dialogue content
 */
@Serializable
data class SectionDialogue(
    val speaker: String,
    val text: String,
    val type: String = "message"
) : JavaSerializable