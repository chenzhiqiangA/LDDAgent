package com.ldd.agent.cursor;

public record CursorAgentTaskRequest(
        String sessionId,
        String message,
        String repoUrl,
        String startingRef,
        Boolean autoCreatePr
) {
}
