package com.ldd.agent.skill;

import com.ldd.agent.model.ModelClient;
import com.ldd.agent.model.ModelCallException;
import com.ldd.agent.model.ModelTurn;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

@Service
public class SkillRouter {

    private static final Logger log = LoggerFactory.getLogger(SkillRouter.class);
    private static final Duration CACHE_TTL = Duration.ofMinutes(10);

    private final SkillRegistry registry;
    private final ModelClient modelClient;
    private final StringRedisTemplate redisTemplate;

    public SkillRouter(SkillRegistry registry, ModelClient modelClient, StringRedisTemplate redisTemplate) {
        this.registry = registry;
        this.modelClient = modelClient;
        this.redisTemplate = redisTemplate;
    }

    public RouteDecision route(String userMessage, String requestedSkillId, List<ModelTurn> conversationContext) {
        if (requestedSkillId != null && !requestedSkillId.isBlank()) {
            return new RouteDecision(registry.requireEnabled(requestedSkillId), false);
        }

        List<ModelTurn> routingContext = recentContext(conversationContext);
        String cacheSource = routingContext.stream()
                .map(turn -> turn.role() + ":" + turn.content())
                .collect(Collectors.joining("\n"))
                + "\nuser:" + userMessage;
        String cacheKey = "ldd:skill-route:" + sha256(cacheSource.trim().toLowerCase(Locale.ROOT));
        String cachedSkillId = readCache(cacheKey);
        if (cachedSkillId != null) {
            try {
                return new RouteDecision(registry.requireEnabled(cachedSkillId), true);
            } catch (IllegalArgumentException ignored) {
                // The Skill catalog may have changed since this route was cached.
            }
        }

        String catalog = registry.enabledSkills().stream()
                .map(skill -> "- " + skill.id() + ": " + skill.description()
                        + examples(skill))
                .collect(Collectors.joining("\n"));
        String systemPrompt = """
                你是 Skill 路由器。结合最近对话上下文和用户本轮请求，从候选 Skill 中选择唯一最匹配的一项。
                只能返回 Skill id，不要解释，不要输出 Markdown。

                候选 Skill：
                """ + catalog;

        List<ModelTurn> routingMessages = new java.util.ArrayList<>(routingContext);
        routingMessages.add(new ModelTurn("user", userMessage));
        String modelResult = modelClient.complete(systemPrompt, routingMessages, 0);
        SkillDefinition selected = parseSelection(modelResult);
        writeCache(cacheKey, selected.id());
        return new RouteDecision(selected, true);
    }

    private SkillDefinition parseSelection(String modelResult) {
        String normalized = modelResult.trim()
                .replace("`", "")
                .replace("\"", "")
                .toLowerCase(Locale.ROOT);
        return registry.enabledSkills().stream()
                .filter(skill -> normalized.equals(skill.id()) || normalized.contains(skill.id()))
                .findFirst()
                .orElseThrow(() -> new ModelCallException("模型没有返回有效的 Skill id：" + modelResult));
    }

    private String examples(SkillDefinition skill) {
        if (skill.examples() == null || skill.examples().isEmpty()) {
            return "";
        }
        return "；示例：" + String.join("、", skill.examples());
    }

    private List<ModelTurn> recentContext(List<ModelTurn> context) {
        if (context == null || context.isEmpty()) {
            return List.of();
        }
        int fromIndex = Math.max(0, context.size() - 6);
        return context.subList(fromIndex, context.size()).stream()
                .map(turn -> new ModelTurn(turn.role(), truncate(turn.content(), 2_000)))
                .toList();
    }

    private String truncate(String value, int maxLength) {
        return value.length() <= maxLength ? value : value.substring(0, maxLength);
    }

    private String readCache(String key) {
        try {
            return redisTemplate.opsForValue().get(key);
        } catch (RuntimeException exception) {
            log.debug("Redis unavailable while reading Skill route cache", exception);
            return null;
        }
    }

    private void writeCache(String key, String skillId) {
        try {
            redisTemplate.opsForValue().set(key, skillId, CACHE_TTL);
        } catch (RuntimeException exception) {
            log.debug("Redis unavailable while writing Skill route cache", exception);
        }
    }

    private String sha256(String value) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }
}
