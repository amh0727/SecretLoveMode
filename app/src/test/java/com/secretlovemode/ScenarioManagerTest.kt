import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class ScenarioManagerTest {

    private lateinit var baseScenario: Scenario
    private lateinit var eventScenario: Scenario

    @Before
    fun setup() {
        baseScenario = Scenario(
            id = "CHAPTER_1_START",
            setting = "setting",
            characterGoal = "goal",
            trigger = Trigger(listOf(Condition("CONVERSATION_GTE", "0")))
        )
        eventScenario = Scenario(
            id = "EVENT_TEST",
            setting = "event setting",
            characterGoal = "event goal",
            trigger = Trigger(
                listOf(
                    Condition("CONVERSATION_GTE", "2"),
                    Condition("CURRENT_SCENARIO_IS", "CHAPTER_1_START")
                )
            )
        )
        ScenarioManager.setScenariosForTest(listOf(baseScenario, eventScenario))
    }

    @Test
    fun testScenarioTransitions() {
        var state = GameState(conversationCount = 0)
        var id = ScenarioManager.checkAndTriggerNextScenario(state)
        assertEquals("CHAPTER_1_START", id)

        state = state.copy(currentScenarioId = id, conversationCount = 1)
        id = ScenarioManager.checkAndTriggerNextScenario(state)
        assertEquals("CHAPTER_1_START", id)

        state = state.copy(conversationCount = 2)
        id = ScenarioManager.checkAndTriggerNextScenario(state)
        assertEquals("EVENT_TEST", id)

        state = state.copy(currentScenarioId = id, conversationCount = 4)
        id = ScenarioManager.checkAndTriggerNextScenario(state)
        assertEquals("EVENT_TEST", id)

        state = state.copy(conversationCount = 5)
        id = ScenarioManager.checkAndTriggerNextScenario(state)
        assertEquals("DEFAULT", id)
    }
}
