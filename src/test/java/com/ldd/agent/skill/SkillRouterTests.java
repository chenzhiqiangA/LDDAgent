package com.ldd.agent.skill;

import com.ldd.agent.model.ModelClient;
import com.ldd.agent.model.ModelTurn;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SkillRouterTests {

    private SkillRegistry registry;
    private ModelClient modelClient;
    private SkillRouter router;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        registry = mock(SkillRegistry.class);
        modelClient = mock(ModelClient.class);
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        ValueOperations<String, String> valueOperations = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        router = new SkillRouter(registry, modelClient, redisTemplate);
    }

    @Test
    void usesModelToAutomaticallySelectSpecificSkill() {
        SkillDefinition tableCreator = skill(
                "table-creator",
                "建表设计",
                "根据业务需求设计 MySQL 表结构并生成 CREATE TABLE DDL"
        );
        SkillDefinition general = skill("general-assistant", "通用助手", "处理其他通用请求");
        when(registry.enabledSkills()).thenReturn(List.of(tableCreator, general));
        when(registry.requireEnabled("table-creator")).thenReturn(tableCreator);
        when(modelClient.complete(anyString(), any(), anyDouble())).thenReturn("table-creator");

        RouteDecision decision = router.route(
                "创建一张订单表，需要订单状态和支付信息",
                null,
                List.of(new ModelTurn("user", "这是一个电商系统"))
        );

        assertThat(decision.skill().id()).isEqualTo("table-creator");
        assertThat(decision.automatic()).isTrue();
        verify(modelClient).complete(anyString(), any(), anyDouble());
    }

    @Test
    void manualSelectionDoesNotCallRoutingModel() {
        SkillDefinition apiExtractor = skill(
                "task-api-extractor",
                "任务接口提取",
                "从任务文档定位指定接口并按指定格式返回"
        );
        when(registry.requireEnabled("task-api-extractor")).thenReturn(apiExtractor);

        RouteDecision decision = router.route(
                "提取创建订单接口",
                "task-api-extractor",
                List.of()
        );

        assertThat(decision.skill().id()).isEqualTo("task-api-extractor");
        assertThat(decision.automatic()).isFalse();
        verify(modelClient, never()).complete(anyString(), any(), anyDouble());
    }

    private SkillDefinition skill(String id, String name, String description) {
        return new SkillDefinition(
                id,
                name,
                description,
                "完成对应任务",
                List.of("示例"),
                true
        );
    }
}
