package com.ldd.agent.model;

import com.ldd.agent.config.ModelProperties;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;

import java.net.URI;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class OpenAiCompatibleModelClient implements ModelClient {

    private final ModelProperties properties;
    private final RestClient restClient;

    public OpenAiCompatibleModelClient(ModelProperties properties) {
        this.properties = properties;
        this.restClient = RestClient.builder().build();
    }

    @Override
    public String complete(String systemPrompt, List<ModelTurn> messages, double temperature) {
        if (!isConfigured()) {
            throw new ModelCallException("模型尚未配置，请设置 MODEL_NAME；远程模型通常还需要 MODEL_API_KEY。");
        }

        List<Map<String, String>> requestMessages = new ArrayList<>();
        requestMessages.add(Map.of("role", "system", "content", systemPrompt));
        messages.forEach(message -> requestMessages.add(Map.of(
                "role", message.role(),
                "content", message.content()
        )));

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", properties.getName());
        body.put("messages", requestMessages);
        body.put("temperature", temperature);

        try {
            RestClient.RequestBodySpec request = restClient.post()
                    .uri(resolveEndpoint())
                    .contentType(MediaType.APPLICATION_JSON);
            if (properties.getApiKey() != null && !properties.getApiKey().isBlank()) {
                request.header(HttpHeaders.AUTHORIZATION, "Bearer " + properties.getApiKey());
            }

            JsonNode response = request.body(body).retrieve().body(JsonNode.class);
            JsonNode content = response == null ? null : response.at("/choices/0/message/content");
            if (content == null || content.isMissingNode() || content.asText().isBlank()) {
                throw new ModelCallException("模型返回内容为空。");
            }
            return content.asText().trim();
        } catch (ModelCallException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new ModelCallException("模型调用失败：" + exception.getMessage(), exception);
        }
    }

    @Override
    public boolean isConfigured() {
        return properties.isConfigured();
    }

    private URI resolveEndpoint() {
        String baseUrl = properties.getBaseUrl().replaceAll("/+$", "");
        String path = properties.getChatPath().replaceAll("^/+", "");
        return URI.create(baseUrl + "/" + path);
    }
}
