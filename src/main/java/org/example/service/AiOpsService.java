package org.example.service;

import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatModel;
import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.agent.ReactAgent;
import com.alibaba.cloud.ai.graph.agent.flow.agent.SupervisorAgent;
import com.alibaba.cloud.ai.graph.exception.GraphRunnerException;
import org.example.agent.tool.DateTimeTools;
import org.example.agent.tool.InternalDocsTools;
import org.example.agent.tool.QueryLogsTools;
import org.example.agent.tool.QueryMetricsTools;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * AI Ops 智能运维服务
 * 负责多 Agent 协作的告警分析流程
 */
@Service
public class AiOpsService {

    private static final Logger logger = LoggerFactory.getLogger(AiOpsService.class);
    private static final int SUMMARY_MAX_CHARS = 1800;
    private static final int SECTION_MAX_LINES = 8;
    private static final int FALLBACK_MAX_CHARS = 1200;

    @Autowired
    private DateTimeTools dateTimeTools;

    @Autowired
    private InternalDocsTools internalDocsTools;

    @Autowired
    private QueryMetricsTools queryMetricsTools;

    @Autowired(required = false)  // Mock 模式下才注册
    private QueryLogsTools queryLogsTools;

    /**
     * 执行 AI Ops 告警分析流程
     *
     * @param chatModel      大模型实例
     * @param toolCallbacks  工具回调数组
     * @return 分析结果状态
     * @throws GraphRunnerException 如果 Agent 执行失败
     */
    public Optional<OverAllState> executeAiOpsAnalysis(DashScopeChatModel chatModel, ToolCallback[] toolCallbacks) throws GraphRunnerException {
        logger.info("开始执行 AI Ops 多 Agent 协作流程");

        // 构建 Planner 和 Executor Agent
        ReactAgent plannerAgent = buildPlannerAgent(chatModel, toolCallbacks);
        ReactAgent executorAgent = buildExecutorAgent(chatModel, toolCallbacks);

        // 构建 Supervisor Agent
        SupervisorAgent supervisorAgent = SupervisorAgent.builder()
                .name("ai_ops_supervisor")
                .description("负责调度 Planner 与 Executor 的多 Agent 控制器")
                .model(chatModel)
                .systemPrompt(buildSupervisorSystemPrompt())
                .subAgents(List.of(plannerAgent, executorAgent))
                .build();

        String taskPrompt = "你是企业级 SRE，接到了自动化告警排查任务。请结合工具调用，执行**规划→执行→再规划**的闭环，并最终按照固定模板输出《告警分析报告》。禁止编造虚假数据，如连续多次查询失败需诚实反馈无法完成的原因。";

        logger.info("调用 Supervisor Agent 开始编排...");
        return supervisorAgent.invoke(taskPrompt);
    }

    /**
     * 从执行结果中提取最终报告文本
     *
     * @param state 执行状态
     * @return 报告文本（如果存在）
     */
    public Optional<String> extractFinalReport(OverAllState state) {
        logger.info("开始提取最终报告...");

        // 提取 Planner 最终输出（包含完整的告警分析报告）
        Optional<AssistantMessage> plannerFinalOutput = state.value("planner_plan")
                .filter(AssistantMessage.class::isInstance)
                .map(AssistantMessage.class::cast);

        if (plannerFinalOutput.isPresent()) {
            String reportText = plannerFinalOutput.get().getText();
            logger.info("成功提取到 Planner 最终报告，长度: {}", reportText.length());
            return Optional.of(reportText);
        } else {
            logger.warn("未能提取到 Planner 最终报告");
            return Optional.empty();
        }
    }

    /**
     * 创建 AIOps 流式分析 Agent
     * 用于 /api/ai_ops 接口的实时输出场景，与 /api/chat_stream 使用相同的 stream 范式。
     */
    public ReactAgent createStreamingAiOpsAgent(DashScopeChatModel chatModel, ToolCallback[] toolCallbacks) {
        return ReactAgent.builder()
                .name("ai_ops_streaming_agent")
                .description("执行告警分析并以 Markdown 形式流式输出报告")
                .model(chatModel)
                .systemPrompt(buildStreamingAiOpsPrompt())
                .methodTools(buildMethodToolsArray())
                .tools(toolCallbacks)
                .build();
    }

