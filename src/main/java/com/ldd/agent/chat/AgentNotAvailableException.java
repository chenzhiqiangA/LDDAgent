package com.ldd.agent.chat;

public class AgentNotAvailableException extends RuntimeException {

    public AgentNotAvailableException() {
        super("Agent 能力已经预留，当前版本尚未启用。");
    }
}
