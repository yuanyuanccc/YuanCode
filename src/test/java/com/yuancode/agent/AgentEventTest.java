package com.yuancode.agent;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AgentEventTest {
    @Test
    void eventAndTerminationModelsHaveTheDocumentedClosedSets() {
        Set<String> events = Arrays.stream(AgentEvent.class.getPermittedSubclasses())
                .map(Class::getSimpleName).collect(Collectors.toSet());
        assertEquals(Set.of("UserMessage", "ThinkingDelta", "TextDelta", "ToolCallStarted",
                "ToolResultCompleted", "UsageUpdated", "TurnCompleted", "FinalReply",
                "LoopCompleted", "PlanModeChanged", "Error"), events);
        assertEquals(Set.of("COMPLETED", "CANCELLED", "TIMED_OUT", "MAX_ITERATIONS", "ERROR"),
                Arrays.stream(AgentTermination.values()).map(Enum::name).collect(Collectors.toSet()));
    }
}
