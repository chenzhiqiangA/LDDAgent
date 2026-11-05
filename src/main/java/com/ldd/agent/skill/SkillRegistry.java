package com.ldd.agent.skill;

import jakarta.annotation.PostConstruct;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

@Component
public class SkillRegistry {

    private final ObjectMapper objectMapper;
    private final Map<String, SkillDefinition> skills = new LinkedHashMap<>();

    public SkillRegistry(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @PostConstruct
    void loadSkills() throws IOException {
        Resource[] resources = new PathMatchingResourcePatternResolver()
                .getResources("classpath*:skills/*.json");
        for (Resource resource : resources) {
            SkillDefinition skill = objectMapper.readValue(resource.getInputStream(), SkillDefinition.class);
            validate(skill, resource.getFilename());
            if (skills.putIfAbsent(skill.id(), skill) != null) {
                throw new IllegalStateException("Skill id 重复：" + skill.id());
            }
        }
        if (skills.isEmpty()) {
            throw new IllegalStateException("至少需要配置一个 Skill。");
        }
    }

    public Collection<SkillDefinition> enabledSkills() {
        return skills.values().stream().filter(SkillDefinition::enabled).toList();
    }

    public SkillDefinition requireEnabled(String id) {
        SkillDefinition skill = skills.get(id);
        if (skill == null || !skill.enabled()) {
            throw new IllegalArgumentException("Skill 不存在或未启用：" + id);
        }
        return skill;
    }

    private void validate(SkillDefinition skill, String source) {
        if (skill.id() == null || !skill.id().matches("[a-z0-9][a-z0-9-]{1,62}")) {
            throw new IllegalStateException(source + " 中的 Skill id 不合法。");
        }
        if (skill.name() == null || skill.name().isBlank()
                || skill.description() == null || skill.description().isBlank()
                || skill.systemPrompt() == null || skill.systemPrompt().isBlank()) {
            throw new IllegalStateException(source + " 缺少必要的 Skill 字段。");
        }
    }
}