    /**
     * 构建用于会话记忆的 AI Ops 摘要，控制长度并提取关键结论，避免占满上下文窗口。
     *
     * @param fullReport AI Ops 最终完整报告
     * @return 可写入会话历史的摘要文本
     */
    public String buildAiOpsConversationSummary(String fullReport) {
        if (fullReport == null || fullReport.trim().isEmpty()) {
            return "AI Ops 分析摘要\n\n- 本次流程未生成有效报告，请重新执行 AI Ops。";
        }

        String normalizedReport = fullReport.replace("\r\n", "\n").trim();
        Map<String, String> sections = parseSecondLevelSections(normalizedReport);
        if (sections.isEmpty()) {
            String fallback = truncate(normalizedReport, FALLBACK_MAX_CHARS);
            return truncate("AI Ops 分析摘要\n\n" + fallback, SUMMARY_MAX_CHARS);
        }

        String activeAlerts = extractSectionByKeywords(sections, "活跃告警清单");
        String rootCause = extractSectionByKeywords(sections, "告警根因分析");
        String handling = extractSectionByKeywords(sections, "处理建议", "处理方案执行");
        String conclusion = extractSectionByKeywords(sections, "结论");

        StringBuilder summaryBuilder = new StringBuilder();
        summaryBuilder.append("AI Ops 分析摘要\n");
        appendSummarySection(summaryBuilder, "活跃告警", activeAlerts);
        appendSummarySection(summaryBuilder, "根因分析", rootCause);
        appendSummarySection(summaryBuilder, "处理建议", handling);
        appendSummarySection(summaryBuilder, "结论", conclusion);

        return truncate(summaryBuilder.toString().trim(), SUMMARY_MAX_CHARS);
    }

    /**
     * 构建 Planner Agent
     */
    private ReactAgent buildPlannerAgent(DashScopeChatModel chatModel, ToolCallback[] toolCallbacks) {
        return ReactAgent.builder()
                .name("planner_agent")
                .description("负责拆解告警、规划与再规划步骤")
                .model(chatModel)
                .systemPrompt(buildPlannerPrompt())
                .methodTools(buildMethodToolsArray())
                .tools(toolCallbacks)
                .outputKey("planner_plan")
                .build();
    }

    /**
     * 构建 Executor Agent
     */
    private ReactAgent buildExecutorAgent(DashScopeChatModel chatModel, ToolCallback[] toolCallbacks) {
        return ReactAgent.builder()
                .name("executor_agent")
                .description("负责执行 Planner 的首个步骤并及时反馈")
                .model(chatModel)
                .systemPrompt(buildExecutorPrompt())
                .methodTools(buildMethodToolsArray())
                .tools(toolCallbacks)
                .outputKey("executor_feedback")
                .build();
    }

    /**
     * 动态构建方法工具数组
     * 根据 cls.mock-enabled 决定是否包含 QueryLogsTools
     */
    private Object[] buildMethodToolsArray() {
        if (queryLogsTools != null) {
            // Mock 模式：包含 QueryLogsTools
            return new Object[]{dateTimeTools, internalDocsTools, queryMetricsTools, queryLogsTools};
        } else {
            // 真实模式：不包含 QueryLogsTools（由 MCP 提供日志查询功能）
            return new Object[]{dateTimeTools, internalDocsTools, queryMetricsTools};
        }
    }

