package com.secretlovemode

import android.app.Application
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner

// [수정] ViewModelStoreOwner를 구현하여 표준 ViewModel 생명주기를 따르도록 합니다.
class MyApplication : Application(), ViewModelStoreOwner {

    // ViewModel을 저장할 공간을 만듭니다.
    override val viewModelStore: ViewModelStore by lazy {
        ViewModelStore()
    }

    // 앱 전체에서 공유될 ViewModel 인스턴스입니다.
    // 이 방식은 앱이 살아있는 동안 ViewModel이 단 하나만 존재하도록 보장합니다.
    val slmViewModel: SlmViewModel by lazy {
        ViewModelProvider(
            this,
            ViewModelProvider.AndroidViewModelFactory.getInstance(this)
        )[SlmViewModel::class.java]
    }
}