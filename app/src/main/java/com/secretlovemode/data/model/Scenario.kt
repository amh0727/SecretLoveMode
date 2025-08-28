package com.secretlovemode.data.model

import kotlinx.serialization.Serializable

@Serializable
data class Trigger(
    val conditions: List<Condition> // All these conditions must be met to trigger (AND condition)
)

@Serializable
data class Scenario(
    val id: String,
    val setting: String,
    val characterGoal: String,
    val trigger: Trigger, // 로직 대신 트리거 데이터 객체를 가짐
    val season: String? = null, // 특정 계절에만 적용 (e.g., "SPRING"), 없으면 모든 계절
    val imageName: String? = null,
    val requiresUserInput: Boolean = false
)
