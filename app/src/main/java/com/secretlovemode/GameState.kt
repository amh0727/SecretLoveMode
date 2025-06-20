package com.secretlovemode

data class GameState(
    val characterName: String ="かおる",
    val characterPersona: String = "真面目で優秀な大学院生。指導教官であるプレイヤーに惹かれているが、その気持ちを悟られないよう必死に隠している。冷静沈着を装っているが、内心は常に揺れ動いている。",
    val currentSituation: String = "研究室で、指導教官のあなたと二人きりで話している。",
    val affinity: Int = 40, // 초기 호감도를 더 낮게 설정
    val drunkenness: Int = 0,
    val conversationCount: Int = 0,
    val currentScenarioId: String = "DEFAULT" // 현재 시나리오 ID를 저장. "DEFAULT"는 일반 대화 상태.

) {
    fun getAffinityDescription(): String = when {
        affinity >= 90 -> "愛情"
        affinity >= 75 -> "信頼"
        affinity >= 60 -> "普通"
        affinity >= 45 -> "微妙"
        affinity >= 30 -> "冷たい"
        affinity >= 15 -> "うざい"
        else -> "最悪"
    }

    fun getDrunkennessDescription(): String = when {
        drunkenness >= 80 -> "かなり酔っている"
        drunkenness >= 60 -> "酔っている"
        drunkenness >= 40 -> "ほろ酔い"
        drunkenness >= 20 -> "少し酔っている"
        else -> "素面"
    }
    
    // 호감도 변화량을 더 엄격하게 계산
    fun calculateAffinityChange(playerChoice: String, characterResponse: String): Int {

        return when {
            // 매우 좋은 선택 (드물게)
            isExceptionalChoice(playerChoice) -> 5
            // 괜찮은 선택
            isGoodChoice(playerChoice) -> 2
            // 무난한 선택
            isNeutralChoice(playerChoice) -> 0
            // 나쁜 선택
            isBadChoice(playerChoice) -> -3
            // 매우 나쁜 선택
            else -> -5
        }
    }
    
    private fun isExceptionalChoice(choice: String): Boolean {
        // 매우 지적이고 흥미로운 대화, 그녀의 관심사에 정확히 맞는 선택
        val keywords = listOf("研究", "論理", "理論", "客観的", "合理的")
        return keywords.any { choice.contains(it) }
    }
    
    private fun isGoodChoice(choice: String): Boolean {
        // 적당히 괜찮은 선택
        val keywords = listOf("面白い", "興味深い", "そうですね")
        return keywords.any { choice.contains(it) }
    }
    
    private fun isNeutralChoice(choice: String): Boolean {
        // 평범한 대답
        val keywords = listOf("はい", "そうかも", "なるほど")
        return keywords.any { choice.contains(it) }
    }
    
    private fun isBadChoice(choice: String): Boolean {
        // 그녀가 싫어할 만한 선택
        val keywords = listOf("しつこい", "綺麗", "付き合", "デート")
        return keywords.any { choice.contains(it) }
    }
}