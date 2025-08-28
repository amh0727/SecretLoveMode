package com.secretlovemode.data.model

import kotlinx.serialization.Serializable

@Serializable
data class Condition(
    val type: String, // e.g., "AFFINITY_GTE" (affinity greater than or equal), "CONVERSATION_GTE" (conversation count greater than or equal)
    val value: String // Value to compare. Declared as String for flexibility
)
