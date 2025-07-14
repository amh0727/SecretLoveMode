package com.secretlovemode

import java.io.Serializable

/**
 * MainActivity에서 GameActivity로 전달될 캐릭터 정보
 */
data class Character(
    val id: String,
    val characterName: String,
    val characterPersona: String,
    val scenarioFileName: String,
    val modelFileName: String
) : Serializable

/**
 * 게임의 현재 상태를 저장하는 데이터 클래스
 */
data class GameState(
    val characterName: String,
    val characterPersona: String,
    val affinity: Int = 40, // 호감도
    val conversationCount: Int = 0, // 총 대화 횟수
    val currentScenarioId: String = "CHAPTER_1_START",
    val currentSeason: Season = Season.SPRING, // 현재 계절
    val responsesInSeason: Int = 0, // 현재 계절에서 진행된 대화 수
    val seasonChangeThreshold: Int = 3 // 계절이 바뀌기까지 필요한 대화 수
) {
    fun getAffinityDescription(): String = when {
        affinity >= 90 -> "大好き"
        affinity >= 70 -> "好意的"
        affinity >= 50 -> "普通"
        affinity >= 30 -> "微妙"
        affinity >= 10 -> "嫌悪"
        else -> "険悪"
    }
}

/**
 * 계절을 나타내는 Enum 클래스
 */
enum class Season {
    SPRING, SUMMER, AUTUMN, WINTER
}

/**
 * 대화 기록을 위한 데이터 클래스
 */
data class ChatMessage(val role: String, val content: String) {
    companion object {
        const val ROLE_USER = "user"
        const val ROLE_MODEL = "model"
    }
}

/**
 * CharacterAi가 생성하는 최종 응답 객체.
 * 스트림 대신 완전한 응답 문자열과 계산된 호감도를 포함합니다.
 */
data class AiResponse(
    val responseText: String,
    val newAffinity: Int
)