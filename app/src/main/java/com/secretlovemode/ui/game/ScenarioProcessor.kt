package com.secretlovemode.ui.game

import android.content.Intent
import android.util.Log
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import com.secretlovemode.data.model.ScenarioFile
import com.secretlovemode.data.model.ScenarioNode
import com.secretlovemode.data.model.ScenarioOption
import com.secretlovemode.data.repository.ScenarioManager
import com.secretlovemode.ui.main.SlmViewModel
import com.secretlovemode.util.LanguageManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class ScenarioProcessor(
    private val activity: GameActivity,
    private val lifecycleOwner: LifecycleOwner,
    private val slmViewModel: SlmViewModel,
    private val uiManager: GameUIManager
) {
    companion object {
        private const val TAG = "ScenarioProcessor"
        private const val REQUEST_CODE_FINAL_MESSAGE = 1001
    }

    private var currentScenarioFile: ScenarioFile? = null
    private var currentScenarioNodeIndex: Int = 0
    private var currentScenarioId: String? = null
    private var isWaitingForTextInput: Boolean = false
    private var isTyping = false
    private var canProceed = false
    private lateinit var playerName: String

    fun initialize(playerName: String) {
        this.playerName = playerName
    }

    fun loadScenario(scenarioId: String) {
        this.currentScenarioId = scenarioId
        Log.d(TAG, "Attempting to load scenario: session$scenarioId.json")
        currentScenarioFile = ScenarioManager.getScenario(activity, scenarioId)
        if (currentScenarioFile == null) {
            Log.e(TAG, "シナリオの読み込みに失敗しました: session$scenarioId.json")
            val errorMessage = if (LanguageManager.getLanguage(activity) == "en") {
                "Cannot load scenario file."
            } else {
                "シナリオファイルを読み込めません。"
            }
            activity.showGameOverDialog(errorMessage)
            return
        }
        Log.d(TAG, "Scenario loaded successfully: ${currentScenarioFile!!.title}")
        
        // 섹션 대화 수집 및 저장 (요약 생성을 위해)
        activity.collectAndStoreSectionDialogues(scenarioId, currentScenarioFile!!, playerName)
        
        // 섹션 전환 오버레이 표시 (첫 번째 섹션 제외)
        if (scenarioId != "1") {
            uiManager.showSectionTransitionOverlay(scenarioId, currentScenarioFile!!.title)
        }
        
        currentScenarioNodeIndex = 0
        // 첫 번째 섹션이 아닌 경우에만 기존 대화를 유지하고, 첫 번째 섹션인 경우 초기화
        if (scenarioId == "1") {
            uiManager.tvCurrentMessage.text = ""
        }
        processCurrentScenarioNode()
    }

    fun processCurrentScenarioNode() {
        val nodes = currentScenarioFile?.scenarios ?: return
        Log.d(TAG, "Processing scenario node: index=$currentScenarioNodeIndex, total nodes=${nodes.size}")
        
        if (currentScenarioNodeIndex >= nodes.size) {
            Log.d(TAG, "シナリオ '${currentScenarioFile?.title}' 終了")
            val nextTriggeredScenarioId = ScenarioManager.checkAndTriggerNextScenario(slmViewModel.gameState.value!!)
            if (nextTriggeredScenarioId != slmViewModel.gameState.value!!.currentScenarioId) {
                Log.d(TAG, "Dynamic scenario triggered: $nextTriggeredScenarioId")
                slmViewModel.updateCurrentScenarioId(nextTriggeredScenarioId)
                loadScenario(nextTriggeredScenarioId)
            } else {
                uiManager.disableAllOptions()
                uiManager.hideAllChoiceButtons()
                val endMessage = if (LanguageManager.getLanguage(activity) == "en") {
                    "Scenario ended."
                } else {
                    "シナリオが終了しました。"
                }
                activity.appendSystemMessage(endMessage)
            }
            return
        }

        val node = nodes[currentScenarioNodeIndex]
        Log.d(TAG, "Processing node: type=${node.type}, speaker=${node.speaker}, text=${node.text.take(50)}...")

        uiManager.tvCharacterName.text = currentScenarioFile?.title

        when (node.type) {
            "message" -> {
                Log.d(TAG, "Displaying message node")
                displayMessage(node)
            }
            "choice" -> {
                Log.d(TAG, "Displaying choice node")
                displayChoice(node)
            }
            "text_input" -> {
                Log.d(TAG, "Displaying text input node")
                displayTextInput(node)
            }
            "input" -> { // Handle "input" type
                Log.d(TAG, "Displaying input node")
                // 노드 인덱스를 여기서 증가 (input 노드 처리 후)
                currentScenarioNodeIndex++
                
                node.keyInput?.let { key ->
                    val intent = Intent(activity, FinalMessageActivity::class.java)
                    intent.putExtra("input_key", key)
                    intent.putExtra("currentAffinity", slmViewModel.gameState.value!!.affinity)
                    activity.startActivityForResult(intent, REQUEST_CODE_FINAL_MESSAGE)
                } ?: run {
                    Log.e(TAG, "Input node without keyInput specified.")
                    val errorMessage = if (LanguageManager.getLanguage(activity) == "en") {
                        "Scenario error: Input key not specified."
                    } else {
                        "シナリオエラー: 入力キーが指定されていません。"
                    }
                    activity.showGameOverDialog(errorMessage)
                }
            }
            else -> {
                Log.w(TAG, "Unknown node type: ${node.type}")
            }
        }
    }

    private fun displayMessage(node: ScenarioNode) {
        Log.d(TAG, "displayMessage called for speaker: ${node.speaker}")
        lifecycleOwner.lifecycleScope.launch {
            isTyping = true
            canProceed = false
            uiManager.disableAllOptions()
            uiManager.hideAllChoiceButtons()

            val speakerPrefix = when (node.speaker) {
                "system" -> "[システム] "
                "主人公(心の声)" -> "(心の声...)"
                "主人公(会話)" -> "私: "
                else -> "${node.speaker}: "
            }

            val processedText = processScenarioText(node.text)
            if (node.text.contains("{{playerName}}")) {
                Log.d(TAG, "Replacing playerName in text: '${node.text}' -> '$processedText'")
                Log.d(TAG, "Current playerName value: '$playerName'")
            }
            
            // GAME CLEAR/GAME OVER 메시지 감지
            val isGameClearMessage = processedText.contains("GAME CLEAR")
            val isGameOverMessage = processedText.contains("GAME OVER")

            // 캐릭터 이미지 변경 먼저 실행
            activity.updateCharacterImage(node.images)
            
            val fullMessage = "$speakerPrefix$processedText"
            
            // 대화 기록에 추가
            activity.addToConversationHistory(fullMessage)
            
            // 타이핑 애니메이션으로 텍스트 출력
            uiManager.displayTypingMessage(fullMessage)
            
            isTyping = false
            
            // 노드 인덱스를 여기서 증가
            currentScenarioNodeIndex++
            
            delay(300)
            
            // GAME CLEAR/GAME OVER 메시지인 경우 게임 종료 처리
            if (isGameClearMessage) {
                Log.d(TAG, "GAME CLEAR detected, showing game over dialog")
                val gameOverMessage = if (LanguageManager.getLanguage(activity) == "en") {
                    "🎉 Congratulations! You've successfully completed the story!\n\nYour romance with Megumi has reached its perfect ending!"
                } else {
                    "🎉 おめでとうございます！物語をクリアしました！\n\nめぐみちゃんとの恋愛が完璧な終末を迎えました！"
                }
                activity.showGameOverDialog(gameOverMessage, "CONFESSION_SUCCESS")
            } else if (isGameOverMessage) {
                Log.d(TAG, "GAME OVER detected, showing game over dialog")
                val gameOverMessage = if (LanguageManager.getLanguage(activity) == "en") {
                    "The story has ended.\n\nYour first love came to a close, but you've learned valuable lessons about life and relationships."
                } else {
                    "物語が終了しました。\n\n初恋は終わりましたが、人生と恋愛について貴重な教訓を得ました。"
                }
                activity.showGameOverDialog(gameOverMessage, "CONFESSION_FAILURE")
            } else {
                canProceed = true
            }
            
            Log.d(TAG, "Message display completed, canProceed=$canProceed")
        }
    }

    private fun displayChoice(node: ScenarioNode) {
        val options = node.options ?: return

        // 캐릭터 이미지 업데이트
        activity.updateCharacterImage(node.images)
        
        uiManager.setupChoiceButtons(options, node.text) { option, situationText ->
            activity.onPlayerOptionSelected(option, situationText)
        }
        
        canProceed = false
        
        // 노드 인덱스를 여기서 증가
        currentScenarioNodeIndex++
    }

    private fun displayTextInput(node: ScenarioNode) {
        // 먼저 이미지 업데이트
        activity.updateCharacterImage(node.images)
        
        lifecycleOwner.lifecycleScope.launch {
            isTyping = false
            canProceed = false
            uiManager.disableAllOptions()
            uiManager.hideAllChoiceButtons()

            val speakerPrefix = when (node.speaker) {
                "system" -> "[システム] "
                "主人公(心の声)" -> "(心の声...)"
                "主人公(会話)" -> "私: "
                else -> "${node.speaker}: "
            }

            val processedText = processScenarioText(node.text)
            val fullMessage = "$speakerPrefix$processedText"
            
            // 대화 기록에 추가
            activity.addToConversationHistory(fullMessage)
            
            // 타이핑 애니메이션으로 텍스트 출력
            uiManager.displayTypingMessage(fullMessage)
            
            delay(300)
            
            // 노드 인덱스를 여기서 증가
            currentScenarioNodeIndex++
            
            // Launch FinalMessageActivity for text input
            node.keyInput?.let { key ->
                isWaitingForTextInput = true
                val intent = Intent(activity, FinalMessageActivity::class.java)
                intent.putExtra("input_key", key)
                intent.putExtra("placeholder", node.placeholder ?: "Enter your answer here")
                intent.putExtra("message_text", node.text)
                activity.startActivityForResult(intent, REQUEST_CODE_FINAL_MESSAGE)
            } ?: run {
                Log.e(TAG, "Text input node without keyInput specified.")
                val errorMessage = if (LanguageManager.getLanguage(activity) == "en") {
                    "Scenario error: Input key not specified."
                } else {
                    "シナリオエラー: 入力キーが指定されていません。"
                }
                activity.showGameOverDialog(errorMessage)
            }
            
            canProceed = false
        }
    }

    fun handleActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (requestCode == REQUEST_CODE_FINAL_MESSAGE && resultCode == android.app.Activity.RESULT_OK) {
            Log.d(TAG, "FinalMessageActivity completed successfully")
            isWaitingForTextInput = false
            
            val inputKey = data?.getStringExtra("input_key")
            Log.d(TAG, "Input completed: $inputKey")
            
            processCurrentScenarioNode()
        } else if (requestCode == REQUEST_CODE_FINAL_MESSAGE) {
            Log.w(TAG, "FinalMessageActivity was cancelled or failed")
            isWaitingForTextInput = false
        }
    }

    fun handleResume() {
        // Check if we were waiting for text input and should proceed to next node
        if (isWaitingForTextInput) {
            isWaitingForTextInput = false
            // Continue to next scenario node
            currentScenarioNodeIndex++
            if (currentScenarioNodeIndex < (currentScenarioFile?.scenarios?.size ?: 0)) {
                lifecycleOwner.lifecycleScope.launch {
                    delay(500) // Brief delay for better UX
                    processCurrentScenarioNode()
                }
            } else {
                // End of scenario
                val scenarioEndMessage = if (LanguageManager.getLanguage(activity) == "en") {
                    "Scenario completed."
                } else {
                    "シナリオが終了しました。"
                }
                activity.showGameOverDialog(scenarioEndMessage, "SCENARIO_END")
            }
        }
    }

    fun handleMainLayoutClick() {
        if (uiManager.sectionTransitionOverlay.visibility == android.view.View.VISIBLE) {
            uiManager.hideSectionTransitionOverlay()
        } else if (isTyping) {
            isTyping = false
        } else if (canProceed) {
            processCurrentScenarioNode()
        }
    }

    // Getters for accessing private variables
    fun getCurrentScenarioFile(): ScenarioFile? = currentScenarioFile
    fun getCurrentScenarioNodeIndex(): Int = currentScenarioNodeIndex
    fun getCurrentScenarioId(): String? = currentScenarioId
    fun isWaitingForInput(): Boolean = isWaitingForTextInput
    
    private fun processScenarioText(text: String): String {
        var processedText = text.replace("{{playerName}}", playerName)
        
        // 모든 키 입력값을 치환
        val keyInputValues = slmViewModel.gameState.value?.keyInputValues ?: emptyMap()
        keyInputValues.forEach { (key, value) ->
            processedText = processedText.replace("{{$key}}", value)
        }
        
        // 고백 판정 이유 치환
        val judgmentReason = slmViewModel.gameState.value?.confessionJudgmentReason
        if (judgmentReason != null) {
            processedText = processedText.replace("{{judgment_reason}}", judgmentReason)
        } else {
            // 판정 이유가 없으면 빈 문자열로 대체
            processedText = processedText.replace("{{judgment_reason}}", "")
        }
        
        return processedText
    }
}