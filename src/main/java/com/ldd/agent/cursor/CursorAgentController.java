package com.ldd.agent.cursor;

import com.ldd.agent.config.CursorAgentProperties;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/agent")
public class CursorAgentController {

    private final CursorAgentClient client;
    private final CursorAgentProperties properties;

    public CursorAgentController(CursorAgentClient client, CursorAgentProperties properties) {
        this.client = client;
        this.properties = properties;
    }

    @GetMapping("/status")
    public AgentStatus status() {
        return new AgentStatus(properties.isConfigured(),
                properties.getRepoUrl() != null && !properties.getRepoUrl().isBlank());
    }

    @PostMapping("/tasks")
    public CursorAgentTaskResponse create(@RequestBody CursorAgentTaskRequest request) {
        return client.create(request);
    }

    @GetMapping("/tasks/{agentId}/runs/{runId}")
    public CursorAgentTaskResponse getRun(@PathVariable String agentId, @PathVariable String runId) {
        return client.getRun(agentId, runId);
    }

    public record AgentStatus(boolean configured, boolean defaultRepositoryConfigured) {
    }
}
