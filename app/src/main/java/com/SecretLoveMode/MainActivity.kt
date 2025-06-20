// MainActivity.kt
package com.SecretLoveMode

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream

class MainActivity : AppCompatActivity() {

    private lateinit var btnSelectModelFile: Button
    private lateinit var tvSelectedModelFile: TextView
    private lateinit var btnStartGame: Button
    private lateinit var btnClearSelection: Button
    private lateinit var sharedPreferences: SharedPreferences
    private lateinit var progressBarMain: ProgressBar
    private lateinit var tvLoadingMessageMain: TextView

    private var selectedModelPath: String? = null

    // ViewModel 인스턴스
    private lateinit var llmViewModel: LlmViewModel

    companion object {
        const val PREFS_NAME = "GamePrefs"
        const val KEY_SELECTED_MODEL_PATH = "selectedModelPath"
        const val KEY_SELECTED_MODEL_ORIGINAL_NAME = "selectedModelOriginalName"
    }

    private val openFileLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.data?.let { uri ->
                try {
                    // 지속적 권한 요청
                    contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    val originalFileName = getFileName(uri) ?: "selected_model.task"
                    val cachedPath = copyUriToAppCache(this, uri, originalFileName)

                    if (cachedPath != null) {
                        // 이전 선택된 모델 파일이 있다면 삭제 (선택 사항)
                        clearPreviousCachedModel()

                        selectedModelPath = cachedPath // UI 및 저장용 경로 업데이트
                        tvSelectedModelFile.text = "選択中: $originalFileName"
                        saveSelectedModelInfo(cachedPath, originalFileName)
                        Log.d("MainActivity", "File selected and cached: $cachedPath")

                        // 파일 선택 후 바로 모델 로딩 시작 (ViewModel에 요청)
                        llmViewModel.loadModel(cachedPath)

                    } else {
                        Toast.makeText(this, "ファイルのコピーに失敗しました。", Toast.LENGTH_SHORT).show()
                        tvSelectedModelFile.text = "選択されていません"
                        selectedModelPath = null
                        // 파일 복사 실패 시 ViewModel 상태 초기화 (필요하다면)
                        // llmViewModel.resetState() // 이런 함수를 ViewModel에 추가할 수 있음
                    }
                } catch (e: SecurityException) {
                    Log.e("MainActivity", "Permission error for URI: $uri", e)
                    Toast.makeText(this, "ファイルへのアクセス許可がありません。", Toast.LENGTH_SHORT).show()
                    selectedModelPath = null
                } catch (e: Exception) {
                    Log.e("MainActivity", "Error processing file URI: $uri", e)
                    Toast.makeText(this, "ファイルの処理中にエラーが発生しました。", Toast.LENGTH_SHORT).show()
                    selectedModelPath = null
                }
            }
        }
        // 파일 선택 결과와 관계없이 버튼 상태 업데이트 (로딩 상태는 ViewModel 관찰로 처리)
        // updateClearButtonState() // ViewModel 로딩 상태 관찰로 대체
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        llmViewModel = (application as MyApplication).llmViewModel

        btnSelectModelFile = findViewById(R.id.btnSelectModelFile)
        tvSelectedModelFile = findViewById(R.id.tvSelectedModelFile)
        btnStartGame = findViewById(R.id.btnStartGame)
        btnClearSelection = findViewById(R.id.btnClearSelection)
        progressBarMain = findViewById(R.id.progressBarMain)
        tvLoadingMessageMain = findViewById(R.id.tvLoadingMessageMain)
        sharedPreferences = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        // 저장된 모델 정보 로드 (UI 표시용)
        loadLastSelectedModelInfo()

        // ViewModel 상태 관찰
        llmViewModel.isModelLoading.observe(this) { isLoading ->
            showLoadingUI(isLoading)
        }

        llmViewModel.isModelReady.observe(this) { isReady ->
            // 모델 준비 상태에 따라 게임 시작 버튼 활성화/비활성화
            btnStartGame.isEnabled = isReady && selectedModelPath != null // 모델 준비 + 경로 유효 시 활성화
            // 선택 해제 버튼 상태 업데이트 (모델 경로가 있고 로딩 중이 아닐 때 활성화)
            updateClearButtonState()
        }

        llmViewModel.loadingError.observe(this) { errorMessage ->
            if (errorMessage != null) {
                Toast.makeText(this, errorMessage, Toast.LENGTH_LONG).show()
                // 오류 발생 시 UI 상태 초기화 (선택 해제 상태로)
                clearSelectedModelInfoOnly() // 파일은 그대로 두고 정보만 지움
            }
        }

        // ViewModel에 현재 저장된 모델 경로로 로딩 요청 (앱 시작 시)
        // loadLastSelectedModelInfo()에서 selectedModelPath가 설정된 후에 호출
        selectedModelPath?.let { path ->
            if (File(path).exists()) {
                llmViewModel.loadModel(path)
            } else {
                // 파일이 존재하지 않으면 저장된 정보 삭제
                clearSelectedModelInfoOnly()
            }
        }


        btnSelectModelFile.setOnClickListener {
            // 로딩 중이 아닐 때만 파일 선택 가능
            if (llmViewModel.isModelLoading.value != true) {
                val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                    addCategory(Intent.CATEGORY_OPENABLE)
                    type = "*/*" // 모든 파일 타입 허용
                }
                openFileLauncher.launch(intent)
            }
        }

        btnStartGame.setOnClickListener {
            // 게임 시작 버튼은 ViewModel의 isModelReady 관찰로 활성화/비활성화됨
            // 클릭 시 GameActivity로 이동
            if (llmViewModel.isModelReady.value == true && selectedModelPath != null) {
                // 로딩 UI는 ViewModel 관찰로 표시됨
                val intent = Intent(this, GameActivity::class.java)
                // GameActivity에는 모델 경로를 전달할 필요 없음. GameActivity가 ViewModel에서 가져갈 것임.
                // intent.putExtra("SELECTED_MODEL_PATH", selectedModelPath) // 이 줄 삭제
                startActivity(intent)
            } else {
                // isEnabled = false 상태이므로 이 else 블록은 실행되지 않아야 함.
                // 혹시 모를 경우를 대비한 안전 장치.
                Toast.makeText(this, "モデルがまだ準備できていません。", Toast.LENGTH_SHORT).show()
            }
        }

        btnClearSelection.setOnClickListener {
            clearSelectedModel() // 파일 삭제 및 정보 초기화
        }
    }

    override fun onResume() {
        super.onResume()
        // GameActivity에서 돌아왔을 때 로딩 UI 숨김 (ViewModel 상태에 따라 자동 갱신됨)
        // showLoadingUI(false) // ViewModel 관찰로 대체
    }

    // 로딩 UI 표시 상태 제어 (ViewModel 상태에 따라 호출됨)
    private fun showLoadingUI(isLoading: Boolean) {
        if (isLoading) {
            progressBarMain.visibility = View.VISIBLE
            tvLoadingMessageMain.visibility = View.VISIBLE
            // 로딩 중에는 모든 버튼 비활성화
            btnStartGame.isEnabled = false
            btnSelectModelFile.isEnabled = false
            btnClearSelection.isEnabled = false
        } else {
            progressBarMain.visibility = View.GONE
            tvLoadingMessageMain.visibility = View.GONE
            // 로딩 완료 후 버튼 상태 복구 (ViewModel 상태 및 selectedModelPath에 따라 결정)
            btnSelectModelFile.isEnabled = true
            // btnStartGame.isEnabled 는 ViewModel.isModelReady 관찰자가 처리
            updateClearButtonState() // 선택 해제 버튼 상태 업데이트
        }
    }

    private fun getFileName(uri: Uri): String? {
        var fileName: String? = null
        contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (nameIndex != -1) {
                    fileName = cursor.getString(nameIndex)
                }
            }
        }
        return fileName
    }

    // MainActivity에서 모델 파일이 올바르게 복사되었는지 확인
    private fun copyUriToAppCache(context: Context, uri: Uri, desiredFileName: String): String? {
        try {
            val inputStream: InputStream = context.contentResolver.openInputStream(uri) ?: return null
            val cacheDir = File(context.cacheDir, "models")
            if (!cacheDir.exists()) {
                cacheDir.mkdirs()
            }
        
            val outputFile = File(cacheDir, desiredFileName)
            val outputStream = FileOutputStream(outputFile)
        
            inputStream.copyTo(outputStream)
            inputStream.close()
            outputStream.close()
        
            // 파일이 실제로 생성되었는지 확인
            if (outputFile.exists() && outputFile.length() > 0) {
                Log.d("MainActivity", "모델 파일 복사 성공: ${outputFile.absolutePath}, 크기: ${outputFile.length()}")
                return outputFile.absolutePath
            } else {
                Log.e("MainActivity", "모델 파일 복사 실패: 파일이 존재하지 않거나 크기가 0")
                return null
            }
        } catch (e: Exception) {
            Log.e("MainActivity", "모델 파일 복사 중 오류 발생", e)
            return null
        }
    }

    private fun saveSelectedModelInfo(path: String, originalName: String) {
        sharedPreferences.edit()
            .putString(KEY_SELECTED_MODEL_PATH, path)
            .putString(KEY_SELECTED_MODEL_ORIGINAL_NAME, originalName)
            .apply()
    }

    private fun loadLastSelectedModelInfo() {
        selectedModelPath = sharedPreferences.getString(KEY_SELECTED_MODEL_PATH, null)
        val originalName = sharedPreferences.getString(KEY_SELECTED_MODEL_ORIGINAL_NAME, null)

        if (selectedModelPath != null && originalName != null && File(selectedModelPath!!).exists()) {
            tvSelectedModelFile.text = "選択中: $originalName"
            // 파일은 존재하지만 ViewModel에 로드되지 않은 상태일 수 있음.
            // onCreate 마지막 부분에서 ViewModel.loadModel(selectedModelPath) 호출
        } else {
            // 저장된 경로가 유효하지 않으면 정보 초기화
            clearSelectedModelInfoOnly() // 파일은 그대로 두고 정보만 지움
        }
    }

    // 선택된 모델 정보 및 캐시된 파일 삭제
    private fun clearSelectedModel() {
        if (selectedModelPath != null) {
            val fileToDelete = File(selectedModelPath!!)
            if (fileToDelete.exists()) {
                if (fileToDelete.delete()) {
                    Log.d("MainActivity", "Cached model file deleted: ${selectedModelPath!!}")
                } else {
                    Log.w("MainActivity", "Failed to delete cached model file: ${selectedModelPath!!}")
                }
            }
        }

        // ViewModel 상태 초기화 및 CharacterAi 인스턴스 해제
        llmViewModel.getCharacterAi()?.close() // ViewModel이 가진 CharacterAi 인스턴스 해제
        // ViewModel 내부 상태 초기화는 ViewModel에서 직접 관리하도록 할 수 있음.
        // 예: llmViewModel.resetState()

        clearSelectedModelInfoOnly() // UI 및 SharedPreferences 정보만 초기화
        Toast.makeText(this, "選択が解除されました。", Toast.LENGTH_SHORT).show()
        updateClearButtonState() // 버튼 상태 업데이트
    }

    // UI 표시 및 SharedPreferences 정보만 초기화하고 파일은 삭제하지 않음
    private fun clearSelectedModelInfoOnly() {
        selectedModelPath = null
        sharedPreferences.edit()
            .remove(KEY_SELECTED_MODEL_PATH)
            .remove(KEY_SELECTED_MODEL_ORIGINAL_NAME)
            .apply()
        tvSelectedModelFile.text = "選択されていません"
        // ViewModel 상태 초기화는 clearSelectedModel 또는 ViewModel 내부 로직에서 처리
        // llmViewModel.resetState() // 이런 함수를 ViewModel에 추가하여 호출 가능
    }


    // 새 모델 선택 시 이전 캐시된 모델 파일 삭제 (선택 사항)
    private fun clearPreviousCachedModel() {
        val previousPath = sharedPreferences.getString(KEY_SELECTED_MODEL_PATH, null)
        // 현재 선택하려는 파일과 다르고, ViewModel에 로드된 모델 경로와도 다른 경우에만 삭제
        // ViewModel에 로드된 모델은 clearSelectedModel() 또는 ViewModel.loadModel()에서 close() 처리됨
        if (previousPath != null && previousPath != selectedModelPath && previousPath != llmViewModel.loadedModelPath.value) {
            val fileToDelete = File(previousPath)
            if (fileToDelete.exists()) {
                if (fileToDelete.delete()) {
                    Log.d("MainActivity", "Previous cached model file deleted: $previousPath")
                } else {
                    Log.w("MainActivity", "Failed to delete previous cached model file: $previousPath")
                }
            }
        }
    }


    // "選択解除" ボタン 활성화/비활성화 상태 업데이트
    private fun updateClearButtonState() {
        // 모델 경로가 있고, 로딩 중이 아닐 때 활성화
        btnClearSelection.isEnabled = selectedModelPath != null && llmViewModel.isModelLoading.value != true
    }
}