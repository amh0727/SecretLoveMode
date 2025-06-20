// GameActivity.kt
package com.SecretLoveMode

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
    private lateinit var llmViewModel: LlmViewModel // 이렇게 변경

    // MainActivity로부터 전달받을 모델 경로 (더 이상 필요 없지만, 혹시 몰라 남겨둠)
    // private lateinit var modelPathFromIntent: String


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d("GameActivity", "onCreate() 呼び出し")
        llmViewModel = (application as MyApplication).llmViewModel

        // 다크모드 강제 비활성화
        delegate.localNightMode = androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_NO

        setContentView(R.layout.activity_game)

        initializeViews()
        forceColors() // 초기 색상 설정
        setupUI()

        // ViewModel의 모델 준비 상태를 관찰
        llmViewModel.isModelReady.observe(this) { isReady ->
            Log.d("GameActivity", "isModelReady 状態変化: $isReady")
            if (isReady) {
                characterAi = llmViewModel.getCharacterAi()
                if (characterAi != null) {
                    val loadedModelPath = llmViewModel.loadedModelPath.value ?: "不明なパス"
                    val modelFileName = try { File(loadedModelPath).name } catch (e: Exception) { "unknown.task" }
                    showToastWithAnimation("AIの準備ができました！(${modelFileName}を使用) ✨")
                    startGame()
                } else {
                    Log.e("GameActivity", "ViewModel.isModelReady は true ですが、getCharacterAi() が null を返しました。")
                    showToastWithAnimation("AIの準備に問題が発生しました。アプリを再起動してください。")
                    disableOptions()
                }
            } else {
                // 모델이 준비되지 않았거나 로딩 실패 상태
                Log.w("GameActivity", "isModelReady is false. Disabling options.")
                disableOptions()
            }
        }

        // ViewModel의 로딩 상태를 관찰 (선택 사항, MainActivity에서 주로 사용)
        llmViewModel.isModelLoading.observe(this) { isLoading ->
            // GameActivity에서는 로딩 중 UI를 별도로 표시하지 않아도 됨 (MainActivity에서 이미 표시)
            // 필요하다면 여기에 로딩 스피너 등을 추가할 수 있음
            Log.d("GameActivity", "isModelLoading 状態変化: $isLoading")
        }

        llmViewModel.loadingError.observe(this) { errorMessage ->
            // 로딩 실패 시 MainActivity에서 Toast를 띄우므로 여기서는 추가 처리가 필요 없을 수 있음
            if (errorMessage != null) {
                Log.e("GameActivity", "モデルロードエラー: $errorMessage")
                // 오류 발생 시 게임 진행 불가 상태로 만듦
                disableOptions()
            }
        }


        // initializeAI() 호출 삭제 - ViewModel이 모델 로딩을 관리함
        // Log.d("GameActivity", "initializeAI() 呼び出し前")
        // initializeAI() // modelPathFromIntent 使用
        // Log.d("GameActivity", "initializeAI() 呼び出し後")

        Log.d("GameActivity", "onCreate() 完了")
    }

    private fun initializeViews() {
        tvConversation = findViewById(R.id.tvConversation)
        questionButton1 = findViewById(R.id.questionButton1)
        questionButton2 = findViewById(R.id.questionButton2)
        questionButton3 = findViewById(R.id.questionButton3)
        tvCharacterName = findViewById(R.id.tvCharacterName)
        tvAffinity = findViewById(R.id.tvAffinity)
        tvDrunkenness = findViewById(R.id.tvDrunkenness)
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
        // forceColors에서는 초기 상태의 색상만 설정하고, 실제 호감도에 따른 색상 변경은 updateStatusDisplay에서 처리
        tvAffinity.setTextColor(ContextCompat.getColor(this, R.color.affinity_color)) // 기본 색상
        tvDrunkenness.setTextColor(ContextCompat.getColor(this, R.color.drunkenness_color))

        // 버튼 초기 상태 (활성화된 모습으로)
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

    // initializeAI 함수 삭제 - ViewModel이 모델 로딩을 담당
    // private fun initializeAI() { ... }


    private fun startGame() {
        Log.d("GameActivity", "startGame() 呼び出し")
        // 모델이 준비되지 않았다면 시작하지 않음 (ViewModel 관찰자가 처리)
        if (characterAi == null || !characterAi!!.isModelReady) {
            Log.w("GameActivity", "startGame() 呼び出し時、モデルが準備できていません。")
            return
        }

        val initialMessages = listOf(
            "はぁ...また飲み会ですか。正直面倒なんですけど。",
            "何ですか、その視線。何か用事でもあるんですか？",
            "お疲れ様です。でも別に話したいことないんですけど。",
            "飲み会って時間の無駄だと思いませんか？",
            "先輩、暇そうですね。私は忙しいんですけど。"
        )

        val initialMessage = initialMessages.random()
        characterLastSaid = initialMessage
        appendMessageToConversationWithAnimation(gameState.characterName, initialMessage)
        conversationHistory.add(ChatMessage(ChatMessage.ROLE_MODEL, initialMessage))
        presentPlayerChoicesFor(initialMessage)
        Log.d("GameActivity", "startGame() 完了")
    }

    // 호감도 변화량을 시각적으로 표시
    private fun showAffinityChange(change: Int) {
        val changeText = when {
            change > 0 -> "+$change"
            change < 0 -> "$change"
            else -> "±0" // 호감도 변화가 없을 때는 표시하지 않거나 다른 메시지
        }
        if (change == 0) return // 변화가 없으면 표시하지 않음

        val colorResId = when {
            change > 0 -> android.R.color.holo_green_light
            change < 0 -> android.R.color.holo_red_light
            else -> R.color.text_secondary // 이 경우는 위에서 return됨
        }

        val affinityChangeMessage = "好感度 $changeText 💫\n\n"

        lifecycleScope.launch {
            var displayText = tvConversation.text.toString()
            for (char in affinityChangeMessage) {
                displayText += char
                tvConversation.text = displayText

                val spannable = SpannableString(tvConversation.text)
                val startIndex = displayText.lastIndexOf("好感度") // 마지막에 추가된 메시지를 대상으로
                if (startIndex >= 0) {
                    val endIndex = displayText.length -2 // 끝의 \n\n 제외
                    val colorToUse = ContextCompat.getColor(this@GameActivity, colorResId)
                    spannable.setSpan(
                        ForegroundColorSpan(colorToUse),
                        startIndex,
                        endIndex,
                        Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                    )
                    spannable.setSpan(
                        StyleSpan(Typeface.BOLD),
                        startIndex,
                        endIndex,
                        Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                    )
                    tvConversation.text = spannable
                }
                scrollToBottom()
                delay(50)
            }
            delay(2000) // 표시 시간 단축
            fadeOutAffinityChange(affinityChangeMessage)
        }
    }

    private fun fadeOutAffinityChange(affinityChangeMessage: String) {
        lifecycleScope.launch {
            val currentText = tvConversation.text.toString()
            if (currentText.endsWith(affinityChangeMessage)) { // 정확히 해당 메시지로 끝나는 경우만 제거
                val newText = currentText.removeSuffix(affinityChangeMessage)
                tvConversation.animate()
                    .alpha(0.5f)
                    .setDuration(300)
                    .withEndAction {
                        tvConversation.text = newText
                        tvConversation.setTextColor(ContextCompat.getColor(this@GameActivity, R.color.text_primary))
                        tvConversation.animate()
                            .alpha(1.0f)
                            .setDuration(300)
                            .start()
                    }
                    .start()
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
        scrollViewConversation.post {
            scrollViewConversation.fullScroll(View.FOCUS_DOWN)
        }
    }

    private fun updateStatusDisplay() {
        animateStatusUpdate(tvAffinity, "好感度: ${gameState.affinity} (${gameState.getAffinityDescription()})")
        animateStatusUpdate(tvDrunkenness, "酔い具合: ${gameState.drunkenness} (${gameState.getDrunkennessDescription()})")

        val affinityColor = when {
            gameState.affinity <= 0 -> android.R.color.holo_red_dark // 게임 오버 직전 강조
            gameState.affinity < 30 -> android.R.color.holo_red_light
            gameState.affinity < 60 -> R.color.text_secondary
            else -> R.color.affinity_color // 기본 핑크색 (높은 호감도)
        }

        tvAffinity.setTextColor(ContextCompat.getColor(this, affinityColor))
        tvDrunkenness.setTextColor(ContextCompat.getColor(this, R.color.drunkenness_color))
        Log.d("GameActivity", "Status Updated - Affinity: ${gameState.affinity}, Drunkenness: ${gameState.drunkenness}")
    }

    private fun animateStatusUpdate(textView: TextView, newText: String) {
        textView.animate()
            .scaleX(1.1f).scaleY(1.1f).setDuration(200)
            .withEndAction {
                textView.text = newText
                textView.animate().scaleX(1.0f).scaleY(1.0f).setDuration(200).start()
            }.start()
    }

    private fun presentPlayerChoicesFor(characterResponse: String) {
        Log.d("GameActivity", "presentPlayerChoicesFor() 呼び出し")
        // 모델이 준비되지 않았다면 선택지 생성 시도 안 함
        if (characterAi == null || !characterAi!!.isModelReady) {
            Log.w("GameActivity", "モデル準備未完了。選択肢生成処理不可。")
            updateButtonsWithOptions(listOf("モデル準備中...", "お待ちください...", "...")) // 로딩 중 메시지 표시
            disableOptions() // 버튼 비활성화
            return
        }

        disableOptions()
        showLoadingMessage("選択肢を生成中です... ")
        lifecycleScope.launch(Dispatchers.IO) {
            Log.d("GameActivity", "presentPlayerChoicesFor Coroutine 開始 (Thread: ${Thread.currentThread().name})")
            // ViewModel에서 가져온 characterAi 사용
            val options = characterAi?.generatePlayerOptions(
                gameState = gameState,
                characterLastResponse = characterResponse,
                conversationHistory = conversationHistory.takeLast(4) // 最近の4つの会話のみを渡す
            ) ?: listOf("選択肢生成エラー1", "選択肢生成エラー2", "選択肢生成エラー3") // null일 경우 기본값 반환
            Log.d("GameActivity", "characterAi.generatePlayerOptions 完了")
            withContext(Dispatchers.Main) {
                Log.d("GameActivity", "presentPlayerChoicesFor Coroutine Main Context (Thread: ${Thread.currentThread().name})")
                removeLoadingMessage()
                if (options.size == 3 && !options.any { it.contains("選択肢生成エラー") }) { // 에러 메시지 포함 여부 체크
                    updateButtonsWithOptions(options)
                    tvConversation.append("どうしますか？ \n\n")
                    tvConversation.setTextColor(ContextCompat.getColor(this@GameActivity, R.color.text_primary))
                    enableOptions()
                } else {
                    tvConversation.append("選択肢の生成に失敗しました。デフォルトの選択肢を表示します。\n\n")
                    tvConversation.setTextColor(ContextCompat.getColor(this@GameActivity, R.color.text_primary))
                    updateButtonsWithOptions(listOf("はい", "いいえ", "分からない")) // デフォルト選択肢
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
            delay(100) // adjustButtonForTextが適用される時間を確保
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
        // "生成中" メッセージが含まれる行とその次の空行を削除
        val loadingLineIndex = lines.indexOfLast { it.contains("生成中") }
        if (loadingLineIndex != -1 && loadingLineIndex + 1 < lines.size) {
            lines.removeAt(loadingLineIndex + 1) // 空行を削除
            lines.removeAt(loadingLineIndex)     // ローディングメッセージ行を削除
            tvConversation.text = lines.joinToString("\n") + if (lines.isNotEmpty() && lines.last().isNotEmpty()) "\n" else "" // 最後の行が空でなければ改行を追加
            tvConversation.setTextColor(ContextCompat.getColor(this, R.color.text_primary))
        }
    }

    private fun onPlayerOptionSelected(selectedOptionText: String) {
        // ViewModel에서 가져온 characterAi 사용
        if (characterAi == null || !characterAi!!.isModelReady) {
            Log.w("GameActivity", "モデル準備未完了。選択処理不可。")
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
            // ViewModel에서 가져온 characterAi 사용
            val gameResponse = characterAi?.generateGameResponse(
                gameState = gameState,
                playerSelectedOption = selectedOptionText,
                conversationHistory = conversationHistory // 全体の会話履歴を渡す
            ) ?: GameResponse("エラーが発生しました。", gameState.affinity, gameState.drunkenness) // null일 경우 기본 응답

            Log.d("GameActivity", "characterAi.generateGameResponse 完了")

            withContext(Dispatchers.Main) {
                Log.d("GameActivity", "onPlayerOptionSelected Coroutine Main Context (Thread: ${Thread.currentThread().name})")
                removeLoadingMessage()

                val previousAffinity = gameState.affinity
                gameState = gameState.copy(
                    affinity = gameResponse.newAffinity,
                    drunkenness = gameResponse.newDrunkenness,
                    conversationCount = gameState.conversationCount + 1
                )

                val affinityChange = gameResponse.newAffinity - previousAffinity
                if (affinityChange != 0) { // 好感度変化がある場合のみ表示
                    showAffinityChange(affinityChange)
                }

                updateStatusDisplay() // 好感度変化表示後にステータスを更新

                // ゲームオーバー条件確認
                if (gameState.affinity <= 0) {
                    showGameOverDialog()
                    return@withContext // ゲームオーバー時は以降の処理を中断
                }

                characterLastSaid = gameResponse.characterResponse
                appendMessageToConversationWithAnimation(gameState.characterName, gameResponse.characterResponse)
                conversationHistory.add(ChatMessage(ChatMessage.ROLE_MODEL, gameResponse.characterResponse))

                if (gameState.conversationCount < 15) { // 会話回数制限 (例: 15回)
                    presentPlayerChoicesFor(gameResponse.characterResponse)
                } else {
                    appendMessageToConversationWithAnimation("システム", "会話は終了しました。お疲れ様でした！")
                    disableOptions()
                    // 여기에 엔딩 처리 로직 추가 가능
                }
                Log.d("GameActivity", "onPlayerOptionSelected Coroutine 完了")
            }
        }
    }

    private fun showGameOverDialog() {
        disableOptions() // ゲームオーバー時にボタンを無効化
        AlertDialog.Builder(this)
            .setTitle("ゲームオーバー")
            .setMessage("${gameState.characterName}との関係は終わってしまいました...")
            .setCancelable(false) // 外側タッチでの閉じを防止
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
            button.animate()
                .scaleX(1.0f)
                .scaleY(1.0f)
                .setDuration(300)
                .start()
        }
    }

    private fun disableOptions() {
        val buttons = listOf(questionButton1, questionButton2, questionButton3)
        buttons.forEach { button ->
            button.isEnabled = false
            // 비활성화 시 배경색 및 텍스트 색상 명시적 설정
            button.backgroundTintList = ContextCompat.getColorStateList(this, R.color.button_background_disabled_light)
            button.setTextColor(ContextCompat.getColor(this, R.color.button_text_light)) // colors.xml에 정의된 색상 사용
            button.alpha = 1.0f // 색상으로 구분하므로 알파는 1.0유지
            button.animate()
                .scaleX(0.95f)
                .scaleY(0.95f)
                .setDuration(200)
                .start()
        }
    }

    private fun showToastWithAnimation(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
        // 필요하다면 여기에 Toast 애니메이션 추가
    }

    // onDestroy에서 CharacterAi.close() 호출 삭제 - ViewModel의 onCleared()에서 처리
    override fun onDestroy() {
        Log.d("GameActivity", "onDestroy() 呼び出し")
        super.onDestroy()
        // CharacterAi 인스턴스는 ViewModel이 관리하므로 여기서 close() 호출하지 않음
        // characterAi?.close() // 이 줄 삭제
        // characterAi = null // 이 줄 삭제
        Log.d("GameActivity", "onDestroy() 完了")
    }
}