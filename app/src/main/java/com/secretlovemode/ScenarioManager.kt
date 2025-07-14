package com.secretlovemode

import android.content.Context
import android.util.Log
import kotlinx.serialization.json.Json
import kotlin.collections.isNotEmpty
import kotlinx.serialization.Serializable

@Serializable
data class Condition(
    val type: String, // 예: "AFFINITY_GTE" (호감도 이상), "CONVERSATION_GTE" (대화 횟수 이상)
    val value: String // 비교할 값. 유연성을 위해 String으로 선언
)

// JSON의 "trigger" 객체 부분을 위한 데이터 클래스
@Serializable
data class Trigger(
    val conditions: List<Condition> // 이 조건들을 모두 만족해야 트리거됨 (AND 조건)
)

// 최종 시나리오 데이터 클래스
@Serializable
data class Scenario(
    val id: String,
    val setting: String,
    val characterGoal: String,
    val trigger: Trigger, // 로직 대신 트리거 데이터 객체를 가짐
    val season: String? = null // 특정 계절에만 적용 (e.g., "SPRING"), 없으면 모든 계절
)

object ScenarioManager {

    private const val DEFAULT_SCENARIO_ID = "CHAPTER_1_START"
    private const val EVENT_SCENARIO_DURATION = 3

    private var currentScenarios: List<Scenario> = emptyList()
    private var eventStartConversation = -1

    // 앱 시작 시 한번만 호출하여 시나리오를 로딩하는 함수
    fun loadScenarios(context: Context, scenarioFileName: String) {
        try {
            val jsonString = context.assets.open(scenarioFileName).bufferedReader().use { it.readText() }
            currentScenarios = Json.decodeFromString<List<Scenario>>(jsonString)
            Log.d("ScenarioManager", "'${scenarioFileName}'에서 ${currentScenarios.size}개의 시나리오를 성공적으로 로드했습니다.")
        } catch (e: Exception) {
            Log.e("ScenarioManager", "'${scenarioFileName}' 파일 로딩 실패", e)
            currentScenarios = emptyList() // 실패 시 리스트를 비웁니다.
        }
    }

    // 테스트를 위해 시나리오를 직접 주입할 수 있는 함수
    fun setScenariosForTest(testScenarios: List<Scenario>) {
        currentScenarios = testScenarios
    }

    fun getDefaultScenario(): Scenario? = getScenario(DEFAULT_SCENARIO_ID)

    fun getScenario(id: String): Scenario? {
        return currentScenarios.find { it.id == id }
    }

    // 조건에 맞는 시나리오를 찾아 ID를 반환
    fun checkAndTriggerNextScenario(gameState: GameState): String {
        val currentId = gameState.currentScenarioId

        if (currentId.startsWith("EVENT_")) {
            if (eventStartConversation >= 0 &&
                gameState.conversationCount - eventStartConversation >= EVENT_SCENARIO_DURATION
            ) {
                Log.d("ScenarioManager", "イベント $currentId 終了条件達成")
                eventStartConversation = -1
                return "DEFAULT"
            }
            return currentId
        }

        // 현재 계절에 맞는 시나리오만 필터링
        val seasonMatchingScenarios = currentScenarios.filter { 
            it.season == null || it.season.equals(gameState.currentSeason.name, ignoreCase = true)
        }

        // 모든 조건을 만족하는 첫번째 시나리오를 찾음
        val triggeredScenario = seasonMatchingScenarios.find { scenario ->
            scenario.trigger.conditions.all { condition ->
                isConditionMet(condition, gameState)
            }
        }

        triggeredScenario?.let {
            if (it.id.startsWith("EVENT_")) {
                eventStartConversation = gameState.conversationCount
                Log.d("ScenarioManager", "イベント ${it.id} 開始")
            }
        }

        return triggeredScenario?.id ?: currentId
    }

    // JSON에 정의된 조건을 실제 게임 상태와 비교하여 판단하는 로직
    private fun isConditionMet(condition: Condition, gameState: GameState): Boolean {
        return try {
            when (condition.type) {
                "AFFINITY_GTE" -> gameState.affinity >= condition.value.toInt()
                "CONVERSATION_GTE" -> gameState.conversationCount >= condition.value.toInt()
                "CURRENT_SCENARIO_IS" -> gameState.currentScenarioId == condition.value
                // 여기에 필요한 다른 조건 타입들을 추가할 수 있습니다.
                // "AFFINITY_LTE", "ITEM_OWNED" 등
                else -> false
            }
        } catch (e: NumberFormatException) {
            Log.e("ScenarioManager", "조건 값 파싱 오류: ${condition.value}", e)
            false
        }
    }
}