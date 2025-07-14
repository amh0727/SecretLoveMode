package com.secretlovemode

import android.content.Context
import android.util.Log
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import kotlin.random.Random

/**
 * 한 턴의 AI 처리 결과를 모두 담는 데이터 클래스.
 */
data class TurnResult(
    val fullCharacterResponse: String,
    val updatedAffinity: Int,
    val nextPlayerOptions: List<String>
)

/**
 * [신규] 게임 시작 시 필요한 데이터만 담는 클래스
 */
data class InitialTurnResult(
    val fullInitialResponse: String,
    val firstPlayerOptions: List<String>
)

class CharacterAi(
    private val context: Context,
    private val modelAssetPath: String
) {

    companion object {
        private const val TAG = "CharacterAi"
        private const val DEFAULT_MAX_TOKENS_RESPONSE = 1024
        private const val DEFAULT_TOP_K = 20
    }

    private var llmInference: LlmInference? = null
    var isModelReady: Boolean = false
        private set

    private val mutex = Mutex()

    init {
        initializeLlm()
    }

    private fun initializeLlm() {
        Log.i(TAG, "LLM 초기화 시작: $modelAssetPath")
        val modelFile = File(modelAssetPath)
        if (!modelFile.exists() || modelFile.length() == 0L) {
            Log.e(TAG, "모델 파일이 유효하지 않습니다: $modelAssetPath")
            isModelReady = false
            return
        }

        try {
            val options = LlmInference.LlmInferenceOptions.builder()
                .setModelPath(modelAssetPath)
                .setMaxTokens(DEFAULT_MAX_TOKENS_RESPONSE)
                // [중요 오타 수정] setMaxTopK가 아니라 setTopK가 올바른 메서드명입니다.
                .setMaxTopK(DEFAULT_TOP_K)
                .build()

            llmInference = LlmInference.createFromOptions(context, options)
            isModelReady = true
            Log.i(TAG, "LLM 초기화 성공")
        } catch (e: Exception) {
            isModelReady = false
            llmInference = null
            Log.e(TAG, "LLM 초기화 실패", e)
        }
    }

    /**
     * [핵심 수정] 게임 시작 시 필요한 모든 것을 한 번에 요청하는 함수
     */
    suspend fun processInitialTurn(
        gameState: GameState,
        scenario: Scenario
    ): InitialTurnResult {
        return mutex.withLock {
            val promptTemplate = PromptManager.getInitialTurnPrompt()
                ?: return@withLock InitialTurnResult("プロンプト更新失敗", emptyList())

            val prompt = promptTemplate
                .replace("{characterName}", gameState.characterName)
                .replace("{characterPersona}", gameState.characterPersona)
                .replace("{setting}", scenario.setting)
                .replace("{characterGoal}", scenario.characterGoal)

            val unifiedResponse = generateFullResponseInternal(prompt)
            parseInitialTurnResponse(unifiedResponse)
        }
    }

    /**
     * [핵심 수정] 플레이어의 턴을 한 번의 AI 호출로 처리하는 통합 함수
     */
    suspend fun processPlayerTurn(
        gameState: GameState,
        playerSelectedOption: String,
        conversationHistory: List<ChatMessage>,
        scenario: Scenario
    ): TurnResult {
        return mutex.withLock {
            val promptTemplate = PromptManager.getUnifiedTurnPrompt()
                ?: return@withLock TurnResult("프롬프트 로딩 실패", gameState.affinity, emptyList())

            val historyText = conversationHistory.takeLast(4).joinToString("\n") { message ->
                val role = if (message.role == ChatMessage.ROLE_USER) "プレイヤー(先輩)" else "${gameState.characterName}(後輩)"
                "$role: ${message.content}"
            }

            val prompt = promptTemplate
                .replace("{characterName}", gameState.characterName)
                .replace("{characterPersona}", gameState.characterPersona)
                .replace("{setting}", scenario.setting)
                .replace("{characterGoal}", scenario.characterGoal)
                .replace("{conversationHistory}", historyText)
                .replace("{playerSelectedOption}", playerSelectedOption)

            val unifiedResponse = generateFullResponseInternal(prompt)
            parseUnifiedTurnResponse(unifiedResponse, gameState.affinity)
        }
    }

    // 스트리밍 없이 전체 응답만 받는 내부 헬퍼 함수
    private suspend fun generateFullResponseInternal(prompt: String): String {
        val fullResponse = StringBuilder()
        generateResponseInternal(prompt) { partialResult ->
            fullResponse.append(partialResult)
        }
        return fullResponse.toString()
    }

    // 모든 AI 호출의 최종 도착지.
    private suspend fun generateResponseInternal(prompt: String, onPartialResult: (String) -> Unit) {
        if (!isModelReady || llmInference == null) {
            Log.w(TAG, "모델이 준비되지 않아 AI 호출을 건너뜁니다.")
            return
        }

        val deferred = CompletableDeferred<Unit>()
        try {
            llmInference?.generateResponseAsync(prompt) { partialResult, done ->
                partialResult?.let(onPartialResult)
                if (done && !deferred.isCompleted) {
                    deferred.complete(Unit)
                }
            }
            deferred.await() // AI 작업이 끝날 때까지 여기서 대기
        } catch (ce: CancellationException) {
            // 호출이 중단된 경우 세션을 초기화하여 이후 호출을 방지합니다.
            llmInference?.resetImplicitSession()
            Log.w(TAG, "AI 호출이 취소되었습니다.", ce)
            if (!deferred.isCompleted) deferred.completeExceptionally(ce)
            throw ce
        } catch (e: Exception) {
            Log.e(TAG, "generateResponseInternal 실행 중 오류 발생", e)
            llmInference?.resetImplicitSession()
            if (!deferred.isCompleted) deferred.completeExceptionally(e)
        }
    }


    private fun parseInitialTurnResponse(response: String): InitialTurnResult {
        val dialogue = response.substringAfter("<RESPONSE>", "").substringBefore("</RESPONSE>").trim()
        val optionsBlock = response.substringAfter("<OPTIONS>", "").substringBefore("</OPTIONS>").trim()
        val options = parseOptions(optionsBlock)

        return InitialTurnResult(
            fullInitialResponse = if (dialogue.isNotEmpty()) dialogue else "...",
            firstPlayerOptions = options
        )
    }

    private fun parseUnifiedTurnResponse(response: String, currentAffinity: Int): TurnResult {
        val dialogue = response.substringAfter("<RESPONSE>", "").substringBefore("</RESPONSE>").trim()
        val affinityStr = response.substringAfter("<AFFINITY>", "").substringBefore("</AFFINITY>").trim()
        val optionsBlock = response.substringAfter("<OPTIONS>", "").substringBefore("</OPTIONS>").trim()

        val affinityChange = affinityStr.replace("+", "").toIntOrNull() ?: 0
        val newAffinity = (currentAffinity + affinityChange).coerceIn(0, 100)
        val options = parseOptions(optionsBlock)

        return TurnResult(
            fullCharacterResponse = if (dialogue.isNotEmpty()) dialogue else "...",
            updatedAffinity = newAffinity,
            nextPlayerOptions = options
        )
    }

    private fun parseOptions(response: String): List<String> {
        val defaultOptions = listOf("そうだな", "うーん…", "どうだろうな")
        if (response.isBlank()) return defaultOptions

        val extractedOptions = response.lines()
            .mapNotNull { line ->
                // "1." 또는 "선택지 1:" 형식 모두 지원
                Regex("""^\s*\d+\s*[.:]\s*(.+)""").find(line.trim())
                    ?.groupValues?.getOrNull(1)?.trim()?.takeIf { it.isNotEmpty() }
            }
            .filter { it.isNotEmpty() }

        return if (extractedOptions.size >= 3) {
            extractedOptions.take(3)
        } else {
            (extractedOptions + defaultOptions).distinct().take(3)
        }
    }

    fun close() {
        try {
            llmInference?.close()
        } catch (e: Exception) {
            Log.e(TAG, "LLM 리소스 해제 중 오류 발생", e)
        } finally {
            llmInference = null
            isModelReady = false
            Log.i(TAG, "LLM 리소스가 해제되었습니다.")
        }
    }
}
