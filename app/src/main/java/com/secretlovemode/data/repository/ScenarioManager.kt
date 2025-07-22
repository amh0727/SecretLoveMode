package com.secretlovemode.data.repository

import android.content.Context
import android.util.Log
import com.secretlovemode.data.model.GameState
import com.secretlovemode.data.model.Scenario
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json

object ScenarioManager {

    private var scenarios: Map<String, Scenario> = emptyMap()

    fun loadScenarios(context: Context, scenarioFileName: String) {
        try {
            val jsonString = context.assets.open(scenarioFileName).bufferedReader().use { it.readText() }
            val scenarioList = Json.decodeFromString<List<Scenario>>(jsonString)
            scenarios = scenarioList.associateBy { it.id }
            Log.d("ScenarioManager", "Loaded ${scenarios.size} scenarios from '$scenarioFileName'.")
        } catch (e: Exception) {
            Log.e("ScenarioManager", "Failed to load '$scenarioFileName'", e)
            scenarios = emptyMap()
        }
    }

    fun getScenario(id: String): Scenario? {
        return scenarios[id]
    }

    fun checkAndTriggerNextScenario(gameState: GameState): String {
        // Find the first scenario that meets all conditions
        val nextScenario = scenarios.values.find { scenario ->
            scenario.trigger.conditions.all { condition ->
                when (condition.type) {
                    "AFFINITY_GTE" -> gameState.affinity >= condition.value.toInt()
                    "CONVERSATION_GTE" -> gameState.conversationCount >= condition.value.toInt()
                    // Add other conditions here
                    else -> false
                }
            }
        }
        return nextScenario?.id ?: gameState.currentScenarioId
    }
}
