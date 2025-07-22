package com.secretlovemode.data.model

import kotlinx.serialization.Serializable

@Serializable
data class Condition(
    val type: String, // 예: "AFFINITY_GTE" (호감도 이상), "CONVERSATION_GTE" (대화 횟수 이상)
    val value: String // 비교할 값. 유연성을 위해 String으로 선언
)
