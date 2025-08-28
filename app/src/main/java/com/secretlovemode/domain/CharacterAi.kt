package com.secretlovemode.domain

import android.content.Context
import android.util.Log
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import com.secretlovemode.data.model.GameState
import com.secretlovemode.data.repository.PromptManager
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.int
import kotlinx.serialization.json.JsonNull
import java.io.File
import com.secretlovemode.data.model.ChatMessage
import android.content.ContentValues.TAG




class CharacterAi(
    private val context: Context,
    private val modelAssetPath: String,
    private val playerName: String
) {

    companion object {
        private const val TAG = "CharacterAi"
        private const val DEFAULT_MAX_TOKENS_RESPONSE = 4000  // 캐시 크기 제한 내로 조정
        private const val DEFAULT_TOP_K = 75
    }

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    private var llmInference: LlmInference? = null
    var isModelReady: Boolean = false
        private set
    
    private var isProcessing: Boolean = false
    private val mutex = Mutex()

    init {
        initializeLlm()
    }

    private fun initializeLlm() {
        Log.i(TAG, "LLM initialization started: $modelAssetPath")
        val modelFile = File(modelAssetPath)
        Log.d(TAG, "Model file exists: ${modelFile.exists()}, length: ${modelFile.length()}")
        if (!modelFile.exists() || modelFile.length() == 0L) {
            Log.e(TAG, "Invalid model file or empty: $modelAssetPath")
            isModelReady = false
            return
        }

        try {
            Log.d(TAG, "Building LlmInferenceOptions...")
            val options = LlmInference.LlmInferenceOptions.builder()
                .setModelPath(modelAssetPath)
                .setMaxTokens(DEFAULT_MAX_TOKENS_RESPONSE)
                .setMaxTopK(DEFAULT_TOP_K)
                .setPreferredBackend(LlmInference.Backend.GPU)
                .build()

            Log.d(TAG, "Calling LlmInference.createFromOptions...")
            llmInference = LlmInference.createFromOptions(context, options)
            isModelReady = true
            Log.i(TAG, "LLM initialization successful")
        } catch (e: Exception) {
            isModelReady = false
            llmInference = null
            Log.e(TAG, "LLM initialization failed with exception: ${e.message}", e)
        }
    }

    /**
     * AI를 사용하여 플레이어의 선택에 대한 호감도 변화를 판단합니다.
     * @param gameState 현재 게임 상태
     * @param situationText 현재 상황에 대한 설명 (예: 선택지가 제시된 이유)
     * @param playerSelectedOption 플레이어가 선택한 옵션의 텍스트
     * @param baseAffectionChange 시나리오에 명시된 기본 호감도 변화량 (String 타입)
     * @return -20에서 +20 사이의 호감도 변화량. 오류 발생 시 기본 점수를 반환합니다.
     */
    suspend fun judgeAffection(
        gameState: GameState,
        situationText: String,
        playerSelectedOption: String,
        baseAffectionChange: Int,
        conversationHistory: List<ChatMessage>
    ): Int {
        if (!isModelReady || llmInference == null) {
            Log.e(TAG, "Affection Judge: Model not ready.")
            return 0
        }

        return mutex.withLock {
            val promptTemplate = PromptManager.getAffectionJudgePrompt(playerName)
                ?: run {
                    Log.e(TAG, "Affection Judge: Prompt template not found.")
                    return@withLock 0
                }

            val prompt = promptTemplate
                .replace("{characterName}", gameState.characterName)
                .replace("{characterPersona}", gameState.characterPersona)
                .replace("{situation_text}", situationText)
                .replace("{playerSelectedOption}", playerSelectedOption)
                .replace("{baseAffectionChange}", baseAffectionChange.toString())
                .replace("{conversationHistory}", formatConversationHistory(conversationHistory))
                .replace("{keyInputContext}", formatKeyInputContext(gameState.keyInputValues))
                .replace("{sectionSummaries}", formatSectionSummaries(gameState.sectionSummaries))

            Log.d(TAG, "Affection Judge Prompt: $prompt")

            val responseString = generateFullResponse(prompt)
                ?: run {
                    Log.e(TAG, "Affection Judge: Failed to generate response.")
                    return@withLock 0
                }

            Log.d(TAG, "Affection Judge Raw Response: $responseString")

            try {
                val jsonString = extractJson(responseString)
                if (jsonString.isEmpty()) {
                    Log.e(TAG, "Affection Judge: No JSON found in response: $responseString")
                    return@withLock 0
                }

                Log.d(TAG, "Extracted JSON: $jsonString") // Log the extracted JSON
                
                // Try parsing with flexible field names
                val affectionChange = try {
                    val result = json.decodeFromString<AffectionJudgeResponse>(jsonString)
                    result.affectionChange
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to parse with 'affectionChange', trying alternative field names")
                    // Try parsing with alternative field names
                    parseAffectionFromAlternativeFields(jsonString)
                }
                
                Log.d(TAG, "Parsed affection change: $affectionChange")
                affectionChange.coerceIn(-20, 20)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to parse affection judge response: $responseString", e)
                0
            }
        }
    }

    /**
     * LLM 응답을 비동기적으로 받아 전체 문자열로 반환하는 헬퍼 함수
     */
    private suspend fun generateFullResponse(prompt: String): String? {
        if (!isModelReady || llmInference == null) {
            Log.w(TAG, "LLM not ready, cannot generate response.")
            return null
        }
        
        if (isProcessing) {
            Log.w(TAG, "LLM is already processing another request. Skipping this request.")
            return null
        }

        return try {
            isProcessing = true
            val deferred = CompletableDeferred<String>()
            val fullResponse = StringBuilder()
            
            llmInference?.generateResponseAsync(prompt) { partialResult, done ->
                partialResult?.let { fullResponse.append(it) }
                if (done) {
                    isProcessing = false
                    deferred.complete(fullResponse.toString())
                }
            }
            
            deferred.await()
        } catch (e: Exception) {
            isProcessing = false
            Log.e(TAG, "Error during LLM response generation", e)
            null
        }
    }

    /**
     * LLM이 반환한 텍스트에서 JSON 부분만 안정적으로 추출합니다.
     */
    private fun extractJson(text: String): String {
        val startIndex = text.indexOf('{')
        val endIndex = text.lastIndexOf('}')
        return if (startIndex != -1 && endIndex != -1 && startIndex < endIndex) {
            text.substring(startIndex, endIndex + 1)
        } else {
            ""
        }
    }

    private fun formatConversationHistory(history: List<ChatMessage>): String {
        if (history.isEmpty()) {
            return "なし"
        }
        
        return history.takeLast(10).joinToString("\n") { message ->
            val truncatedMessage = if (message.message.length > 50) {
                message.message.take(50) + "..."
            } else {
                message.message
            }
            when (message.role) {
                ChatMessage.ROLE_USER -> "P: $truncatedMessage"
                ChatMessage.ROLE_MODEL -> "M: $truncatedMessage"
                ChatMessage.ROLE_SYSTEM -> "[S]: $truncatedMessage"
                else -> ""
            }
        }
    }

    /**
     * Key input 값들을 포맷팅하여 문자열로 변환합니다.
     */
    private fun formatKeyInputContext(keyInputValues: Map<String, String>): String {
        if (keyInputValues.isEmpty()) {
            return "なし"
        }
        
        return keyInputValues.entries.take(5).joinToString(", ") { (key, value) ->
            val truncatedValue = if (value.length > 30) value.take(30) + "..." else value
            when (key) {
                "travel_destination" -> "旅行:$truncatedValue"
                "confession" -> "告白:$truncatedValue"
                "name_input" -> "名前:$truncatedValue"
                else -> "$key:$truncatedValue"
            }
        }
    }

    /**
     * 섹션별 대화 내용을 포맷팅하여 문자열로 변환합니다.
     */
    private fun formatSectionDialogues(sectionDialogues: Map<String, List<com.secretlovemode.data.model.SectionDialogue>>): String {
        Log.d(TAG, "formatSectionDialogues called with sections: ${sectionDialogues.keys}")
        
        if (sectionDialogues.isEmpty()) {
            Log.d(TAG, "sectionDialogues is empty, returning default message")
            return "まだセクションの会話はありません。"
        }
        
        val formatted = sectionDialogues.entries.joinToString("\n\n") { (sectionId, dialogues) ->
            val sectionTitle = "=== セクション$sectionId ==="
            val dialogueText = dialogues.joinToString("\n") { dialogue ->
                val speakerName = when (dialogue.speaker) {
                    "system" -> "[システム]"
                    "主人公(心の声)" -> "(心の声)"
                    "主人公(会話)" -> "プレイヤー"
                    "教授" -> "教授"
                    else -> dialogue.speaker
                }
                "$speakerName: ${dialogue.text}"
            }
            "$sectionTitle\n$dialogueText"
        }
        
        Log.d(TAG, "Formatted sectionDialogues length: ${formatted.length}")
        return formatted
    }

    /**
     * 섹션별 요약을 포맷팅하여 문자열로 변환합니다.
     */
    private fun formatSectionSummaries(sectionSummaries: Map<String, String>): String {
        if (sectionSummaries.isEmpty()) {
            return "なし"
        }
        
        return sectionSummaries.entries.toList().takeLast(3).joinToString("\n") { entry ->
            val truncatedSummary = if (entry.value.length > 100) {
                entry.value.take(100) + "..."
            } else {
                entry.value
            }
            "S${entry.key}: $truncatedSummary"
        }
    }

    /**
     * AI를 사용하여 플레이어의 고백 성공 여부를 판단합니다.
     * @param gameState 현재 게임 상태
     * @param confessionMessage 플레이어가 입력한 고백 메시지
     * @return Pair<Boolean, String?> - (성공여부, 판정이유)
     */
    suspend fun judgeConfession(
        gameState: GameState,
        confessionMessage: String,
        conversationHistory: List<ChatMessage>
    ): Pair<Boolean, String?> {
        if (!isModelReady || llmInference == null) {
            Log.e(TAG, "Confession Judge: Model not ready.")
            return Pair(false, null)
        }

        return mutex.withLock {
            val promptTemplate = PromptManager.getConfessionPrompt(playerName)
                ?: run {
                    Log.e(TAG, "Confession Judge: Prompt template not found.")
                    return@withLock Pair(false, null)
                }

            val prompt = promptTemplate
                .replace("{characterName}", gameState.characterName)
                .replace("{characterPersona}", gameState.characterPersona)
                .replace("{confessionMessage}", confessionMessage)
                .replace("{currentAffinity}", gameState.affinity.toString())
                .replace("{conversationHistory}", formatConversationHistory(conversationHistory))
                .replace("{keyInputContext}", formatKeyInputContext(gameState.keyInputValues))
                .replace("{sectionSummaries}", formatSectionSummaries(gameState.sectionSummaries))

            Log.d(TAG, "Confession Judge Prompt: $prompt")

            val responseString = generateFullResponse(prompt)
                ?: run {
                    Log.e(TAG, "Confession Judge: Failed to generate response.")
                    return@withLock Pair(false, null)
                }

            Log.d(TAG, "Confession Judge Raw Response: $responseString")

            // 호감도 85 이상이면 강제 성공 (안전장치)
            if (gameState.affinity >= 85) {
                Log.i(TAG, "Confession Judge: Affinity ${gameState.affinity} >= 85, forcing success")
                return@withLock Pair(true, "高い好感度による自動成功")
            }

            try {
                val jsonString = extractJson(responseString)
                if (jsonString.isEmpty()) {
                    Log.e(TAG, "Confession Judge: No JSON found in response: $responseString")
                    // 호감도 80 이상이면 JSON 파싱 실패해도 성공으로 처리
                    return@withLock if (gameState.affinity >= 80) {
                        Log.i(TAG, "Confession Judge: High affinity ${gameState.affinity}, defaulting to success")
                        Pair(true, "JSON解析失敗、高好感度により成功")
                    } else {
                        Pair(false, "JSON解析失敗")
                    }
                }

                Log.d(TAG, "Extracted JSON: $jsonString")
                val result = json.decodeFromString<ConfessionResponse>(jsonString)
                Log.d(TAG, "Parsed ConfessionResponse: $result")
                
                // 고백 메시지 품질에 따른 상세 로그
                result.messageBonus?.let { bonus ->
                    Log.i(TAG, "🎯 Confession message bonus: $bonus points")
                }
                result.finalAffinity?.let { finalAff ->
                    Log.i(TAG, "📊 Final calculated affinity: $finalAff (original: ${gameState.affinity})")
                }
                result.reason?.let { reason ->
                    Log.i(TAG, "💬 AI judgment reason: $reason")
                }
                
                Pair(result.success, result.reason)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to parse confession response: $responseString", e)
                // 호감도 80 이상이면 파싱 실패해도 성공으로 처리
                if (gameState.affinity >= 80) {
                    Log.i(TAG, "Confession Judge: High affinity ${gameState.affinity}, defaulting to success despite parse error")
                    Pair(true, "解析エラー、高好感度により成功")
                } else {
                    Pair(false, "解析エラー")
                }
            }
        }
    }

    fun close() {
        try {
            llmInference?.close()
        } catch (e: Exception) {
            Log.e(TAG, "Error releasing LLM resources", e)
        } finally {
            llmInference = null
            isModelReady = false
            isProcessing = false
            Log.i(TAG, "LLM resources released.")
        }
    }

    /**
     * 섹션 대화를 요약하고 메구미의 내심을 분석합니다.
     */
    suspend fun generateSectionSummary(
        sectionId: String,
        sectionDialogues: List<com.secretlovemode.data.model.SectionDialogue>,
        gameState: GameState
    ): String? {
        if (!isModelReady || llmInference == null) {
            Log.e(TAG, "Section Summary: Model not ready.")
            return null
        }

        return mutex.withLock {
            val dialogueText = sectionDialogues.joinToString("\n") { dialogue ->
                val speakerName = when (dialogue.speaker) {
                    "system" -> "[システム]"
                    "主人公(心の声)" -> "(心の声)"
                    "主人公(会話)" -> "プレイヤー"
                    "教授" -> "教授"
                    else -> dialogue.speaker
                }
                "$speakerName: ${dialogue.text}"
            }

            val prompt = """
<|system|>
恋愛ゲーム要約AI。セクション会話を簡潔に要約。

キャラ: ${gameState.characterName} (好感度${gameState.affinity})
セクション$sectionId:
$dialogueText

出力: **要約:** (8-15文) **めぐみ:** (感情変化) **ポイント:** (重要要素)
<|assistant|>
"""

            val responseString = generateFullResponse(prompt)
            if (responseString.isNullOrBlank()) {
                Log.e(TAG, "Section Summary: Failed to generate response for section $sectionId")
                return@withLock null
            }

            Log.d(TAG, "Section Summary Generated for section $sectionId: ${responseString.take(200)}...")
            responseString
        }
    }

    /**
     * 다양한 필드 이름으로 호감도 변화값을 파싱하는 헬퍼 함수
     */
    private fun parseAffectionFromAlternativeFields(jsonString: String): Int {
        return try {
            val jsonObject = json.parseToJsonElement(jsonString).jsonObject
            
            // 가능한 필드 이름들을 순서대로 시도
            val possibleFields = listOf("affectionChange", "affection", "change", "value", "score")
            
            for (fieldName in possibleFields) {
                val element = jsonObject[fieldName]
                if (element != null && element !is JsonNull) {
                    return element.jsonPrimitive.int
                }
            }
            
            Log.e(TAG, "No valid affection field found in JSON: $jsonString")
            0
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse affection from alternative fields: $jsonString", e)
            0
        }
    }

    // --- JSON 파싱을 위한 내부 데이터 클래스 ---

    @Serializable
    private data class AffectionJudgeResponse(
        val affectionChange: Int
    )

    @Serializable
    private data class ConfessionResponse(
        val success: Boolean,
        val finalAffinity: Int? = null,
        val messageBonus: Int? = null,
        val reason: String? = null
    )
    
    /**
     * 고백 메시지에 대한 캐릭터의 반응을 생성합니다.
     */
    suspend fun generateConfessionResponse(
        confessionMessage: String,
        characterName: String,
        affinity: Int,
        conversationHistory: List<ChatMessage>
    ): String? = mutex.withLock {
        if (!isModelReady) {
            Log.e(TAG, "Confession Response: Model not ready")
            return@withLock null
        }

        if (isProcessing) {
            Log.w(TAG, "Confession Response: Already processing another request")
            return@withLock null
        }

        isProcessing = true
        try {
            Log.d(TAG, "Generating confession response for message: $confessionMessage")
            
            // 대화 기록을 텍스트로 변환
            val historyText = conversationHistory.takeLast(10).joinToString("\n") { message ->
                "${message.role}: ${message.message}"
            }
            
            val prompt = """
<|system|>
あなたは${characterName}です。プレイヤー「${playerName}」からの告白メッセージに対して、自然で感情豊かに返答してください。

**現在の状況:**
- プレイヤー名: ${playerName}
- 現在の好感度: ${affinity}/100
- 告白メッセージ: 「${confessionMessage}」

**最近の会話履歴:**
${historyText}

**返答のガイドライン:**
- 好感度に応じて適切な反応を示す
- ${characterName}の性格を維持する
- 感情的で自然な日本語で返答する
- 200文字以内で簡潔に返答する

**好感度に応じた反応の傾向:**
- 80以上: とても嬉しそうで積極的な反応
- 60-79: 嬉しいが少し戸惑いもある反応  
- 40-59: 驚きや困惑を示しながらも優しい反応
- 20-39: 困惑しているが傷つけないよう配慮した反応
- 20未満: 申し訳なさそうに断る反応

<|assistant|>
"""

            val responseString = generateFullResponse(prompt)
            if (responseString.isNullOrBlank()) {
                Log.e(TAG, "Confession Response: Failed to generate response")
                return@withLock null
            }

            Log.d(TAG, "Confession Response Generated: ${responseString.take(100)}...")
            responseString

        } catch (e: Exception) {
            Log.e(TAG, "Error generating confession response", e)
            null
        } finally {
            isProcessing = false
        }
    }
}