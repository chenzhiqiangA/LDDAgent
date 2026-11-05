package com.ldd.agent.chat;

import com.ldd.agent.model.ModelClient;
import com.ldd.agent.config.CursorAgentProperties;
import com.ldd.agent.skill.SkillRegistry;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api")
public class ChatController {

    private final ChatService chatService;
    private final ChatRepository repository;
    private final SkillRegistry skillRegistry;
    private final ModelClient modelClient;
    private final CursorAgentProperties cursorAgentProperties;

    public ChatController(ChatService chatService, ChatRepository repository,
                          SkillRegistry skillRegistry, ModelClient modelClient,
                          CursorAgentProperties cursorAgentProperties) {
        this.chatService = chatService;
        this.repository = repository;
        this.skillRegistry = skillRegistry;
        this.modelClient = modelClient;
        this.cursorAgentProperties = cursorAgentProperties;
    }

    @GetMapping("/bootstrap")
    public BootstrapResponse bootstrap() {
        List<SkillSummary> skills = skillRegistry.enabledSkills().stream()
                .map(skill -> new SkillSummary(skill.id(), skill.name(), skill.description()))
                .toList();
        return new BootstrapResponse(
                modelClient.isConfigured(),
                cursorAgentProperties.isConfigured(),
                skills,
                repository.findRecentSessions()
        );
    }

    @GetMapping("/chat/sessions/{sessionId}/messages")
    public List<ChatMessage> messages(@PathVariable String sessionId) {
        repository.findSession(sessionId)
                .orElseThrow(() -> new IllegalArgumentException("会话不存在：" + sessionId));
        return repository.findMessages(sessionId);
    }

    @PostMapping("/chat/messages")
    public ChatReply send(@RequestBody SendMessageRequest request) {
        return chatService.send(request);
    }

    @DeleteMapping("/chat/sessions/{sessionId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteSession(@PathVariable String sessionId) {
        repository.deleteSession(sessionId);
    }
}
