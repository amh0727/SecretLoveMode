package com.secretlovemode.data.model

import kotlinx.serialization.Serializable
import java.io.Serializable as JavaSerializable



// 게임 상태를 나타내는 데이터 클래스
data class GameState(
    val characterName: String,
    val characterPersona: String,
    val affinity: Int = 50, // 호감도 (0-100)
    val conversationCount: Int = 0, // 대화 횟수
    val currentScenarioId: String = "CHAPTER_1_START", // 현재 시나리오 ID
    val currentSeason: Season = Season.SPRING, // 현재 계절
    val responsesInSeason: Int = 0, // 현재 계절에서 응답한 횟수
    val seasonChangeThreshold: Int = 3, // 계절이 바뀌는 응답 횟수
    val confessionKeyword: String? = null // 유저에게 플레그을 입력받는 변수 // 시나리오 json에 "requiresUserInput": true, // << 추가
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

// 캐릭터 정보 데이터 클래스
data class Character(
    val id: String,
    val characterName: String,
    val characterPersona: String,
    val modelFileName: String,
    val scenarioFileName: String
) : JavaSerializable

// 채팅 메시지 데이터 클래스
data class ChatMessage(
    val role: String,
    val message: String
) {
    companion object {
        const val ROLE_USER = "user"
        const val ROLE_MODEL = "model"
    }
}

/**
 * AI 응답의 캐릭터 대사 부분 (속마음 + 실제 대사)
 * JSON 파싱을 위해 @Serializable 어노테이션을 추가합니다.
 */
@Serializable
data class CharacterResponse(
    val inner_monologue: String,
    val spoken_response: String
)

/**
 * 초기 턴 결과 데이터 클래스
 * CharacterAi.kt에 있던 중복 정의를 삭제하고 이 클래스를 사용합니다.
 */
data class InitialTurnResult(
    val fullInitialResponse: CharacterResponse,
    val firstPlayerOptions: List<String>
)

/**
 * 플레이어 턴 결과 데이터 클래스
 * 이름을 TurnResult로 통일하고, CharacterAi.kt의 중복 정의를 삭제합니다.
 */
data class TurnResult(
    val fullCharacterResponse: CharacterResponse,
    val updatedAffinity: Int,
    val nextPlayerOptions: List<String>
)

enum class Season {
    SPRING, SUMMER, AUTUMN, WINTER
}