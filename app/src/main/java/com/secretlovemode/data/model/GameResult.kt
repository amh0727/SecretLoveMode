package com.secretlovemode.data.model

import java.text.SimpleDateFormat
import java.util.*

data class GameResult(
    val playerName: String,
    val finalAffinity: Int,
    val confessionSuccess: Boolean,
    val gameEndType: GameEndType,
    val timestamp: Long = System.currentTimeMillis()
) {
    enum class GameEndType {
        CONFESSION_SUCCESS,  // 고백 성공
        CONFESSION_FAILURE,  // 고백 실패
        FRIEND_END,         // 친구 엔딩
        GAME_OVER,          // 호감도 0 이하로 게임 오버
        SCENARIO_END        // 시나리오 완료
    }
    
    fun getFormattedDate(): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
        return sdf.format(Date(timestamp))
    }
    
    fun getScoreDescription(): String {
        return when (gameEndType) {
            GameEndType.CONFESSION_SUCCESS -> "告白成功 ♥"
            GameEndType.CONFESSION_FAILURE -> "告白失敗"
            GameEndType.FRIEND_END -> "友達エンド"
            GameEndType.GAME_OVER -> "ゲームオーバー"
            GameEndType.SCENARIO_END -> "シナリオ完了"
        }
    }
    
    fun getScoreDescriptionEn(): String {
        return when (gameEndType) {
            GameEndType.CONFESSION_SUCCESS -> "Confession Success ♥"
            GameEndType.CONFESSION_FAILURE -> "Confession Failed"
            GameEndType.FRIEND_END -> "Friend End"
            GameEndType.GAME_OVER -> "Game Over"
            GameEndType.SCENARIO_END -> "Scenario Complete"
        }
    }
}