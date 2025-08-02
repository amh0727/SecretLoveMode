package com.secretlovemode.ui.language

import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import android.widget.Button
import com.secretlovemode.R
import com.secretlovemode.ui.main.MainActivity
import com.secretlovemode.ui.game.GameActivity
import com.secretlovemode.util.LanguageManager

class LanguageSelectionActivity : AppCompatActivity() {
    
    companion object {
        private const val TAG = "LanguageSelectionActivity"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        Log.d(TAG, "onCreate called")
        
        // Force light mode
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
        
        setContentView(R.layout.activity_language_selection)

        setupLanguageButtons()
    }

    private fun setupLanguageButtons() {
        val btnJapanese = findViewById<Button>(R.id.btnJapanese)
        val btnEnglish = findViewById<Button>(R.id.btnEnglish)

        btnJapanese.setOnClickListener {
            Log.d(TAG, "Japanese button clicked")
            selectLanguage("ja")
        }

        btnEnglish.setOnClickListener {
            Log.d(TAG, "English button clicked")
            selectLanguage("en")
        }
    }

    private fun selectLanguage(languageCode: String) {
        Log.d(TAG, "selectLanguage called with: $languageCode")
        
        try {
            // 언어 설정 저장
            LanguageManager.setLanguage(this, languageCode)
            Log.d(TAG, "Language saved successfully: $languageCode")
            
            // 저장된 언어 확인
            val savedLanguage = LanguageManager.getLanguage(this)
            Log.d(TAG, "Saved language verified: $savedLanguage")
            
            // MainActivity로 이동하기 전에 잠시 대기
            runOnUiThread {
                // Always go to MainActivity after language selection
                val mainIntent = Intent(this, MainActivity::class.java)
                mainIntent.putExtra("FROM_LANGUAGE_SELECTION", true)
                mainIntent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                Log.d(TAG, "Starting MainActivity with intent")
                startActivity(mainIntent)
                finish()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in selectLanguage", e)
        }
    }
}