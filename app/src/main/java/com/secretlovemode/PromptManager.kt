package com.secretlovemode

import android.content.Context
import android.util.Log
import kotlinx.serialization.json.Json
import java.io.IOException

object PromptManager {
    private const val TAG = "PromptManager"
    private var promptData: PromptData? = null

    private val json = Json { ignoreUnknownKeys = true }

    fun loadPrompts(context: Context, fileName: String = "prompts.json") {
        if (promptData != null) return // 이미 로드되었으면 다시 로드하지 않음

        try {
            val jsonString = context.assets.open(fileName).bufferedReader().use { it.readText() }
            val promptFilePaths = json.decodeFromString<PromptFilePaths>(jsonString)

            val initialPrompt = context.assets.open(promptFilePaths.initialTurnPrompt).bufferedReader().use { it.readText() }
            val unifiedPrompt = context.assets.open(promptFilePaths.unifiedTurnPrompt).bufferedReader().use { it.readText() }

            promptData = PromptData(initialTurnPrompt = initialPrompt, unifiedTurnPrompt = unifiedPrompt)

            Log.d(TAG, "'$fileName'에서 프롬프트를 성공적으로 로드했습니다.")
        } catch (e: IOException) {
            Log.e(TAG, "'$fileName' 로드 실패", e)
            // 실제 앱에서는 기본 프롬프트를 하드코딩해두는 등 오류 처리 필요
        }
    }

    fun getInitialTurnPrompt(): String? = promptData?.initialTurnPrompt
    fun getUnifiedTurnPrompt(): String? = promptData?.unifiedTurnPrompt
}