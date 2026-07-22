package com.yuancode.agent;

import com.yuancode.llm.TokenUsage;

import java.time.Duration;
import java.util.Map;

public sealed interface AgentEvent permits AgentEvent.UserMessage, AgentEvent.ThinkingDelta,
        AgentEvent.TextDelta, AgentEvent.ToolCallStarted, AgentEvent.ToolResultCompleted,
        AgentEvent.UsageUpdated, AgentEvent.TurnCompleted, AgentEvent.FinalReply,
        AgentEvent.LoopCompleted, AgentEvent.PlanModeChanged, AgentEvent.Error {
    record UserMessage(String text) implements AgentEvent {}
    record ThinkingDelta(String text) implements AgentEvent {}
    record TextDelta(String text) implements AgentEvent {}
    record ToolCallStarted(String callId, String toolName, Map<String, Object> arguments) implements AgentEvent {}
    record ToolResultCompleted(String callId, String toolName, String output,
                               boolean isError, Duration elapsed) implements AgentEvent {}
    record UsageUpdated(TokenUsage usage) implements AgentEvent {}
    record TurnCompleted(int iteration) implements AgentEvent {}
    record FinalReply(String text, TokenUsage usage) implements AgentEvent {}
    record LoopCompleted(AgentTermination reason, int iterations) implements AgentEvent {}
    record PlanModeChanged(AgentMode mode) implements AgentEvent {}
    record Error(String message) implements AgentEvent {}
}