    /**
     * 构建 Planner Agent 系统提示词
     */
    private String buildPlannerPrompt() {
        return """
                你是 Planner Agent，同时承担 Replanner 角色，负责：
                1. 读取当前输入任务 {input} 以及 Executor 的最近反馈 {executor_feedback}。
                2. 分析 Prometheus 告警、日志、内部文档等信息，制定可执行的下一步步骤。
                3. 在执行阶段，输出 JSON，包含 decision (PLAN|EXECUTE|FINISH)、step 描述、预期要调用的工具、以及必要的上下文。
                4. 调用任何腾讯云日志/主题相关工具时，region 参数必须使用连字符格式（如 ap-guangzhou），若不确定请省略以使用默认值。
                5. 严格禁止编造数据，只能引用工具返回的真实内容；如果连续 3 次调用同一工具仍失败或返回空结果，需停止该方向并在最终报告的结论部分说明"无法完成"的原因。
                
                ## 最终报告输出要求（CRITICAL）
                
                当 decision=FINISH 时，你必须：
                1. **不要输出 JSON 格式**
                2. **直接输出完整的 Markdown 格式报告文本**
                3. **报告必须严格遵循以下模板**：
                
                ```
                # 告警分析报告
                
                ---
                
                ## 📋 活跃告警清单
                
                | 告警名称 | 级别 | 目标服务 | 首次触发时间 | 最新触发时间 | 状态 |
                |---------|------|----------|-------------|-------------|------|
                | [告警1名称] | [级别] | [服务名] | [时间] | [时间] | 活跃 |
                | [告警2名称] | [级别] | [服务名] | [时间] | [时间] | 活跃 |
                
                ---
                
                ## 🔍 告警根因分析1 - [告警名称]
                
                ### 告警详情
                - **告警级别**: [级别]
                - **受影响服务**: [服务名]
                - **持续时间**: [X分钟]
                
                ### 症状描述
                [根据监控指标描述症状]
                
                ### 日志证据
                [引用查询到的关键日志]
                
                ### 根因结论
                [基于证据得出的根本原因]
                
                ---
                
                ## 🛠️ 处理方案执行1 - [告警名称]
                
                ### 已执行的排查步骤
                1. [步骤1]
                2. [步骤2]
                
                ### 处理建议
                [给出具体的处理建议]
                
                ### 预期效果
                [说明预期的效果]
                
                ---
                
                ## 🔍 告警根因分析2 - [告警名称]
                [如果有第2个告警，重复上述格式]
                
                ---
                
                ## 📊 结论
                
                ### 整体评估
                [总结所有告警的整体情况]
                
                ### 关键发现
                - [发现1]
                - [发现2]
                
                ### 后续建议
                1. [建议1]
                2. [建议2]
                
                ### 风险评估
                [评估当前风险等级和影响范围]
                ```
                
                **重要提醒**：
                - 最终输出必须是纯 Markdown 文本，不要包含 JSON 结构
                - 不要使用 "finalReport": "..." 这样的格式
                - 直接从 "# 告警分析报告" 开始输出
                - 所有内容必须基于工具查询的真实数据，严禁编造
                - 如果某个步骤失败，在结论中如实说明，不要跳过
                
                """;
    }

    /**
     * 构建 Executor Agent 系统提示词
     */
    private String buildExecutorPrompt() {
        return """
                你是 Executor Agent，负责读取 Planner 最新输出 {planner_plan}，只执行其中的第一步。
                - 确认步骤所需的工具与参数，尤其是 region 参数要使用连字符格式（ap-guangzhou）；若 Planner 未给出则使用默认区域。
                - 调用相应的工具并收集结果，如工具返回错误或空数据，需要将失败原因、请求参数一并记录，并停止进一步调用该工具（同一工具失败达到 3 次时应直接返回 FAILED）。
                - 将日志、指标、文档等证据整理成结构化摘要，标注对应的告警名称或资源，方便 Planner 填充"告警根因分析 / 处理方案执行"章节。
                - 以 JSON 形式返回执行状态、证据以及给 Planner 的建议，写入 executor_feedback，严禁编造未实际查询到的内容。


                输出示例：
                {
                  "status": "SUCCESS",
                  "summary": "近1小时未见 error 日志，仅有 info",
                  "evidence": "...",
                  "nextHint": "建议转向高占用进程"
                }
                """;
    }

    /**
     * 构建 Supervisor Agent 系统提示词
     */
    private String buildSupervisorSystemPrompt() {
        return """
                你是 AI Ops Supervisor，负责调度 planner_agent 与 executor_agent：
                1. 当需要拆解任务或重新制定策略时，调用 planner_agent。
                2. 当 planner_agent 输出 decision=EXECUTE 时，调用 executor_agent 执行第一步。
                3. 根据 executor_agent 的反馈，评估是否需要再次调用 planner_agent，直到 decision=FINISH。
                4. FINISH 后，确保向最终用户输出完整的《告警分析报告》，格式必须严格为：
                   告警分析报告\n---\n# 告警处理详情\n## 活跃告警清单\n## 告警根因分析N\n## 处理方案执行N\n## 结论。
                5. 若步骤涉及腾讯云日志/主题工具，请确保使用连字符区域 ID（ap-guangzhou 等），或省略 region 以采用默认值。
                6. 如果发现 Planner/Executor 在同一方向连续 3 次调用工具仍失败或没有数据，必须终止流程，直接输出"任务无法完成"的报告，明确告知失败原因，严禁凭空编造结果。

                只允许在 planner_agent、executor_agent 与 FINISH 之间做出选择。

                """;
    }

