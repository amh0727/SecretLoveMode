package com.secretlovemode.ui.game

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
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.secretlovemode.util.ButtonUtils
import com.secretlovemode.data.model.Character
import com.secretlovemode.domain.CharacterAi
import com.secretlovemode.data.model.CharacterResponse
import com.secretlovemode.data.model.ChatMessage
import com.secretlovemode.data.model.GameState
import com.secretlovemode.ui.main.MainActivity
import com.secretlovemode.MyApplication
import com.secretlovemode.ui.common.ParticleView
import com.secretlovemode.R
import com.secretlovemode.data.model.Scenario
import com.secretlovemode.data.model.Season
import com.secretlovemode.ui.main.SlmViewModel
import com.secretlovemode.data.repository.ScenarioManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.text.iterator

class GameActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "GameActivity"
        private const val MAX_CONVERSATION_COUNT = 10 // 최대 대화 횟수 (조금 늘렸습니다)
    }

    private val loadingMessages = listOf(
        "うーん、なんて言おうかな…",
        "ちょっと考え中…",
        "ふむふむ…",
        "先輩、待っててくださいね…",
        "えーっと…"
    )

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

    private lateinit var inputLayout: LinearLayout
    private lateinit var etUserInput: EditText
    private lateinit var btnSubmitInput: Button

    private lateinit var gameState: GameState
    private lateinit var slmViewModel: SlmViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d(TAG, "onCreate() 호출")
        slmViewModel = (application as MyApplication).slmViewModel
        delegate.localNightMode = AppCompatDelegate.MODE_NIGHT_NO
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
        if (characterAi == null || !characterAi!!.isModelReady) {
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
        inputLayout = findViewById(R.id.inputLayout)
        etUserInput = findViewById(R.id.etUserInput)
        btnSubmitInput = findViewById(R.id.btnSubmitInput)
    }

    private fun setupUI() {
        tvCharacterName.text = gameState.characterName
        updateStatusDisplay()
        questionButton1.setOnClickListener { onPlayerOptionSelected(questionButton1.text.toString()) }
        questionButton2.setOnClickListener { onPlayerOptionSelected(questionButton2.text.toString()) }
        questionButton3.setOnClickListener { onPlayerOptionSelected(questionButton3.text.toString()) }
        btnConfess.setOnClickListener { onConfessButtonClicked() }
        btnSubmitInput.setOnClickListener { onUserInputSubmit() }
    }

    private fun startGame() {
        Log.d(TAG, "startGame() 호출")
        val initialScenarioId = ScenarioManager.checkAndTriggerNextScenario(gameState)
        currentScenario = ScenarioManager.getScenario(initialScenarioId)
        if (currentScenario == null) {
            Log.e(TAG, "초기 시나리오 로딩 실패")
            showToastWithAnimation("게임 시작에 실패했습니다")
            disableAllOptions()
            return
        }
        gameState = gameState.copy(currentScenarioId = initialScenarioId)

        appendSystemMessage("[状況]\n${currentScenario!!.setting}")
        updateCharacterImage(currentScenario)
        checkAndApplySeasonChange()
        handleInitialTurn()
    }

    private fun onPlayerOptionSelected(selectedOptionText: String) {
        if (characterAi == null || !characterAi!!.isModelReady) {
            showToastWithAnimation("AIがまだ準備できていません")
            return
        }
        Log.d(TAG, "選択: $selectedOptionText")
        appendPlayerMessage(selectedOptionText)
        // [수정] 로딩 메시지 없이 바로 스트리밍 함수 호출
        handlePlayerAction(selectedOptionText)
    }

    private fun handleInitialTurn() {
        val scenarioForResponse = currentScenario ?: return
        disableAllOptions()
        showLoadingMessage("${gameState.characterName}: ${loadingMessages.random()} ")

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val initialTurnResult = characterAi!!.processInitialTurn(
                    gameState = gameState,
                    scenario = scenarioForResponse
                )
                withContext(Dispatchers.Main) {
                    displayFullResponse(initialTurnResult.fullInitialResponse)
                    conversationHistory.add(
                        ChatMessage(
                            ChatMessage.Companion.ROLE_MODEL,
                            initialTurnResult.fullInitialResponse.spoken_response
                        )
                    )
                    presentPlayerChoices(initialTurnResult.firstPlayerOptions)
                }
            } catch (e: Exception) {
                Log.e(TAG, "초기 턴 처리 실패", e)
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@GameActivity, "エラーが発生しました: ${e.message}", Toast.LENGTH_LONG).show()
                    presentPlayerChoices(listOf("うん", "いいえ", "よくわからない"))
                }
            }
        }
    }

    private suspend fun displayFullResponse(characterResponse: CharacterResponse) {
        removeLoadingMessage()
        if (characterResponse.inner_monologue.isNotBlank()) {
            val styledInnerMonologue = SpannableString("\n（${characterResponse.inner_monologue}）\n")
            styledInnerMonologue.setSpan(StyleSpan(Typeface.ITALIC), 0, styledInnerMonologue.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
            styledInnerMonologue.setSpan(ForegroundColorSpan(ContextCompat.getColor(this, R.color.text_secondary)), 0, styledInnerMonologue.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
            tvConversation.append(styledInnerMonologue)
            scrollToBottom()
            delay(1500)
        }
        tvConversation.append("\n${gameState.characterName}: ")
        for (char in characterResponse.spoken_response) {
            tvConversation.append(char.toString())
            scrollToBottom()
            delay(100)
        }
        tvConversation.append("\n\n")
        scrollToBottom()
    }

    /**
     * [핵심 수정] 스트리밍을 지원하는 새로운 handlePlayerAction 함수
     */
    private fun handlePlayerAction(playerAction: String) {
        val scenarioForResponse = currentScenario ?: return
        disableAllOptions()

        // 스트리밍 UI 준비
        tvConversation.append("\n${gameState.characterName}: ")
        val fullSpokenResponse = StringBuilder()

        lifecycleScope.launch { // UI 업데이트를 위해 메인 스레드에서 시작
            characterAi!!.processPlayerTurnStream(
                gameState = gameState,
                playerSelectedOption = playerAction,
                conversationHistory = conversationHistory,
                scenario = scenarioForResponse
            )
                .onStart {
                    // 스트리밍 시작 시점에 플레이어의 말을 히스토리에 추가
                    conversationHistory.add(ChatMessage(ChatMessage.Companion.ROLE_USER, playerAction))
                }
                .flowOn(Dispatchers.IO) // AI 작업은 IO 스레드에서 실행
                .collect { event ->
                    // collect 블록은 launch의 컨텍스트(메인 스레드)에서 실행됨
                    when (event) {
                        is CharacterAi.StreamEvent.TextChunk -> {
                            // 텍스트 조각이 올 때마다 즉시 UI에 추가
                            tvConversation.append(event.text)
                            fullSpokenResponse.append(event.text)
                            scrollToBottom()
                        }
                        is CharacterAi.StreamEvent.TurnComplete -> {
                            // 스트리밍이 끝나고 최종 데이터가 도착
                            val payload = event.payload
                            val finalResponse = fullSpokenResponse.toString().trim()
                            conversationHistory.add(ChatMessage(ChatMessage.Companion.ROLE_MODEL, finalResponse))

                            // 속마음 표시
                            if (payload.inner_monologue.isNotBlank()) {
                                val styledInnerMonologue = SpannableString("\n（${payload.inner_monologue}）\n")
                                styledInnerMonologue.setSpan(StyleSpan(Typeface.ITALIC), 0, styledInnerMonologue.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                                styledInnerMonologue.setSpan(
                                    ForegroundColorSpan(
                                        ContextCompat.getColor(
                                            this@GameActivity,
                                            R.color.text_secondary
                                        )
                                    ), 0, styledInnerMonologue.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                                tvConversation.append(styledInnerMonologue)
                                scrollToBottom()
                                delay(1500) // 속마음 표시 후 잠시 대기
                            }
                            tvConversation.append("\n\n")

                            // 게임 상태 업데이트
                            val newAffinity = (gameState.affinity + payload.affinity_change).coerceIn(0, 100)
                            processGameStateUpdate(newAffinity, false)

                            // 다음 선택지 표시 또는 다른 로직 처리
                            if (currentScenario?.requiresUserInput == true) {
                                showUserInputView()
                            } else if (gameState.affinity > 0 && gameState.conversationCount < MAX_CONVERSATION_COUNT) {
                                presentPlayerChoices(payload.player_options)
                            } else {
                                if (gameState.affinity > 0) {
                                    appendSystemMessage("会話が終わりました！")
                                }
                                disableAllOptions()
                            }
                        }
                        is CharacterAi.StreamEvent.Error -> {
                            Toast.makeText(this@GameActivity, event.message, Toast.LENGTH_LONG).show()
                            presentPlayerChoices(listOf("うん", "いいえ", "よくわからない")) // 에러 시 기본 선택지
                        }
                    }
                }
        }
    }

    private fun showUserInputView() {
        disableAllOptions()
        questionButton1.visibility = View.GONE
        questionButton2.visibility = View.GONE
        questionButton3.visibility = View.GONE
        appendSystemMessage("春が終わります。彼女に何か伝えたいことはありませんか？")
        inputLayout.visibility = View.VISIBLE
        etUserInput.requestFocus()
    }

    private fun onUserInputSubmit() {
        val userInput = etUserInput.text.toString().trim()
        if (userInput.isEmpty()) {
            Toast.makeText(this, "気持ちを書いてください。", Toast.LENGTH_SHORT).show()
            return
        }
        gameState = gameState.copy(confessionKeyword = userInput)
        Log.d(TAG, "사용자 입력 저장: $userInput")
        inputLayout.visibility = View.GONE
        appendSystemMessage("あなたの心を伝えました")
        updateConfessButtonVisibility()
    }

    private fun processGameStateUpdate(newAffinity: Int, isInitial: Boolean) {
        val previousAffinity = gameState.affinity
        val previousScenarioId = gameState.currentScenarioId

        gameState = gameState.copy(
            affinity = newAffinity,
            conversationCount = if (isInitial) gameState.conversationCount else gameState.conversationCount + 1,
            responsesInSeason = if (isInitial) gameState.responsesInSeason else gameState.responsesInSeason + 1
        )

        checkAndApplySeasonChange()
        checkAndApplyScenarioChange(previousScenarioId)

        val affinityChange = newAffinity - previousAffinity
        if (affinityChange != 0 && !isInitial) {
            showAffinityChange(affinityChange)
            if (affinityChange > 0) particleView.startAnimation(ParticleView.ParticleType.HEART)
            else particleView.startAnimation(ParticleView.ParticleType.SAD)
        }
        updateStatusDisplay()

        if (gameState.affinity <= 0) {
            showGameOverDialog("${gameState.characterName}との関係は終わってしまいました...")
        }
    }

    private fun presentPlayerChoices(options: List<String>) {
        removeLoadingMessage()
        if (options.size >= 3 && !options.any { it.contains("エラー") }) {
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
        buttons.forEach { it.visibility = View.GONE } // 일단 모든 버튼 숨기기
        options.take(3).forEachIndexed { index, option ->
            buttons[index].text = option
            buttons[index].visibility = View.VISIBLE
            ButtonUtils.adjustButtonForText(buttons[index], option)
        }
        lifecycleScope.launch {
            delay(100)
            ButtonUtils.balanceButtonSizes(buttons.filter { it.visibility == View.VISIBLE })
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
        val loadingLine = loadingMessages.find { currentText.contains(it) }
        if (loadingLine != null) {
            val newText = currentText.replaceFirst("${gameState.characterName}: $loadingLine \n\n", "")
            tvConversation.text = newText
        }
    }

    private fun checkAndApplySeasonChange() {
        if (gameState.responsesInSeason >= gameState.seasonChangeThreshold) {
            val currentSeasonIndex = Season.values().indexOf(gameState.currentSeason)
            val nextSeason = Season.values()[(currentSeasonIndex + 1) % Season.values().size]
            gameState = gameState.copy(
                currentSeason = nextSeason,
                responsesInSeason = 0,
                currentScenarioId = "CHAPTER_${currentSeasonIndex + 2}_START" // 다음 챕터로 강제 이동
            )
            appendSystemMessage("[季節変化] ${nextSeason.name}になりました。")
        }

        when (gameState.currentSeason) {
            Season.SPRING -> particleView.startAnimation(ParticleView.ParticleType.CHERRY_BLOSSOMS)
            Season.WINTER -> particleView.startAnimation(ParticleView.ParticleType.SNOW)
            else -> particleView.stopAnimation()
        }
    }

    private fun checkAndApplyScenarioChange(previousScenarioId: String) {
        val nextScenarioId = ScenarioManager.checkAndTriggerNextScenario(gameState)
        if (nextScenarioId != previousScenarioId) {
            currentScenario = ScenarioManager.getScenario(nextScenarioId)
            if (currentScenario != null) {
                gameState = gameState.copy(currentScenarioId = nextScenarioId)
                appendSystemMessage("[状況変化]\n${currentScenario!!.setting}")
                updateCharacterImage(currentScenario)
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

    private fun updateCharacterImage(scenario: Scenario?) {
        scenario?.imageName?.let { imageName ->
            val resourceId = resources.getIdentifier(imageName, "drawable", packageName)
            if (resourceId != 0) {
                ivCharacter.setImageResource(resourceId)
            } else {
                Log.w(TAG, "Drawable 리소스를 찾을 수 없습니다: $imageName")
                ivCharacter.setImageResource(R.drawable.kaoru_default)
            }
        }
    }

    private fun onConfessButtonClicked() {
        val keyword = gameState.confessionKeyword
        val message = if (keyword != null) {
            "'${keyword}' と伝えた気持ちを込めて, ${gameState.characterName}に告白しますか？"
        } else {
            "${gameState.characterName}に告白しますか？"
        }

        AlertDialog.Builder(this)
            .setTitle("告白")
            .setMessage(message) // [수정] 중복된 setMessage 호출 제거
            .setPositiveButton("はい") { _, _ ->
                // [수정] 오타 수정
                val ending = if (gameState.affinity >= 80) "ハッピーエンド！" else "サッドエンド"
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
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
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
}