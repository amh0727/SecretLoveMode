package com.SecretLoveMode

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
    val newAffinity: Int,
    val newDrunkenness: Int
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
        conversationHistory: List<ChatMessage> = emptyList()
    ): GameResponse {
        if (!isModelReady || llmInference == null) {
            Log.w(TAG, "モデルが準備されていません。generateGameResponse 処理不可。")
            return GameResponse(
                "ごめんなさい、今はちょっと考えられません。（モデル準備エラー）",
                gameState.affinity,
                gameState.drunkenness
            )
        }
        Log.d(TAG, "generateGameResponse 開始 (Thread: ${Thread.currentThread().name})")
        return try {
            // 1. キャラクター応答生成
            val characterResponse = generateCharacterResponse(gameState, playerSelectedOption, conversationHistory)

            // 2. パラメータ更新 (好感度と酔い具合を同時に計算)
            val updatedParameters = calculateUpdatedParameters(gameState, playerSelectedOption, characterResponse, conversationHistory)

            Log.d(TAG, "generateGameResponse 完了 (Thread: ${Thread.currentThread().name})")
            GameResponse(
                characterResponse,
                updatedParameters.first, // 新しい好感度
                updatedParameters.second // 新しい酔い具合レベル
            )
        } catch (e: Exception) {
            Log.e(TAG, "ゲーム応答生成中にエラー発生: ${e.message}", e)
            GameResponse(
                "ごめんなさい、ちょっと混乱しています。（エラー発生）",
                gameState.affinity,
                gameState.drunkenness
            )
        }
    }

    private fun generateCharacterResponse(
        gameState: GameState,
        playerSelectedOption: String,
        conversationHistory: List<ChatMessage>
    ): String {
        if (llmInference == null) {
            Log.w(TAG, "llmInference is null in generateCharacterResponse.")
            return "エラー：モデルが利用できません。"
        }
        val prompt = buildCharacterResponsePrompt(gameState, playerSelectedOption, conversationHistory)
        Log.d(TAG, "キャラクター応答プロンプト: $prompt")
        Log.d(TAG, "llmInference?.generateResponse(character) 呼び出し前 (Thread: ${Thread.currentThread().name})")
        val response = llmInference?.generateResponse(prompt)
        Log.d(TAG, "llmInference?.generateResponse(character) 呼び出し後 (Thread: ${Thread.currentThread().name})")
        return cleanResponse(response, ChatMessage.ROLE_MODEL)
    }

    private fun buildCharacterResponsePrompt(
        gameState: GameState,
        playerSelectedOption: String,
        history: List<ChatMessage>
    ): String {
        val conversationContext = if (history.isNotEmpty()) {
            "最近の会話:\n" + history.takeLast(4).joinToString("\n") { message ->
                val role = if (message.role == ChatMessage.ROLE_USER) "プレイヤー" else gameState.characterName
                "$role: ${message.content}"
            } + "\n"
        } else ""
//TODO シナリオを動的に管理する必要あり {動的閾値を導入}
        return """

<|system|>
あなたは「${gameState.characterName}」という名前のキャラクターです。
性格: ${gameState.characterPersona}

## 現在の状況:
- 場所: 研究室の飲み会で、周りは騒がしいですが、二人だけで話しています。
- ${gameState.currentSituation}
- プレイヤー(先輩)への好感度: ${gameState.affinity}/100 (${gameState.getAffinityDescription()})
- あなたの酔い具合: ${gameState.drunkenness}/100 (${gameState.getDrunkennessDescription()})

## ${gameState.characterName}の話し方のルール (重要):
- プレイヤーはあなたの先輩なので、あなたは基本的に丁寧語（敬語）で応答します。
- ただし、酔いが進むと（酔い具合が60以上など）、少しずつくだけた言葉遣い（タメ口）が混じることがあります。
- 特に酔いが浅い時や会話の初期では、丁寧な言葉遣いを強く意識してください。
- 飲み会自体を面倒くさがっている態度は維持してください。
- お酒の話題、仕事の愚痴、研究の話などが自然な会話の流れです。
- 時間が経つにつれて、少しずつ本音や素の感情が表に出ることがあります。
- 冷静で論理的な性格ですが、酔うと感情的になることもあります。
- 応答は常に50文字以内で、簡潔にしてください。
- 会話中に「点数」「ポイント」「好感度変化」などの具体的な数値や、それを示唆する表現は絶対に使用しないでください。

$conversationContext
プレイヤー(先輩): 「$playerSelectedOption」 (プレイヤーは後輩であるあなたに対して、常にくだけた口調（タメ口）で話します)

上記の指示に厳密に従い、${gameState.characterName}として自然に応答してください:
<|assistant|>
    """.trimIndent()
    }

    private fun calculateUpdatedParameters(
        gameState: GameState,
        playerSelectedOption: String,
        characterResponse: String,
        conversationHistory: List<ChatMessage>
    ): Pair<Int, Int> {
        if (llmInference == null) {
            Log.w(TAG, "llmInference is null in calculateUpdatedParameters.")
            return Pair(gameState.affinity, gameState.drunkenness)
        }
        val prompt = buildParameterUpdatePrompt(gameState, playerSelectedOption, characterResponse, conversationHistory)
        Log.d(TAG, "パラメータ更新プロンプト: $prompt")
        Log.d(TAG, "llmInference?.generateResponse(parameter) 呼び出し前 (Thread: ${Thread.currentThread().name})")
        val response = llmInference?.generateResponse(prompt)
        Log.d(TAG, "llmInference?.generateResponse(parameter) 呼び出し後 (Thread: ${Thread.currentThread().name})")
        return parseParameterUpdate(response, gameState.affinity, gameState.drunkenness)
    }

    private fun buildParameterUpdatePrompt(
        gameState: GameState,
        playerSelectedOption: String,
        characterResponse: String,
        history: List<ChatMessage>
    ): String {
        // このプロンプトは内部的なスコア計算用なので、スコア関連の表現があっても問題ありません。
        // ユーザーに直接表示されるテキストではありません。
        return """
<|system|>
「禁じられた愛」。指導教官と学生という越えてはならない一線が存在します。

## 現在の状況:
- 場所:研究室にいます。
- 現在の${gameState.characterName}のプレイヤー(先輩)への好感度: ${gameState.affinity}/100

## 直近のやり取り:
プレイヤー(先輩):「$playerSelectedOption」
${gameState.characterName}(後輩):「$characterResponse」

## 飲み会における厳格な採点基準:
好感度変化 (Affinity Change):
+10: 仕事の悩みへの共感、適度に知的な会話、彼女の意見や考えを尊重する姿勢 (非常に稀)
+5: 研究への真剣な関心、深い洞察、建設的な議論の提案 (標準的)
-5: 表面的な褒め言葉、酔った勢いでの不用意な発言、軽薄な態度 (多発)
-10: 外見だけを褒める、恋愛関係を迫るような発言、セクハラと受け取られかねない言動 (稀、絶対に避けるべき)

次の形式で、変化量を示す数字のみを返答してください (例: +2,+1 または -3,-5):
好感度変化
判定結果:
<|assistant|>
    """.trimIndent()
    }

    private fun parseParameterUpdate(response: String?, currentAffinity: Int, currentDrunkenness: Int): Pair<Int, Int> {
        if (response.isNullOrBlank()) {
            return Pair(currentAffinity, currentDrunkenness)
        }

        return try {
            val cleanResponse = response.replace(" ", "").replace("　", "") // 空白除去
            val parts = cleanResponse.split(",")

            if (parts.size >= 2) {
                val affinityChange = parts[0].replace("+", "").toIntOrNull() ?: 0
                val drunkennessChange = parts[1].replace("+", "").toIntOrNull() ?: 0

                val newAffinity = (currentAffinity + affinityChange).coerceIn(0, 100)
                val newDrunkenness = (currentDrunkenness + drunkennessChange).coerceIn(0, 100)

                Log.d(TAG, "パラメータ更新: 好感度 $currentAffinity -> $newAffinity, 酔い具合 $currentDrunkenness -> $newDrunkenness")
                Pair(newAffinity, newDrunkenness)
            } else {
                Pair(currentAffinity, currentDrunkenness)
            }
        } catch (e: Exception) {
            Log.e(TAG, "パラメータ解析エラー: ${e.message}")
            Pair(currentAffinity, currentDrunkenness)
        }
    }

    fun generatePlayerOptions(
        gameState: GameState,
        characterLastResponse: String,
        conversationHistory: List<ChatMessage> = emptyList()
    ): List<String> {
        if (!isModelReady || llmInference == null) {
            Log.w(TAG, "モデルが準備されていません。選択肢生成は不可能です。")
            return listOf("選択肢生成エラー1", "選択肢生成エラー2", "選択肢生成エラー3")
        }
        Log.d(TAG, "generatePlayerOptions 開始 (Thread: ${Thread.currentThread().name})")
        val prompt = buildPlayerOptionsPrompt(gameState, characterLastResponse, conversationHistory)
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
        history: List<ChatMessage>
    ): String {
        return """
<|system|>
あなたはプレイヤー(先輩)の立場です。研究室の飲み会で、後輩である「${gameState.characterName}」(${gameState.characterPersona}) との会話を続けるための、あなたの発言選択肢を3つ生成してください。

## 飲み会の状況:
- 場所: 研究室の飲み会、周りは騒がしいですが、二人だけで話しています。
- ${gameState.characterName}のあなた(先輩)への好感度: ${gameState.affinity}/100 (${gameState.getAffinityDescription()})
- ${gameState.characterName}の最後の発言: 「$characterLastResponse」

## プレイヤー(先輩)の話し方と選択肢設計のルール (重要):
- あなたは先輩なので、後輩である${gameState.characterName}に対して、常にくだけた口調（タメ口）で話します。
- 生成する3つの選択肢は全て、このくだけた口調（タメ口）に従ってください。
- **重要: 各選択肢は、直前の「${gameState.characterName}の最後の発言」に自然に応答する内容、または関連する内容にしてください。全く関係のない唐突な選択肢は避けてください。**
- 各選択肢は15文字から40文字程度で、飲み会らしい自然な会話として成り立つようにしてください。
- 会話中に「点数」「ポイント」「好感度変化」などの具体的な数値や、それを示唆する表現は絶対に含めないでください。

選択肢1 (優秀な選択肢): 「${gameState.characterName}の最後の発言」に対して、研究や仕事に関する建設的な話題、彼女の意見や考えを尊重し深掘りするような質問で応答する。 → ${gameState.characterName}の好感度が上がる可能性が高い。
選択肢2 (普通の選択肢): 「${gameState.characterName}の最後の発言」に対して、飲み会の定番の話題、当たり障りのない一般的な会話で応答する。 → ${gameState.characterName}の好感度にあまり影響しない。
選択肢3 (問題のある選択肢): 「${gameState.characterName}の最後の発言」に対して、酔った勢いでの軽薄な発言、表面的な外見褒め、強引な恋愛アプローチで応答する。 → ${gameState.characterName}の好感度が下がる可能性が高い。

## 話題例 (プレイヤーのタメ口) - 「${gameState.characterName}の最後の発言」が「最近、研究が忙しくて…」だった場合:
- 選択肢1例: 「そうなんだ、どんな研究してるの？詳しく聞かせてよ。」
- 選択肢2例: 「大変だね。まあ、今日は飲んで忘れようぜ。」
- 選択肢3例: 「忙しいとか言って、俺と話したくないだけじゃないの？」

## 応答形式 (厳守):
選択肢1: [くだけた口調の優秀な選択肢]
選択肢2: [くだけた口調の普通の選択肢]
選択肢3: [くだけた口調の問題のある選択肢]

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