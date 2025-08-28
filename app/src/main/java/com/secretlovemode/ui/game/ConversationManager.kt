package com.secretlovemode.ui.game

import android.util.Log
import android.view.View
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import com.secretlovemode.data.model.ChatMessage
import com.secretlovemode.data.model.SectionDialogue
import com.secretlovemode.ui.main.SlmViewModel
import com.secretlovemode.util.LanguageManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class ConversationManager(
    private val activity: GameActivity,
    private val lifecycleOwner: LifecycleOwner,
    private val slmViewModel: SlmViewModel,
    private val uiManager: GameUIManager
) {
    companion object {
        private const val TAG = "ConversationManager"
    }

    // 대화 기록 관리
    private val conversationHistory = mutableListOf<String>()
    private val sectionHistory = mutableMapOf<String, MutableList<String>>()
    private var currentSectionName = "Game Start"

    fun initializeForScenario(scenarioId: String) {
        currentSectionName = when (scenarioId) {
            "1" -> "始まりの章"
            "2A", "2B", "2C" -> "出会いの章"
            "3A", "3B", "3C" -> "親しみの章"
            "4A", "4B", "4C" -> "信頼の章"
            "5A", "5B", "5C" -> "絆の章"
            "6A", "6B" -> "愛の章"
            "7A", "7B" -> "告白の章"
            else -> "セクション$scenarioId"
        }

        // 첫 번째 섹션인 경우 히스토리 초기화
        if (scenarioId == "1") {
            conversationHistory.clear()
            sectionHistory.clear()
        }
    }

    fun addToConversationHistory(message: String) {
        conversationHistory.add(message)
        addToSectionHistory(message)
    }

    fun appendSystemMessage(message: String) {
        lifecycleOwner.lifecycleScope.launch {
            val fullMessage = "[システム] $message"
            
            // 대화 기록에 추가
            addToConversationHistory(fullMessage)
            
            // 현재 메시지 카드에 표시
            uiManager.tvCurrentMessage.text = ""
            
            // 시스템 메시지도 타이핑 효과 적용
            val systemTypingDelay = 40L
            for (i in fullMessage.indices) {
                uiManager.tvCurrentMessage.text = fullMessage.substring(0, i + 1)
                delay(systemTypingDelay)
            }
            
            slmViewModel.updateGameState(
                newAffinity = slmViewModel.gameState.value!!.affinity,
                conversationCount = slmViewModel.gameState.value!!.conversationCount,
                conversationHistory = slmViewModel.gameState.value!!.conversationHistory + ChatMessage(ChatMessage.ROLE_SYSTEM, message)
            )
        }
    }

    fun appendPlayerMessage(message: String) {
        lifecycleOwner.lifecycleScope.launch {
            val fullMessage = "私: $message"
            
            // 대화 기록에 추가
            addToConversationHistory(fullMessage)
            
            // 현재 메시지 카드에 표시
            uiManager.tvCurrentMessage.text = ""
            
            // 플레이어 메시지도 타이핑 효과 적용
            val playerTypingDelay = 30L
            for (i in fullMessage.indices) {
                uiManager.tvCurrentMessage.text = fullMessage.substring(0, i + 1)
                delay(playerTypingDelay)
            }
            
            slmViewModel.updateGameState(
                newAffinity = slmViewModel.gameState.value!!.affinity,
                conversationCount = slmViewModel.gameState.value!!.conversationCount,
                conversationHistory = slmViewModel.gameState.value!!.conversationHistory + ChatMessage(ChatMessage.ROLE_USER, message)
            )
        }
    }

    private fun addToSectionHistory(message: String) {
        if (!sectionHistory.containsKey(currentSectionName)) {
            sectionHistory[currentSectionName] = mutableListOf()
        }
        sectionHistory[currentSectionName]?.add(message)
    }
    
    fun showHistoryOverlay() {
        updateHistoryContent()
        uiManager.historyOverlay.visibility = View.VISIBLE
        uiManager.historyOverlay.alpha = 0f
        uiManager.historyOverlay.animate()
            .alpha(1f)
            .setDuration(300)
            .start()
    }
    
    fun hideHistoryOverlay() {
        uiManager.historyOverlay.animate()
            .alpha(0f)
            .setDuration(200)
            .withEndAction {
                uiManager.historyOverlay.visibility = View.GONE
            }
            .start()
    }
    
    private fun updateHistoryContent() {
        val historyText = StringBuilder()
        
        sectionHistory.entries.forEach { (sectionName, messages) ->
            historyText.append("【$sectionName】\n")
            messages.forEach { message ->
                historyText.append("$message\n\n")
            }
            historyText.append("────────────────\n\n")
        }
        
        if (historyText.isEmpty()) {
            val noHistoryText = if (LanguageManager.getLanguage(activity) == "en") {
                "No conversation history yet."
            } else {
                "まだ対話履歴がありません。"
            }
            uiManager.tvHistoryContent.text = noHistoryText
        } else {
            uiManager.tvHistoryContent.text = historyText.toString().trimEnd()
        }
    }

    fun collectAndStoreSectionDialogues(scenarioId: String, scenarioFile: com.secretlovemode.data.model.ScenarioFile, playerName: String) {
        val dialogues = mutableListOf<SectionDialogue>()
        
        scenarioFile.scenarios.forEach { node ->
            if (node.type == "message" && node.text.isNotBlank()) {
                val processedText = node.text.replace("{{playerName}}", playerName)
                dialogues.add(
                    SectionDialogue(
                        speaker = node.speaker,
                        text = processedText,
                        type = node.type
                    )
                )
            }
        }
        
        Log.d(TAG, "Collected ${dialogues.size} dialogues for section $scenarioId")
        slmViewModel.addSectionDialogue(scenarioId, dialogues)
        
        // 섹션 요약 생성 (첫 번째 섹션은 제외하고 지연 실행으로 AI 충돌 방지)
        if (scenarioId != "1") {
            lifecycleOwner.lifecycleScope.launch {
                delay(3000) // 3초 지연으로 다른 AI 작업과 충돌 방지
                generateSectionSummaryAsync(scenarioId, dialogues)
            }
        }
    }
    
    private fun generateSectionSummaryAsync(sectionId: String, dialogues: List<SectionDialogue>) {
        lifecycleOwner.lifecycleScope.launch {
            try {
                // Activity가 활성 상태인지 확인
                if (activity.isFinishing || activity.isDestroyed) {
                    Log.d(TAG, "Activity is finishing/destroyed, skipping section summary generation")
                    return@launch
                }
                
                val characterAi = slmViewModel.getCharacterAi()
                if (characterAi != null && characterAi.isModelReady) {
                    val gameState = slmViewModel.gameState.value ?: return@launch
                    
                    Log.d(TAG, "Starting section summary generation for section $sectionId")
                    val summary = characterAi.generateSectionSummary(sectionId, dialogues, gameState)
                    
                    if (summary != null && !activity.isFinishing && !activity.isDestroyed) {
                        // 요약을 GameState에 저장
                        val currentState = slmViewModel.gameState.value ?: return@launch
                        val updatedSummaries = currentState.sectionSummaries.toMutableMap()
                        updatedSummaries[sectionId] = summary
                        
                        slmViewModel.updateGameStateWithSummary(updatedSummaries)
                        Log.d(TAG, "Section summary saved for section $sectionId")
                    } else {
                        Log.w(TAG, "Failed to generate summary for section $sectionId or activity is finishing")
                    }
                } else {
                    Log.w(TAG, "CharacterAi not ready for section summary generation")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error generating section summary for $sectionId", e)
            }
        }
    }

    /**
     * 고백 판정 시 생각중 오버레이 표시
     */
    fun showThinkingOverlay(thinkingText: String) {
        Log.d(TAG, "Showing thinking overlay: $thinkingText")
        
        uiManager.tvThinkingOverlay.text = thinkingText
        uiManager.tvThinkingOverlay.visibility = View.VISIBLE
        uiManager.tvThinkingOverlay.alpha = 0f
        
        uiManager.tvThinkingOverlay.animate()
            .alpha(1f)
            .setDuration(500)
            .withEndAction {
                // 생각중 애니메이션 시작
                lifecycleOwner.lifecycleScope.launch {
                    animateThinkingText(thinkingText)
                }
            }
            .start()
    }
    
    /**
     * 생각중 오버레이 숨기기
     */
    fun hideThinkingOverlay() {
        Log.d(TAG, "Hiding thinking overlay")
        
        uiManager.tvThinkingOverlay.animate()
            .alpha(0f)
            .setDuration(300)
            .withEndAction {
                uiManager.tvThinkingOverlay.visibility = View.GONE
            }
            .start()
    }
    
    /**
     * 생각중 텍스트 점점점 애니메이션
     */
    private suspend fun animateThinkingText(baseText: String) {
        val dots = arrayOf(".", "..", "...")
        var dotIndex = 0
        
        while (uiManager.tvThinkingOverlay.visibility == View.VISIBLE) {
            uiManager.tvThinkingOverlay.text = "$baseText${dots[dotIndex]}"
            dotIndex = (dotIndex + 1) % dots.size
            delay(800) // 0.8초마다 점 변경
        }
    }

    // Getters for accessing private data
    fun getConversationHistory(): List<String> = conversationHistory.toList()
    fun getSectionHistory(): Map<String, List<String>> = sectionHistory.mapValues { it.value.toList() }
    fun getCurrentSectionName(): String = currentSectionName
}