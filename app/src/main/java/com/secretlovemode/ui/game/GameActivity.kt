package com.secretlovemode.ui.game

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import com.secretlovemode.MyApplication
import com.secretlovemode.R
import com.secretlovemode.data.model.ScenarioOption
import com.secretlovemode.domain.CharacterAi
import com.secretlovemode.ui.main.SlmViewModel
import com.secretlovemode.ui.ranking.RankingActivity
import com.secretlovemode.util.LanguageManager

class GameActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "GameActivity"
    }

    // Core managers
    private lateinit var uiManager: GameUIManager
    private lateinit var scenarioProcessor: ScenarioProcessor
    private lateinit var animationManager: AnimationManager
    private lateinit var conversationManager: ConversationManager
    private lateinit var gameStateHandler: GameStateHandler
    private lateinit var confessionHandler: ConfessionHandler

    // Core components
    private lateinit var slmViewModel: SlmViewModel
    private lateinit var playerName: String
    private var characterAi: CharacterAi? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d(TAG, "onCreate() called")
        
        // Force light mode
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
        delegate.localNightMode = AppCompatDelegate.MODE_NIGHT_NO
        
        // Initialize core components
        slmViewModel = (application as MyApplication).slmViewModel
        playerName = intent.getStringExtra("PLAYER_NAME") ?: "플레이어"
        Log.d(TAG, "Player name received from intent: '$playerName'")
        slmViewModel.setPlayerName(playerName)
        setContentView(R.layout.activity_game)
        
        // Initialize managers
        initializeManagers()
        
        // Setup UI and observers
        setupGame()
        
        Log.d(TAG, "onCreate() completed")
    }

    private fun initializeManagers() {
        // Initialize managers in dependency order
        uiManager = GameUIManager(this, this)
        animationManager = AnimationManager(this, this, uiManager)
        conversationManager = ConversationManager(this, this, slmViewModel, uiManager)
        scenarioProcessor = ScenarioProcessor(this, this, slmViewModel, uiManager)
        gameStateHandler = GameStateHandler(this, this, slmViewModel, animationManager, conversationManager, scenarioProcessor)
        confessionHandler = ConfessionHandler(this, this, slmViewModel, conversationManager)

        // Initialize all managers
        uiManager.initializeViews()
        uiManager.initializeMultilingualUI()
        
        scenarioProcessor.initialize(playerName)
        confessionHandler.initialize(playerName)
        
        characterAi = slmViewModel.getCharacterAi()
        gameStateHandler.initialize(characterAi)
    }

    private fun setupGame() {
        // Check if model is ready
        if (characterAi == null || !characterAi!!.isModelReady) {
            val errorMessage = if (LanguageManager.getLanguage(this) == "en") {
                "Model is not ready yet. Returning to main screen."
            } else {
                "モデルがまだ呼び出されていません。最初画面に戻ります。"
            }
            Toast.makeText(this, errorMessage, Toast.LENGTH_LONG).show()
            finish()
            return
        }

        // Setup UI interactions
        uiManager.setupClickListeners(
            onConfessClicked = { confessionHandler.onConfessButtonClicked() },
            onMainLayoutClicked = { 
                // 타이핑 스킵을 우선 시도
                uiManager.skipTyping()
                // 그 다음 기존 메인 레이아웃 클릭 처리
                scenarioProcessor.handleMainLayoutClick() 
            },
            onHistoryClicked = { conversationManager.showHistoryOverlay() },
            onCloseHistoryClicked = { conversationManager.hideHistoryOverlay() }
        )

        // Observe game state changes
        gameStateHandler.observeGameState()

        // Start the game
        startGame()
    }

    private fun startGame() {
        Log.d(TAG, "startGame() called")
        scenarioProcessor.loadScenario("1") // Load first scenario
    }

    override fun onResume() {
        super.onResume()
        scenarioProcessor.handleResume()
    }

    override fun onPause() {
        super.onPause()
        animationManager.onPause()
    }
    
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        scenarioProcessor.handleActivityResult(requestCode, resultCode, data)
    }

    // Public methods for managers to call
    fun showGameOverDialog(message: String, gameEndType: String = "GAME_OVER") {
        val dialogTitle = if (LanguageManager.getLanguage(this) == "en") "Game Over" else "ゲームオーバー"
        val buttonText = if (LanguageManager.getLanguage(this) == "en") "View Results" else "結果を見る"
        
        AlertDialog.Builder(this)
            .setTitle(dialogTitle)
            .setMessage(message)
            .setCancelable(false)
            .setPositiveButton(buttonText) { _, _ ->
                navigateToRanking(gameEndType)
            }
            .show()
    }
    
    private fun navigateToRanking(gameEndType: String) {
        val intent = Intent(this, RankingActivity::class.java)
        intent.putExtra(RankingActivity.EXTRA_PLAYER_NAME, playerName)
        intent.putExtra(RankingActivity.EXTRA_FINAL_AFFINITY, slmViewModel.gameState.value?.affinity ?: 0)
        intent.putExtra(RankingActivity.EXTRA_CONFESSION_SUCCESS, gameEndType == "CONFESSION_SUCCESS")
        intent.putExtra(RankingActivity.EXTRA_GAME_END_TYPE, gameEndType)
        startActivity(intent)
        finish()
    }

    fun showLoadingProgressBar(show: Boolean) {
        uiManager.showLoadingProgressBar(show)
    }

    fun updateCharacterImage(imagePath: String?) {
        animationManager.updateCharacterImage(imagePath)
    }

    fun addToConversationHistory(message: String) {
        conversationManager.addToConversationHistory(message)
    }

    fun appendSystemMessage(message: String) {
        conversationManager.appendSystemMessage(message)
    }

    fun onPlayerOptionSelected(selectedOption: ScenarioOption, situationText: String) {
        gameStateHandler.onPlayerOptionSelected(selectedOption, situationText)
    }
    
    fun collectAndStoreSectionDialogues(scenarioId: String, scenarioFile: com.secretlovemode.data.model.ScenarioFile, playerName: String) {
        conversationManager.collectAndStoreSectionDialogues(scenarioId, scenarioFile, playerName)
    }
    
    fun disableAllChoiceButtons() {
        uiManager.disableAllOptions()
    }
    
    fun hideAllChoiceButtons() {
        uiManager.hideAllChoiceButtons()
    }
}