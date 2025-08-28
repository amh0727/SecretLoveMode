package com.secretlovemode.ui.game

import android.util.Log
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import com.secretlovemode.data.model.ScenarioOption
import com.secretlovemode.data.repository.ScenarioManager
import com.secretlovemode.domain.CharacterAi
import com.secretlovemode.ui.common.ParticleView
import com.secretlovemode.ui.main.SlmViewModel
import com.secretlovemode.util.LanguageManager
import kotlinx.coroutines.launch

class GameStateHandler(
    private val activity: GameActivity,
    private val lifecycleOwner: LifecycleOwner,
    private val slmViewModel: SlmViewModel,
    private val animationManager: AnimationManager,
    private val conversationManager: ConversationManager,
    private val scenarioProcessor: ScenarioProcessor
) {
    companion object {
        private const val TAG = "GameStateHandler"
    }

    private var characterAi: CharacterAi? = null

    fun initialize(characterAi: CharacterAi?) {
        this.characterAi = characterAi
    }

    fun onPlayerOptionSelected(selectedOption: ScenarioOption, situationText: String) {
        // 즉시 모든 선택지 비활성화 (중복 클릭 방지)
        Log.d(TAG, "🔒 Immediately disabling all choice buttons after selection")
        activity.runOnUiThread {
            activity.disableAllChoiceButtons()
            activity.hideAllChoiceButtons()
        }
        
        conversationManager.appendPlayerMessage(selectedOption.text)
        Log.d(TAG, "Player selected option: ${selectedOption.text}, next scenario: ${selectedOption.next}")

        lifecycleOwner.lifecycleScope.launch {
            // 고백 결과 선택지인지 확인 (일본어 + 영어)
            val isConfessionResult = (selectedOption.text.contains("結果を見る") || selectedOption.text.contains("View Results")) && 
                (selectedOption.next == "7A" || selectedOption.next == "7B")
            
            Log.d(TAG, "Checking confession result condition:")
            Log.d(TAG, "  - Option text: '${selectedOption.text}'")
            Log.d(TAG, "  - Contains '結果を見る': ${selectedOption.text.contains("結果を見る")}")
            Log.d(TAG, "  - Contains 'View Results': ${selectedOption.text.contains("View Results")}")
            Log.d(TAG, "  - Next scenario: ${selectedOption.next}")
            Log.d(TAG, "  - Is confession result: $isConfessionResult")
            
            if (isConfessionResult) {
                Log.d(TAG, "🎯 CONFESSION RESULT CHOICE DETECTED! Starting confession evaluation...")
                handleConfessionResult(selectedOption.next)
                return@launch
            }
            
            // 일반 선택지도 SLM 추론 중 버튼 비활성화 유지
            Log.d(TAG, "⚡ Starting SLM affection judgment for regular choice")
            
            // AI 추론 지연을 숨기기 위한 방법들 시작
            animationManager.startDelayDistractionEffects()
            
            val affectionChange = characterAi?.judgeAffection(
                gameState = slmViewModel.gameState.value!!,
                situationText = situationText,
                playerSelectedOption = selectedOption.text,
                baseAffectionChange = selectedOption.affectionChange ?: 0,
                conversationHistory = slmViewModel.gameState.value!!.conversationHistory
            ) ?: (selectedOption.affectionChange ?: 0)

            Log.d(TAG, "Affection change from AI: $affectionChange")

            // 지연 효과 종료
            animationManager.stopDelayDistractionEffects()

            processGameStateUpdate(slmViewModel.gameState.value!!.affinity + affectionChange)

            if (selectedOption.next.isNotBlank()) {
                scenarioProcessor.loadScenario(selectedOption.next)
            } else {
                Log.e(TAG, "Selected option has no next scenario ID. Ending game.")
                val gameEndMessage = if (LanguageManager.getLanguage(activity) == "en") {
                    "Game ended."
                } else {
                    "ゲームが終了しました。"
                }
                activity.showGameOverDialog(gameEndMessage, "SCENARIO_END")
            }
        }
    }

    private fun processGameStateUpdate(newAffinity: Int) {
        val currentGameState = slmViewModel.gameState.value!!
        val previousAffinity = currentGameState.affinity

        slmViewModel.updateGameState(
            newAffinity = newAffinity,
            conversationCount = currentGameState.conversationCount + 1,
            conversationHistory = currentGameState.conversationHistory
        )

        val affinityChange = slmViewModel.gameState.value!!.affinity - previousAffinity
        if (affinityChange != 0) {
            animationManager.startAffinityChangeAnimation(affinityChange > 0, activity.findViewById(com.secretlovemode.R.id.particleView))
        }
        // updateStatusDisplay() is now called via LiveData observation

        if (slmViewModel.gameState.value!!.affinity <= 0) {
            val gameOverMessage = if (LanguageManager.getLanguage(activity) == "en") {
                "Your relationship with ${slmViewModel.gameState.value!!.characterName} has ended..."
            } else {
                "${slmViewModel.gameState.value!!.characterName}との関係は終わってしまいました..."
            }
            activity.showGameOverDialog(gameOverMessage, "GAME_OVER")
        }

        val nextTriggeredScenarioId = ScenarioManager.checkAndTriggerNextScenario(slmViewModel.gameState.value!!)
        if (nextTriggeredScenarioId != slmViewModel.gameState.value!!.currentScenarioId) {
            Log.d(TAG, "Dynamic scenario triggered: $nextTriggeredScenarioId")
            slmViewModel.updateCurrentScenarioId(nextTriggeredScenarioId)
            scenarioProcessor.loadScenario(nextTriggeredScenarioId)
        }
    }

    private fun handleConfessionResult(defaultNextScenario: String) {
        Log.d(TAG, "handleConfessionResult() called with default: $defaultNextScenario")
        
        val confessionMessage = slmViewModel.gameState.value?.confessionInput
        if (confessionMessage.isNullOrEmpty()) {
            Log.e(TAG, "No confession message found, proceeding with default scenario")
            scenarioProcessor.loadScenario(defaultNextScenario)
            return
        }
        
        Log.d(TAG, "🚀 STARTING SLM CONFESSION EVALUATION")
        Log.d(TAG, "  - Confession message: '$confessionMessage'")
        Log.d(TAG, "  - Current affinity: ${slmViewModel.gameState.value?.affinity}")
        Log.d(TAG, "  - Character AI ready: ${characterAi?.isModelReady}")
        
        // 즉시 선택지 비활성화 및 숨기기
        Log.d(TAG, "🔒 Disabling all choice buttons for confession evaluation")
        activity.runOnUiThread {
            activity.disableAllChoiceButtons()
            activity.hideAllChoiceButtons()
        }
        
        lifecycleOwner.lifecycleScope.launch {
            try {
                // 극적인 연출 시작
                Log.d(TAG, " Starting dramatic confession evaluation sequence")
                startDramaticConfessionSequence()
                
                // 로딩 표시
                activity.showLoadingProgressBar(true)
                
                // AI 판정으로 고백 메시지 품질 반영
                val currentAffinity = slmViewModel.gameState.value!!.affinity
                val confessionSuccess = if (currentAffinity >= 95) {
                    Log.d(TAG, "✨ Extremely high affinity ($currentAffinity), forcing confession success")
                    // 높은 호감도로 인한 자동 성공 이유 저장
                    slmViewModel.setConfessionJudgmentReason("非常に高い好感度により成功")
                    // 짧은 지연으로 사용자가 연출을 볼 수 있게 함
                    kotlinx.coroutines.delay(3000)
                    true
                } else {
                    Log.d(TAG, "💖 Starting AI confession judgment (affinity: $currentAffinity)")
                    
                    // 타임아웃 설정으로 SLM 추정 제한 (30초)
                    val result = kotlinx.coroutines.withTimeoutOrNull(30000L) {
                        characterAi?.judgeConfession(
                            gameState = slmViewModel.gameState.value!!,
                            confessionMessage = confessionMessage,
                            conversationHistory = slmViewModel.gameState.value?.conversationHistory ?: emptyList()
                        ) ?: Pair(false, null)
                    }
                    
                    if (result == null) {
                        Log.w(TAG, "⏰ SLM confession judgment timed out (30s), defaulting to success")
                        // 판정 이유를 GameState에 저장
                        slmViewModel.setConfessionJudgmentReason("推論タイムアウト、成功として処理")
                        true // 타임아웃 시 성공으로 처리
                    } else {
                        Log.d(TAG, "🎯 AI confession judgment completed: ${result.first}")
                        
                        // 판정 이유를 GameState에 저장
                        result.second?.let { reason ->
                            Log.i(TAG, "💭 Saving judgment reason: $reason")
                            slmViewModel.setConfessionJudgmentReason(reason)
                        }
                        
                        result.first
                    }
                }
                
                // 극적 연출 종료
                stopDramaticConfessionSequence()
                activity.showLoadingProgressBar(false)
                
                // 결과에 따라 시나리오 분기 - 성공시 무조건 7A
                val nextScenario = if (confessionSuccess) {
                    Log.d(TAG, "Confession successful, loading success scenario 7A")
                    "7A" // 성공 시나리오
                } else {
                    Log.d(TAG, "Confession failed, loading failure scenario 7B")  
                    "7B" // 실패 시나리오
                }
                
                Log.d(TAG, "Loading scenario: $nextScenario")
                scenarioProcessor.loadScenario(nextScenario)
                
            } catch (e: Exception) {
                Log.e(TAG, "Error during confession evaluation", e)
                stopDramaticConfessionSequence()
                activity.showLoadingProgressBar(false)
                // 에러 발생 시 기본 시나리오로 진행
                scenarioProcessor.loadScenario(defaultNextScenario)
            }
        }
    }

    private fun startDramaticConfessionSequence() {
        Log.d(TAG, "Starting dramatic confession sequence effects")
        
        // 캐릭터 이미지 heartbeat 애니메이션 시작
        animationManager.startCharacterHeartbeatAnimation()
        
        // 생각중 텍스트 표시
        val thinkingText = if (LanguageManager.getLanguage(activity) == "en") {
            "Megumi is thinking..."
        } else {
            "めぐみちゃんが考え中..."
        }
        
        activity.runOnUiThread {
            // 기존 메시지 위에 생각중 텍스트 오버레이
            conversationManager.showThinkingOverlay(thinkingText)
        }
        
        // 두근거림 효과 시작 (파티클 효과)
        val particleView = activity.findViewById<ParticleView>(com.secretlovemode.R.id.particleView)
        animationManager.startHeartbeatParticleEffect(particleView)
        
        Log.d(TAG, "All dramatic effects started successfully")
    }
    
    private fun stopDramaticConfessionSequence() {
        Log.d(TAG, "Stopping dramatic confession sequence effects")
        
        // 모든 극적 효과 종료
        animationManager.stopCharacterHeartbeatAnimation()
        conversationManager.hideThinkingOverlay()
        
        val particleView = activity.findViewById<ParticleView>(com.secretlovemode.R.id.particleView)
        animationManager.stopHeartbeatParticleEffect(particleView)
        
        Log.d(TAG, "All dramatic effects stopped")
    }

    fun observeGameState() {
        slmViewModel.gameState.observe(activity) { gameState ->
            gameState?.let {
                animationManager.updateStatusDisplay(it.affinity)
            }
        }
    }
}