package com.secretlovemode.ui.main

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.secretlovemode.data.model.Character
import com.secretlovemode.MyApplication
import com.secretlovemode.data.repository.PromptManager
import com.secretlovemode.R
import com.secretlovemode.ui.game.GameActivity
import androidx.constraintlayout.widget.Group
import kotlinx.coroutines.launch
import com.secretlovemode.data.repository.ScenarioManager
import com.secretlovemode.ui.language.LanguageSelectionActivity
import com.secretlovemode.util.LanguageManager
import android.annotation.SuppressLint
import androidx.appcompat.app.AppCompatDelegate


class MainActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "MainActivity"
    }

    // UI elements
    private lateinit var modelSelectionGroup: Group
    private lateinit var tvSelectedModelFile: TextView // Display selected model file name
    private lateinit var btnSelectModelFile: Button // Model selection button
    private lateinit var btnClearSelection: Button // Clear selection button
    private lateinit var progressBarMain: ProgressBar
    private lateinit var tvLoadingMessageMain: TextView
    private lateinit var etPlayerName: EditText
    private lateinit var btnConfirmPlayerName: Button
    private lateinit var startButton: Button


    // State variables
    private lateinit var slmViewModel: SlmViewModel

    // Character information (fixed for single scenario)
    private val fixedCharacter = Character(
        id = "megumi",
        characterName = "めぐみ",
        characterPersona = "情報系のツンデレ修士",
        modelFileName = "gemma-3n-E2B-it-int4.task",
        scenarioFileName = "session1.json"
    )

    /**
     *  Launcher to open file selection and get URI of selected file
     */
    private val selectModelLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        Log.d(TAG, "selectModelLauncher result URI: $uri")
        uri?.let {
            slmViewModel.setSelectedModelUri(it)
            Log.d(TAG, "setSelectedModelUri called with: $it")
        } ?: run {
            Log.d(TAG, "selectModelLauncher received null URI.")
        }
    }


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        Log.d(TAG, "MainActivity onCreate called")
        
        // Force light mode
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
        
        // For testing: Clear language selection to always show language screen
        // Only clear if we're not coming from LanguageSelectionActivity
        val fromLanguageSelection = intent.getBooleanExtra("FROM_LANGUAGE_SELECTION", false)
        if (!fromLanguageSelection) {
            LanguageManager.clearLanguageSelection(this)
        }
        
        // Always go to language selection first
        if (!LanguageManager.isLanguageSelected(this)) {
            Log.d(TAG, "Language not selected, redirecting to LanguageSelectionActivity")
            val intent = Intent(this, LanguageSelectionActivity::class.java)
            startActivity(intent)
            finish()
            return
        }
        
        Log.d(TAG, "Language is selected: ${LanguageManager.getLanguage(this)}")
        
        setContentView(R.layout.activity_main)

        PromptManager.loadPrompts(applicationContext)
        slmViewModel = (application as MyApplication).slmViewModel

        initializeViews()
        initializeMultilingualUI()
        setupClickListeners()
        observeViewModel()

        // Initially hide model selection and start button
        modelSelectionGroup.visibility = View.GONE
        startButton.visibility = View.GONE
        startButton.isEnabled = false
    }

    private fun initializeViews() {
        tvSelectedModelFile = findViewById(R.id.tvSelectedModelFile)
        btnSelectModelFile = findViewById(R.id.btnSelectModelFile)
        btnClearSelection = findViewById(R.id.btnClearSelection)
        progressBarMain = findViewById(R.id.progressBarMain)
        tvLoadingMessageMain = findViewById(R.id.tvLoadingMessageMain)
        etPlayerName = findViewById(R.id.etPlayerName)
        btnConfirmPlayerName = findViewById(R.id.btnConfirmPlayerName)
        modelSelectionGroup = findViewById(R.id.modelSelectionGroup)
        startButton = findViewById(R.id.startButton)
    }

    private fun initializeMultilingualUI() {
        try {
            // Update UI texts based on selected language
            findViewById<TextView>(R.id.tvPlayerNameLabel)?.text = LanguageManager.getText(this, "player_name_label")
            findViewById<EditText>(R.id.etPlayerName)?.hint = LanguageManager.getText(this, "name_hint")
            findViewById<Button>(R.id.btnConfirmPlayerName)?.text = LanguageManager.getText(this, "confirm")
            findViewById<TextView>(R.id.tvSelectModelLabel)?.text = LanguageManager.getText(this, "select_model_label")
            findViewById<Button>(R.id.btnSelectModelFile)?.text = LanguageManager.getText(this, "select_model_file")
            findViewById<Button>(R.id.btnClearSelection)?.text = LanguageManager.getText(this, "clear_selection")
            findViewById<Button>(R.id.startButton)?.text = LanguageManager.getText(this, "start_game")
            
            Log.d(TAG, "Multilingual UI initialized successfully for MainActivity")
        } catch (e: Exception) {
            Log.e(TAG, "Error initializing multilingual UI in MainActivity", e)
        }
    }

    private fun setupClickListeners() {
        btnConfirmPlayerName.setOnClickListener {
            val playerName = etPlayerName.text.toString().trim()
            if (playerName.isNotEmpty()) {
                // Save player name to ViewModel
                slmViewModel.setPlayerName(playerName)
                Log.d(TAG, "Player name set to: $playerName")
                
                // Hide name input UI
                findViewById<TextView>(R.id.tvPlayerNameLabel).visibility = View.GONE
                etPlayerName.visibility = View.GONE
                btnConfirmPlayerName.visibility = View.GONE

                // Show model selection UI in center
                findViewById<androidx.constraintlayout.widget.Group>(R.id.modelSelectionGroup).visibility = View.VISIBLE
                
                // Start button remains hidden until model is selected
                startButton.visibility = View.GONE
                startButton.isEnabled = false
            } else {
                Toast.makeText(this, LanguageManager.getText(this, "enter_player_name"), Toast.LENGTH_SHORT).show()
            }
        }

        // When model selection button is clicked, open file selection.
        btnSelectModelFile.setOnClickListener {
            selectModelLauncher.launch("*/*")
        }


        // Implement clear selection button functionality
        btnClearSelection.setOnClickListener {
            slmViewModel.setSelectedModelUri(null)
        }

        startButton.setOnClickListener {
            Log.d(TAG, "Start button click listener attached.")
            Log.d(TAG, "Start button clicked.")
            showLoadingUI(true)
            startButton.isEnabled = false

            val currentModelUri = slmViewModel.selectedModelUri.value
            Log.d(TAG, "Current model URI at click: $currentModelUri")

            lifecycleScope.launch {
                Log.d(TAG, "Attempting to initialize CharacterAi...")
                val modelUri = slmViewModel.selectedModelUri.value
                Log.d(TAG, "Model URI before check: $modelUri")
                if (modelUri != null) {
                    val success = slmViewModel.initializeCharacterAi(contentResolver, modelUri)
                    if (success) {
                        // Load fixed scenario after model is ready
                        // ScenarioManager.loadScenarios(applicationContext, fixedCharacter.scenarioFileName)

                        val intent = Intent(this@MainActivity, GameActivity::class.java)
                        intent.putExtra("PLAYER_NAME", slmViewModel.playerName.value)
                        startActivity(intent)
                        finish() // 메인 화면을 종료하여 뒤로 가기 시 다시 나타나지 않도록 함
                    } else {
                        showLoadingUI(false)
                        startButton.isEnabled = true
                        Toast.makeText(this@MainActivity, slmViewModel.loadingError.value ?: "モデルの読み込みに失敗しました。", Toast.LENGTH_LONG).show()
                    }
                } else {
                    showLoadingUI(false)
                    startButton.isEnabled = true
                    Toast.makeText(this@MainActivity, LanguageManager.getText(this@MainActivity, "no_model_selected"), Toast.LENGTH_SHORT).show()
                }
            }
        }
    }


    


    private fun clearSelections() {
        tvSelectedModelFile.text = "未選択"
        slmViewModel.unloadModel() // Also initialize ViewModel's model state
        ScenarioManager.clearScenarios() // Also initialize scenario data
        updateButtonStates()
    }

    /**
     * ViewModel의 LiveData를 관찰하여 UI를 자동으로 업데이트합니다.
     */
    private fun observeViewModel() {
        slmViewModel.selectedModelUri.observe(this) { uri ->
            Log.d(TAG, "Selected URI observed: $uri")
            if (uri != null) {
                try {
                    val fileName = getFileNameFromUri(uri) ?: LanguageManager.getText(this, "model_selected")
                    tvSelectedModelFile.text = "${LanguageManager.getText(this, "model_selected")} $fileName"
                    Log.d(TAG, "Updated selected file name: $fileName")
                } catch (e: SecurityException) {
                    Log.w(TAG, "SecurityException accessing URI: ${e.message}")
                    tvSelectedModelFile.text = LanguageManager.getText(this, "model_selected")
                } catch (e: Exception) {
                    Log.w(TAG, "Error accessing URI: ${e.message}")
                    tvSelectedModelFile.text = LanguageManager.getText(this, "model_selected")
                }
            } else {
                tvSelectedModelFile.text = LanguageManager.getText(this, "not_selected")
            }
            updateButtonStates()
        }

        slmViewModel.isModelReady.observe(this) { isReady ->
            Log.d(TAG, "isModelReady observed: $isReady")
            updateButtonStates()
        }

        slmViewModel.isModelLoading.observe(this) { isLoading ->
            Log.d(TAG, "isModelLoading observed: $isLoading")
            updateButtonStates()
            showLoadingUI(isLoading)
        }
    }
    

    private fun showLoadingUI(isLoading: Boolean) {
        progressBarMain.visibility = if (isLoading) View.VISIBLE else View.GONE
        tvLoadingMessageMain.visibility = if (isLoading) View.VISIBLE else View.GONE
        // Disable all interaction buttons during loading
        btnSelectModelFile.isEnabled = !isLoading
        btnClearSelection.isEnabled = !isLoading
    }

    /**
     * Function to manage the state of all buttons in one place
     */
    private fun updateButtonStates() {
        val isModelSelected = slmViewModel.selectedModelUri.value != null
        val isModelReady = slmViewModel.isModelReady.value == true
        val isModelLoading = slmViewModel.isModelLoading.value == true

        Log.d(TAG, "updateButtonStates: isModelSelected=$isModelSelected, isModelReady=$isModelReady, isModelLoading=$isModelLoading")

        // Start game: Only visible and enabled when model is selected and not loading
        if (isModelSelected && !isModelLoading) {
            startButton.visibility = View.VISIBLE
            startButton.isEnabled = true
        } else {
            startButton.visibility = View.GONE
            startButton.isEnabled = false
        }

        // Clear selection: Enabled if model is selected
        btnClearSelection.isEnabled = isModelSelected

        // Select model button: Enabled if not loading
        btnSelectModelFile.isEnabled = !isModelLoading
    }

    @SuppressLint("Range")
    private fun getFileNameFromUri(uri: Uri): String? {
        var fileName: String? = null
        
        try {
            if (uri.scheme == "content") {
                contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val displayNameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        if (displayNameIndex != -1) {
                            fileName = cursor.getString(displayNameIndex)
                        }
                    }
                }
            }
        } catch (e: SecurityException) {
            Log.w(TAG, "SecurityException querying URI: ${e.message}")
            // 권한 오류 시 URI에서 파일명 추출 시도
            fileName = uri.lastPathSegment
        } catch (e: Exception) {
            Log.w(TAG, "Exception querying URI: ${e.message}")
            fileName = uri.lastPathSegment
        }
        
        if (fileName == null) {
            fileName = uri.lastPathSegment
        }
        return fileName
    }
}