package com.secretlovemode.ui.game

import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.ScrollView
import android.widget.TextView
import androidx.cardview.widget.CardView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.secretlovemode.R
import com.secretlovemode.data.model.ScenarioOption
import com.secretlovemode.ui.common.ParticleView
import com.secretlovemode.util.LanguageManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class GameUIManager(
    private val activity: GameActivity,
    private val lifecycleOwner: LifecycleOwner
) {
    // UI 요소들
    lateinit var tvCurrentMessage: TextView
    lateinit var questionButton1: Button
    lateinit var questionButton2: Button
    lateinit var questionButton3: Button
    lateinit var btnConfess: Button
    lateinit var tvCharacterName: TextView
    lateinit var ivHeartIcon: ImageView
    lateinit var ivCharacter: ImageView
    lateinit var ivBackgroundLeft: ImageView
    lateinit var ivBackgroundRight: ImageView
    lateinit var currentMessageCard: CardView
    lateinit var particleView: ParticleView
    lateinit var characterCard: CardView
    lateinit var mainLayout: ConstraintLayout
    lateinit var sectionTransitionOverlay: CardView
    lateinit var tvSectionTransition: TextView
    lateinit var tvThinkingOverlay: TextView
    lateinit var choiceScrollView: ScrollView
    lateinit var loadingProgressCard: CardView
    
    // 대화 이력 UI 요소들
    lateinit var fabHistory: FloatingActionButton
    lateinit var historyOverlay: ConstraintLayout
    lateinit var btnCloseHistory: android.widget.ImageButton
    lateinit var tvHistoryContent: TextView
    
    // 타이핑 스킵 변수
    private var isTypingSkipped = false

    fun initializeViews() {
        tvCurrentMessage = activity.findViewById(R.id.tvCurrentMessage)
        questionButton1 = activity.findViewById(R.id.questionButton1)
        questionButton2 = activity.findViewById(R.id.questionButton2)
        questionButton3 = activity.findViewById(R.id.questionButton3)
        btnConfess = activity.findViewById(R.id.btnConfess)
        tvCharacterName = activity.findViewById(R.id.tvCharacterName)
        ivHeartIcon = activity.findViewById(R.id.ivHeartIcon)
        ivCharacter = activity.findViewById(R.id.ivCharacter)
        ivBackgroundLeft = activity.findViewById(R.id.ivBackgroundLeft)
        ivBackgroundRight = activity.findViewById(R.id.ivBackgroundRight)
        currentMessageCard = activity.findViewById(R.id.currentMessageCard)
        
        // 새로운 대화 이력 UI 요소들
        fabHistory = activity.findViewById(R.id.fabHistory)
        historyOverlay = activity.findViewById(R.id.historyOverlay)
        btnCloseHistory = activity.findViewById(R.id.btnCloseHistory)
        tvHistoryContent = activity.findViewById(R.id.tvHistoryContent)
        particleView = activity.findViewById(R.id.particleView)
        characterCard = activity.findViewById(R.id.characterCard)
        mainLayout = activity.findViewById(R.id.main)
        sectionTransitionOverlay = activity.findViewById(R.id.sectionTransitionOverlay)
        tvSectionTransition = activity.findViewById(R.id.tvSectionTransition)
        tvThinkingOverlay = activity.findViewById(R.id.tvThinkingOverlay)
        choiceScrollView = activity.findViewById(R.id.choiceScrollView)
        
        // 로딩 프로그래스바 관련 UI 요소들
        loadingProgressCard = activity.findViewById(R.id.loadingProgressCard)
    }

    fun initializeMultilingualUI() {
        try {
            // Update UI texts based on selected language
            activity.findViewById<TextView>(R.id.tvCharacterName)?.text = LanguageManager.getText(activity, "current_scenario")
            activity.findViewById<TextView>(R.id.tvThinkingOverlay)?.text = LanguageManager.getText(activity, "thinking")
            activity.findViewById<TextView>(R.id.tvHistoryTitle)?.text = LanguageManager.getText(activity, "conversation_history")
            activity.findViewById<TextView>(R.id.tvHistoryContent)?.text = LanguageManager.getText(activity, "no_history")
            activity.findViewById<Button>(R.id.btnConfess)?.text = LanguageManager.getText(activity, "confess")
            
            android.util.Log.d("GameUIManager", "Multilingual UI initialized successfully")
        } catch (e: Exception) {
            android.util.Log.e("GameUIManager", "Error initializing multilingual UI", e)
        }
    }

    fun setupClickListeners(
        onConfessClicked: () -> Unit,
        onMainLayoutClicked: () -> Unit,
        onHistoryClicked: () -> Unit,
        onCloseHistoryClicked: () -> Unit
    ) {
        btnConfess.setOnClickListener { onConfessClicked() }
        mainLayout.setOnClickListener { onMainLayoutClicked() }
        fabHistory.setOnClickListener { onHistoryClicked() }
        btnCloseHistory.setOnClickListener { onCloseHistoryClicked() }
        historyOverlay.setOnClickListener { onCloseHistoryClicked() }
    }

    fun setupChoiceButtons(
        options: List<ScenarioOption>,
        situationText: String,
        onOptionSelected: (ScenarioOption, String) -> Unit
    ) {
        val buttons = listOf(questionButton1, questionButton2, questionButton3)
        
        android.util.Log.d("GameUIManager", "setupChoiceButtons: showing ${options.size} options")
        buttons.forEach { it.visibility = View.GONE }

        options.take(buttons.size).forEachIndexed { index, option ->
            val button = buttons[index]
            button.text = option.text
            button.visibility = View.VISIBLE
            button.setOnClickListener { 
                // 클릭 즉시 모든 버튼 비활성화 (중복 클릭 방지)
                android.util.Log.d("GameUIManager", "🔒 Button clicked, immediately disabling all choice buttons")
                disableAllOptions()
                hideAllChoiceButtons()
                onOptionSelected(option, situationText) 
            }
            android.util.Log.d("GameUIManager", "Option $index: ${option.text} (${option.text.length} chars)")
        }
        enableChoiceButtons()
        
        // 선택지 스크롤뷰 맨 위로 초기화
        choiceScrollView.post {
            choiceScrollView.scrollTo(0, 0)
        }
    }

    fun enableChoiceButtons() {
        questionButton1.isEnabled = true
        questionButton2.isEnabled = true
        questionButton3.isEnabled = true
    }

    fun disableAllOptions() {
        questionButton1.isEnabled = false
        questionButton2.isEnabled = false
        questionButton3.isEnabled = false
        questionButton1.setOnClickListener(null)
        questionButton2.setOnClickListener(null)
        questionButton3.setOnClickListener(null)
    }

    fun hideAllChoiceButtons() {
        questionButton1.visibility = View.GONE
        questionButton2.visibility = View.GONE
        questionButton3.visibility = View.GONE
    }

    fun updateConfessButtonVisibility() {
        // 시나리오 기반으로만 고백이 진행되도록 버튼을 항상 숨김
        btnConfess.visibility = View.GONE
    }

    suspend fun displayTypingMessage(fullMessage: String) {
        tvCurrentMessage.text = ""
        isTypingSkipped = false
        
        // 타이핑 애니메이션으로 텍스트 출력
        val typingDelay = 50L
        
        for (i in fullMessage.indices) {
            if (isTypingSkipped) {
                // 타이핑이 스킵되면 나머지 텍스트를 즉시 표시
                tvCurrentMessage.text = fullMessage
                break
            }
            
            tvCurrentMessage.text = fullMessage.substring(0, i + 1)
            delay(typingDelay)
        }
    }
    
    fun skipTyping() {
        isTypingSkipped = true
    }

    fun showSectionTransitionOverlay(sectionId: String, sectionTitle: String) {
        val transitionMessage = getTransitionMessage(sectionId, sectionTitle)
        
        tvSectionTransition.text = transitionMessage
        sectionTransitionOverlay.visibility = View.VISIBLE
        
        // 자동으로 10초 후에 사라지도록 설정
        sectionTransitionOverlay.postDelayed({
            if (sectionTransitionOverlay.visibility == View.VISIBLE) {
                hideSectionTransitionOverlay()
            }
        }, 10000)
    }
    
    fun hideSectionTransitionOverlay() {
        sectionTransitionOverlay.visibility = View.GONE
    }

    fun showLoadingProgressBar(show: Boolean) {
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
    
    private fun getTransitionMessage(sectionId: String, sectionTitle: String): String {
        val language = LanguageManager.getLanguage(activity)
        
        return if (language == "en") {
            when (sectionId) {
                "2A", "2B", "2C" -> "📱 M1GP App Idea Decision Time!\n    Joint work with Megumi begins...\n\n『$sectionTitle』"
                "3A", "3B", "3C" -> "💻 Crucial moment to choose development method\n    Technical discussions might bring you closer to Megumi?\n\n『$sectionTitle』"
                "4A", "4B", "4C" -> "🛠️ Actual development work starts!\n    What will unfold in the chosen workspace...\n\n『$sectionTitle』"
                "5A", "5B", "5C" -> "🍽️ Meal time to heal development fatigue\n    Your relationship might deepen in this relaxed atmosphere\n\n『$sectionTitle』"
                "6A", "6B" -> "🚌 The fateful day has finally arrived!\n    Lab trip and M1GP presentation... something will change on this journey\n\n『$sectionTitle』"
                "7A", "7B" -> "💕 Final chapter of the story, emotional climax\n    Time for all your choices to bear fruit...\n\n『$sectionTitle』"
                "ending" -> "🎬 The entire story comes to an end\n\n『$sectionTitle』"
                else -> "📖 New developments await...\n\n『$sectionTitle』"
            }
        } else {
            when (sectionId) {
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
    }
}