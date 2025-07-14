package com.secretlovemode

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.view.View
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import java.io.File
import java.io.FileOutputStream
import android.util.Log
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import java.io.IOException



class MainActivity : AppCompatActivity() {

    // UI 요소
    private lateinit var btnSelectKaoru: Button
    private lateinit var btnSelectSecretary: Button
    private lateinit var tvSelectedCharacter: TextView
    private lateinit var btnSelectModelFile: Button // 모델 선택 버튼
    private lateinit var tvSelectedModelFile: TextView // 선택된 모델 파일명 표시
    private lateinit var btnClearSelection: Button // 선택 해제 버튼
    private lateinit var btnStartGame: Button
    private lateinit var progressBarMain: ProgressBar
    private lateinit var tvLoadingMessageMain: TextView

    // 상태 변수
    private var selectedCharacter: Character? = null

    private var selectedModelPath: String? = null // 모델 파일 경로 저장
    private lateinit var slmViewModel: SlmViewModel

    // 캐릭터 정보
    private val kaoru by lazy {
        Character(
            id = "kaoru",
            characterName = "かおる",
            characterPersona = "情報系のツンデレ修士",
            modelFileName = "gemma-3n-E2B-it-int4.task",
            scenarioFileName = "scenarios_kaoru.json"
        )
    }
    private val secretary by lazy {
        Character(
            id = "secretary",
            characterName = "秘書",
            characterPersona = "完璧主義者の秘書さん",
            modelFileName = "gemma-3n-E2B-it-int4.task",
            scenarioFileName = "scenarios_secretary.json"
        )
    }

    /**
     *  파일 선택 창을 띄우고, 선택된 파일의 URI를 받아오는 런처
     */
    private val modelFilePickerLauncher = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        uri?.let {
            handleSelectedFile(it)
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
        updateButtonStates() // 초기 버튼 상태 설정
    }

    private fun initializeViews() {
        btnSelectKaoru = findViewById(R.id.btnSelectKaoru)
        btnSelectSecretary = findViewById(R.id.btnSelectSecretary)
        tvSelectedCharacter = findViewById(R.id.tvSelectedCharacter)
        btnSelectModelFile = findViewById(R.id.btnSelectModelFile)
        tvSelectedModelFile = findViewById(R.id.tvSelectedModelFile)
        btnClearSelection = findViewById(R.id.btnClearSelection)
        btnStartGame = findViewById(R.id.btnStartGame)
        progressBarMain = findViewById(R.id.progressBarMain)
        tvLoadingMessageMain = findViewById(R.id.tvLoadingMessageMain)
    }

    private fun setupClickListeners() {
        btnSelectKaoru.setOnClickListener { selectCharacterAndLoadModel(kaoru) }
        btnSelectSecretary.setOnClickListener { selectCharacterAndLoadModel(secretary) }
        btnStartGame.setOnClickListener { startGame() }

        // 모델 선택 버튼 클릭 시 파일 선택 창을 띄웁니다.
        btnSelectModelFile.setOnClickListener {
            modelFilePickerLauncher.launch(arrayOf("*/*")) // 모든 종류의 파일 선택 가능
        }

        // 선택 해제 버튼 기능 구현
        btnClearSelection.setOnClickListener {
            clearSelections()
        }
    }

    private fun selectCharacterAndLoadModel(character: Character) {
        this.selectedCharacter = character
        tvSelectedCharacter.text = "選択済み: ${character.characterName}"
        tvSelectedModelFile.text = "モデル準備中: ${character.modelFileName}"
        // assets에서 모델 파일을 내부 저장소로 복사하고 로드 시작
        lifecycleScope.launch {
            try {
                val modelFile = copyAssetToFile(character.modelFileName)
                selectedModelPath = modelFile.absolutePath
                slmViewModel.loadModel(selectedModelPath!!)
            } catch (e: IOException) {
                Log.e("MainActivity", "Asset 파일 복사 실패: ${character.modelFileName}", e)
                Toast.makeText(this@MainActivity, "モデルファイルの準備に失敗しました。", Toast.LENGTH_SHORT).show()
                clearSelections() // 실패 시 선택 초기화
            }
        }
    }


