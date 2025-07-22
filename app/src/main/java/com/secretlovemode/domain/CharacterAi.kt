package com.secretlovemode.domain

import android.content.Context
import android.util.Log
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import com.secretlovemode.data.model.CharacterResponse
import com.secretlovemode.data.model.ChatMessage
import com.secretlovemode.data.model.GameState
import com.secretlovemode.data.model.InitialTurnResult
import com.secretlovemode.data.model.Scenario
import com.secretlovemode.data.model.TurnResult
import com.secretlovemode.data.repository.PromptManager
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow


class CharacterAi(
    private val context: Context,
    private val modelAssetPath: String
) {

    companion object {
        private const val TAG = "CharacterAi"
        private const val DEFAULT_MAX_TOKENS_RESPONSE = 1024
        private const val DEFAULT_TOP_K = 75
    }

    //  JSON 파싱을 위한 안정적인 인스턴스 생성
    private val json = Json {
        ignoreUnknownKeys = true // 알 수 없는 필드는 무시
        isLenient = true // 약간의 형식 오류는 너그럽게 처리
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
                // setMaxTopK
                .setMaxTopK(DEFAULT_TOP_K)
                .setPreferredBackend(LlmInference.Backend.GPU)
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

    // 스트리밍 결과를 전달하기 위한 Sealed Class
    sealed class StreamEvent {
        data class TextChunk(val text: String) : StreamEvent()
        data class TurnComplete(val payload: TurnEndPayload) : StreamEvent()
        data class Error(val message: String) : StreamEvent()
    }

    // 스트리밍이 끝난 후 전달될 JSON 데이터를 위한 데이터 클래스
    @Serializable
    data class TurnEndPayload(
        val inner_monologue: String,
        val affinity_change: Int,
        val player_options: List<String>
    )

    /**
     * 스트리밍을 지원하는 새로운 함수.
     * Kotlin Flow를 사용하여 AI의 응답을 실시간으로 전달합니다.
     */
    fun processPlayerTurnStream(
        gameState: GameState,
        playerSelectedOption: String,
        conversationHistory: List<ChatMessage>,
        scenario: Scenario
    ): Flow<StreamEvent> = callbackFlow {
        // 모델이 준비되지 않았으면 에러 이벤트를 보내고 Flow를 종료합니다.
        if (!isModelReady || llmInference == null) {
            trySend(StreamEvent.Error("モデルが準備されていません。"))
            close()
            return@callbackFlow
        }

        // 프롬프트 준비
        val promptTemplate = PromptManager.getUnifiedTurnPrompt()
            ?: run {
                trySend(StreamEvent.Error("プロンプトの読み込みに失敗しました。"))
                close()
                return@callbackFlow
            }
        val historyText = conversationHistory.takeLast(4).joinToString("\n") { message ->
            val role = if (message.role == ChatMessage.ROLE_USER) "プレイヤー(先輩)" else "${gameState.characterName}(後輩)"
            "$role: ${message.message}"
        }
        val prompt = promptTemplate
            .replace("{characterName}", gameState.characterName)
            .replace("{characterPersona}", gameState.characterPersona)
            .replace("{setting}", scenario.setting)
            .replace("{characterGoal}", scenario.characterGoal)
            .replace("{conversationHistory}", historyText)
            .replace("{playerSelectedOption}", playerSelectedOption)

        // 스트리밍 처리 로직
        val separator = "|||JSON_START|||"
        val responseJsonPart = StringBuilder()
        var separatorFound = false

        try {
            llmInference?.generateResponseAsync(prompt) { partialResult, done ->
                if (partialResult != null) {
                    // 구분자가 발견되었는지 여부에 따라 처리 분기
                    if (separatorFound) {
                        // 구분자 이후의 모든 내용은 JSON 부분이므로 버퍼에 추가
                        responseJsonPart.append(partialResult)
                    } else {
                        // 아직 구분자를 찾지 못한 경우
                        if (partialResult.contains(separator)) {
                            val parts = partialResult.split(separator, limit = 2)
                            // 구분자 앞부분은 텍스트 청크로 즉시 전송
                            if (parts[0].isNotEmpty()) {
                                trySend(StreamEvent.TextChunk(parts[0]))
                            }
                            separatorFound = true
                            // 구분자 뒷부분은 JSON 버퍼에 추가
                            responseJsonPart.append(parts[1])
                        } else {
                            // 구분자가 없는 순수 텍스트 청크는 즉시 전송
                            trySend(StreamEvent.TextChunk(partialResult))
                        }
                    }
                }

                // AI 응답 생성이 완료되었을 때
                if (done) {
                    try {
                        if (separatorFound) {
                            // JSON 버퍼에 쌓인 내용을 파싱하여 TurnComplete 이벤트 전송
                            val payloadJson = stripJsonMarkdown(responseJsonPart.toString())
                            val payload = json.decodeFromString<TurnEndPayload>(payloadJson)
                            trySend(StreamEvent.TurnComplete(payload))
                        } else {
                            // 끝까지 구분자가 안나왔으면 형식 오류
                            Log.e(TAG, "AI 응답에서 구분자($separator)를 찾지 못했습니다.")
                            trySend(StreamEvent.Error("AI 응답 형식 오류"))
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "스트리밍 JSON 파싱 실패", e)
                        trySend(StreamEvent.Error("AI 응답 파싱 오류: ${e.message}"))
                    }
                    close() // Flow 정상 종료
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "generateResponseAsync 호출 중 오류 발생", e)
            trySend(StreamEvent.Error("AI 응답 생성 중 오류 발생: ${e.message}"))
            close(e) // Flow 예외 종료
        }

        // Flow가 외부에서 취소될 때 정리 작업을 수행합니다.
        awaitClose {
            Log.d(TAG, "스트리밍 Flow가 종료되었습니다.")
        }
    }


    suspend fun processInitialTurn(
        gameState: GameState,
        scenario: Scenario
    ): InitialTurnResult {
        // 이 함수는 스트리밍을 사용하지 않으므로 기존 로직 유지
        return mutex.withLock {
            val promptTemplate = PromptManager.getInitialTurnPrompt()
                ?: return@withLock createDefaultInitialTurnResult("プロンプトの読み込みに失敗しました")

            val prompt = promptTemplate
                .replace("{characterName}", gameState.characterName)
                .replace("{characterPersona}", gameState.characterPersona)
                .replace("{setting}", scenario.setting)
                .replace("{characterGoal}", scenario.characterGoal)

            // 임시로 내부 함수를 다시 만듭니다.
            val fullResponse = StringBuilder()
            val deferred = CompletableDeferred<Unit>()
            llmInference?.generateResponseAsync(prompt) { partialResult, done ->
                partialResult?.let { fullResponse.append(it) }
                if (done) deferred.complete(Unit)
            }
            deferred.await()

            parseInitialTurnResponse(fullResponse.toString())
        }
    }

    suspend fun processPlayerTurn(
        gameState: GameState,
        playerSelectedOption: String,
        conversationHistory: List<ChatMessage>,
        scenario: Scenario
    ): TurnResult {
        return mutex.withLock {
            val promptTemplate = PromptManager.getUnifiedTurnPrompt()
                ?: return@withLock TurnResult(createDefaultInitialTurnResult("プロンプトの読み込みに失敗しました").fullInitialResponse, gameState.affinity, emptyList())

            val historyText = conversationHistory.takeLast(4).joinToString("\n") { message ->
                val role = if (message.role == ChatMessage.ROLE_USER) "プレイヤー(先輩)" else "${gameState.characterName}(後輩)"
                "$role: ${message.message}"
            }
            val prompt = promptTemplate
                .replace("{characterName}", gameState.characterName)
                .replace("{characterPersona}", gameState.characterPersona)
                .replace("{setting}", scenario.setting)
                .replace("{characterGoal}", scenario.characterGoal)
                .replace("{conversationHistory}", historyText)
                .replace("{playerSelectedOption}", playerSelectedOption)

            val fullResponse = StringBuilder()
            val deferred = CompletableDeferred<Unit>()
            llmInference?.generateResponseAsync(prompt) { partialResult, done ->
                partialResult?.let { fullResponse.append(it) }
                if (done) deferred.complete(Unit)
            }
            deferred.await()

            parsePlayerTurnResponse(fullResponse.toString(), gameState.affinity)
        }
    }


    //  JSON 파싱 로직을 안전하게 변경
    private fun parseInitialTurnResponse(response: String): InitialTurnResult {
        val cleanResponse = stripJsonMarkdown(response)
        return try {
            val jsonResponse = json.decodeFromString<InitialTurnJson>(cleanResponse)
            InitialTurnResult(
                fullInitialResponse = jsonResponse.response,
                firstPlayerOptions = jsonResponse.player_options.takeIf { it.isNotEmpty() } ?: listOf("…", "…", "…")
            )
        } catch (e: Exception) {
            Log.e(TAG, "초기 턴 JSON 파싱 실패: $response", e)
            createDefaultInitialTurnResult("AI 응답 형식 오류")
        }
    }

    private fun parsePlayerTurnResponse(response: String, currentAffinity: Int): TurnResult {
        val cleanResponse = stripJsonMarkdown(response)
        return try {
            val jsonResponse = json.decodeFromString<UnifiedTurnJson>(cleanResponse)
            TurnResult(
                fullCharacterResponse = jsonResponse.response,
                updatedAffinity = currentAffinity + jsonResponse.affinity_change,
                nextPlayerOptions = jsonResponse.player_options.takeIf { it.isNotEmpty() } ?: listOf("…", "…", "…")
            )
        } catch (e: Exception) {
            Log.e(TAG, "플레이어 턴 JSON 파싱 실패: $response", e)
            TurnResult(
                fullCharacterResponse = CharacterResponse("(오류 발생)", "AI 응답 형식 오류"),
                updatedAffinity = currentAffinity,
                nextPlayerOptions = listOf("はい", "いいえ", "分からない")
            )
        }
    }



    private fun stripJsonMarkdown(jsonString: String): String {
        var cleanedString = jsonString.replaceFirst("""```json\s*""".toRegex(), "")
        cleanedString = cleanedString.replaceFirst("""\s*```$""".toRegex(), "")
        return cleanedString.trim()
    }

    // [신규] 오류 발생 시 기본값을 생성하는 헬퍼 함수
    private fun createDefaultInitialTurnResult(errorMessage: String): InitialTurnResult {
        return InitialTurnResult(
            fullInitialResponse = CharacterResponse("(오류 발생)", errorMessage),
            firstPlayerOptions = listOf("はい", "いいえ", "分からない")
        )
    }


    //  JSON 파싱을 위한 내부 데이터 클래스를 private으로 변경하여 캡슐화
    @Serializable
    private data class InitialTurnJson(
        val response: CharacterResponse,
        val player_options: List<String>
    )

    @Serializable
    private data class UnifiedTurnJson(
        val response: CharacterResponse,
        val affinity_change: Int,
        val player_options: List<String>
    )

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