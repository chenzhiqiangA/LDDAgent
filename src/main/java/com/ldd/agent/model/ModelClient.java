package com.ldd.agent.model;

import java.util.List;

public interface ModelClient {

    String complete(String systemPrompt, List<ModelTurn> messages, double temperature);

    boolean isConfigured();
}
