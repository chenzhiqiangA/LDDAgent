package com.ldd.agent.chat;

public record SendMessageRequest(
        String sessionId,
        String message,
        CapabilityMode mode,
        String capabilityId
) {
}