    /**
     * Assets 폴더의 파일을 앱 내부 캐시 디렉토리로 복사하는 함수
     * @param assetFileName assets 폴더에 있는 파일 이름
     * @return 복사된 파일 객체
     */
    @Throws(IOException::class)
    private fun copyAssetToFile(assetFileName: String): File {
        val destinationFile = File(cacheDir, assetFileName)
        // 이미 파일이 존재하면 다시 복사하지 않고 바로 반환 (효율성)
        if (destinationFile.exists()) {
            return destinationFile
        }

        assets.open(assetFileName).use { inputStream ->
            FileOutputStream(destinationFile).use { outputStream ->
                inputStream.copyTo(outputStream)
            }
        }
        return destinationFile
    }

    /**
     *  사용자가 선택한 파일을 처리하는 함수
     */
    private fun handleSelectedFile(uri: Uri) {
        // URI로부터 파일 이름을 가져옵니다.
        val fileName = getFileNameFromUri(uri) ?: "selected_model.bin"
        val destinationFile = File(cacheDir, fileName)

        try {
            // 선택된 파일을 앱 내부 캐시 디렉토리로 복사하여 안정적인 파일 경로를 확보합니다.
            contentResolver.openInputStream(uri)?.use { inputStream ->
                FileOutputStream(destinationFile).use { outputStream ->
                    inputStream.copyTo(outputStream)
                }
            }
            // 모델 경로를 저장하고 UI를 업데이트한 후, 모델 로드를 시작합니다.
            selectedModelPath = destinationFile.absolutePath
            tvSelectedModelFile.text = "選択済み: $fileName"
            slmViewModel.loadModel(selectedModelPath!!)
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, "ファイルの処理中にエラーが発生しました。", Toast.LENGTH_SHORT).show()
        }
    }


    private fun clearSelections() {
        selectedCharacter = null
        selectedModelPath = null
        tvSelectedCharacter.text = "未選択"
        tvSelectedModelFile.text = "未選択"
        slmViewModel.unloadModel() // ViewModel의 모델 상태도 초기화
        updateButtonStates()
    }

    private fun observeViewModel() {
        slmViewModel.isModelLoading.observe(this) { isLoading ->
            showLoadingUI(isLoading)
            updateButtonStates()
        }

        slmViewModel.isModelReady.observe(this) { isReady ->
            updateButtonStates()
        }

        slmViewModel.loadingError.observe(this) { errorMessage ->
            if (errorMessage != null) {
                Toast.makeText(this, errorMessage, Toast.LENGTH_LONG).show()
                // 에러 발생 시 모델 선택 상태 초기화
                selectedModelPath = null
                tvSelectedModelFile.text = "未選択"
                updateButtonStates()
            }
        }
    }

    private fun showLoadingUI(isLoading: Boolean) {
        progressBarMain.visibility = if (isLoading) View.VISIBLE else View.GONE
        tvLoadingMessageMain.visibility = if (isLoading) View.VISIBLE else View.GONE
        // 로딩 중에는 모든 상호작용 버튼 비활성화
        btnSelectKaoru.isEnabled = !isLoading
        btnSelectSecretary.isEnabled = !isLoading
        btnSelectModelFile.isEnabled = !isLoading
        btnClearSelection.isEnabled = !isLoading
    }

    /**
     * 모든 버튼의 상태를 한 곳에서 관리하는 함수
     */
    private fun updateButtonStates() {
        val isCharacterSelected = selectedCharacter != null
        val isModelSelected = selectedModelPath != null
        val isModelReady = slmViewModel.isModelReady.value == true
        val isNotLoading = slmViewModel.isModelLoading.value == false

        // 게임 시작: 캐릭터 선택되고, 모델이 준비되었으며, 로딩 중이 아닐 때만 활성화
        btnStartGame.isEnabled = isCharacterSelected && isModelReady && isNotLoading

        // 선택 해제: 캐릭터나 모델 중 하나라도 선택되어 있으면 활성화
        btnClearSelection.isEnabled = isCharacterSelected || isModelSelected
    }

    private fun startGame() {
        if (btnStartGame.isEnabled) { // 버튼이 활성화된 상태일 때만 실행
            val intent = Intent(this, GameActivity::class.java).apply {
                putExtra("SELECTED_CHARACTER", selectedCharacter)
            }
            startActivity(intent)
        }
    }

    /**
     *  URI에서 파일 이름을 추출하는 헬퍼 함수
     */
    private fun getFileNameFromUri(uri: Uri): String? {
        var fileName: String? = null
        contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val displayNameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (displayNameIndex != -1) {
                    fileName = cursor.getString(displayNameIndex)
                }
            }
        }
        return fileName
    }

}