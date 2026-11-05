package com.ldd.agent.chat;

import java.time.Instant;

public record ChatSession(
        String id,
        String title,
        CapabilityMode mode,
        String capabilityId,
        Instant createdAt,
        Instant updatedAt
) {
}
