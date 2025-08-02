package com.secretlovemode.ui.main

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.secretlovemode.domain.CharacterAi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import android.net.Uri
import android.content.ContentResolver
import com.secretlovemode.data.model.GameState
import android.annotation.SuppressLint

class SlmViewModel(application: Application) : AndroidViewModel(application) {

    private val _isModelLoading = MutableLiveData<Boolean>(false)
    val isModelLoading: LiveData<Boolean> = _isModelLoading

    private val _isModelReady = MutableLiveData<Boolean>(false)
    val isModelReady: LiveData<Boolean> = _isModelReady

    private val _loadingError = MutableLiveData<String?>()
    val loadingError: LiveData<String?> = _loadingError

    private val _playerName = MutableLiveData<String>()
    val playerName: LiveData<String> = _playerName

    private val _gameState = MutableLiveData<GameState>()
    val gameState: LiveData<GameState> = _gameState

    // [MODIFIED] The ViewModel now owns the CharacterAi instance.
    private var characterAi: CharacterAi? = null


    private val _selectedModelUri = MutableLiveData<Uri?>(null)
    val selectedModelUri: LiveData<Uri?> = _selectedModelUri

    fun setSelectedModelUri(uri: Uri?) {
        _selectedModelUri.value = uri
    }


    init {
        _gameState.value = GameState(
            characterName = "megumi",
            characterPersona = "情報系のツンデレ大学院新入生",
            playerName = _playerName.value ?: "Player",
            sectionSummaries = emptyMap(),
            keyInputValues = emptyMap(),
            sectionDialogues = emptyMap()
        )
    }

    private suspend fun loadModel(modelPath: String): Boolean {
        if (_isModelLoading.value == true) {
            return false
        }

        return withContext(Dispatchers.IO) {
            try {
                _isModelLoading.postValue(true)
                _loadingError.postValue(null)
                
                val playerName = _playerName.value ?: "Player"
                characterAi = CharacterAi(getApplication(), modelPath, playerName)
                _isModelReady.postValue(true)
                Log.d("SlmViewModel", "Model loaded successfully from: $modelPath")
                true
            } catch (e: Exception) {
                Log.e("SlmViewModel", "Failed to load model: ${e.message}", e)
                _loadingError.postValue("Model loading failed: ${e.message}")
                _isModelReady.postValue(false)
                false
            } finally {
                _isModelLoading.postValue(false)
            }
        }
    }

    fun unloadModel() {
        characterAi?.close()
        characterAi = null
        _isModelReady.postValue(false)
        _isModelLoading.postValue(false)
        _loadingError.postValue(null)
    }

    fun getCharacterAi(): CharacterAi? {
        return if (_isModelReady.value == true) characterAi else null
    }

    fun setPlayerName(name: String) {
        _playerName.value = name
        _gameState.value = _gameState.value?.copy(playerName = name)
    }

    // Map to store custom input values
    private val _customInputs = mutableMapOf<String, String>()
    
    fun setGameStateInput(key: String, value: String) {
        Log.d("SlmViewModel", "setGameStateInput called with key: $key, value: $value")
        val currentState = _gameState.value ?: return
        Log.d("SlmViewModel", "Current GameState keyInputValues: ${currentState.keyInputValues}")
        
        val updatedKeyInputs = currentState.keyInputValues.toMutableMap()
        updatedKeyInputs[key] = value
        Log.d("SlmViewModel", "Updated keyInputValues: $updatedKeyInputs")
        
        when (key) {
            "confession" -> {
                _gameState.value = currentState.copy(
                    confessionInput = value,
                    keyInputValues = updatedKeyInputs
                )
            }
            "name_input" -> {
                _gameState.value = currentState.copy(
                    playerName = value,
                    keyInputValues = updatedKeyInputs
                )
            }
            else -> {
                // Store custom inputs in GameState
                _customInputs[key] = value
                _gameState.value = currentState.copy(keyInputValues = updatedKeyInputs)
            }
        }
        Log.d("SlmViewModel", "GameState updated for key: $key, value: $value")
        Log.d("SlmViewModel", "Final GameState keyInputValues: ${_gameState.value?.keyInputValues}")
    }
    
