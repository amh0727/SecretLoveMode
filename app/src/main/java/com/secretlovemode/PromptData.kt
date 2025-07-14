package com.secretlovemode

import kotlinx.serialization.Serializable

@Serializable
data class PromptData(
    val initialTurnPrompt: String,
    val unifiedTurnPrompt: String
)

@Serializable
data class PromptFilePaths(
    val initialTurnPrompt: String,
    val unifiedTurnPrompt: String
)