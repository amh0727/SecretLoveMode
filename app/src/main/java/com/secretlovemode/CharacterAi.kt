package com.secretlovemode

import android.content.Context
import android.util.Log
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File

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
                // [중요 수정] setMaxTopK -> setTopK 오타를 수정했습니다.
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
     * AI 응답 스트리밍을 콜백(lambda) 방식으로 처리합니다.
     */
    suspend fun generateCharacterResponseStream(
        gameState: GameState,
        playerSelectedOption: String,
        conversationHistory: List<ChatMessage>,
        scenario: Scenario,
        onPartialResult: (String) -> Unit // 스트리밍 결과를 전달할 콜백
    ) {
        val prompt = buildCharacterResponsePrompt(gameState, playerSelectedOption, conversationHistory, scenario)
        // 새로운 내부 함수를 호출하여 안전하게 스트리밍을 처리합니다.
        generateResponseInternal(prompt, onPartialResult)
    }

    /**
     * 호감도 계산 로직도 새로운 내부 함수를 사용합니다.
     */
    suspend fun calculateAffinity(
        gameState: GameState,
        playerSelectedOption: String,
        fullCharacterResponse: String,
        conversationHistory: List<ChatMessage>,
        scenario: Scenario
    ): Int {
        val prompt = buildParameterUpdatePrompt(gameState, playerSelectedOption, fullCharacterResponse, conversationHistory, scenario)
        val response = generateFullResponseInternal(prompt)
        return parseParameterUpdate(response, gameState.affinity)
    }

    /**
     * 플레이어 선택지 생성 로직도 새로운 내부 함수를 사용합니다.
     */
    suspend fun generatePlayerOptions(
        gameState: GameState,
        characterLastResponse: String,
        conversationHistory: List<ChatMessage>,
        scenario: Scenario
    ): List<String> {
        val prompt = buildPlayerOptionsPrompt(gameState, characterLastResponse, conversationHistory, scenario)
        val response = generateFullResponseInternal(prompt)
        return parseOptions(response)
    }

    /**
     * 스트리밍 없이 전체 응답만 받는 내부 함수
     */
    private suspend fun generateFullResponseInternal(prompt: String): String {
        val fullResponse = StringBuilder()
        generateResponseInternal(prompt) { partialResult ->
            fullResponse.append(partialResult)
        }
        return fullResponse.toString()
    }

    /**
     *Mutex와 CompletableDeferred를 사용하여 AI 호출을 완벽하게 동기화하는 단일 진입점.
     */
    private suspend fun generateResponseInternal(
        prompt: String,
        onPartialResult: (String) -> Unit
    ) {
        mutex.withLock {
            if (!isModelReady || llmInference == null) {
                Log.w(TAG, "모델이 준비되지 않아 AI 호출을 건너뜁니다.")
                return@withLock
            }

            val deferred = CompletableDeferred<Unit>()
            try {
                llmInference?.generateResponseAsync(prompt) { partialResult, done ->
                    partialResult?.let(onPartialResult)
                    if (done) {
                        // 작업이 완료되면 대기를 해제합니다.
                        if (!deferred.isCompleted) deferred.complete(Unit)
                    }
                }
                // AI 작업이 'done' 신호를 보내 완료될 때까지 여기서 대기합니다.
                // 이 시간 동안 Mutex 잠금은 절대 해제되지 않습니다.
                deferred.await()
            } catch (e: Exception) {
                Log.e(TAG, "generateResponseInternal 실행 중 오류 발생", e)
                if (!deferred.isCompleted) {
                    deferred.completeExceptionally(e)
                }
            }
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

    private fun buildCharacterResponsePrompt(gameState: GameState, playerSelectedOption: String, history: List<ChatMessage>, scenario: Scenario): String {
        val conversationContext = if (history.isNotEmpty()) {
            "最近の会話:\n" + history.takeLast(4).joinToString("\n") { message ->
                val role = if (message.role == ChatMessage.ROLE_USER) "プレイヤー" else gameState.characterName
                "$role: ${message.content}"
            } + "\n"
        } else ""

        return """
        <|system|>
        あなたは「${gameState.characterName}」という名前のキャラクターです。
        性格: ${gameState.characterPersona}

        ## 現在の状況:
        ${scenario.setting}

        ## あなたの現在の目標:
        ${scenario.characterGoal}

        ## ${gameState.characterName}の話し方のルール (重要):
        - あなたはプレイヤーの指示に厳密に従い、ロールプレイを維持してください。
        - プレイヤーはあなたの先輩なので、あなたは基本的に丁寧語（敬語）で応答します。
        - 冷静で論理的な性格ですが、状況に応じて感情が変化します。
        - 応答は常に50文字以内で、簡潔にしてください。
        - 会話中に「点数」「ポイント」「好感度変化」などの具体的な数値や、それを示唆する表現は絶対に使用しないでください。

        $conversationContext
        プレイヤー(先輩): 「$playerSelectedOption」 (プレイヤーは後輩であるあなたに対して、常にくだけた口調（タメ口）で話します)

        上記の指示に厳密に従い、${gameState.characterName}として自然に応答してください:
        <|assistant|>
        """.trimIndent()
    }

    private fun buildParameterUpdatePrompt(gameState: GameState, playerSelectedOption: String, characterResponse: String, history: List<ChatMessage>, scenario: Scenario): String {
        return """
        <|system|>
        あなたは、プレイヤーとキャラクターの会話を分析し、キャラクターの「好感度」の変化を判定するAIです。

        ## 現在のシナリオ状況:
        ${scenario.setting}

        ## キャラクターの目標:
        ${scenario.characterGoal}

        ## 現在の${gameState.characterName}のプレイヤー(先輩)への好感度: ${gameState.affinity}/100

        ## 直近のやり取り:
        プレイヤー(先輩):「$playerSelectedOption」
        ${gameState.characterName}(後輩):「$characterResponse」

        ## 好感度変化の採点基準:
        - キャラクターの目標（${scenario.characterGoal}）に沿った、またはそれを助けるようなプレイヤーの発言は、好感度を上げます。
        - 目標に反する、またはキャラクターを不快にさせる発言は、好感度を下げます。
        - 非常にポジティブなやり取り: +10 ～ +15
        - ややポジティブなやり取り: +5 ～ +9
        - 中立または無関係: 0
        - ややネガティブなやり取り: -5 ～ -9
        - 非常にネガティブなやり取り: -10 ～ -15

        次の形式で、変化量を示す数字のみを返答してください (例: +2 または -3):
        好感度変化
        判定結果:
        <|assistant|>
        """.trimIndent()
    }

    private fun parseParameterUpdate(response: String?, currentAffinity: Int): Int {
        if (response.isNullOrBlank()) return currentAffinity
        return try {
            val affinityChange = response.trim().replace("+", "").toIntOrNull() ?: 0
            (currentAffinity + affinityChange).coerceIn(0, 100).also {
                Log.d(TAG, "파라미터 업데이트: 호감도 $currentAffinity -> $it (변화: $affinityChange)")
            }
        } catch (e: Exception) {
            Log.e(TAG, "파라미터 파싱 오류: $response", e)
            currentAffinity
        }
    }

    private fun buildPlayerOptionsPrompt(gameState: GameState, characterLastResponse: String, history: List<ChatMessage>, scenario: Scenario): String {
        return """
        <|system|>
        あなたはプレイヤー(先輩)の立場です。後輩である「${gameState.characterName}」(${gameState.characterPersona}) との会話を続けるための、あなたの発言選択肢を3つ生成してください。

        ## 現在の状況:
        ${scenario.setting}

        ## ${gameState.characterName}の現在の目標:
        ${scenario.characterGoal}

        ## ${gameState.characterName}の最後の発言:
        「$characterLastResponse」

        ## プレイヤー(先輩)の話し方と選択肢設計のルール (重要):
        - あなたは先輩なので、後輩である${gameState.characterName}に対して、常にくだけた口調（タメ口）で話します。
        - 生成する3つの選択肢は全て、このくだけた口調（タメ口）に従ってください。
        - **重要: 各選択肢は、直前の「${gameState.characterName}の最後の発言」に自然に応答する内容、または関連する内容にしてください。全く関係のない唐突な選択肢は避けてください。**
        - 各選択肢は15文字から40文字程度で、自然な会話として成り立つようにしてください。
        - 会話中に「点数」「ポイント」「好感度変化」などの具体的な数値や、それを示唆する表現は絶対に含めないでください。

        ## 応答形式 (厳守):
        選択肢1: [くだけた口調のポジティブな選択肢]
        選択肢2: [くだけた口調の中立的な選択肢]
        選択肢3: [くだけた口調のネガティブな選択肢] 

        上記の指示に厳密に従い、プレイヤー(先輩)の立場からの自然なタメ口の選択肢を3つ生成してください:
        <|assistant|>
        """.trimIndent()
    }

    private fun parseOptions(response: String?): List<String> {
        val defaultOptions = listOf("そうだな", "うーん…", "どうだろうな")
        if (response.isNullOrBlank()) return defaultOptions

        val extractedOptions = response.lines()
            .mapNotNull { line ->
                Regex("""^選択肢\s*\d+\s*[:：]\s*(.+)""").find(line.trim())
                    ?.groupValues?.getOrNull(1)?.trim()?.takeIf { it.isNotEmpty() }
            }
            .filter { it.isNotEmpty() }
            .toList()

        return if (extractedOptions.size >= 3) {
            extractedOptions.take(3)
        } else {
            (extractedOptions + defaultOptions).distinct().take(3)
        }
    }
}