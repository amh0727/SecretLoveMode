// ScenarioManager.kt

package com.secretlovemode.data.repository

import android.content.Context
import android.util.Log
import com.secretlovemode.data.model.GameState
import com.secretlovemode.data.model.Scenario
import com.secretlovemode.data.model.ScenarioFile
import com.secretlovemode.util.LanguageManager
import kotlinx.serialization.json.Json

object ScenarioManager {
    private const val TAG = "ScenarioManager"
    private val scenarioCache = mutableMapOf<String, ScenarioFile>()
    private val scenarios = mutableListOf<Scenario>()

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    fun loadScenarios(context: Context, fileName: String) {
        if (scenarios.isNotEmpty()) return

        try {
            Log.d(TAG, "Loading scenarios from: $fileName")
            val jsonString = context.assets.open(fileName).bufferedReader().use { it.readText() }
            val loadedScenarios = json.decodeFromString<List<Scenario>>(jsonString)
            scenarios.addAll(loadedScenarios)
            Log.d(TAG, "${scenarios.size} scenarios loaded successfully.")
        } catch (e: Exception) {
            Log.e(TAG, "Error loading or parsing scenarios from $fileName", e)
        }
    }

    fun getScenario(context: Context, scenarioId: String): ScenarioFile? {
        if (scenarioCache.containsKey(scenarioId)) {
            return scenarioCache[scenarioId]
        }

        return try {
            val fileName = LanguageManager.getScenarioFileName(context, "session$scenarioId")
            Log.d(TAG, "Loading scenario: $fileName")
            val jsonString = context.assets.open(fileName).reader(Charsets.UTF_8).use { it.readText() }
            val scenarioFile = json.decodeFromString<ScenarioFile>(jsonString)
            scenarioCache[scenarioId] = scenarioFile
            scenarioFile
        } catch (e: Exception) {
            Log.e(TAG, "Error loading or parsing scenario file for ID: $scenarioId", e)
            null
        }
    }

    fun checkAndTriggerNextScenario(gameState: GameState): String {
        val triggeredScenario = scenarios.lastOrNull { scenario ->
            scenario.trigger.conditions.all { condition ->
                checkCondition(condition, gameState)
            }
        }

        return triggeredScenario?.id ?: gameState.currentScenarioId
    }

    private fun checkCondition(condition: com.secretlovemode.data.model.Condition, gameState: GameState): Boolean {
        return when (condition.type) {
            "AFFINITY_GTE" -> gameState.affinity >= (condition.value.toIntOrNull() ?: Int.MAX_VALUE)
            "CONVERSATION_GTE" -> gameState.conversationCount >= (condition.value.toIntOrNull() ?: Int.MAX_VALUE)
            "CURRENT_SCENARIO_IS" -> gameState.currentScenarioId == condition.value
            else -> false
        }
    }

    fun clearCache() {
        scenarioCache.clear()
    }

    fun clearScenarios() {
        scenarios.clear()
    }
}