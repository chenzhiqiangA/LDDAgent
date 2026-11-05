package com.ldd.agent.chat;

import java.time.Instant;

public record ChatReply(
        String sessionId,
        String messageId,
        String content,
        String capabilityId,
        String capabilityName,
        CapabilityMode mode,
        boolean automatic,
        Instant createdAt
) {
}
