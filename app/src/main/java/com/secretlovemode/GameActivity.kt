// GameActivity.kt
package com.secretlovemode

import android.content.Intent
import android.graphics.Typeface
import android.os.Bundle
import android.text.Spannable
import android.text.SpannableString
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class GameActivity : AppCompatActivity() {

    private var characterAi: CharacterAi? = null
    private var currentScenario: Scenario? = null // シナリオ変数を追加

    private val conversationHistory = mutableListOf<ChatMessage>()

    // UI 요소
    private lateinit var tvConversation: TextView
    private lateinit var questionButton1: Button
    private lateinit var questionButton2: Button
    private lateinit var questionButton3: Button
    private lateinit var tvCharacterName: TextView
    private lateinit var tvAffinity: TextView
    private lateinit var tvDrunkenness: TextView
    private lateinit var ivCharacter: ImageView
    private lateinit var scrollViewConversation: ScrollView

    // 게임 상태
    private var gameState = GameState()
    private var characterLastSaid = ""

    // ViewModel 인스턴스
    private lateinit var SlmViewModel: SlmViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d("GameActivity", "onCreate() 呼び出し")
        SlmViewModel = (application as MyApplication).SlmViewModel
        delegate.localNightMode = androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_NO

        setContentView(R.layout.activity_game)

        ScenarioManager.loadScenarios(this) // シナリオをロード

        initializeViews()
        forceColors()
        setupUI()

        SlmViewModel.isModelReady.observe(this) { isReady ->
            Log.d("GameActivity", "isModelReady 状態変化: $isReady")
            if (isReady) {
                characterAi = SlmViewModel.getCharacterAi()
                if (characterAi != null) {
                    val loadedModelPath = SlmViewModel.loadedModelPath.value ?: "不明なパス"
                    val modelFileName = try { File(loadedModelPath).name } catch (e: Exception) { "unknown.task" }
                    showToastWithAnimation("AIの準備ができました！(${modelFileName}を使用) ✨")
                    startGame()
                } else {
                    Log.e("GameActivity", "ViewModel.isModelReady は true ですが、getCharacterAi() が null を返しました。")
                    showToastWithAnimation("AIの準備に問題が発生しました。アプリを再起動してください。")
                    disableOptions()
                }
            } else {
                Log.w("GameActivity", "isModelReady is false. Disabling options.")
                disableOptions()
            }
        }

        SlmViewModel.isModelLoading.observe(this) { isLoading ->
            Log.d("GameActivity", "isModelLoading 状態変化: $isLoading")
        }

        SlmViewModel.loadingError.observe(this) { errorMessage ->
            if (errorMessage != null) {
                Log.e("GameActivity", "モデルロードエラー: $errorMessage")
                disableOptions()
            }
        }
        Log.d("GameActivity", "onCreate() 完了")
    }

    private fun initializeViews() {
        tvConversation = findViewById(R.id.tvConversation)
        questionButton1 = findViewById(R.id.questionButton1)
        questionButton2 = findViewById(R.id.questionButton2)
        questionButton3 = findViewById(R.id.questionButton3)
        tvCharacterName = findViewById(R.id.tvCharacterName)
        tvAffinity = findViewById(R.id.tvAffinity)
        ivCharacter = findViewById(R.id.ivCharacter)
        scrollViewConversation = findViewById(R.id.scrollViewConversation)
    }

    private fun forceColors() {
        window.statusBarColor = ContextCompat.getColor(this, R.color.primary_pink)
        window.navigationBarColor = ContextCompat.getColor(this, R.color.background_light)
        findViewById<View>(android.R.id.content).setBackgroundColor(
            ContextCompat.getColor(this, R.color.background_light)
        )
        tvCharacterName.setTextColor(ContextCompat.getColor(this, R.color.text_accent))
        tvConversation.setTextColor(ContextCompat.getColor(this, R.color.text_primary))
        tvAffinity.setTextColor(ContextCompat.getColor(this, R.color.affinity_color))
        tvDrunkenness.setTextColor(ContextCompat.getColor(this, R.color.drunkenness_color))
        listOf(questionButton1, questionButton2, questionButton3).forEach { button ->
            button.backgroundTintList = ContextCompat.getColorStateList(this, R.color.button_primary)
            button.setTextColor(ContextCompat.getColor(this, R.color.white))
        }
    }

    private fun setupUI() {
        tvCharacterName.text = gameState.characterName
        updateStatusDisplay()
        questionButton1.setOnClickListener { onPlayerOptionSelected(questionButton1.text.toString()) }
        questionButton2.setOnClickListener { onPlayerOptionSelected(questionButton2.text.toString()) }
        questionButton3.setOnClickListener { onPlayerOptionSelected(questionButton3.text.toString()) }
    }

    private fun startGame() {
        Log.d("GameActivity", "startGame() 呼び出し")
        if (characterAi == null || !characterAi!!.isModelReady) {
            Log.w("GameActivity", "startGame() 呼び出し時、モデルが準備できていません。")
            return
        }

        // 1. 初期シナリオをロードして設定
        val initialScenarioId = ScenarioManager.checkAndTriggerNextScenario(gameState)
        currentScenario = ScenarioManager.getScenario(initialScenarioId)
        gameState = gameState.copy(currentScenarioId = initialScenarioId)

        if (currentScenario == null) {
            Log.e("GameActivity", "初期シナリオのロードに失敗しました。")
            showToastWithAnimation("ゲームの開始に失敗しました。")
            disableOptions()
            return
        }

        // 2. 初期状況を説明
        appendMessageToConversationWithAnimation("システム", "[状況]\n${currentScenario!!.setting}")

        // 3. シナリオに合った最初のメッセージをAIに生成させる
        disableOptions()
        showLoadingMessage("${gameState.characterName}が考えています... 💭")
        lifecycleScope.launch(Dispatchers.IO) {
            val initialResponse = characterAi!!.generateGameResponse(
                gameState = gameState,
                playerSelectedOption = "(あなたは静かに彼女の前に座った)", // AIに応答を促すためのダミー入力
                conversationHistory = conversationHistory,
                scenario = currentScenario!!
            )

            withContext(Dispatchers.Main) {
                removeLoadingMessage()
                characterLastSaid = initialResponse.characterResponse
                appendMessageToConversationWithAnimation(gameState.characterName, characterLastSaid)
                conversationHistory.add(ChatMessage(ChatMessage.ROLE_MODEL, characterLastSaid))

                // 4. 最初の選択肢を提示
                presentPlayerChoicesFor(characterLastSaid, currentScenario!!)
                Log.d("GameActivity", "startGame() 完了")
            }
        }
    }

    private fun showAffinityChange(change: Int) {
        val changeText = when {
            change > 0 -> "+$change"
            change < 0 -> "$change"
            else -> "±0"
        }
        if (change == 0) return

        val colorResId = if (change > 0) android.R.color.holo_green_light else android.R.color.holo_red_light
        val affinityChangeMessage = "好感度 $changeText 💫\n\n"

        lifecycleScope.launch {
            var displayText = tvConversation.text.toString()
            for (char in affinityChangeMessage) {
                displayText += char
                tvConversation.text = displayText
                val spannable = SpannableString(tvConversation.text)
                val startIndex = displayText.lastIndexOf("好感度")
                if (startIndex >= 0) {
                    val endIndex = displayText.length - 2
                    val colorToUse = ContextCompat.getColor(this@GameActivity, colorResId)
                    spannable.setSpan(ForegroundColorSpan(colorToUse), startIndex, endIndex, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                    spannable.setSpan(StyleSpan(Typeface.BOLD), startIndex, endIndex, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                    tvConversation.text = spannable
                }
                scrollToBottom()
                delay(50)
            }
            delay(2000)
            fadeOutAffinityChange(affinityChangeMessage)
        }
    }

    private fun fadeOutAffinityChange(affinityChangeMessage: String) {
        lifecycleScope.launch {
            val currentText = tvConversation.text.toString()
            if (currentText.endsWith(affinityChangeMessage)) {
                val newText = currentText.removeSuffix(affinityChangeMessage)
                tvConversation.animate().alpha(0.5f).setDuration(300).withEndAction {
                    tvConversation.text = newText
                    tvConversation.setTextColor(ContextCompat.getColor(this@GameActivity, R.color.text_primary))
                    tvConversation.animate().alpha(1.0f).setDuration(300).start()
                }.start()
            }
        }
    }

    private fun appendMessageToConversationWithAnimation(speaker: String, message: String) {
        val fullMessage = "$speaker: $message\n\n"
        lifecycleScope.launch {
            val currentText = tvConversation.text.toString()
            var displayText = currentText
            for (char in fullMessage) {
                displayText += char
                tvConversation.text = displayText
                tvConversation.setTextColor(ContextCompat.getColor(this@GameActivity, R.color.text_primary))
                scrollToBottom()
                delay(30)
            }
        }
    }

    private fun scrollToBottom() {
        scrollViewConversation.post { scrollViewConversation.fullScroll(View.FOCUS_DOWN) }
    }

    private fun updateStatusDisplay() {
        animateStatusUpdate(tvAffinity, "好感度: ${gameState.affinity} (${gameState.getAffinityDescription()})")
        animateStatusUpdate(tvDrunkenness, "酔い具合: ${gameState.drunkenness} (${gameState.getDrunkennessDescription()})")
        val affinityColor = when {
            gameState.affinity <= 0 -> android.R.color.holo_red_dark
            gameState.affinity < 30 -> android.R.color.holo_red_light
            gameState.affinity < 60 -> R.color.text_secondary
            else -> R.color.affinity_color
        }
        tvAffinity.setTextColor(ContextCompat.getColor(this, affinityColor))
        tvDrunkenness.setTextColor(ContextCompat.getColor(this, R.color.drunkenness_color))
        Log.d("GameActivity", "Status Updated - Affinity: ${gameState.affinity}, Drunkenness: ${gameState.drunkenness}")
    }

    private fun animateStatusUpdate(textView: TextView, newText: String) {
        textView.animate().scaleX(1.1f).scaleY(1.1f).setDuration(200).withEndAction {
            textView.text = newText
            textView.animate().scaleX(1.0f).scaleY(1.0f).setDuration(200).start()
        }.start()
    }

    private fun presentPlayerChoicesFor(characterResponse: String, scenario: Scenario) {
        Log.d("GameActivity", "presentPlayerChoicesFor() 呼び出し")
        if (characterAi == null || !characterAi!!.isModelReady) {
            Log.w("GameActivity", "モデル準備未完了。選択肢生成処理不可。")
            updateButtonsWithOptions(listOf("モデル準備中...", "お待ちください...", "..."))
            disableOptions()
            return
        }

        disableOptions()
        showLoadingMessage("選択肢を生成中です... ")
        lifecycleScope.launch(Dispatchers.IO) {
            Log.d("GameActivity", "presentPlayerChoicesFor Coroutine 開始 (Thread: ${Thread.currentThread().name})")
            val options = characterAi!!.generatePlayerOptions(
                gameState = gameState,
                characterLastResponse = characterResponse,
                conversationHistory = conversationHistory.takeLast(4),
                scenario = scenario
            )

            Log.d("GameActivity", "characterAi.generatePlayerOptions 完了")
            withContext(Dispatchers.Main) {
                Log.d("GameActivity", "presentPlayerChoicesFor Coroutine Main Context (Thread: ${Thread.currentThread().name})")
                removeLoadingMessage()
                if (options.size == 3 && !options.any { it.contains("選択肢生成エラー") }) {
                    updateButtonsWithOptions(options)
                    tvConversation.append("どうしますか？ \n\n")
                    tvConversation.setTextColor(ContextCompat.getColor(this@GameActivity, R.color.text_primary))
                    enableOptions()
                } else {
                    tvConversation.append("選択肢の生成に失敗しました。デフォルトの選択肢を表示します。\n\n")
                    tvConversation.setTextColor(ContextCompat.getColor(this@GameActivity, R.color.text_primary))
                    updateButtonsWithOptions(listOf("はい", "いいえ", "分からない"))
                    enableOptions()
                }
                scrollToBottom()
                Log.d("GameActivity", "presentPlayerChoicesFor Coroutine 完了")
            }
        }
    }

    private fun updateButtonsWithOptions(options: List<String>) {
        val buttons = listOf(questionButton1, questionButton2, questionButton3)
        options.forEachIndexed { index, option ->
            if (index < buttons.size) {
                buttons[index].text = option
                ButtonUtils.adjustButtonForText(buttons[index], option)
            }
        }
        lifecycleScope.launch {
            delay(100)
            ButtonUtils.balanceButtonSizes(buttons)
        }
    }

    private fun showLoadingMessage(message: String) {
        tvConversation.append("$message\n\n")
        tvConversation.setTextColor(ContextCompat.getColor(this, R.color.text_primary))
        scrollToBottom()
    }

    private fun removeLoadingMessage() {
        val currentText = tvConversation.text.toString()
        val lines = currentText.lines().toMutableList()
        val loadingLineIndex = lines.indexOfLast { it.contains("生成中") || it.contains("考えています") }
        if (loadingLineIndex != -1) {
            if (loadingLineIndex + 1 < lines.size) lines.removeAt(loadingLineIndex + 1)
            lines.removeAt(loadingLineIndex)
            tvConversation.text = lines.joinToString("\n") + if (lines.isNotEmpty() && lines.last().isNotEmpty()) "\n" else ""
            tvConversation.setTextColor(ContextCompat.getColor(this, R.color.text_primary))
        }
    }

    private fun onPlayerOptionSelected(selectedOptionText: String) {
        val scenarioForResponse = currentScenario
        if (characterAi == null || !characterAi!!.isModelReady || scenarioForResponse == null) {
            Log.w("GameActivity", "モデル準備未完了またはシナリオがNULLです。選択処理不可。")
            showToastWithAnimation("AIがまだ準備できていません。")
            return
        }
        Log.d("GameActivity", "onPlayerOptionSelected() 呼び出し: $selectedOptionText")

        appendMessageToConversationWithAnimation("あなた", selectedOptionText)
        conversationHistory.add(ChatMessage(ChatMessage.ROLE_USER, selectedOptionText))
        disableOptions()
        showLoadingMessage("${gameState.characterName}が考えています... 💭")

        lifecycleScope.launch(Dispatchers.IO) {
            Log.d("GameActivity", "onPlayerOptionSelected Coroutine 開始 (Thread: ${Thread.currentThread().name})")
            val gameResponse = characterAi!!.generateGameResponse(
                gameState = gameState,
                playerSelectedOption = selectedOptionText,
                conversationHistory = conversationHistory,
                scenario = scenarioForResponse
            )

            Log.d("GameActivity", "characterAi.generateGameResponse 完了")

            withContext(Dispatchers.Main) {
                Log.d("GameActivity", "onPlayerOptionSelected Coroutine Main Context (Thread: ${Thread.currentThread().name})")
                removeLoadingMessage()

                val previousAffinity = gameState.affinity
                val previousScenarioId = gameState.currentScenarioId

                gameState = gameState.copy(
                    affinity = gameResponse.newAffinity,
                    drunkenness = gameResponse.newDrunkenness,
                    conversationCount = gameState.conversationCount + 1
                )

                val newScenarioId = ScenarioManager.checkAndTriggerNextScenario(gameState)
                if (newScenarioId != previousScenarioId) {
                    currentScenario = if (newScenarioId == "DEFAULT") {
                        ScenarioManager.getDefaultScenario()
                    } else {
                        ScenarioManager.getScenario(newScenarioId)
                    }
                    gameState = gameState.copy(currentScenarioId = newScenarioId)
                    currentScenario?.let {
                        appendMessageToConversationWithAnimation("システム", "[状況変化]\n${it.setting}")
                    }
                }

                val affinityChange = gameResponse.newAffinity - previousAffinity
                if (affinityChange != 0) {
                    showAffinityChange(affinityChange)
                }

                updateStatusDisplay()

                if (gameState.affinity <= 0) {
                    showGameOverDialog()
                    return@withContext
                }

                characterLastSaid = gameResponse.characterResponse
                appendMessageToConversationWithAnimation(gameState.characterName, gameResponse.characterResponse)
                conversationHistory.add(ChatMessage(ChatMessage.ROLE_MODEL, gameResponse.characterResponse))

                val scenarioForChoices = currentScenario
                if (gameState.conversationCount < 15 && scenarioForChoices != null) {
                    presentPlayerChoicesFor(gameResponse.characterResponse, scenarioForChoices)
                } else {
                    appendMessageToConversationWithAnimation("システム", "会話は終了しました。お疲れ様でした！")
                    disableOptions()
                }
                Log.d("GameActivity", "onPlayerOptionSelected Coroutine 完了")
            }
        }
    }

    private fun showGameOverDialog() {
        disableOptions()
        AlertDialog.Builder(this)
            .setTitle("ゲームオーバー")
            .setMessage("${gameState.characterName}との関係は終わってしまいました...")
            .setCancelable(false)
            .setPositiveButton("最初から") { dialog, _ ->
                val intent = Intent(this@GameActivity, MainActivity::class.java)
                intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK)
                startActivity(intent)
                finish()
                dialog.dismiss()
            }
            .show()
    }

    private fun enableOptions() {
        val buttons = listOf(questionButton1, questionButton2, questionButton3)
        buttons.forEach { button ->
            button.isEnabled = true
            button.backgroundTintList = ContextCompat.getColorStateList(this, R.color.button_primary)
            button.setTextColor(ContextCompat.getColor(this, R.color.white))
            button.alpha = 1.0f
            button.animate().scaleX(1.0f).scaleY(1.0f).setDuration(300).start()
        }
    }

    private fun disableOptions() {
        val buttons = listOf(questionButton1, questionButton2, questionButton3)
        buttons.forEach { button ->
            button.isEnabled = false
            button.backgroundTintList = ContextCompat.getColorStateList(this, R.color.button_background_disabled_light)
            button.setTextColor(ContextCompat.getColor(this, R.color.button_text_light))
            button.alpha = 1.0f
            button.animate().scaleX(0.95f).scaleY(0.95f).setDuration(200).start()
        }
    }

    private fun showToastWithAnimation(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    override fun onDestroy() {
        Log.d("GameActivity", "onDestroy() 呼び出し")
        super.onDestroy()
        Log.d("GameActivity", "onDestroy() 完了")
    }
}