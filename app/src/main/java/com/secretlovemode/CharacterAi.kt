package com.secretlovemode

import android.content.Context
import android.util.Log
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import java.io.File

data class ChatMessage(val role: String, val content: String) {
    companion object {
        const val ROLE_USER = "user"
        const val ROLE_MODEL = "model"
    }
}

data class GameResponse(
    val characterResponse: String,
    val newAffinity: Int
)

class CharacterAi(
    private val context: Context,
    private val modelAssetPath: String
) {

    companion object {
        private const val TAG = "CharacterAi"
        private const val DEFAULT_MAX_TOKENS_RESPONSE = 512
        private const val DEFAULT_TOP_K = 40
    }

    private var llmInference: LlmInference? = null
    var isModelReady: Boolean = false
        private set

    init {
        // initializeLlm already uses Dispatchers.IO internally via MediaPipe's createFromOptions
        initializeLlm()
    }

    // CharacterAi.kt의 initializeLlm() 함수 개선
    private fun initializeLlm() {
        Log.i(TAG, "LLM 초기화 시작: $modelAssetPath")
        
        // 파일 존재여부 확인
        val modelFile = File(modelAssetPath)
        if (!modelFile.exists()) {
            Log.e(TAG, "모델 파일이 존재하지 않습니다: $modelAssetPath")
            isModelReady = false
            return
        }
        
        if (modelFile.length() == 0L) {
            Log.e(TAG, "모델 파일 크기가 0입니다: $modelAssetPath")
            isModelReady = false
            return
        }
        
        Log.d(TAG, "모델 파일 확인 완료: 크기=${modelFile.length()}")
        
        try {
            val options = LlmInference.LlmInferenceOptions.builder()
                .setModelPath(modelAssetPath)
                .setMaxTokens(DEFAULT_MAX_TOKENS_RESPONSE)
                .setMaxTopK(DEFAULT_TOP_K)
                .build()

            llmInference = LlmInference.createFromOptions(context, options)
            isModelReady = true
            Log.i(TAG, "LLM 초기화 성공")
        } catch (e: Exception) {
            isModelReady = false
            llmInference = null
            Log.e(TAG, "LLM 초기화 실패: ${e.message}", e)
            Log.e(TAG, "스택 트레이스:", e)
        }
    }

    // 統合されたゲーム応答生成 (キャラクター応答 + パラメータ更新)
    fun generateGameResponse(
        gameState: GameState,
        playerSelectedOption: String,
        conversationHistory: List<ChatMessage> = emptyList(),
        scenario: Scenario // シナリオ情報を追加
    ): GameResponse {
        if (!isModelReady || llmInference == null) {
            Log.w(TAG, "モデルが準備されていません。generateGameResponse 処理不可。")
            return GameResponse(
                "ごめんなさい、今はちょっと考えられません。（モデル準備エラー）",
                gameState.affinity
            )
        }
        Log.d(TAG, "generateGameResponse 開始 (Thread: ${Thread.currentThread().name})")
        return try {
            // 1. キャラクター応答生成
            val characterResponse = generateCharacterResponse(gameState, playerSelectedOption, conversationHistory, scenario)

            // 2. パラメータ更新 (好感度を計算)
            val newAffinity = calculateUpdatedAffinity(gameState, playerSelectedOption, characterResponse, conversationHistory, scenario)

            Log.d(TAG, "generateGameResponse 完了 (Thread: ${Thread.currentThread().name})")
            GameResponse(
                characterResponse,
                newAffinity
            )
        } catch (e: Exception) {
            Log.e(TAG, "ゲーム応答生成中にエラー発生: ${e.message}", e)
            GameResponse(
                "ごめんなさい、ちょっと混乱しています。（エラー発生）",
                gameState.affinity
            )
        }
    }

    private fun generateCharacterResponse(
        gameState: GameState,
        playerSelectedOption: String,
        conversationHistory: List<ChatMessage>,
        scenario: Scenario // シナリオ情報を追加
    ): String {
        if (llmInference == null) {
            Log.w(TAG, "llmInference is null in generateCharacterResponse.")
            return "エラー：モデルが利用できません。"
        }
        val prompt = buildCharacterResponsePrompt(gameState, playerSelectedOption, conversationHistory, scenario)
        Log.d(TAG, "キャラクター応答プロンプト: $prompt")
        Log.d(TAG, "llmInference?.generateResponse(character) 呼び出し前 (Thread: ${Thread.currentThread().name})")
        val response = llmInference?.generateResponse(prompt)
        Log.d(TAG, "llmInference?.generateResponse(character) 呼び出し後 (Thread: ${Thread.currentThread().name})")
        return cleanResponse(response, ChatMessage.ROLE_MODEL)
    }

    private fun buildCharacterResponsePrompt(
        gameState: GameState,
        playerSelectedOption: String,
        history: List<ChatMessage>,
        scenario: Scenario // シナリオ情報を追加
    ): String {
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

    private fun calculateUpdatedAffinity(
        gameState: GameState,
        playerSelectedOption: String,
        characterResponse: String,
        conversationHistory: List<ChatMessage>,
        scenario: Scenario // シナリオ情報を追加
    ): Int {
        if (llmInference == null) {
            Log.w(TAG, "llmInference is null in calculateUpdatedAffinity.")
            return gameState.affinity
        }
        val prompt = buildParameterUpdatePrompt(gameState, playerSelectedOption, characterResponse, conversationHistory, scenario)
        Log.d(TAG, "パラメータ更新プロンプト: $prompt")
        Log.d(TAG, "llmInference?.generateResponse(parameter) 呼び出し前 (Thread: ${Thread.currentThread().name})")
        val response = llmInference?.generateResponse(prompt)
        Log.d(TAG, "llmInference?.generateResponse(parameter) 呼び出し後 (Thread: ${Thread.currentThread().name})")
        return parseParameterUpdate(response, gameState.affinity)
    }

    private fun buildParameterUpdatePrompt(
        gameState: GameState,
        playerSelectedOption: String,
        characterResponse: String,
        history: List<ChatMessage>,
        scenario: Scenario // シナリオ情報を追加
    ): String {
        // このプロンプトは内部的なスコア計算用なので、スコア関連の表現があっても問題ありません。
        // ユーザーに直接表示されるテキストではありません。
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
- 非常にポジティブなやり取り: +5 ～ +10
- ややポジティブなやり取り: +1 ～ +4
- 中立または無関係: 0
- ややネガティブなやり取り: -1 ～ -4
- 非常にネガティブなやり取り: -5 ～ -10

次の形式で、変化量を示す数字のみを返答してください (例: +2 または -3):
好感度変化
判定結果:
<|assistant|>
    """.trimIndent()
    }

    private fun parseParameterUpdate(response: String?, currentAffinity: Int): Int {
        if (response.isNullOrBlank()) {
            return currentAffinity
        }

        return try {
            val cleanResponse = response.replace(" ", "").replace("　", "") // 空白除去
            val affinityChange = cleanResponse.replace("+", "").toIntOrNull() ?: 0
            val newAffinity = (currentAffinity + affinityChange).coerceIn(0, 100)
            Log.d(TAG, "パラメータ更新: 好感度 $currentAffinity -> $newAffinity")
            newAffinity
        } catch (e: Exception) {
            Log.e(TAG, "パラメータ解析エラー: ${e.message}")
            currentAffinity
        }
    }

    fun generatePlayerOptions(
        gameState: GameState,
        characterLastResponse: String,
        conversationHistory: List<ChatMessage> = emptyList(),
        scenario: Scenario // シナリオ情報を追加
    ): List<String> {
        if (!isModelReady || llmInference == null) {
            Log.w(TAG, "モデルが準備されていません。選択肢生成は不可能です。")
            return listOf("選択肢生成エラー1", "選択肢生成エラー2", "選択肢生成エラー3")
        }
        Log.d(TAG, "generatePlayerOptions 開始 (Thread: ${Thread.currentThread().name})")
        val prompt = buildPlayerOptionsPrompt(gameState, characterLastResponse, conversationHistory, scenario)
        Log.d(TAG, "プレイヤー選択肢プロンプト: $prompt")
        Log.d(TAG, "llmInference?.generateResponse(options) 呼び出し前 (Thread: ${Thread.currentThread().name})")
        val response = llmInference?.generateResponse(prompt)
        Log.d(TAG, "llmInference?.generateResponse(options) 呼び出し後 (Thread: ${Thread.currentThread().name})")
        Log.d(TAG, "generatePlayerOptions 完了 (Thread: ${Thread.currentThread().name})")
        return parseOptions(response)
    }

    private fun buildPlayerOptionsPrompt(
        gameState: GameState,
        characterLastResponse: String,
        history: List<ChatMessage>,
        scenario: Scenario // シナリオ情報を追加
    ): String {
        return """
<|system|>
あなたはプレイヤー(先輩)の立場です。後輩である「${gameState.characterName}」(${gameState.characterPersona}) との会話を続けるための、あなたの発言選択肢を3つ生成してください。

## 現在の状況:
${scenario.setting}

## ${gameState.characterName}の現在の目標:
${scenario.characterGoal}

## ${gameState.characterName}の最後の発言: 「$characterLastResponse」

## プレイヤー(先輩)の話し方と選択肢設計のルール (重要):
- あなたは先輩なので、後輩である${gameState.characterName}に対して、常にくだけた口調（タメ口）で話します。
- 生成する3つの選択肢は全て、このくだけた口調（タメ口）に従ってください。
- **重要: 各選択肢は、直前の「${gameState.characterName}の最後の発言」に自然に応答する内容、または関連する内容にしてください。全く関係のない唐突な選択肢は避けてください。**
- 各選択肢は15文字から40文字程度で、自然な会話として成り立つようにしてください。
- 会話中に「点数」「ポイント」「好感度変化」などの具体的な数値や、それを示唆する表現は絶対に含めないでください。

選択肢1 (ポジティブな選択肢): 「${gameState.characterName}の最後の発言」と現在の状況を踏まえ、彼女の目標達成を助ける、または好意的に受け取られるような発言。
選択肢2 (中立的な選択肢): 「${gameState.characterName}の最後の発言」に対して、当たり障りのない一般的な会話で応答する。
選択肢3 (ネガティブな選択肢): 「${gameState.characterName}の最後の発言」に対して、彼女の目標を妨害する、または不快にさせるような発言。

## 応答形式 (厳守):
選択肢1: [くだけた口調のポジティブな選択肢]
選択肢2: [くだけた口調の中立的な選択肢]
選択肢3: [くだけた口調のネガティブな選択肢]

上記の指示に厳密に従い、プレイヤー(先輩)の立場からの自然なタメ口の選択肢を3つ生成してください:
<|assistant|>
    """.trimIndent()
    }

    private fun parseOptions(response: String?): List<String> {
        val defaultOptions = listOf("そうだな", "うーん…", "どうだろうな") // 日本語タメ口のデフォルト選択肢

        if (response.isNullOrBlank()) {
            Log.w(TAG, "LLM으로부터 선택지 응답이 없거나 비어있습니다. 기본 선택지를 사용합니다.")
            return defaultOptions
        }
        Log.d(TAG, "LLM으로부터 받은 원본 선택지 응답: $response")

        val extractedOptions = response.lines()
            .mapNotNull { line ->
                val trimmedLine = line.trim()
                val matchResult = Regex("""^選択肢\s*\d+\s*[:：]\s*(.+)""").find(trimmedLine)
                matchResult?.groupValues?.getOrNull(1)?.trim()?.takeIf { it.isNotEmpty() }
            }
            .map { optionText -> // 抽出されたオプションテキストからスコア関連表現を削除
                optionText.replace(Regex("""[+-]?\d+\s*(点|ポイント|点数変動|好感度変化|スコア)"""), "")
                    .replace(Regex("""(好感度|評価|スコア)\s*([+-]\d+)"""), "")
                    .trim()
            }
            .filter { it.isNotEmpty() } // スコア削除後に空になる可能性があるのでフィルタリング
            .toList()

        Log.d(TAG, "파싱 후 추출된 선택지 (${extractedOptions.size}개): $extractedOptions")

        return if (extractedOptions.size >= 3) {
            extractedOptions.take(3)
        } else {
            Log.w(TAG, "3개 미만의 선택지가 추출되었습니다 (${extractedOptions.size}개). 기본 선택지로 보충합니다.")
            (extractedOptions + defaultOptions).distinct().take(3)
        }
    }

    private fun cleanResponse(response: String?, role: String): String {
        if (response == null) {
            return "うーん…何て答えたらいいでしょう？" // キャラクターのデフォルト応答 (丁寧語)
        }

        var clean = response
            .replace("<|assistant|>", "")
            .replace("<|user|>", "")
            .replace("<|system|>", "")
            .replace("${ChatMessage.ROLE_MODEL}:", "")
            .trim()

        // キャラクター応答からスコア関連テキストを削除
        clean = clean.replace(Regex("""[+-]?\d+\s*(点|ポイント|点数変動|好感度変化|スコア)"""), "")
            .replace(Regex("""(好感度|評価|スコア)\s*([+-]\d+)"""), "")
            .trim()

        return if (clean.isNotEmpty()) clean else "うーん…" // キャラクターのデフォルト応答 (丁寧語)
    }

    fun close() {
        Log.i(TAG, "LLMリソース解放試行開始。")
        try {
            llmInference?.close()
            llmInference = null // Explicitly nullify after closing
            isModelReady = false
            Log.i(TAG, "LLMリソース解放成功。llmInference = $llmInference, isModelReady = $isModelReady")
        } catch (e: Exception) {
            Log.e(TAG, "LLMリソース解放中にエラー発生: ${e.message}", e)
        }
    }
}