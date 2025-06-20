package com.secretlovemode

import android.app.Application
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
//pull test
class MyApplication : Application(), ViewModelStoreOwner {

    override fun onCreate(){
        super.onCreate()
        ScenarioManager.loadScenarios(this)
    }

    override val viewModelStore: ViewModelStore by lazy {
        ViewModelStore()
    }

    // 앱 전체에서 공유될 LlmViewModel 인스턴스
    val llmViewModel: LlmViewModel by lazy {
        // ViewModelProvider에 ViewModelStoreOwner인 'this'(MyApplication)를 전달합니다.
        ViewModelProvider(
            this,
            ViewModelProvider.AndroidViewModelFactory.getInstance(this)
        )[LlmViewModel::class.java]
    }
}