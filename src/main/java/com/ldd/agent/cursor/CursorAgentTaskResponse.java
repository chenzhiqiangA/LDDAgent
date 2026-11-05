package com.ldd.agent.cursor;

import java.time.Instant;

public record CursorAgentTaskResponse(
        String agentId,
        String runId,
        String status,
        String result,
        String agentUrl,
        String branch,
        String pullRequestUrl,
        Instant updatedAt
) {
}
