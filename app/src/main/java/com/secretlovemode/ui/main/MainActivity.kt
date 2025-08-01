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
import android.annotation.SuppressLint


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
        setContentView(R.layout.activity_main)

        PromptManager.loadPrompts(applicationContext)
        slmViewModel = (application as MyApplication).slmViewModel

        initializeViews()
        setupClickListeners()
        observeViewModel()

        modelSelectionGroup.visibility = View.GONE
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

                // Show model selection UI
                findViewById<androidx.constraintlayout.widget.Group>(R.id.modelSelectionGroup).visibility = View.VISIBLE
                // Re-attach click listener for startButton after it becomes visible
                startButton.setOnClickListener {
                    Log.d(TAG, "Start button click listener attached (re-attached).")
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
                            Toast.makeText(this@MainActivity, "モデルファイルが選択されていません。", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            } else {
                Toast.makeText(this, "플레이어 이름을 입력해주세요.", Toast.LENGTH_SHORT).show()
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
                    Toast.makeText(this@MainActivity, "モデルファイルが選択されていません。", Toast.LENGTH_SHORT).show()
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
                    val fileName = getFileNameFromUri(uri) ?: "選択されたファイル"
                    tvSelectedModelFile.text = fileName
                    Log.d(TAG, "Updated selected file name: $fileName")
                } catch (e: SecurityException) {
                    Log.w(TAG, "SecurityException accessing URI: ${e.message}")
                    tvSelectedModelFile.text = "選択されたモデル"
                } catch (e: Exception) {
                    Log.w(TAG, "Error accessing URI: ${e.message}")
                    tvSelectedModelFile.text = "選択されたファイル"
                }
            } else {
                tvSelectedModelFile.text = "未選択"
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

        // Start game: Enabled when model is selected and not loading
        startButton.isEnabled = isModelSelected && !isModelLoading
        startButton.visibility = if (isModelSelected) View.VISIBLE else View.GONE

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