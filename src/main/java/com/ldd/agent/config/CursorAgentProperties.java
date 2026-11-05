package com.ldd.agent.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.cursor")
public class CursorAgentProperties {

    private String baseUrl = "https://api.cursor.com";
    private String apiKey = "";
    private String repoUrl = "";
    private String startingRef = "main";
    private String modelId = "";
    private boolean autoCreatePr = true;

    public String getBaseUrl() { return baseUrl; }
    public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }
    public String getApiKey() { return apiKey; }
    public void setApiKey(String apiKey) { this.apiKey = apiKey; }
    public String getRepoUrl() { return repoUrl; }
    public void setRepoUrl(String repoUrl) { this.repoUrl = repoUrl; }
    public String getStartingRef() { return startingRef; }
    public void setStartingRef(String startingRef) { this.startingRef = startingRef; }
    public String getModelId() { return modelId; }
    public void setModelId(String modelId) { this.modelId = modelId; }
    public boolean isAutoCreatePr() { return autoCreatePr; }
    public void setAutoCreatePr(boolean autoCreatePr) { this.autoCreatePr = autoCreatePr; }

    public boolean isConfigured() {
        return apiKey != null && !apiKey.isBlank();
    }
}
