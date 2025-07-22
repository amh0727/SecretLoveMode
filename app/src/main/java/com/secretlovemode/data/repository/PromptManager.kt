package com.secretlovemode.data.repository

import android.content.Context
import android.util.Log
import java.io.IOException

object PromptManager {
    private var initialTurnPrompt: String? = null
    private var unifiedTurnPrompt: String? = null

    fun loadPrompts(context: Context, fileName: String = "prompts.json") {
        try {
            val jsonString = context.assets.open(fileName).bufferedReader().use { it.readText() }
            // A simple parser to get file paths from JSON
            val initialPath = getValueFromJson(jsonString, "initialTurnPrompt")
            val unifiedPath = getValueFromJson(jsonString, "unifiedTurnPrompt")

            initialTurnPrompt = context.assets.open(initialPath).bufferedReader().use { it.readText() }
            unifiedTurnPrompt = context.assets.open(unifiedPath).bufferedReader().use { it.readText() }

            Log.d("PromptManager", "Prompts loaded successfully from '$fileName'.")
        } catch (e: IOException) {
            Log.e("PromptManager", "Failed to load prompts from '$fileName'", e)
        }
    }

    // A helper function to parse simple JSON, avoiding a full library for this object
    private fun getValueFromJson(jsonString: String, key: String): String {
        val keyPattern = """$key"\s*:\s*"([^"]*)""".toRegex()
        return keyPattern.find(jsonString)?.groups?.get(1)?.value ?: ""
    }

    fun getInitialTurnPrompt(): String? = initialTurnPrompt
    fun getUnifiedTurnPrompt(): String? = unifiedTurnPrompt
}
