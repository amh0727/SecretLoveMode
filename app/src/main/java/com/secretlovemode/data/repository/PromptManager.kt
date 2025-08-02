package com.secretlovemode.data.repository

import android.content.Context
import android.util.Log
import com.secretlovemode.util.LanguageManager
import java.io.IOException

object PromptManager {
    private var initialTurnPrompt: String? = null
    private var unifiedTurnPrompt: String? = null
    private var affectionJudgePrompt: String? = null
    private var confessionPrompt: String? = null

    fun loadPrompts(context: Context, fileName: String = "prompts.json") {
        try {
            val jsonString = context.assets.open(fileName).bufferedReader().use { it.readText() }

            // Load each prompt individually and safely
            getValueFromJson(jsonString, "initialTurnPrompt").takeIf { it.isNotEmpty() }?.let {
                initialTurnPrompt = try {
                    context.assets.open(it).bufferedReader().use { reader -> reader.readText() }
                } catch (e: IOException) {
                    Log.e("PromptManager", "Failed to load initialTurnPrompt from $it", e)
                    null
                }
            }

            getValueFromJson(jsonString, "unifiedTurnPrompt").takeIf { it.isNotEmpty() }?.let {
                unifiedTurnPrompt = try {
                    context.assets.open(it).bufferedReader().use { reader -> reader.readText() }
                } catch (e: IOException) {
                    Log.e("PromptManager", "Failed to load unifiedTurnPrompt from $it", e)
                    null
                }
            }

            getValueFromJson(jsonString, "affectionJudgePrompt").takeIf { it.isNotEmpty() }?.let {
                affectionJudgePrompt = try {
                    val fileName = LanguageManager.getPromptFileName(context, it.removeSuffix(".txt"))
                    context.assets.open(fileName).bufferedReader().use { reader -> reader.readText() }
                } catch (e: IOException) {
                    Log.e("PromptManager", "Failed to load affectionJudgePrompt from $it", e)
                    null
                }
            }

            getValueFromJson(jsonString, "confessionPrompt").takeIf { it.isNotEmpty() }?.let {
                confessionPrompt = try {
                    val fileName = LanguageManager.getPromptFileName(context, it.removeSuffix(".txt"))
                    context.assets.open(fileName).bufferedReader().use { reader -> reader.readText() }
                } catch (e: IOException) {
                    Log.e("PromptManager", "Failed to load confessionPrompt from $it", e)
                    null
                }
            }

            Log.d("PromptManager", "Prompts loading process completed from '$fileName'.")

        } catch (e: IOException) {
            Log.e("PromptManager", "Failed to load the main prompts file '$fileName'", e)
        }
    }

    // A helper function to parse simple JSON, avoiding a full library for this object
    private fun getValueFromJson(jsonString: String, key: String): String {
        val keyPattern = """$key"\s*:\s*"([^"]*)""".toRegex()
        return keyPattern.find(jsonString)?.groups?.get(1)?.value ?: ""
    }

    fun getInitialTurnPrompt(playerName: String): String? {
        return initialTurnPrompt?.replace("{{playerName}}", playerName)
    }
    fun getUnifiedTurnPrompt(playerName: String): String? {
        return unifiedTurnPrompt?.replace("{{playerName}}", playerName)
    }
    fun getAffectionJudgePrompt(playerName: String): String? {
        return affectionJudgePrompt?.replace("{{playerName}}", playerName)
    }

    fun getConfessionPrompt(playerName: String): String? {
        return confessionPrompt?.replace("{{playerName}}", playerName)
    }
}
