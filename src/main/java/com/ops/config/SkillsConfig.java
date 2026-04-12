package com.ops.config;

import com.alibaba.cloud.ai.graph.agent.hook.skills.SkillsAgentHook;
import com.alibaba.cloud.ai.graph.skills.registry.SkillRegistry;
import com.alibaba.cloud.ai.graph.skills.registry.classpath.ClasspathSkillRegistry;
import com.ops.agent.tool.WebSearchTools;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * Skills 配置
 * 使用官方 SkillRegistry 与 SkillsAgentHook 挂载资源目录下的技能。
 */
@Configuration
public class SkillsConfig {

    /**
     * 加载 classpath:skills 下的所有官方 Skill 定义。
     */
    @Bean
    public SkillRegistry skillRegistry() {
        return ClasspathSkillRegistry.builder()
                .classpathPath("skills")
                .build();
    }

    /**
     * 官方 Skills Hook，会向 Agent 注入技能列表，并在 read_skill 后开放分组工具。
     */
    @Bean
    public SkillsAgentHook skillsAgentHook(SkillRegistry skillRegistry, WebSearchTools webSearchTools) {
        ToolCallback[] webSearchCallbacks = MethodToolCallbackProvider.builder()
                .toolObjects(webSearchTools)
                .build()
                .getToolCallbacks();

        Map<String, List<ToolCallback>> groupedTools = Map.of(
                "web-search",
                Arrays.asList(webSearchCallbacks)
        );

        return SkillsAgentHook.builder()
                .skillRegistry(skillRegistry)
                .groupedTools(groupedTools)
                .build();
    }
}
