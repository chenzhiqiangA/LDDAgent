package com.ldd.agent.chat;

import com.ldd.agent.model.ModelClient;
import com.ldd.agent.model.ModelTurn;
import com.ldd.agent.skill.RouteDecision;
import com.ldd.agent.skill.SkillRouter;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class ChatService {

    private static final int MAX_CONTEXT_MESSAGES = 30;

    private final ChatRepository repository;
    private final SkillRouter skillRouter;
    private final ModelClient modelClient;

    public ChatService(ChatRepository repository, SkillRouter skillRouter, ModelClient modelClient) {
        this.repository = repository;
        this.skillRouter = skillRouter;
        this.modelClient = modelClient;
    }

    public ChatReply send(SendMessageRequest request) {
        String userMessage = requireMessage(request.message());
        CapabilityMode mode = request.mode() == null ? CapabilityMode.SKILL : request.mode();
        if (mode == CapabilityMode.AGENT) {
            throw new AgentNotAvailableException();
        }

        ChatSession session = resolveSession(request.sessionId(), mode);
        List<ChatMessage> history = repository.findRecentMessages(session.id(), MAX_CONTEXT_MESSAGES);
        List<ModelTurn> modelTurns = new ArrayList<>();
        history.stream()
                .filter(message -> "user".equals(message.role()) || "assistant".equals(message.role()))
                .map(message -> new ModelTurn(message.role(), message.content()))
                .forEach(modelTurns::add);

        RouteDecision route = skillRouter.route(userMessage, request.capabilityId(), modelTurns);
        repository.appendMessage(session.id(), "user", userMessage, route.skill().id());
        modelTurns.add(new ModelTurn("user", userMessage));

        String systemPrompt = """
                你正在一个多 Skill 对话系统中工作。
                请直接完成用户请求；不要解释路由过程，也不要声称自己调用了尚未提供的工具。
                回复应准确、清晰，并延续已有对话上下文。

                当前 Skill 指令：
                """ + route.skill().systemPrompt();
        String answer = modelClient.complete(systemPrompt, modelTurns, 0.3);
        ChatMessage assistantMessage = repository.appendMessage(
                session.id(), "assistant", answer, route.skill().id());

        String title = history.isEmpty() ? buildTitle(userMessage) : session.title();
        repository.updateSessionAfterReply(session.id(), title, route.skill().id());

        return new ChatReply(
                session.id(),
                assistantMessage.id(),
                assistantMessage.content(),
                route.skill().id(),
                route.skill().name(),
                mode,
                route.automatic(),
                assistantMessage.createdAt()
        );
    }

    private ChatSession resolveSession(String sessionId, CapabilityMode mode) {
        if (sessionId == null || sessionId.isBlank()) {
            return repository.createSession(mode);
        }
        ChatSession session = repository.findSession(sessionId)
                .orElseThrow(() -> new IllegalArgumentException("会话不存在：" + sessionId));
        if (session.mode() != mode) {
            throw new IllegalArgumentException("当前会话模式与请求模式不一致。");
        }
        return session;
    }

    private String requireMessage(String message) {
        if (message == null || message.isBlank()) {
            throw new IllegalArgumentException("消息内容不能为空。");
        }
        String trimmed = message.trim();
        if (trimmed.length() > 10_000) {
            throw new IllegalArgumentException("单条消息不能超过 10000 个字符。");
        }
        return trimmed;
    }

    private String buildTitle(String message) {
        String singleLine = message.replaceAll("\\s+", " ").trim();
        return singleLine.length() <= 28 ? singleLine : singleLine.substring(0, 28) + "...";
    }
}
