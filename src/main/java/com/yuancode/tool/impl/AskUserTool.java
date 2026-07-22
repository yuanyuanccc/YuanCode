package com.yuancode.tool.impl;

import com.yuancode.llm.JsonSupport;
import com.yuancode.tool.*;

import java.util.*;
import java.util.concurrent.*;

public final class AskUserTool implements Tool {
    private final UserQuestionHandler handler;

    public AskUserTool(UserQuestionHandler handler) { this.handler = Objects.requireNonNull(handler); }
    public String name() { return "AskUserQuestion"; }
    public String description() {
        return "Ask the user one to four structured clarification questions before making assumptions.";
    }
    public ToolCategory category() { return ToolCategory.READ; }
    public boolean shouldDefer() { return false; }
    public Map<String, Object> schema() {
        Map<String, Object> option = ToolSchemas.object(Map.of(
                "label", ToolSchemas.string("Choice label"),
                "description", ToolSchemas.string("Choice consequence")), "label");
        Map<String, Object> question = ToolSchemas.object(Map.of(
                "header", ToolSchemas.string("Short header"),
                "question", ToolSchemas.string("Question text"),
                "options", Map.of("type", "array", "minItems", 2, "maxItems", 4, "items", option)),
                "header", "question", "options");
        return ToolSchemas.object(Map.of("questions", Map.of(
                "type", "array", "minItems", 1, "maxItems", 4, "items", question)), "questions");
    }
    @SuppressWarnings("unchecked")
    public ToolResult execute(Map<String, Object> arguments) {
        Object raw = arguments.get("questions");
        if (!(raw instanceof List<?> list) || list.isEmpty() || list.size() > 4) {
            return ToolResult.error("questions must contain 1 to 4 items");
        }
        List<Map<String, Object>> questions = new ArrayList<>();
        for (Object item : list) {
            if (!(item instanceof Map<?, ?> map)) return ToolResult.error("question must be an object");
            Object options = map.get("options");
            if (!(options instanceof List<?> choices) || choices.size() < 2 || choices.size() > 4) {
                return ToolResult.error("each question must contain 2 to 4 options");
            }
            questions.add((Map<String, Object>) map);
        }
        ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
        try {
            Future<Map<String, String>> pending = executor.submit(() -> handler.ask(List.copyOf(questions)));
            Map<String, String> answers = pending.get(5, TimeUnit.MINUTES);
            if (answers == null || answers.containsKey("_declined")) return ToolResult.error("User declined the question");
            return ToolResult.success(JsonSupport.write(answers));
        } catch (TimeoutException error) {
            return ToolResult.error("Question timed out after 5 minutes");
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            return ToolResult.error("Question interrupted");
        } catch (Exception error) {
            return ToolResult.error(error.getMessage() == null ? error.toString() : error.getMessage());
        } finally {
            executor.shutdownNow();
        }
    }
}
