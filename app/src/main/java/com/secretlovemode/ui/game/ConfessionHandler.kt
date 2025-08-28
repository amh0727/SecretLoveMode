package com.secretlovemode.ui.game

import android.content.Intent
import androidx.appcompat.app.AlertDialog
import androidx.lifecycle.LifecycleOwner
import com.secretlovemode.ui.common.ParticleView
import com.secretlovemode.ui.main.SlmViewModel
import com.secretlovemode.ui.ranking.RankingActivity
import com.secretlovemode.util.LanguageManager

class ConfessionHandler(
    private val activity: GameActivity,
    private val lifecycleOwner: LifecycleOwner,
    private val slmViewModel: SlmViewModel,
    private val conversationManager: ConversationManager
) {
    companion object {
        private const val TAG = "ConfessionHandler"
    }

    private lateinit var playerName: String

    fun initialize(playerName: String) {
        this.playerName = playerName
    }

    fun onConfessButtonClicked() {
        // 고백 확인 다이얼로그
        val title = if (LanguageManager.getLanguage(activity) == "en") "Confession" else "告白"
        val message = if (LanguageManager.getLanguage(activity) == "en") {
            "Do you want to confess to ${slmViewModel.gameState.value!!.characterName}?\nCurrent affinity: ${slmViewModel.gameState.value!!.affinity}"
        } else {
            "${slmViewModel.gameState.value!!.characterName}に告白しますか？\n現在の好感度: ${slmViewModel.gameState.value!!.affinity}"
        }
        val yesText = if (LanguageManager.getLanguage(activity) == "en") "Yes" else "はい"
        val noText = if (LanguageManager.getLanguage(activity) == "en") "No" else "いいえ"

        AlertDialog.Builder(activity)
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton(yesText) { _, _ ->
                performConfession()
            }
            .setNegativeButton(noText, null)
            .show()
    }
    
    private fun performConfession() {
        val currentAffinity = slmViewModel.gameState.value!!.affinity
        
        // 호감도 구간별 성공 확률 계산
        val successRate = when {
            currentAffinity >= 90 -> 0.95f  // 거의 확실한 성공
            currentAffinity >= 80 -> 0.80f  // 높은 성공률
            currentAffinity >= 70 -> 0.60f  // 보통 성공률  
            currentAffinity >= 60 -> 0.35f  // 낮은 성공률
            currentAffinity >= 50 -> 0.15f  // 매우 낮은 성공률
            else -> 0.05f                   // 거의 실패
        }
        
        val isSuccess = kotlin.random.Random.nextFloat() < successRate
        val resultMessage = getConfessionResultMessage(currentAffinity, isSuccess)
        
        android.util.Log.d(TAG, "Confession attempt: affinity=$currentAffinity, successRate=$successRate, result=$isSuccess")
        
        if (isSuccess) {
            // 성공 시 해피 엔딩
            val particleView = activity.findViewById<ParticleView>(com.secretlovemode.R.id.particleView)
            particleView.startAnimation(ParticleView.ParticleType.HEART)
            
            val successMessage = if (LanguageManager.getLanguage(activity) == "en") {
                "💕 Confession Success!\n\n$resultMessage\n\nHappy Ending Achieved!"
            } else {
                "💕 告白成功！\n\n$resultMessage\n\nハッピーエンド達成！"
            }
            activity.showGameOverDialog(successMessage, "CONFESSION_SUCCESS")
        } else {
            // 실패 시에도 게임 종료가 아닌 피드백
            val particleView = activity.findViewById<ParticleView>(com.secretlovemode.R.id.particleView)
            particleView.startAnimation(ParticleView.ParticleType.SAD)
            showConfessionFailureDialog(resultMessage)
        }
    }
    
    private fun getConfessionResultMessage(affinity: Int, isSuccess: Boolean): String {
        val language = LanguageManager.getLanguage(activity)
        
        return if (isSuccess) {
            if (language == "en") {
                when {
                    affinity >= 90 -> "\"I... I've always liked you too, senpai!\" Megumi's face turns bright red as she smiles happily."
                    affinity >= 80 -> "\"Really? Actually, I also...\" Megumi nods shyly."
                    affinity >= 70 -> "\"I-I can't believe you said that so suddenly... but I'm happy.\" Megumi looks confused but pleased."
                    else -> "\"Thank you... I also enjoy being with you, senpai.\" Megumi smiles quietly."
                }
            } else {
                when {
                    affinity >= 90 -> "「私も...ずっと先輩のことが好きでした！」\n恵の顔が真っ赤になりながらも、嬉しそうに微笑んでいる。"
                    affinity >= 80 -> "「えっ...本当ですか？実は私も...」\n恵が恥ずかしそうに頷いている。"
                    affinity >= 70 -> "「そ、そんなこと急に言われても...でも、嬉しいです」\n恵が困惑しながらも喜んでいる。"
                    else -> "「ありがとうございます...私も、先輩といると楽しいです」\n恵が静かに微笑んでいる。"
                }
            }
        } else {
            if (language == "en") {
                when {
                    affinity >= 70 -> "\"I'm sorry... I can't feel that way right now...\" Megumi looks down apologetically, but doesn't seem to dislike you."
                    affinity >= 50 -> "\"Eh... that's... too sudden.\" Megumi blushes in confusion. You don't need to give up completely."
                    affinity >= 30 -> "\"I'm sorry... I don't really...\" Megumi looks confused. You might need to reconsider your relationship."
                    else -> "\"I'm sorry, but I can't return your feelings.\" Megumi politely declines."
                }
            } else {
                when {
                    affinity >= 70 -> "「ごめんなさい...今はまだ、そういう気持ちになれなくて...」\n恵が申し訳なさそうに俯いている。でも嫌がってはいないようだ。"
                    affinity >= 50 -> "「え...そ、そんな...急すぎます」\n恵が慌てて顔を赤くしている。完全に諦める必要はなさそうだ。"
                    affinity >= 30 -> "「すみません...私、そういうの...」\n恵が困惑している。関係性を見直す必要がありそうだ。"
                    else -> "「...申し訳ありませんが、お気持ちにお応えできません」\n恵が丁寧に断っている。"
                }
            }
        }
    }
    
    private fun showConfessionFailureDialog(message: String) {
        val affinity = slmViewModel.gameState.value!!.affinity
        val canRetry = affinity >= 50 // 호감도 50 이상이면 재도전 기회
        
        val language = LanguageManager.getLanguage(activity)
        val title = if (language == "en") "Confession Result" else "告白の結果"
        val failureMsg = if (language == "en") "💔 Confession Failed...\n\n$message" else "💔 告白失敗...\n\n$message"
        
        val dialogBuilder = AlertDialog.Builder(activity)
            .setTitle(title)
            .setMessage(failureMsg)
            .setCancelable(false)
        
        if (canRetry) {
            val retryText = if (language == "en") "Try Again" else "もう一度頑張る"
            val giveUpText = if (language == "en") "Give Up" else "諦める"
            
            dialogBuilder
                .setPositiveButton(retryText) { _, _ ->
                    // 게임 계속 진행 (호감도 약간 감소)
                    val newAffinity = (affinity - 10).coerceAtLeast(0)
                    slmViewModel.updateGameState(
                        newAffinity = newAffinity,
                        conversationCount = slmViewModel.gameState.value!!.conversationCount,
                        conversationHistory = slmViewModel.gameState.value!!.conversationHistory
                    )
                    val retryMessage = if (language == "en") {
                        "Let's reconsider the relationship and try to get another chance..."
                    } else {
                        "関係を見直して、もう一度チャンスを掴もう..."
                    }
                    conversationManager.appendSystemMessage(retryMessage)
                }
                .setNegativeButton(giveUpText) { _, _ ->
                    val friendEndMessage = if (language == "en") {
                        "Decided to continue a good relationship as friends...\n\nFriend End"
                    } else {
                        "友達として良い関係を続けることにした...\n\nフレンドエンド"
                    }
                    activity.showGameOverDialog(friendEndMessage, "FRIEND_END")
                }
        } else {
            val viewResultsText = if (language == "en") "View Results" else "結果を見る"
            dialogBuilder.setPositiveButton(viewResultsText) { _, _ ->
                navigateToRanking("CONFESSION_FAILURE")
            }
        }
        
        dialogBuilder.show()
    }

    private fun navigateToRanking(gameEndType: String) {
        val intent = Intent(activity, RankingActivity::class.java)
        intent.putExtra(RankingActivity.EXTRA_PLAYER_NAME, playerName)
        intent.putExtra(RankingActivity.EXTRA_FINAL_AFFINITY, slmViewModel.gameState.value?.affinity ?: 0)
        intent.putExtra(RankingActivity.EXTRA_CONFESSION_SUCCESS, gameEndType == "CONFESSION_SUCCESS")
        intent.putExtra(RankingActivity.EXTRA_GAME_END_TYPE, gameEndType)
        activity.startActivity(intent)
        activity.finish()
    }
}