    /**
     * 构建 AIOps 流式分析系统提示词
     * 要求模型边分析边输出最终报告内容，确保前端可以实时渲染。
     */
    private String buildStreamingAiOpsPrompt() {
        return """
                你是企业级智能运维助手，需要调用工具完成告警分析，并输出结构化 Markdown 报告。
                关键要求：
                1. 必须优先通过工具获取真实数据，禁止编造。
                2. 输出内容应直接是最终报告正文，支持流式增量输出，不要等待全部分析完成后一次性输出。
                3. 禁止输出过程性口语（如“正在查询…/已获取…/接下来…”），这类信息由系统进度通道输出，不属于报告正文。
                4. 报告格式必须为 Markdown，至少包含：
                   - # 告警分析报告
                   - ## 活跃告警清单
                   - ## 告警根因分析
                   - ## 处理建议
                   - ## 结论
                5. 标题、列表、表格都必须严格使用 Markdown 语法并保留必要换行。
                6. 当工具失败或无数据时，必须在报告中明确写出“无法完成”的原因。
                7. 调用日志相关工具时，地域默认使用 ap-guangzhou。
                """;
    }

    private void appendSummarySection(StringBuilder summaryBuilder, String sectionTitle, String sectionContent) {
        summaryBuilder.append("\n## ").append(sectionTitle).append("\n");
        if (sectionContent == null || sectionContent.isBlank()) {
            summaryBuilder.append("- 未提取到该部分，详见 AI Ops 原始报告。\n");
            return;
        }
        summaryBuilder.append(sectionContent).append("\n");
    }

    private Map<String, String> parseSecondLevelSections(String markdown) {
        Map<String, StringBuilder> sectionBuilders = new LinkedHashMap<>();
        String currentSection = null;

        String[] lines = markdown.split("\n");
        for (String line : lines) {
            String trimmedLine = line.trim();
            if (trimmedLine.startsWith("## ")) {
                currentSection = trimmedLine.substring(3).trim();
                sectionBuilders.putIfAbsent(currentSection, new StringBuilder());
                continue;
            }

            if (currentSection != null) {
                sectionBuilders.get(currentSection).append(line).append("\n");
            }
        }

        Map<String, String> sections = new LinkedHashMap<>();
        for (Map.Entry<String, StringBuilder> entry : sectionBuilders.entrySet()) {
            sections.put(entry.getKey(), entry.getValue().toString().trim());
        }
        return sections;
    }

    private String extractSectionByKeywords(Map<String, String> sections, String... keywords) {
        StringBuilder contentBuilder = new StringBuilder();

        for (Map.Entry<String, String> entry : sections.entrySet()) {
            if (!containsAnyKeyword(entry.getKey(), keywords)) {
                continue;
            }
            if (!contentBuilder.isEmpty()) {
                contentBuilder.append("\n");
            }
            contentBuilder.append("### ").append(entry.getKey()).append("\n");
            contentBuilder.append(limitLines(entry.getValue(), SECTION_MAX_LINES));
        }

        return contentBuilder.toString().trim();
    }

    private boolean containsAnyKeyword(String text, String... keywords) {
        if (text == null || keywords == null) {
            return false;
        }

        for (String keyword : keywords) {
            if (keyword != null && !keyword.isBlank() && text.contains(keyword)) {
                return true;
            }
        }
        return false;
    }

    private String limitLines(String text, int maxLines) {
        if (text == null || text.isBlank() || maxLines <= 0) {
            return "- （该部分为空）";
        }

        StringBuilder result = new StringBuilder();
        int lineCount = 0;
        String[] lines = text.split("\n");
        for (String line : lines) {
            String trimmedLine = line.trim();
            if (trimmedLine.isEmpty()) {
                continue;
            }
            if (lineCount >= maxLines) {
                result.append("...(已截断)\n");
                break;
            }
            result.append(trimmedLine).append("\n");
            lineCount++;
        }

        if (result.isEmpty()) {
            return "- （该部分为空）";
        }
        return result.toString().trim();
    }

    private String truncate(String text, int maxChars) {
        if (text == null || maxChars <= 0 || text.length() <= maxChars) {
            return text;
        }
        int endIndex = Math.max(0, maxChars - 8);
        return text.substring(0, endIndex) + "...(截断)";
    }
}
