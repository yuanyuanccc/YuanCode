package com.yuancode.tool;

import java.util.List;
import java.util.Map;

@FunctionalInterface
public interface UserQuestionHandler {
    Map<String, String> ask(List<Map<String, Object>> questions) throws Exception;
}