    fun getCustomInput(key: String): String? {
        return _customInputs[key]
    }
    
    fun getAllCustomInputs(): Map<String, String> {
        return _customInputs.toMap()
    }

    fun updateGameState(
        newAffinity: Int,
        conversationCount: Int,
        conversationHistory: List<com.secretlovemode.data.model.ChatMessage>
    ) {
        val currentState = _gameState.value ?: return
        _gameState.value = currentState.copy(
            affinity = newAffinity.coerceIn(0, 100),
            conversationCount = conversationCount,
            conversationHistory = conversationHistory
        )
    }

    fun updateCurrentScenarioId(newScenarioId: String) {
        _gameState.value = _gameState.value?.copy(currentScenarioId = newScenarioId)
    }
    
    fun addSectionDialogue(sectionId: String, dialogues: List<com.secretlovemode.data.model.SectionDialogue>) {
        val currentState = _gameState.value ?: return
        val updatedSectionDialogues = currentState.sectionDialogues.toMutableMap()
        updatedSectionDialogues[sectionId] = dialogues
        
        _gameState.value = currentState.copy(sectionDialogues = updatedSectionDialogues)
        Log.d("SlmViewModel", "Added section dialogue for section: $sectionId, dialogues count: ${dialogues.size}")
    }
    
    fun updateGameStateWithSummary(sectionSummaries: Map<String, String>) {
        val currentState = _gameState.value ?: return
        _gameState.value = currentState.copy(sectionSummaries = sectionSummaries)
        Log.d("SlmViewModel", "Updated section summaries: ${sectionSummaries.keys}")
    }
    
    /**
     * 고백 판정 이유를 GameState에 저장
     */
    fun setConfessionJudgmentReason(reason: String) {
        val currentState = _gameState.value ?: return
        _gameState.value = currentState.copy(confessionJudgmentReason = reason)
        Log.d("SlmViewModel", "Set confession judgment reason: $reason")
    }

    /**
     * [MODIFIED] This is the correct place to close resources.
     * It's called only when the ViewModel is permanently destroyed.
     */
    override fun onCleared() {
        super.onCleared()
        Log.d("SlmViewModel", "ViewModel is being destroyed, releasing AI resources.")
        characterAi?.close()
        characterAi = null
    }

    suspend fun initializeCharacterAi(contentResolver: ContentResolver, modelUri: Uri): Boolean {
        Log.d("SlmViewModel", "initializeCharacterAi called with URI: $modelUri")
        return withContext(Dispatchers.IO) {
            try {
                val fileName = getFileNameFromUri(contentResolver, modelUri) ?: "selected_model.bin"
                val destinationFile = java.io.File(getApplication<Application>().cacheDir, fileName)

                contentResolver.openInputStream(modelUri)?.use { inputStream ->
                    java.io.FileOutputStream(destinationFile).use { outputStream ->
                        inputStream.copyTo(outputStream)
                    }
                }
                Log.d("SlmViewModel", "File copied to cache: ${destinationFile.absolutePath}")
                return@withContext loadModel(destinationFile.absolutePath)
            } catch (e: Exception) {
                Log.e("SlmViewModel", "AI モデル初期化失敗: ${e.message}", e)
                _loadingError.postValue("AI モデル初期化失敗: ${e.message}")
                false
            }
        }
    }

    @SuppressLint("Range")
    private fun getFileNameFromUri(contentResolver: ContentResolver, uri: Uri): String? {
        var fileName: String? = null
        if (uri.scheme == "content") {
            contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val displayNameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                    if (displayNameIndex != -1) {
                        fileName = cursor.getString(displayNameIndex)
                    }
                }
            }
        }
        if (fileName == null) {
            fileName = uri.lastPathSegment
        }
        return fileName
    }
}