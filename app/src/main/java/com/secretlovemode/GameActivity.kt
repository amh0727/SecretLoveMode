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

class GameActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "GameActivity"
        private const val MAX_CONVERSATION_COUNT = 5 // 최대 대화 횟수
    }

    private var characterAi: CharacterAi? = null
    private var currentScenario: Scenario? = null
    private val conversationHistory = mutableListOf<ChatMessage>()

    // UI 요소
    private lateinit var tvConversation: TextView
    private lateinit var questionButton1: Button
    private lateinit var questionButton2: Button
    private lateinit var questionButton3: Button
    private lateinit var btnConfess: Button
    private lateinit var tvCharacterName: TextView
    private lateinit var tvAffinity: TextView
    private lateinit var ivCharacter: ImageView
    private lateinit var scrollViewConversation: ScrollView
    private lateinit var particleView: ParticleView

    private lateinit var gameState: GameState
    private lateinit var slmViewModel: SlmViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d(TAG, "onCreate() 호출")
        slmViewModel = (application as MyApplication).slmViewModel
        delegate.localNightMode = androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_NO
        setContentView(R.layout.activity_game)

        val character = intent.getSerializableExtra("SELECTED_CHARACTER") as? Character
        if (character == null) {
            Toast.makeText(this, "キャラクター情報を呼び出されませんでした。", Toast.LENGTH_LONG).show()
            finish()
            return
        }

        gameState = GameState(
            characterName = character.characterName,
            characterPersona = character.characterPersona
        )
        ScenarioManager.loadScenarios(this, character.scenarioFileName)

        initializeViews()
        setupUI()

        characterAi = slmViewModel.getCharacterAi()
        if (characterAi == null) {
            Toast.makeText(this, "モデルがまだ呼び出されていません。最初画面に戻ります。", Toast.LENGTH_LONG).show()
            finish()
            return
        }

        startGame()
        Log.d(TAG, "onCreate() 완료")
    }

    private fun initializeViews() {
        tvConversation = findViewById(R.id.tvConversation)
        questionButton1 = findViewById(R.id.questionButton1)
        questionButton2 = findViewById(R.id.questionButton2)
        questionButton3 = findViewById(R.id.questionButton3)
        btnConfess = findViewById(R.id.btnConfess)
        tvCharacterName = findViewById(R.id.tvCharacterName)
        tvAffinity = findViewById(R.id.tvAffinity)
        ivCharacter = findViewById(R.id.ivCharacter)
        scrollViewConversation = findViewById(R.id.scrollViewConversation)
        particleView = findViewById(R.id.particleView)
    }

    private fun setupUI() {
        tvCharacterName.text = gameState.characterName
        updateStatusDisplay()
        questionButton1.setOnClickListener { onPlayerOptionSelected(questionButton1.text.toString()) }
        questionButton2.setOnClickListener { onPlayerOptionSelected(questionButton2.text.toString()) }
        questionButton3.setOnClickListener { onPlayerOptionSelected(questionButton3.text.toString()) }
        btnConfess.setOnClickListener { onConfessButtonClicked() }
    }

    private fun startGame() {
        Log.d(TAG, "startGame() 호출")
        if (characterAi == null || !characterAi!!.isModelReady) {
            Log.w(TAG, "startGame() 호출 시, 모델이 준비되지 않았습니다.")
            return
        }

        val initialScenarioId = ScenarioManager.checkAndTriggerNextScenario(gameState)
        currentScenario = ScenarioManager.getScenario(initialScenarioId)
        if (currentScenario == null) {
            Log.e(TAG, "シナリオ更新に失敗しました")
            showToastWithAnimation("ゲーム開始に失敗しました")
            disableAllOptions()
            return
        }
        gameState = gameState.copy(currentScenarioId = initialScenarioId)

        appendSystemMessage("[状況]\n${currentScenario!!.setting}")
        handlePlayerAction("(あなたは静かに彼女の前に座っていました)", isInitialAction = true)
    }

    private fun onPlayerOptionSelected(selectedOptionText: String) {
        if (characterAi == null || !characterAi!!.isModelReady || currentScenario == null) {
            showToastWithAnimation("AIがまだ準備できていません")
            return
        }
        Log.d(TAG, "選択: $selectedOptionText")
        appendPlayerMessage(selectedOptionText)
        handlePlayerAction(selectedOptionText)
    }

    /**
     * 모든 AI 관련 작업을 이 함수 안에서 순차적으로 처리하도록 통합합니다.
     */
    private fun handlePlayerAction(playerAction: String, isInitialAction: Boolean = false) {
        val scenarioForResponse = currentScenario ?: return
        disableAllOptions()
        showLoadingMessage("${gameState.characterName}が考えています... ")
        conversationHistory.add(ChatMessage(ChatMessage.ROLE_USER, playerAction))

        lifecycleScope.launch(Dispatchers.IO) { // 백그라운드 스레드에서 AI 작업 수행
            // 1. AI 대사 스트리밍 및 전체 응답 받기
            val fullResponse = displayResponseWithStreamingEffect(playerAction, scenarioForResponse)
            conversationHistory.add(ChatMessage(ChatMessage.ROLE_MODEL, fullResponse))

            // 2. 호감도 계산
            val newAffinity = characterAi!!.calculateAffinity(
                gameState = gameState,
                playerSelectedOption = playerAction,
                fullCharacterResponse = fullResponse,
                conversationHistory = conversationHistory,
                scenario = scenarioForResponse
            )

            // 3. 게임 상태 업데이트 (UI 스레드에서)
            withContext(Dispatchers.Main) {
                processGameStateUpdate(newAffinity, isInitialAction)
            }

            // 4. 게임 오버가 아니고 대화 횟수가 남았다면 다음 선택지 생성
            if (gameState.affinity > 0 && gameState.conversationCount < MAX_CONVERSATION_COUNT) {
                val options = characterAi!!.generatePlayerOptions(
                    gameState = gameState,
                    characterLastResponse = fullResponse,
                    conversationHistory = conversationHistory,
                    scenario = scenarioForResponse
                )
                // 5. 선택지 표시 (UI 스레드에서)
                withContext(Dispatchers.Main) {
                    presentPlayerChoices(options)
                }
            } else {
                // 게임 종료 메시지 표시
                withContext(Dispatchers.Main) {
                    if (gameState.affinity > 0) { // 대화 횟수 초과로 종료된 경우
                        appendSystemMessage("会話が終わりました！")
                    }
                    disableAllOptions()
                }
            }
        }
    }

    /**
     * AI 응답을 스트리밍하고, 최종적으로 완성된 전체 문자열을 반환하는 suspend 함수로 변경합니다.
     */
    private suspend fun displayResponseWithStreamingEffect(playerAction: String, scenario: Scenario): String {
        val fullResponseBuilder = StringBuilder()

        // UI 업데이트를 위해 메인 스레드로 전환
        withContext(Dispatchers.Main) {
            removeLoadingMessage()
            val messagePrefix = "${gameState.characterName}: "
            tvConversation.append(messagePrefix)
        }

        // AI 스트리밍 호출 (이 함수는 내부적으로 완료될 때까지 기다립니다)
        characterAi!!.generateCharacterResponseStream(
            gameState = gameState,
            playerSelectedOption = playerAction,
            conversationHistory = conversationHistory,
            scenario = scenario
        ) { partialResult ->
            // AI가 생성한 부분 텍스트를 UI에 즉시 반영
            lifecycleScope.launch(Dispatchers.Main) {
                fullResponseBuilder.append(partialResult)
                val messagePrefix = "${gameState.characterName}: "
                val currentText = tvConversation.text.toString()
                val lastPrefixIndex = currentText.lastIndexOf(messagePrefix)
                if (lastPrefixIndex != -1) {
                    val baseText = currentText.substring(0, lastPrefixIndex + messagePrefix.length)
                    tvConversation.text = baseText + fullResponseBuilder.toString()
                }
                scrollToBottom()
            }
        }

        // 스트리밍이 모두 끝나면 줄바꿈 추가
        withContext(Dispatchers.Main) {
            tvConversation.append("\n\n")
            scrollToBottom()
        }

        return fullResponseBuilder.toString()
    }

    /**
     * 게임 상태 업데이트 로직만 따로 분리
     */
    private fun processGameStateUpdate(newAffinity: Int, isInitial: Boolean) {
        val previousAffinity = gameState.affinity
        val previousScenarioId = gameState.currentScenarioId

        // 1. 게임 상태 업데이트
        gameState = gameState.copy(
            affinity = newAffinity,
            conversationCount = if (isInitial) gameState.conversationCount else gameState.conversationCount + 1,
            responsesInSeason = gameState.responsesInSeason + 1
        )

        // 2. 계절 및 시나리오 변경 확인
        checkAndApplySeasonChange()
        checkAndApplyScenarioChange(previousScenarioId)

        // 3. 호감도 변화 표시
        val affinityChange = newAffinity - previousAffinity
        if (affinityChange != 0) {
            showAffinityChange(affinityChange)
            if (affinityChange > 0) particleView.startAnimation(ParticleView.ParticleType.HEART)
            else particleView.startAnimation(ParticleView.ParticleType.SAD)
        }
        updateStatusDisplay()

        // 4. 게임 오버 확인
        if (gameState.affinity <= 0) {
            showGameOverDialog("${gameState.characterName}との関係は終わってしまいました...")
        }
    }

    /**
     * 선택지 생성 로직을 제거하고, 받은 선택지를 표시하는 역할만 하도록 변경
     */
    private fun presentPlayerChoices(options: List<String>) {
        removeLoadingMessage()
        if (options.size == 3 && !options.any { it.contains("エラー") }) {
            updateButtonsWithOptions(options)
            appendSystemMessage("どうしよう")
            enableChoiceButtons()
        } else {
            appendSystemMessage("選択肢生成に失敗しました。")
            updateButtonsWithOptions(listOf("うん", "いいえ", "よくわからない"))
            enableChoiceButtons()
        }
    }

    private fun updateStatusDisplay() {
        animateStatusUpdate(tvAffinity, "好感度: ${gameState.affinity} (${gameState.getAffinityDescription()})")
        updateConfessButtonVisibility()
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

    private fun appendSystemMessage(message: String) {
        val styledMessage = SpannableString("システム: $message\n\n")
        styledMessage.setSpan(StyleSpan(Typeface.ITALIC), 0, styledMessage.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        styledMessage.setSpan(ForegroundColorSpan(ContextCompat.getColor(this, R.color.text_primary)), 0, styledMessage.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        tvConversation.append(styledMessage)
        scrollToBottom()
    }

    private fun appendPlayerMessage(message: String) {
        val styledMessage = SpannableString("あなた: $message\n\n")
        styledMessage.setSpan(StyleSpan(Typeface.BOLD), 0, "あなた:".length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        tvConversation.append(styledMessage)
        scrollToBottom()
    }

    private fun showLoadingMessage(message: String) {
        tvConversation.append("$message\n\n")
        scrollToBottom()
    }

    private fun removeLoadingMessage() {
        val currentText = tvConversation.text.toString()
        val lines = currentText.lines().toMutableList()
        val loadingLineIndex = lines.indexOfLast { it.contains("生成中") || it.contains("考え中") }
        if (loadingLineIndex != -1) {
            lines.removeAt(loadingLineIndex)
            // 로딩 메시지 바로 아래의 빈 줄도 함께 제거
            if (loadingLineIndex < lines.size && lines[loadingLineIndex].isBlank()) {
                lines.removeAt(loadingLineIndex)
            }
            tvConversation.text = lines.joinToString("\n")
        }
    }

    private fun checkAndApplySeasonChange() {
        if (gameState.responsesInSeason >= gameState.seasonChangeThreshold) {
            val currentSeasonIndex = Season.values().indexOf(gameState.currentSeason)
            val nextSeason = Season.values()[(currentSeasonIndex + 1) % Season.values().size]
            gameState = gameState.copy(
                currentSeason = nextSeason,
                responsesInSeason = 0
            )
            appendSystemMessage("[季節変化] ${nextSeason.name}になりました。")
        }
    }

    private fun checkAndApplyScenarioChange(previousScenarioId: String) {
        val nextScenarioId = ScenarioManager.checkAndTriggerNextScenario(gameState)
        if (nextScenarioId != previousScenarioId) {
            currentScenario = ScenarioManager.getScenario(nextScenarioId)
            if (currentScenario != null) {
                gameState = gameState.copy(currentScenarioId = nextScenarioId)
                appendSystemMessage("[状況変化]\n${currentScenario!!.setting}")
            } else {
                Log.e(TAG, "시나리오 변경 실패: ID '$nextScenarioId'를 찾을 수 없습니다.")
            }
        }
    }

    private fun showAffinityChange(change: Int) {
        val changeText = if (change > 0) "+$change" else "$change"
        val color = if (change > 0) ContextCompat.getColor(this, R.color.affinity_up) else ContextCompat.getColor(this, R.color.affinity_down)
        val toastText = SpannableString("好感度 $changeText")
        toastText.setSpan(ForegroundColorSpan(color), "好感度 ".length, toastText.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        showToastWithAnimation(toastText)
    }

    private fun onConfessButtonClicked() {
        AlertDialog.Builder(this)
            .setTitle("告白")
            .setMessage("${gameState.characterName}に告白しますか？")
            .setPositiveButton("はい") { _, _ ->
                val ending = if (gameState.affinity >= 80) "ハッピーアンド！" else "サードアンド"
                showGameOverDialog("告白結果: $ending")
            }
            .setNegativeButton("いいえ", null)
            .show()
    }

    private fun showGameOverDialog(message: String) {
        AlertDialog.Builder(this)
            .setTitle("ゲームオーバー")
            .setMessage(message)
            .setCancelable(false)
            .setPositiveButton("メインメニューに戻る") { _, _ ->
                val intent = Intent(this, MainActivity::class.java)
                intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK
                startActivity(intent)
                finish()
            }
            .show()
    }

    private fun animateStatusUpdate(textView: TextView, newText: String) {
        textView.animate().alpha(0f).setDuration(150).withEndAction {
            textView.text = newText
            textView.animate().alpha(1f).setDuration(150).start()
        }.start()
    }

    private fun showToastWithAnimation(message: CharSequence) {
        val toast = Toast.makeText(this, message, Toast.LENGTH_SHORT)
        toast.show()
    }

    private fun enableChoiceButtons() {
        questionButton1.isEnabled = true
        questionButton2.isEnabled = true
        questionButton3.isEnabled = true
    }

    private fun disableAllOptions() {
        questionButton1.isEnabled = false
        questionButton2.isEnabled = false
        questionButton3.isEnabled = false
    }

    private fun scrollToBottom() {
        scrollViewConversation.post { scrollViewConversation.fullScroll(View.FOCUS_DOWN) }
    }

    private fun updateConfessButtonVisibility() {
        btnConfess.visibility = if (gameState.affinity >= 80) View.VISIBLE else View.GONE
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "onDestroy() 호출, 리소스 정리")
    }
}