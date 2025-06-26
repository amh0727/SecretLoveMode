package com.secretlovemode

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SlmViewModel(application: Application) : AndroidViewModel(application) {

    private val TAG = "SlmViewModel"

    // CharacterAi 인스턴스를 ViewModel 내에서 관리
    private var characterAi: CharacterAi? = null

    // 모델 로딩 상태를 외부에 노출 (LiveData 사용)
    private val _isModelLoading = MutableLiveData<Boolean>(false)
    val isModelLoading: LiveData<Boolean> get() = _isModelLoading

    // 모델 준비 상태를 외부에 노출
    private val _isModelReady = MutableLiveData<Boolean>(false)
    val isModelReady: LiveData<Boolean> get() = _isModelReady

    // 로딩 실패 메시지 (선택 사항)
    private val _loadingError = MutableLiveData<String?>(null)
    val loadingError: LiveData<String?> get() = _loadingError

    // 현재 로드된 모델의 경로 (선택 사항)
    private val _loadedModelPath = MutableLiveData<String?>(null)
    val loadedModelPath: LiveData<String?> get() = _loadedModelPath


    // ViewModel이 파괴될 때 CharacterAi 리소스 해제
    override fun onCleared() {
        super.onCleared()
        Log.d(TAG, "ViewModel onCleared() 呼び出し。CharacterAi リソース解放試行。")
        characterAi?.close()
        characterAi = null
        _isModelReady.value = false
        _loadedModelPath.value = null
        Log.d(TAG, "ViewModel onCleared() 完了。")
    }

    // 모델 로딩 요청 함수
    fun loadModel(modelPath: String) {
        // 이미 로딩 중이거나 동일한 모델이 로드되어 있으면 스킵
        if (_isModelLoading.value == true || (_isModelReady.value == true && _loadedModelPath.value == modelPath)) {
            Log.d(TAG, "モデルは既にロード中か、指定されたモデルが既に準備できています。スキップします。")
            return
        }

        _isModelLoading.value = true
        _isModelReady.value = false
        _loadingError.value = null
        _loadedModelPath.value = null // 새 모델 로딩 시작 시 이전 경로 초기화

        // viewModelScope는 ViewModel 클래스 내에서 직접 사용 가능
        viewModelScope.launch(Dispatchers.IO) {
            try {
                // 기존 코드
                Log.d(TAG, "loadModel Coroutine 開始 (Thread: ${Thread.currentThread().name})")
                // 기존 CharacterAi 인스턴스가 있다면 먼저 해제
                characterAi?.close()
                characterAi = null // 참조 해제

                // 새 CharacterAi 인스턴스 생성 및 초기화 시도
                val newCharacterAi = CharacterAi(getApplication<Application>().applicationContext, modelPath)

                // ViewModel 변수에 할당
                characterAi = newCharacterAi
                _loadedModelPath.postValue(modelPath) // 로드 시도하는 모델 경로 업데이트

                Log.d(TAG, "CharacterAi インスタンス作成完了。初期化結果待ち。")

                // 초기화 결과를 메인 스레드에서 처리
                withContext(Dispatchers.Main) {
                    Log.d(TAG, "loadModel Coroutine Main Context (Thread: ${Thread.currentThread().name})")
                    _isModelLoading.value = false // 로딩 완료 (성공 또는 실패)

                    if (newCharacterAi.isModelReady) {
                        _isModelReady.value = true
                        _loadingError.value = null
                        Log.i(TAG, "LLM モデルロード成功: $modelPath")
                    } else {
                        _isModelReady.value = false
                        _loadingError.value = "モデルファイルの読み込みに失敗しました。" // 실제 오류 메시지는 CharacterAi 내부 로그 확인
                        Log.e(TAG, "LLM モデルロード失敗: $modelPath")
                        // 초기화 실패 시에도 CharacterAi 리소스 해제 시도 (CharacterAi 내부에서 이미 하지만, 여기서도 명시적으로)
                        newCharacterAi.close()
                        characterAi = null // 참조 해제
                        _loadedModelPath.value = null // 실패 시 경로 초기화
                    }
                    Log.d(TAG, "loadModel Coroutine 完了")
                }
            } catch (e: Exception) {
                Log.e(TAG, "LLM モデルロード中の例外: ${e.message}", e)
                withContext(Dispatchers.Main) {
                    _isModelLoading.value = false
                    _isModelReady.value = false
                    _loadingError.value = "モデルロード中にエラーが発生しました: ${e.message}"
                }
            }
        }
    }

    // GameActivity 등에서 CharacterAi 인스턴스를 가져갈 때 사용
    fun getCharacterAi(): CharacterAi? {
        return characterAi
    }
}