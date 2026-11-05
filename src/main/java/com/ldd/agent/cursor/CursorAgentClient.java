package com.ldd.agent.cursor;

import com.ldd.agent.config.CursorAgentProperties;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;

import java.net.URI;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class CursorAgentClient {

    private final CursorAgentProperties properties;
    private final RestClient restClient;

    public CursorAgentClient(CursorAgentProperties properties) {
        this.properties = properties;
        this.restClient = RestClient.builder().build();
    }

    public CursorAgentTaskResponse create(CursorAgentTaskRequest task) {
        requireConfigured();
        String repoUrl = valueOrDefault(task.repoUrl(), properties.getRepoUrl());
        String startingRef = valueOrDefault(task.startingRef(), properties.getStartingRef());
        if (repoUrl.isBlank()) {
            throw new CursorAgentException("请填写 GitHub 仓库地址，或设置 CURSOR_REPO_URL。");
        }
        if (startingRef.isBlank()) {
            throw new CursorAgentException("请填写起始分支，或设置 CURSOR_STARTING_REF。");
        }

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("prompt", Map.of("text", requireMessage(task.message())));
        body.put("repos", List.of(Map.of("url", repoUrl, "startingRef", startingRef)));
        body.put("autoCreatePR", task.autoCreatePr() == null
                ? properties.isAutoCreatePr() : task.autoCreatePr());
        if (properties.getModelId() != null && !properties.getModelId().isBlank()) {
            body.put("model", Map.of("id", properties.getModelId()));
        }

        JsonNode response = exchange("/v1/agents", body);
        JsonNode agent = required(response, "/agent");
        JsonNode run = required(response, "/run");
        return toTaskResponse(agent, run);
    }

    public CursorAgentTaskResponse getRun(String agentId, String runId) {
        requireConfigured();
        JsonNode run = get("/v1/agents/" + path(agentId) + "/runs/" + path(runId));
        JsonNode agent = run.path("agentId").isMissingNode()
                ? null : get("/v1/agents/" + path(agentId));
        return toTaskResponse(agent, run);
    }

    private JsonNode exchange(String path, Map<String, Object> body) {
        try {
            return restClient.post()
                    .uri(endpoint(path))
                    .headers(this::authorize)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(JsonNode.class);
        } catch (Exception exception) {
            throw translate(exception);
        }
    }

    private JsonNode get(String path) {
        try {
            return restClient.get()
                    .uri(endpoint(path))
                    .headers(this::authorize)
                    .retrieve()
                    .body(JsonNode.class);
        } catch (Exception exception) {
            throw translate(exception);
        }
    }

    private CursorAgentTaskResponse toTaskResponse(JsonNode agent, JsonNode run) {
        JsonNode git = run == null ? null : run.path("git");
        JsonNode branch = git == null || !git.has("branches") || git.path("branches").isEmpty()
                ? null : git.path("branches").get(0);
        String updatedAt = text(run, "updatedAt");
        return new CursorAgentTaskResponse(
                text(agent == null ? run : agent, "id"),
                text(run, "id"),
                text(run, "status"),
                text(run, "result"),
                text(agent, "url"),
                text(branch, "branch"),
                text(branch, "prUrl"),
                updatedAt == null ? null : InstantParser.parse(updatedAt)
        );
    }

    private JsonNode required(JsonNode node, String pointer) {
        JsonNode value = node == null ? null : node.at(pointer);
        if (value == null || value.isMissingNode() || value.isNull()) {
            throw new CursorAgentException("Cursor 返回数据缺少 " + pointer + "。");
        }
        return value;
    }

    private String text(JsonNode node, String field) {
        if (node == null || node.isMissingNode() || node.path(field).isMissingNode()
                || node.path(field).isNull()) {
            return null;
        }
        return node.path(field).asText();
    }

    private void authorize(HttpHeaders headers) {
        headers.setBasicAuth(properties.getApiKey(), "");
    }

    private URI endpoint(String path) {
        return URI.create(properties.getBaseUrl().replaceAll("/+$", "") + path);
    }

    private String path(String value) {
        return value.replaceAll("[^a-zA-Z0-9._~-]", "");
    }

    private String valueOrDefault(String value, String fallback) {
        return value == null ? fallback : value.trim();
    }

    private String requireMessage(String message) {
        if (message == null || message.isBlank()) {
            throw new CursorAgentException("研发任务不能为空。", 400);
        }
        return message.trim();
    }

    private void requireConfigured() {
        if (!properties.isConfigured()) {
            throw new CursorAgentException("Cursor Agent 尚未配置，请设置 CURSOR_API_KEY。", 503);
        }
    }

    private CursorAgentException translate(Exception exception) {
        return new CursorAgentException("Cursor Cloud Agent 调用失败：" + exception.getMessage());
    }

    private static class InstantParser {
        private static java.time.Instant parse(String value) {
            try {
                return java.time.Instant.parse(value);
            } catch (Exception ignored) {
                return null;
            }
        }
    }
}
