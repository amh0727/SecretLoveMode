package com.secretlovemode

import android.app.Application
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import com.secretlovemode.data.repository.PromptManager
import com.secretlovemode.ui.main.SlmViewModel

// Implements ViewModelStoreOwner to follow standard ViewModel lifecycle.
class MyApplication : Application(), ViewModelStoreOwner {

    override fun onCreate() {
        super.onCreate()
        // Load prompts when the application starts.
        PromptManager.loadPrompts(this)
    }

    // Creates a ViewModelStore to store ViewModels.
    override val viewModelStore: ViewModelStore by lazy {
        ViewModelStore()
    }

    // ViewModel instance shared across the entire app.
    // This ensures that only one ViewModel instance exists as long as the app is alive.
    val slmViewModel: SlmViewModel by lazy {
        ViewModelProvider(
            this,
            ViewModelProvider.AndroidViewModelFactory.getInstance(this)
        )[SlmViewModel::class.java]
    }
}