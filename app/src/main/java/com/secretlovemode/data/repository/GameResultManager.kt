package com.secretlovemode.data.repository

import android.content.Context
import android.content.SharedPreferences
import com.secretlovemode.data.model.GameResult
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.Serializable
import android.util.Log

@Serializable
data class SerializableGameResult(
    val playerName: String,
    val finalAffinity: Int,
    val confessionSuccess: Boolean,
    val gameEndType: String,
    val timestamp: Long
) {
    fun toGameResult(): GameResult {
        val endType = when (gameEndType) {
            "CONFESSION_SUCCESS" -> GameResult.GameEndType.CONFESSION_SUCCESS
            "CONFESSION_FAILURE" -> GameResult.GameEndType.CONFESSION_FAILURE
            "FRIEND_END" -> GameResult.GameEndType.FRIEND_END
            "GAME_OVER" -> GameResult.GameEndType.GAME_OVER
            "SCENARIO_END" -> GameResult.GameEndType.SCENARIO_END
            else -> GameResult.GameEndType.GAME_OVER
        }
        return GameResult(playerName, finalAffinity, confessionSuccess, endType, timestamp)
    }
}

object GameResultManager {
    private const val PREF_NAME = "game_results"
    private const val KEY_RESULTS = "results"
    private const val TAG = "GameResultManager"
    
    private val json = Json { 
        ignoreUnknownKeys = true
        encodeDefaults = true
    }
    
    fun saveGameResult(context: Context, result: GameResult) {
        try {
            val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            val currentResults = getGameResults(context).toMutableList()
            
            // 새 결과 추가
            currentResults.add(result)
            
            // 최대 50개까지만 저장 (오래된 것부터 삭제)
            if (currentResults.size > 50) {
                currentResults.sortBy { it.timestamp }
                while (currentResults.size > 50) {
                    currentResults.removeAt(0)
                }
            }
            
            // 직렬화 가능한 형태로 변환
            val serializableResults = currentResults.map { gameResult ->
                SerializableGameResult(
                    gameResult.playerName,
                    gameResult.finalAffinity,
                    gameResult.confessionSuccess,
                    gameResult.gameEndType.name,
                    gameResult.timestamp
                )
            }
            
            val jsonString = json.encodeToString(serializableResults)
            prefs.edit().putString(KEY_RESULTS, jsonString).apply()
            
            Log.d(TAG, "Game result saved: ${result.playerName} - Affinity: ${result.finalAffinity} - ${result.gameEndType}")
        } catch (e: Exception) {
            Log.e(TAG, "Error saving game result", e)
        }
    }
    
    fun getGameResults(context: Context): List<GameResult> {
        return try {
            val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            val jsonString = prefs.getString(KEY_RESULTS, null)
            
            if (jsonString.isNullOrEmpty()) {
                emptyList()
            } else {
                val serializableResults = json.decodeFromString<List<SerializableGameResult>>(jsonString)
                serializableResults.map { it.toGameResult() }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error loading game results", e)
            emptyList()
        }
    }
    
    fun getRankedResults(context: Context): List<GameResult> {
        val allResults = getGameResults(context)
        
        // 랭킹 순서: 1. 고백 성공 > 2. 호감도 높은 순 > 3. 최신 순
        return allResults.sortedWith(compareByDescending<GameResult> { 
            when (it.gameEndType) {
                GameResult.GameEndType.CONFESSION_SUCCESS -> 1000 + it.finalAffinity
                GameResult.GameEndType.FRIEND_END -> 500 + it.finalAffinity
                GameResult.GameEndType.CONFESSION_FAILURE -> 100 + it.finalAffinity
                GameResult.GameEndType.SCENARIO_END -> 50 + it.finalAffinity
                GameResult.GameEndType.GAME_OVER -> it.finalAffinity
            }
        }.thenByDescending { it.timestamp })
    }
    
    fun clearAllResults(context: Context) {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        prefs.edit().clear().apply()
        Log.d(TAG, "All game results cleared")
    }
}