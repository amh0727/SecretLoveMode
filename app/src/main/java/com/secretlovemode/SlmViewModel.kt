package com.secretlovemode

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class SlmViewModel(application: Application) : AndroidViewModel(application) {

    private val _isModelLoading = MutableLiveData<Boolean>(false)
    val isModelLoading: LiveData<Boolean> = _isModelLoading

    private val _isModelReady = MutableLiveData<Boolean>(false)
    val isModelReady: LiveData<Boolean> = _isModelReady

    private val _loadingError = MutableLiveData<String?>()
    val loadingError: LiveData<String?> = _loadingError

    // [MODIFIED] The ViewModel now owns the CharacterAi instance.
    private var characterAi: CharacterAi? = null

    fun loadModel(modelPath: String) {
        if (_isModelLoading.value == true) return

        viewModelScope.launch {
            _isModelLoading.postValue(true)
            _isModelReady.postValue(false)
            _loadingError.postValue(null)

            // [MODIFIED] Close the previous instance before creating a new one.
            characterAi?.close()
            characterAi = null

            try {
                val newCharacterAi = withContext(Dispatchers.IO) {
                    CharacterAi(getApplication(), modelPath)
                }

                if (newCharacterAi.isModelReady) {
                    characterAi = newCharacterAi
                    _isModelReady.postValue(true)
                } else {
                    _loadingError.postValue("AI モデルの初期化に失敗しました。")
                }
            } catch (e: Exception) {
                _loadingError.postValue("モデルの読み込み中にエラーが発生しました: ${e.message}")
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

    /**
     * [MODIFIED] This is the correct place to close resources.
     * It's called only when the ViewModel is permanently destroyed.
     */
    override fun onCleared() {
        super.onCleared()
        characterAi?.close()
    }
}