package com.ldd.agent.skill;

import java.util.List;

public record SkillDefinition(
        String id,
        String name,
        String description,
        String systemPrompt,
        List<String> examples,
        boolean enabled
) {
}
