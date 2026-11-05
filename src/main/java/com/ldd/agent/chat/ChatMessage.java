package com.ldd.agent.chat;

import java.time.Instant;

public record ChatMessage(
        String id,
        String sessionId,
        String role,
        String content,
        String capabilityId,
        Instant createdAt
) {
}
