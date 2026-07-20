package com.yuancode.conversation;

public record ThinkingBlock(String thinking, String signature) {
    public ThinkingBlock {
        thinking = thinking == null ? "" : thinking;
        signature = signature == null ? "" : signature;
    }
}
