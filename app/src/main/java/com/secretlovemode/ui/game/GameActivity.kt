package com.secretlovemode.ui.game

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Typeface
import android.os.Bundle
import android.text.Spannable
import android.text.SpannableString

import android.text.style.StyleSpan
import android.util.Log
import android.view.MotionEvent
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate

import androidx.lifecycle.lifecycleScope
import com.secretlovemode.MyApplication
import com.secretlovemode.R
import com.secretlovemode.data.model.ChatMessage

import com.secretlovemode.data.model.ScenarioFile
import com.secretlovemode.data.model.ScenarioNode
import com.secretlovemode.data.model.ScenarioOption
import com.secretlovemode.data.repository.ScenarioManager
import com.secretlovemode.domain.CharacterAi
import com.secretlovemode.ui.common.ParticleView
import com.secretlovemode.ui.main.MainActivity
import com.secretlovemode.ui.main.SlmViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.animation.ValueAnimator
import android.view.animation.AccelerateDecelerateInterpolator
import androidx.constraintlayout.widget.ConstraintSet
import androidx.transition.TransitionManager
import android.graphics.drawable.Drawable
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Shader
import android.graphics.Matrix
import kotlinx.coroutines.isActive

class GameActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "GameActivity"
        private const val REQUEST_CODE_FINAL_MESSAGE = 1001
    }
    private var characterAi: CharacterAi? = null
    private var isTyping = false
    private var canProceed = false
    private var typingDelay: Long = 1L // Default typing delay
    private var heartAnimator: ValueAnimator? = null
    private var characterImageAnimator: ValueAnimator? = null
    private var particleAnimationJob: kotlinx.coroutines.Job? = null
    private lateinit var playerName: String

    // UI 요소
    private lateinit var tvCurrentMessage: TextView
    private lateinit var questionButton1: Button
    private lateinit var questionButton2: Button
    private lateinit var questionButton3: Button
    private lateinit var btnConfess: Button
    private lateinit var tvCharacterName: TextView
    private lateinit var ivHeartIcon: ImageView
    private lateinit var ivCharacter: ImageView
    private lateinit var ivBackgroundLeft: ImageView
    private lateinit var ivBackgroundRight: ImageView
    private lateinit var currentMessageCard: androidx.cardview.widget.CardView
    private lateinit var particleView: ParticleView
    private lateinit var characterCard: androidx.cardview.widget.CardView
    private lateinit var mainLayout: androidx.constraintlayout.widget.ConstraintLayout
    private lateinit var sectionTransitionOverlay: androidx.cardview.widget.CardView
    private lateinit var tvSectionTransition: TextView
    private lateinit var tvThinkingOverlay: TextView
    private lateinit var choiceScrollView: ScrollView
    
    // 새로운 대화 이력 UI 요소들
    private lateinit var fabHistory: com.google.android.material.floatingactionbutton.FloatingActionButton
    private lateinit var historyOverlay: androidx.constraintlayout.widget.ConstraintLayout
    private lateinit var btnCloseHistory: android.widget.ImageButton
    private lateinit var tvHistoryContent: TextView
    private lateinit var loadingProgressCard: androidx.cardview.widget.CardView
    
    // 대화 기록 관리
    private val conversationHistory = mutableListOf<String>()
    private val sectionHistory = mutableMapOf<String, MutableList<String>>()
    private var currentSectionName = "시작"
    

    // 게임 상태 관련 변수
    private lateinit var slmViewModel: SlmViewModel
    private var currentScenarioFile: ScenarioFile? = null
    private var currentScenarioNodeIndex: Int = 0
    private var currentScenarioId: String? = null
    private var isWaitingForTextInput: Boolean = false

    override fun onResume(){
        super.onResume()
        updateStatusDisplay()
        
        // Check if we were waiting for text input and should proceed to next node
        if (isWaitingForTextInput) {
            isWaitingForTextInput = false
            // Continue to next scenario node
            currentScenarioNodeIndex++
            if (currentScenarioNodeIndex < (currentScenarioFile?.scenarios?.size ?: 0)) {
                lifecycleScope.launch {
                    delay(500) // Brief delay for better UX
                    processCurrentScenarioNode()
                }
            } else {
                // End of scenario - check if we should load next scenario
                showGameOverDialog("シナリオが終了しました。")
            }
        }
    }

    override fun onPause() {
        super.onPause()
        heartAnimator?.cancel()
        characterImageAnimator?.cancel()
        particleAnimationJob?.cancel()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d(TAG, "onCreate() 呼び出し")
        slmViewModel = (application as MyApplication).slmViewModel
        playerName = intent.getStringExtra("PLAYER_NAME") ?: "플레이어"
        Log.d(TAG, "Player name received from intent: '$playerName'")
        slmViewModel.setPlayerName(playerName)
        delegate.localNightMode = AppCompatDelegate.MODE_NIGHT_NO
        setContentView(R.layout.activity_game)

        // Observe gameState from ViewModel
        slmViewModel.gameState.observe(this) {
            // Update UI based on gameState changes
            updateStatusDisplay()
        }

        initializeViews()
        setupUI()

        characterAi = slmViewModel.getCharacterAi()
        if (characterAi == null || !characterAi!!.isModelReady) {
            Toast.makeText(this, "モデルがまだ呼び出されていません。最初画面に戻ります。", Toast.LENGTH_LONG).show()
            finish()
            return
        }

        startGame()
        Log.d(TAG, "onCreate() 完了")
    }
    
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        
        if (requestCode == REQUEST_CODE_FINAL_MESSAGE && resultCode == RESULT_OK) {
            Log.d(TAG, "FinalMessageActivity completed successfully")
            isWaitingForTextInput = false
            
            // 고백 메시지가 완료된 후 SLM 추론 시작
            val inputKey = data?.getStringExtra("input_key")
            if (inputKey == "confession") {
                Log.d(TAG, "Confession message received, starting SLM inference")
                handleConfessionResponse()
            } else {
                // 다른 입력의 경우 시나리오 계속 진행
                processCurrentScenarioNode()
            }
        } else if (requestCode == REQUEST_CODE_FINAL_MESSAGE) {
            Log.w(TAG, "FinalMessageActivity was cancelled or failed")
            isWaitingForTextInput = false
        }
    }

    private fun initializeViews() {
        tvCurrentMessage = findViewById(R.id.tvCurrentMessage)
        questionButton1 = findViewById(R.id.questionButton1)
        questionButton2 = findViewById(R.id.questionButton2)
        questionButton3 = findViewById(R.id.questionButton3)
        btnConfess = findViewById(R.id.btnConfess)
        tvCharacterName = findViewById(R.id.tvCharacterName)
        ivHeartIcon = findViewById(R.id.ivHeartIcon)
        ivCharacter = findViewById(R.id.ivCharacter)
        ivBackgroundLeft = findViewById(R.id.ivBackgroundLeft)
        ivBackgroundRight = findViewById(R.id.ivBackgroundRight)
        currentMessageCard = findViewById(R.id.currentMessageCard)
        
        // 새로운 대화 이력 UI 요소들
        fabHistory = findViewById(R.id.fabHistory)
        historyOverlay = findViewById(R.id.historyOverlay)
        btnCloseHistory = findViewById(R.id.btnCloseHistory)
        tvHistoryContent = findViewById(R.id.tvHistoryContent)
        particleView = findViewById(R.id.particleView)
        characterCard = findViewById(R.id.characterCard)
        mainLayout = findViewById(R.id.main)
        sectionTransitionOverlay = findViewById(R.id.sectionTransitionOverlay)
        tvSectionTransition = findViewById(R.id.tvSectionTransition)
        tvThinkingOverlay = findViewById(R.id.tvThinkingOverlay)
        choiceScrollView = findViewById(R.id.choiceScrollView)
        
        // 로딩 프로그래스바 관련 UI 요소들 (이미 존재하지만 변수로 저장)
        loadingProgressCard = findViewById(R.id.loadingProgressCard)
    }

    private fun setupUI() {
        updateStatusDisplay()
        btnConfess.setOnClickListener { onConfessButtonClicked() }
        
        // 전체 화면 터치로 대화 진행 또는 타이핑 스킵
        mainLayout.setOnClickListener {
            // 섹션 전환 오버레이가 표시중이면 숨김
            if (sectionTransitionOverlay.visibility == View.VISIBLE) {
                hideSectionTransitionOverlay()
            } else if (isTyping) {
                // 타이핑 중이면 즉시 완료
                isTyping = false
            } else if (canProceed) {
                processCurrentScenarioNode()
            }
        }
        
        // 새로운 대화 이력 관련 이벤트 리스너
        fabHistory.setOnClickListener { showHistoryOverlay() }
        btnCloseHistory.setOnClickListener { hideHistoryOverlay() }
        historyOverlay.setOnClickListener { hideHistoryOverlay() } // 배경 클릭시 닫기
        
        // 초기 캐릭터 카드 크기 설정
        animateCharacterCardSize(0.65f) // 더 큰 기본 크기
    }

    private fun startGame() {
        Log.d(TAG, "startGame() 呼び出し")
        loadScenario("1") // 첫 시나리오(session1.json) 로드
    }

    private fun loadScenario(scenarioId: String) {
        this.currentScenarioId = scenarioId
        Log.d(TAG, "Attempting to load scenario: session$scenarioId.json")
        currentScenarioFile = ScenarioManager.getScenario(this, scenarioId)
        if (currentScenarioFile == null) {
            Log.e(TAG, "シナリオの読み込みに失敗しました: session$scenarioId.json")
            showGameOverDialog("シナリオファイルを読み込めません。")
            return
        }
        Log.d(TAG, "Scenario loaded successfully: ${currentScenarioFile!!.title}")
        
        // 섹션 전환 오버레이 표시 (첫 번째 섹션 제외)
        if (scenarioId != "1") {
            showSectionTransitionOverlay(scenarioId, currentScenarioFile!!.title)
        }
        
        // 섹션 이름 업데이트
        currentSectionName = when (scenarioId) {
            "1" -> "始まりの章"
            "2A", "2B", "2C" -> "出会いの章"
            "3A", "3B", "3C" -> "親しみの章"
            "4A", "4B", "4C" -> "信頼の章"
            "5A", "5B", "5C" -> "絆の章"
            "6A", "6B" -> "愛の章"
            "7A", "7B" -> "告白の章"
            else -> "セクション$scenarioId"
        }
        
        // 섹션 대화 내용 수집 및 저장
        collectAndStoreSectionDialogues(scenarioId)
        
        currentScenarioNodeIndex = 0
        // 첫 번째 섹션이 아닌 경우에만 기존 대화를 유지하고, 첫 번째 섹션인 경우 초기화
        if (scenarioId == "1") {
            conversationHistory.clear()
            sectionHistory.clear()
            tvCurrentMessage.text = ""
        }
        processCurrentScenarioNode()
    }

    private fun processCurrentScenarioNode() {
        val nodes = currentScenarioFile?.scenarios ?: return
        Log.d(TAG, "Processing scenario node: index=$currentScenarioNodeIndex, total nodes=${nodes.size}")
        
        if (currentScenarioNodeIndex >= nodes.size) {
            Log.d(TAG, "シナリオ '${currentScenarioFile?.title}' 終了")
            val nextTriggeredScenarioId = ScenarioManager.checkAndTriggerNextScenario(slmViewModel.gameState.value!!)
            if (nextTriggeredScenarioId != slmViewModel.gameState.value!!.currentScenarioId) {
                Log.d(TAG, "Dynamic scenario triggered: $nextTriggeredScenarioId")
                slmViewModel.updateCurrentScenarioId(nextTriggeredScenarioId)
                loadScenario(nextTriggeredScenarioId)
            } else {
                disableAllOptions()
                hideAllChoiceButtons()
                appendSystemMessage("シナリオが終了しました。")
            }
            return
        }

        val node = nodes[currentScenarioNodeIndex]
        Log.d(TAG, "Processing node: type=${node.type}, speaker=${node.speaker}, text=${node.text.take(50)}...")
        // 인덱스를 미리 증가시키지 않고, 각 노드 처리 함수에서 필요할 때만 증가
        // currentScenarioNodeIndex++

        tvCharacterName.text = currentScenarioFile?.title
        // 이미지 업데이트는 각 노드 타입별 함수에서 처리하도록 변경
        // updateCharacterImage(node.images)

        when (node.type) {
            "message" -> {
                Log.d(TAG, "Displaying message node")
                displayMessage(node)
            }
            "choice" -> {
                Log.d(TAG, "Displaying choice node")
                displayChoice(node)
            }
            "text_input" -> {
                Log.d(TAG, "Displaying text input node")
                displayTextInput(node)
            }
            "input" -> { // Handle "input" type
                Log.d(TAG, "Displaying input node")
                // 노드 인덱스를 여기서 증가 (input 노드 처리 후)
                currentScenarioNodeIndex++
                
                node.keyInput?.let { key ->
                    val intent = Intent(this, FinalMessageActivity::class.java)
                    intent.putExtra("input_key", key)
                    intent.putExtra("currentAffinity", slmViewModel.gameState.value!!.affinity)
                    startActivityForResult(intent, REQUEST_CODE_FINAL_MESSAGE)
                    // finish() 제거 - Activity를 종료하지 않음
                } ?: run {
                    Log.e(TAG, "Input node without keyInput specified.")
                    showGameOverDialog("シナリオエラー: 入力キーが指定されていません。")
                }
            }
            else -> {
                Log.w(TAG, "Unknown node type: ${node.type}")
            }
        }
    }

    

    @SuppressLint("ClickableViewAccessibility")
    private fun displayMessage(node: ScenarioNode) {
        Log.d(TAG, "displayMessage called for speaker: ${node.speaker}")
        lifecycleScope.launch {
            isTyping = true
            canProceed = false
            disableAllOptions()
            hideAllChoiceButtons()

            val speakerPrefix = when (node.speaker) {
                "system" -> "[システム] "
                "主人公(心の声)" -> "(心の声...)"
                "主人公(会話)" -> "私: "
                else -> "${node.speaker}: "
            }

            val processedText = node.text.replace("{{playerName}}", playerName)
            if (node.text.contains("{{playerName}}")) {
                Log.d(TAG, "Replacing playerName in text: '${node.text}' -> '$processedText'")
                Log.d(TAG, "Current playerName value: '$playerName'")
            }

            // 캐릭터 이미지 변경 먼저 실행
            updateCharacterImage(node.images)
            
            // 현재 메시지 카드를 초기화하고 새 메시지를 한 번에 하나씩 표시
            tvCurrentMessage.text = ""
            val fullMessage = "$speakerPrefix$processedText"
            
            // 대화 기록에 추가 (전체 및 섹션별)
            conversationHistory.add(fullMessage)
            addToSectionHistory(fullMessage)
            
            // 타이핑 애니메이션으로 텍스트 출력 (적절한 속도)
            val typingDelay = 50L // 한자한자 보이는 타이핑 속도
            var currentIndex = 0
            
            for (i in fullMessage.indices) {
                if (!isTyping) {
                    // 타이핑이 중단되면 나머지 텍스트를 즉시 표시
                    tvCurrentMessage.text = fullMessage
                    break
                }
                
                tvCurrentMessage.text = fullMessage.substring(0, i + 1)
                delay(typingDelay)
                currentIndex = i + 1
            }
            
            isTyping = false
            
            // 노드 인덱스를 여기서 증가 (다음 텍스트 미리 로딩 방지)
            currentScenarioNodeIndex++
            
            delay(300)
            canProceed = true
            
            // 일반 메시지 처리 완료
            
            Log.d(TAG, "Message display completed, canProceed=true")
        }
    }

    private fun displayChoice(node: ScenarioNode) {
        val options = node.options ?: return
        val buttons = listOf(questionButton1, questionButton2, questionButton3)

        Log.d(TAG, "displayChoice: showing ${options.size} options")
        buttons.forEach { it.visibility = View.GONE }

        options.take(buttons.size).forEachIndexed { index, option ->
            val button = buttons[index]
            button.text = option.text
            button.visibility = View.VISIBLE
            button.setOnClickListener { onPlayerOptionSelected(option, node.text) }
            Log.d(TAG, "Option $index: ${option.text} (${option.text.length} chars)")
        }
        enableChoiceButtons()
        canProceed = false
        
        // 캐릭터 이미지 업데이트 (선택지와 함께)
        updateCharacterImage(node.images)
        
        // 노드 인덱스를 여기서 증가 (선택지 표시 후)
        currentScenarioNodeIndex++
        
        // 선택지 표시 시에는 특별한 처리 없음
        
        // 선택지 스크롤뷰 맨 위로 초기화
        choiceScrollView.post {
            choiceScrollView.scrollTo(0, 0)
        }
    }

    private fun displayTextInput(node: ScenarioNode) {
        // 먼저 이미지 업데이트
        updateCharacterImage(node.images)
        
        // Display the message (이미 displayMessage에서 이미지 업데이트하므로 중복 방지)
        lifecycleScope.launch {
            isTyping = false
            canProceed = false
            disableAllOptions()
            hideAllChoiceButtons()

            val speakerPrefix = when (node.speaker) {
                "system" -> "[システム] "
                "主人公(心の声)" -> "(心の声...)"
                "主人公(会話)" -> "私: "
                else -> "${node.speaker}: "
            }

            val processedText = node.text.replace("{{playerName}}", playerName)
            
            // 현재 메시지 카드에 메시지 표시
            tvCurrentMessage.text = ""
            val fullMessage = "$speakerPrefix$processedText"
            
            // 대화 기록에 추가 (전체 및 섹션별)
            conversationHistory.add(fullMessage)
            addToSectionHistory(fullMessage)
            
            // 타이핑 애니메이션으로 텍스트 출력
            val typingDelay = 50L
            for (i in fullMessage.indices) {
                tvCurrentMessage.text = fullMessage.substring(0, i + 1)
                delay(typingDelay)
            }
            
            delay(300)
            
            // 노드 인덱스를 여기서 증가 (텍스트 입력 표시 후)
            currentScenarioNodeIndex++
            
            // Launch FinalMessageActivity for text input
            node.keyInput?.let { key ->
                isWaitingForTextInput = true
                val intent = Intent(this@GameActivity, FinalMessageActivity::class.java)
                intent.putExtra("input_key", key)
                intent.putExtra("placeholder", node.placeholder ?: "입력하세요")
                intent.putExtra("message_text", node.text)
                startActivityForResult(intent, REQUEST_CODE_FINAL_MESSAGE)
            } ?: run {
                Log.e(TAG, "Text input node without keyInput specified.")
                showGameOverDialog("シナリオエラー: 入力キーが指定されていません。")
            }
            
            canProceed = false
        }
    }
    

    private fun onPlayerOptionSelected(selectedOption: ScenarioOption, situationText: String) {
        disableAllOptions()
        hideAllChoiceButtons()
        appendPlayerMessage(selectedOption.text)
        Log.d(TAG, "Player selected option: ${selectedOption.text}, next scenario: ${selectedOption.next}")

        lifecycleScope.launch {
            // AI 추론 지연을 숨기기 위한 방법들 시작
            startDelayDistractionEffects()
            
            val affectionChange = characterAi?.judgeAffection(
                gameState = slmViewModel.gameState.value!!,
                situationText = situationText,
                playerSelectedOption = selectedOption.text,
                baseAffectionChange = selectedOption.affectionChange ?: 0,
                conversationHistory = slmViewModel.gameState.value!!.conversationHistory
            ) ?: (selectedOption.affectionChange ?: 0)

            Log.d(TAG, "Affection change from AI: $affectionChange")

            // 지연 효과 종료
            stopDelayDistractionEffects()

            processGameStateUpdate(slmViewModel.gameState.value!!.affinity + affectionChange)

            if (selectedOption.next.isNotBlank()) {
                loadScenario(selectedOption.next)
            } else {
                Log.e(TAG, "Selected option has no next scenario ID. Ending game.")
                showGameOverDialog("ゲームが終了しました。")
            }
        }
    }

    

    private fun processGameStateUpdate(newAffinity: Int) {
        val currentGameState = slmViewModel.gameState.value!!
        val previousAffinity = currentGameState.affinity

        slmViewModel.updateGameState(
            newAffinity = newAffinity,
            conversationCount = currentGameState.conversationCount + 1,
            conversationHistory = currentGameState.conversationHistory
        )

        val affinityChange = slmViewModel.gameState.value!!.affinity - previousAffinity
        if (affinityChange != 0) {
            if (affinityChange > 0) particleView.startAnimation(ParticleView.ParticleType.HEART)
            else particleView.startAnimation(ParticleView.ParticleType.SAD)
        }
        // updateStatusDisplay() is now called via LiveData observation

        if (slmViewModel.gameState.value!!.affinity <= 0) {
            showGameOverDialog("${slmViewModel.gameState.value!!.characterName}との関係は終わってしまいました...")
        }

        val nextTriggeredScenarioId = ScenarioManager.checkAndTriggerNextScenario(slmViewModel.gameState.value!!)
        if (nextTriggeredScenarioId != slmViewModel.gameState.value!!.currentScenarioId) {
            Log.d(TAG, "Dynamic scenario triggered: $nextTriggeredScenarioId")
            slmViewModel.updateCurrentScenarioId(nextTriggeredScenarioId)
            loadScenario(nextTriggeredScenarioId)
        }
    }

    private fun updateStatusDisplay() {
        val affinity = slmViewModel.gameState.value?.affinity ?: 0

        heartAnimator?.cancel()
        ivHeartIcon.clearAnimation()

        // Update heart icon size based on affinity (더 큰 범위로 확장)
        val scale = when {
            affinity < 0 -> 0.6f + (kotlin.math.max(affinity, -50) + 50) / 50f * 0.2f // 음수일 때 0.6f~0.8f
            else -> 0.8f + (affinity / 100f) * 0.4f // 양수일 때 0.8f~1.2f
        }
        ivHeartIcon.scaleX = scale
        ivHeartIcon.scaleY = scale

        // Update heart icon color based on affinity with better negative visibility
        val colorMatrix = ColorMatrix()
        when {
            affinity < 0 -> {
                // 음수 호감도: 진한 회색으로 표시하고 투명도 높임
                colorMatrix.setSaturation(0f) // 완전 무채색
                val darkness = kotlin.math.max(affinity, -50) / -50f // 0.0 (덜 어두움) to 1.0 (더 어두움)
                colorMatrix.postConcat(ColorMatrix(floatArrayOf(
                    0.3f, 0.3f, 0.3f, 0f, 0f,  // Red
                    0.3f, 0.3f, 0.3f, 0f, 0f,  // Green  
                    0.3f, 0.3f, 0.3f, 0f, 0f,  // Blue
                    0f, 0f, 0f, 1f, 0f          // Alpha
                )))
                ivHeartIcon.alpha = 0.7f + darkness * 0.3f // 0.7~1.0 투명도
            }
            affinity == 0 -> {
                // 호감도 0: 중간 회색
                colorMatrix.setSaturation(0.2f)
                ivHeartIcon.alpha = 0.8f
            }
            else -> {
                // 양수 호감도: 기존 로직 (회색에서 빨간색으로)
                val saturation = affinity / 100f
                colorMatrix.setSaturation(saturation)
                ivHeartIcon.alpha = 1.0f
            }
        }
        val filter = ColorMatrixColorFilter(colorMatrix)
        ivHeartIcon.colorFilter = filter

        if (affinity > 60) { // 호감도 20 초과 시 애니메이션 시작
            // 호감도가 높을수록 박동이 빨라집니다 (1300ms -> 500ms)
            val beatDuration = (1500 - 10 * affinity).toLong().coerceIn(500, 1500)
            // 호감도가 높을수록 박동이 강해집니다 (5% -> 25% 커짐)
            val beatIntensity = 1.0f + (affinity / 100f) * 0.25f

            heartAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
                duration = beatDuration
                repeatCount = ValueAnimator.INFINITE
                interpolator = AccelerateDecelerateInterpolator()
                addUpdateListener {
                    val pulse = 1f + (kotlin.math.sin((it.animatedValue as Float) * Math.PI) * (beatIntensity - 1f)).toFloat()
                    ivHeartIcon.scaleX = scale * pulse
                    ivHeartIcon.scaleY = scale * pulse
                }
                start()
            }
        }

        updateConfessButtonVisibility()
    }

    private fun appendSystemMessage(message: String) {
        lifecycleScope.launch {
            val fullMessage = "[システム] $message"
            
            // 대화 기록에 추가 (전체 및 섹션별)
            conversationHistory.add(fullMessage)
            addToSectionHistory(fullMessage)
            
            // 현재 메시지 카드에 표시
            tvCurrentMessage.text = ""
            
            // 시스템 메시지도 타이핑 효과 적용
            val systemTypingDelay = 40L
            for (i in fullMessage.indices) {
                tvCurrentMessage.text = fullMessage.substring(0, i + 1)
                delay(systemTypingDelay)
            }
            
            slmViewModel.updateGameState(
                newAffinity = slmViewModel.gameState.value!!.affinity,
                conversationCount = slmViewModel.gameState.value!!.conversationCount,
                conversationHistory = slmViewModel.gameState.value!!.conversationHistory + ChatMessage(ChatMessage.ROLE_SYSTEM, message)
            )
        }
    }

    private fun appendPlayerMessage(message: String) {
        lifecycleScope.launch {
            val fullMessage = "私: $message"
            
            // 대화 기록에 추가 (전체 및 섹션별)
            conversationHistory.add(fullMessage)
            addToSectionHistory(fullMessage)
            
            // 현재 메시지 카드에 표시
            tvCurrentMessage.text = ""
            
            // 플레이어 메시지도 타이핑 효과 적용 (조금 더 빠르게)
            val playerTypingDelay = 30L
            for (i in fullMessage.indices) {
                tvCurrentMessage.text = fullMessage.substring(0, i + 1)
                delay(playerTypingDelay)
            }
            
            slmViewModel.updateGameState(
                newAffinity = slmViewModel.gameState.value!!.affinity,
                conversationCount = slmViewModel.gameState.value!!.conversationCount,
                conversationHistory = slmViewModel.gameState.value!!.conversationHistory + ChatMessage(ChatMessage.ROLE_USER, message)
            )
        }
    }

    private fun updateCharacterImage(imagePath: String?) {
        imagePath?.let { path ->
            val characterName = slmViewModel.gameState.value!!.characterName.lowercase()
            val imageName = path.substringAfterLast('/')
            val correctPath = "images/$characterName/$imageName"

            try {
                assets.open(correctPath).use { inputStream ->
                    val drawable = android.graphics.drawable.Drawable.createFromStream(inputStream, null)
                    
                    drawable?.let {
                        // 메인 캐릭터 이미지 설정
                        ivCharacter.setImageDrawable(it)
                        
                        // 블러 배경 이미지 설정
                        setBlurredBackground(it)
                        
                        Log.d(TAG, "Image loaded successfully from: $correctPath")
                    } ?: Log.e(TAG, "Failed to create drawable from stream: $correctPath")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error loading image from assets: $correctPath", e)
                try {
                    val fallbackImageName = "${characterName}_lab_normal.png"
                    val fallbackPath = "images/$characterName/$fallbackImageName"
                    assets.open(fallbackPath).use { inputStream ->
                        val drawable = android.graphics.drawable.Drawable.createFromStream(inputStream, null)
                        
                        drawable?.let {
                            // 메인 캐릭터 이미지 설정
                            ivCharacter.setImageDrawable(it)
                            
                            // 블러 배경 이미지 설정
                            setBlurredBackground(it)
                            
                            Log.d(TAG, "Fallback image loaded successfully from: $fallbackPath")
                        } ?: Log.e(TAG, "Failed to create fallback drawable from stream: $fallbackPath")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error loading default image from assets.", e)
                }
            }
        }
    }

    private fun setBlurredBackground(drawable: Drawable) {
        try {
            // Drawable을 Bitmap으로 변환
            val originalBitmap = drawableToBitmap(drawable)
            
            // 자연스러운 블러 확장 이미지 생성
            val extendedImage = createNaturalExtendedImage(originalBitmap)
            val extendedDrawable = BitmapDrawable(resources, extendedImage)
            
            // 메인 이미지뷰에 확장된 이미지 설정
            ivCharacter.setImageDrawable(extendedDrawable)
            
        } catch (e: Exception) {
            Log.e(TAG, "Error creating blurred background", e)
            // 에러 발생시 원본 이미지 사용
            ivCharacter.setImageDrawable(drawable)
        }
    }

    private fun drawableToBitmap(drawable: Drawable): Bitmap {
        if (drawable is BitmapDrawable) {
            return drawable.bitmap
        }
        
        val bitmap = Bitmap.createBitmap(
            drawable.intrinsicWidth.takeIf { it > 0 } ?: 1,
            drawable.intrinsicHeight.takeIf { it > 0 } ?: 1,
            Bitmap.Config.ARGB_8888
        )
        
        val canvas = Canvas(bitmap)
        drawable.setBounds(0, 0, canvas.width, canvas.height)
        drawable.draw(canvas)
        return bitmap
    }

    private fun createNaturalExtendedImage(originalBitmap: Bitmap): Bitmap {
        // UI 컨테이너 크기 가져오기
        val containerWidth = ivCharacter.width
        val containerHeight = ivCharacter.height
        
        if (containerWidth <= 0 || containerHeight <= 0) {
            return originalBitmap
        }
        
        // 컨테이너 크기에 맞춰 스케일링 - 이미지를 중앙에 배치하되 컨테이너를 꽉 채움
        val widthScale = containerWidth.toFloat() / originalBitmap.width
        val heightScale = containerHeight.toFloat() / originalBitmap.height
        val scale = kotlin.math.max(widthScale, heightScale)
        
        val scaledWidth = (originalBitmap.width * scale).toInt()
        val scaledHeight = (originalBitmap.height * scale).toInt()
        
        // 최종 이미지 크기는 컨테이너 크기
        val result = Bitmap.createBitmap(containerWidth, containerHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(result)
        
        // 중앙 배치를 위한 오프셋 계산
        val centerLeft = (containerWidth - scaledWidth) / 2
        val centerTop = (containerHeight - scaledHeight) / 2
        
        // 양옆 20% 블러 영역 크기 계산
        val blurWidth = (containerWidth * 0.2f).toInt()
        val centerImageWidth = containerWidth - (blurWidth * 2)
        
        // 1. 전체 배경을 블러 처리된 이미지로 채움
        val backgroundBlurred = blurBitmap(originalBitmap)
        val backgroundScaled = Bitmap.createScaledBitmap(backgroundBlurred, containerWidth, containerHeight, true)
        canvas.drawBitmap(backgroundScaled, 0f, 0f, null)
        
        // 2. 중앙 60% 영역에 선명한 이미지 배치
        val centerRect = android.graphics.Rect(
            blurWidth,
            0,
            containerWidth - blurWidth,
            containerHeight
        )
        
        // 중앙 영역에 맞게 원본 이미지 스케일링
        val centerScale = kotlin.math.max(
            centerImageWidth.toFloat() / originalBitmap.width,
            containerHeight.toFloat() / originalBitmap.height
        )
        
        val centerScaledWidth = (originalBitmap.width * centerScale).toInt()
        val centerScaledHeight = (originalBitmap.height * centerScale).toInt()
        val centerScaled = Bitmap.createScaledBitmap(originalBitmap, centerScaledWidth, centerScaledHeight, true)
        
        // 중앙 이미지 배치 위치 계산
        val centerImageLeft = blurWidth + (centerImageWidth - centerScaledWidth) / 2
        val centerImageTop = (containerHeight - centerScaledHeight) / 2
        
        canvas.drawBitmap(centerScaled, centerImageLeft.toFloat(), centerImageTop.toFloat(), null)
        
        // 3. 양옆 블러 영역에 그라데이션 적용
        // 왼쪽 그라데이션
        val leftGradient = Paint().apply {
            shader = LinearGradient(
                0f, 0f, blurWidth.toFloat(), 0f,
                intArrayOf(0x00FFFFFF, 0xFFFFFFFF.toInt()),
                floatArrayOf(0f, 1f),
                Shader.TileMode.CLAMP
            )
            xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_IN)
        }
        canvas.drawRect(0f, 0f, blurWidth.toFloat(), containerHeight.toFloat(), leftGradient)
        
        // 오른쪽 그라데이션
        val rightGradient = Paint().apply {
            shader = LinearGradient(
                (containerWidth - blurWidth).toFloat(), 0f, containerWidth.toFloat(), 0f,
                intArrayOf(0xFFFFFFFF.toInt(), 0x00FFFFFF),
                floatArrayOf(0f, 1f),
                Shader.TileMode.CLAMP
            )
            xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_IN)
        }
        canvas.drawRect((containerWidth - blurWidth).toFloat(), 0f, containerWidth.toFloat(), containerHeight.toFloat(), rightGradient)
        
        return result
    }

    private fun blurBitmap(bitmap: Bitmap): Bitmap {
        // 강한 블러 효과 - 더 작게 만들어서 더 강한 블러 효과
        val smallBitmap = Bitmap.createScaledBitmap(bitmap, bitmap.width / 12, bitmap.height / 12, false)
        return Bitmap.createScaledBitmap(smallBitmap, bitmap.width, bitmap.height, true)
    }

    private fun onConfessButtonClicked() {
        val lastNode = currentScenarioFile?.scenarios?.lastOrNull()
        if (lastNode?.type == "input" && lastNode.requiresUserInput) {
            Log.w(TAG, "Confess button clicked while input is active. This should not happen.")
            return
        } else {
            // 고백 확인 다이얼로그
            AlertDialog.Builder(this)
                .setTitle("告白")
                .setMessage("${slmViewModel.gameState.value!!.characterName}に告白しますか？\n現在の好感度: ${slmViewModel.gameState.value!!.affinity}")
                .setPositiveButton("はい") { _, _ ->
                    performConfession()
                }
                .setNegativeButton("いいえ", null)
                .show()
        }
    }
    
    private fun performConfession() {
        val currentAffinity = slmViewModel.gameState.value!!.affinity
        
        // 호감도 구간별 성공 확률 계산
        val successRate = when {
            currentAffinity >= 90 -> 0.95f  // 거의 확실한 성공
            currentAffinity >= 80 -> 0.80f  // 높은 성공률
            currentAffinity >= 70 -> 0.60f  // 보통 성공률  
            currentAffinity >= 60 -> 0.35f  // 낮은 성공률
            currentAffinity >= 50 -> 0.15f  // 매우 낮은 성공률
            else -> 0.05f                   // 거의 실패
        }
        
        val isSuccess = kotlin.random.Random.nextFloat() < successRate
        val resultMessage = getConfessionResultMessage(currentAffinity, isSuccess)
        
        Log.d(TAG, "Confession attempt: affinity=$currentAffinity, successRate=$successRate, result=$isSuccess")
        
        if (isSuccess) {
            // 성공 시 해피 엔딩
            particleView.startAnimation(ParticleView.ParticleType.HEART)
            showGameOverDialog("💕 告白成功！\n\n$resultMessage\n\nハッピーエンド達成！")
        } else {
            // 실패 시에도 게임 종료가 아닌 피드백
            particleView.startAnimation(ParticleView.ParticleType.SAD)
            showConfessionFailureDialog(resultMessage)
        }
    }
    
    private fun getConfessionResultMessage(affinity: Int, isSuccess: Boolean): String {
        return if (isSuccess) {
            when {
                affinity >= 90 -> "「私も...ずっと先輩のことが好きでした！」\n恵の顔が真っ赤になりながらも、嬉しそうに微笑んでいる。"
                affinity >= 80 -> "「えっ...本当ですか？実は私も...」\n恵が恥ずかしそうに頷いている。"
                affinity >= 70 -> "「そ、そんなこと急に言われても...でも、嬉しいです」\n恵が困惑しながらも喜んでいる。"
                else -> "「ありがとうございます...私も、先輩といると楽しいです」\n恵が静かに微笑んでいる。"
            }
        } else {
            when {
                affinity >= 70 -> "「ごめんなさい...今はまだ、そういう気持ちになれなくて...」\n恵が申し訳なさそうに俯いている。でも嫌がってはいないようだ。"
                affinity >= 50 -> "「え...そ、そんな...急すぎます」\n恵が慌てて顔を赤くしている。完全に諦める必要はなさそうだ。"
                affinity >= 30 -> "「すみません...私、そういうの...」\n恵が困惑している。関係性を見直す必要がありそうだ。"
                else -> "「...申し訳ありませんが、お気持ちにお応えできません」\n恵が丁寧に断っている。"
            }
        }
    }
    
    private fun showConfessionFailureDialog(message: String) {
        val affinity = slmViewModel.gameState.value!!.affinity
        val canRetry = affinity >= 50 // 호감도 50 이상이면 재도전 기회
        
        val dialogBuilder = AlertDialog.Builder(this)
            .setTitle("告白の結果")
            .setMessage("💔 告白失敗...\n\n$message")
            .setCancelable(false)
        
        if (canRetry) {
            dialogBuilder
                .setPositiveButton("もう一度頑張る") { _, _ ->
                    // 게임 계속 진행 (호감도 약간 감소)
                    val newAffinity = (affinity - 10).coerceAtLeast(0)
                    slmViewModel.updateGameState(
                        newAffinity = newAffinity,
                        conversationCount = slmViewModel.gameState.value!!.conversationCount,
                        conversationHistory = slmViewModel.gameState.value!!.conversationHistory
                    )
                    appendSystemMessage("关係を見直して、もう一度チャンスを掴もう...")
                }
                .setNegativeButton("諦める") { _, _ ->
                    showGameOverDialog("友達として良い関係を続けることにした...\n\nフレンドエンド")
                }
        } else {
            dialogBuilder.setPositiveButton("メインメニューに戻る") { _, _ ->
                val intent = Intent(this, MainActivity::class.java)
                intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK
                startActivity(intent)
                finish()
            }
        }
        
        dialogBuilder.show()
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





    private fun enableChoiceButtons() {
        questionButton1.isEnabled = true
        questionButton2.isEnabled = true
        questionButton3.isEnabled = true
    }

    private fun disableAllOptions() {
        questionButton1.isEnabled = false
        questionButton2.isEnabled = false
        questionButton3.isEnabled = false
        questionButton1.setOnClickListener(null)
        questionButton2.setOnClickListener(null)
        questionButton3.setOnClickListener(null)
    }

    private fun hideAllChoiceButtons() {
        questionButton1.visibility = View.GONE
        questionButton2.visibility = View.GONE
        questionButton3.visibility = View.GONE
        
        // 선택지가 사라질 때는 특별한 처리 없음
        
        // 캐릭터 카드 크기 확대
        animateCharacterCardSize(0.65f)
    }

    // 섹션별 대화 기록에 추가하는 함수
    private fun addToSectionHistory(message: String) {
        if (!sectionHistory.containsKey(currentSectionName)) {
            sectionHistory[currentSectionName] = mutableListOf()
        }
        sectionHistory[currentSectionName]?.add(message)
    }
    
    // 대화 이력 오버레이 표시
    private fun showHistoryOverlay() {
        updateHistoryContent()
        historyOverlay.visibility = View.VISIBLE
        historyOverlay.alpha = 0f
        historyOverlay.animate()
            .alpha(1f)
            .setDuration(300)
            .start()
    }
    
    // 대화 이력 오버레이 숨기기
    private fun hideHistoryOverlay() {
        historyOverlay.animate()
            .alpha(0f)
            .setDuration(200)
            .withEndAction {
                historyOverlay.visibility = View.GONE
            }
            .start()
    }
    
    // 섹션별 대화 이력 내용 업데이트
    private fun updateHistoryContent() {
        val historyText = StringBuilder()
        
        sectionHistory.entries.forEach { (sectionName, messages) ->
            historyText.append("【$sectionName】\n")
            messages.forEach { message ->
                historyText.append("$message\n\n")
            }
            historyText.append("────────────────\n\n")
        }
        
        if (historyText.isEmpty()) {
            tvHistoryContent.text = "まだ対話履歴がありません。"
        } else {
            tvHistoryContent.text = historyText.toString().trimEnd()
        }
    }

    private fun animateCharacterCardSize(targetHeightPercent: Float) {
        val constraintSet = ConstraintSet()
        constraintSet.clone(mainLayout)
        
        // 높이 비율 설정
        constraintSet.constrainPercentHeight(R.id.characterCard, targetHeightPercent)
        
        // 부드러운 애니메이션 적용
        val transition = androidx.transition.ChangeBounds().apply {
            duration = 600L // 0.6초 애니메이션
            interpolator = AccelerateDecelerateInterpolator()
        }
        
        TransitionManager.beginDelayedTransition(mainLayout, transition)
        constraintSet.applyTo(mainLayout)
    }

    private fun updateConfessButtonVisibility() {
        // 시나리오 기반으로만 고백이 진행되도록 버튼을 항상 숨김
        btnConfess.visibility = View.GONE
    }
    
    private fun collectAndStoreSectionDialogues(scenarioId: String) {
        val scenarioFile = currentScenarioFile ?: return
        val dialogues = mutableListOf<com.secretlovemode.data.model.SectionDialogue>()
        
        scenarioFile.scenarios.forEach { node ->
            if (node.type == "message" && node.text.isNotBlank()) {
                val processedText = node.text.replace("{{playerName}}", playerName)
                dialogues.add(
                    com.secretlovemode.data.model.SectionDialogue(
                        speaker = node.speaker,
                        text = processedText,
                        type = node.type
                    )
                )
            }
        }
        
        Log.d(TAG, "Collected ${dialogues.size} dialogues for section $scenarioId")
        slmViewModel.addSectionDialogue(scenarioId, dialogues)
        
        // 섹션 요약 생성 (첫 번째 섹션은 제외하고 지연 실행으로 AI 충돌 방지)
        if (scenarioId != "1") {
            lifecycleScope.launch {
                delay(3000) // 3초 지연으로 다른 AI 작업과 충돌 방지
                generateSectionSummaryAsync(scenarioId, dialogues)
            }
        }
    }
    
    private fun generateSectionSummaryAsync(sectionId: String, dialogues: List<com.secretlovemode.data.model.SectionDialogue>) {
        lifecycleScope.launch {
            try {
                // Activity가 활성 상태인지 확인
                if (isFinishing || isDestroyed) {
                    Log.d(TAG, "Activity is finishing/destroyed, skipping section summary generation")
                    return@launch
                }
                
                val characterAi = slmViewModel.getCharacterAi()
                if (characterAi != null && characterAi.isModelReady) {
                    val gameState = slmViewModel.gameState.value ?: return@launch
                    
                    Log.d(TAG, "Starting section summary generation for section $sectionId")
                    val summary = characterAi.generateSectionSummary(sectionId, dialogues, gameState)
                    
                    if (summary != null && !isFinishing && !isDestroyed) {
                        // 요약을 GameState에 저장
                        val currentState = slmViewModel.gameState.value ?: return@launch
                        val updatedSummaries = currentState.sectionSummaries.toMutableMap()
                        updatedSummaries[sectionId] = summary
                        
                        slmViewModel.updateGameStateWithSummary(updatedSummaries)
                        Log.d(TAG, "Section summary saved for section $sectionId")
                    } else {
                        Log.w(TAG, "Failed to generate summary for section $sectionId or activity is finishing")
                    }
                } else {
                    Log.w(TAG, "CharacterAi not ready for section summary generation")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error generating section summary for $sectionId", e)
            }
        }
    }
    
    private fun showSectionTransitionOverlay(sectionId: String, sectionTitle: String) {
        val transitionMessage = getTransitionMessage(sectionId, sectionTitle)
        
        tvSectionTransition.text = transitionMessage
        sectionTransitionOverlay.visibility = View.VISIBLE
        
        // 자동으로 3초 후에 사라지도록 설정 (사용자가 터치하지 않는 경우)
        sectionTransitionOverlay.postDelayed({
            if (sectionTransitionOverlay.visibility == View.VISIBLE) {
                hideSectionTransitionOverlay()
            }
        }, 10000)
        
        // 시스템 메시지를 대화 기록에 추가
        slmViewModel.updateGameState(
            newAffinity = slmViewModel.gameState.value!!.affinity,
            conversationCount = slmViewModel.gameState.value!!.conversationCount,
            conversationHistory = slmViewModel.gameState.value!!.conversationHistory + 
                ChatMessage(
                    ChatMessage.ROLE_SYSTEM,
                    transitionMessage
                )
        )
    }
    
    private fun hideSectionTransitionOverlay() {
        sectionTransitionOverlay.visibility = View.GONE
    }
    
    private fun getTransitionMessage(sectionId: String, sectionTitle: String): String {
        return when (sectionId) {
            "2A", "2B", "2C" -> "📱 M1GPのアプリアイデア決定タイム！\n　　めぐみちゃんとの共同作業が始まる...\n\n『$sectionTitle』"
            "3A", "3B", "3C" -> "💻 開発手法を選択する重要な局面\n　　技術的な話でめぐみちゃんとの距離が縮まるかも？\n\n『$sectionTitle』"
            "4A", "4B", "4C" -> "🛠️ 実際の開発作業開始！\n　　選んだ場所での作業がどんな展開を生むのか...\n\n『$sectionTitle』"
            "5A", "5B", "5C" -> "🍽️ 開発の疲れを癒やす食事タイム\n　　リラックスした雰囲気で関係が深まりそう\n\n『$sectionTitle』"
            "6A", "6B" -> "🚌 ついに運命の日がやってきた！\n　　研究室旅行とM1GP発表...この旅で何かが変わる予感\n\n『$sectionTitle』"
            "7A", "7B" -> "💕 物語の最終章、感動のクライマックス\n　　すべての選択が結実する時...\n\n『$sectionTitle』"
            "ending" -> "🎬 すべての物語が完結します\n\n『$sectionTitle』"
            else -> "📖 新しい展開が待っています...\n\n『$sectionTitle』"
        }
    }
    
    /**
     * SLM 추론 지연을 숨기기 위한 시각적 효과들 시작
     */
    private fun startDelayDistractionEffects() {
        // 1. 캐릭터 이미지에 미묘한 애니메이션 (고민하는 느낌)
        startCharacterThinkingAnimation()
        
        // 2. 하트 아이콘에 미묘한 펄스 효과 (감정 변화 예고)
        startHeartPulseEffect()
        
        // 3. 캐릭터 이미지에 "思考中…" 텍스트 표시 (내부에서 애니메이션 자동 시작)
        showThinkingTextOverlay()
    }
    
    /**
     * 지연 숨김 효과들 종료
     */
    private fun stopDelayDistractionEffects() {
        particleAnimationJob?.cancel()
        characterImageAnimator?.cancel()
        
        // 캐릭터 이미지를 원래 크기로 복원
        ivCharacter.scaleX = 1.0f
        ivCharacter.scaleY = 1.0f
        
        hideThinkingTextOverlay()
        // 하트 애니메이션은 updateStatusDisplay에서 관리되므로 건드리지 않음
    }
    
    /**
     * "생각하는 중..." 타이핑 시뮬레이션 - 캐릭터 오버레이에 표시
     */
    private suspend fun simulateThinkingAnimation() {
        val thinkingMessages = listOf(
            "...",
            "......",
            ".........",
            "めぐみは考えている...",
            "どう答えようかな...",
            "悩んでいる...",
            "考え中...",
            "どうしよう...",
            "..."
        )
        
        // 오버레이가 이미 표시되어 있는지 확인
        if (tvThinkingOverlay.visibility != View.VISIBLE) {
            return
        }
        
        for (message in thinkingMessages) {
            // 캐릭터 오버레이에 메시지 표시
            tvThinkingOverlay.text = message
            
            delay(kotlin.random.Random.nextLong(600, 1200)) // 600~1200ms 랜덤 딜레이
            
            // 애니메이션이 중단되었으면 종료
            if (tvThinkingOverlay.visibility != View.VISIBLE) {
                break
            }
        }
    }
    
    /**
     * 캐릭터 이미지 고민 애니메이션 (더 큰 스케일 변화)
     */
    private fun startCharacterThinkingAnimation() {
        characterImageAnimator = ValueAnimator.ofFloat(1.0f, 0.85f, 1.0f).apply {
            duration = 1800
            repeatCount = ValueAnimator.INFINITE
            addUpdateListener { animator ->
                val scale = animator.animatedValue as Float
                ivCharacter.scaleX = scale
                ivCharacter.scaleY = scale
            }
            start()
        }
    }
    
    /**
     * 하트 아이콘 미묘한 펄스 효과
     */
    private fun startHeartPulseEffect() {
        // 기존 하트 애니메이션과 겹치지 않도록 미묘하게
        ivHeartIcon.animate()
            .scaleX(1.1f)
            .scaleY(1.1f)
            .setDuration(600)
            .withEndAction {
                ivHeartIcon.animate()
                    .scaleX(1.0f)
                    .scaleY(1.0f)
                    .setDuration(600)
                    .start()
            }
            .start()
    }
    
    /**
     * 캐릭터 이미지에 "思考中…" 텍스트 오버레이 표시
     */
    private fun showThinkingTextOverlay() {
        tvThinkingOverlay.text = "思考中…" // 초기 텍스트 설정
        tvThinkingOverlay.visibility = View.VISIBLE
        
        // 점점 나타나는 애니메이션
        tvThinkingOverlay.alpha = 0f
        tvThinkingOverlay.animate()
            .alpha(1f)
            .setDuration(500)
            .withEndAction {
                // 페이드인 완료 후 생각하는 애니메이션 시작
                lifecycleScope.launch {
                    delay(1000) // 0.5초 후 시작
                    simulateThinkingAnimation()
                }
            }
            .start()
    }
    
    /**
     * "思考中…" 텍스트 오버레이 숨김
     */
    private fun hideThinkingTextOverlay() {
        tvThinkingOverlay.animate()
            .alpha(0f)
            .setDuration(300)
            .withEndAction {
                tvThinkingOverlay.visibility = View.GONE
            }
            .start()
    }
    
    private fun handleConfessionResponse() {
        Log.d(TAG, "handleConfessionResponse() called")
        
        val confessionMessage = slmViewModel.gameState.value?.confessionInput
        if (confessionMessage.isNullOrEmpty()) {
            Log.e(TAG, "No confession message found")
            showGameOverDialog("고백 메시지가 없습니다.")
            return
        }
        
        Log.d(TAG, "Starting SLM inference for confession: $confessionMessage")
        
        // SLM으로 캐릭터의 반응 생성
        lifecycleScope.launch {
            try {
                // 로딩 표시
                showLoadingProgressBar(true)
                
                val characterName = slmViewModel.gameState.value?.characterName ?: "캐릭터"
                val currentAffinity = slmViewModel.gameState.value?.affinity ?: 50
                
                // SLM에게 고백에 대한 반응 요청
                val response = characterAi?.generateConfessionResponse(
                    confessionMessage = confessionMessage,
                    characterName = characterName,
                    affinity = currentAffinity,
                    conversationHistory = slmViewModel.gameState.value?.conversationHistory ?: emptyList()
                )
                
                showLoadingProgressBar(false)
                
                if (response != null) {
                    Log.d(TAG, "SLM response received: $response")
                    // 캐릭터의 반응을 메시지로 표시
                    displayAiResponse(response)
                } else {
                    Log.e(TAG, "SLM response was null")
                    showGameOverDialog("AI 응답을 생성할 수 없습니다.")
                }
                
            } catch (e: Exception) {
                Log.e(TAG, "Error during SLM inference", e)
                showLoadingProgressBar(false)
                showGameOverDialog("AI 추론 중 오류가 발생했습니다: ${e.message}")
            }
        }
    }
    
    private fun displayAiResponse(response: String) {
        Log.d(TAG, "displayAiResponse: $response")
        
        // AI 응답을 현재 메시지로 표시
        lifecycleScope.launch {
            val characterName = slmViewModel.gameState.value?.characterName ?: "캐릭터"
            val fullMessage = "$characterName: $response"
            
            // 대화 기록에 추가
            conversationHistory.add(fullMessage)
            addToSectionHistory(fullMessage)
            
            // 타이핑 효과로 표시
            tvCurrentMessage.text = ""
            val typingDelay = 50L
            
            for (i in fullMessage.indices) {
                tvCurrentMessage.text = fullMessage.substring(0, i + 1)
                delay(typingDelay)
            }
            
            // 응답 완료 후 게임 종료 또는 다음 시나리오로 진행
            delay(2000) // 2초 대기
            
            // 호감도에 따른 엔딩 결정
            val currentAffinity = slmViewModel.gameState.value?.affinity ?: 50
            if (currentAffinity >= 70) {
                particleView.startAnimation(ParticleView.ParticleType.HEART)
                showGameOverDialog("💕 해피 엔딩!\n\n$response\n\n게임을 완료했습니다!")
            } else {
                // 낮은 호감도의 경우 계속 진행하거나 다른 엔딩
                processCurrentScenarioNode()
            }
        }
    }
    
    private fun showLoadingProgressBar(show: Boolean) {
        if (show) {
            loadingProgressCard.visibility = View.VISIBLE
            loadingProgressCard.alpha = 0f
            loadingProgressCard.animate()
                .alpha(1f)
                .setDuration(300)
                .start()
        } else {
            loadingProgressCard.animate()
                .alpha(0f)
                .setDuration(200)
                .withEndAction {
                    loadingProgressCard.visibility = View.GONE
                }
                .start()
        }
    }
}