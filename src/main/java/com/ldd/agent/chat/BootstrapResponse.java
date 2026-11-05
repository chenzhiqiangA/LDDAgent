package com.ldd.agent.chat;

import java.util.List;

public record BootstrapResponse(
        boolean modelConfigured,
        boolean agentAvailable,
        List<SkillSummary> skills,
        List<ChatSession> sessions
) {
}
