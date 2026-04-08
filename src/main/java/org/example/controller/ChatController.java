package org.example.controller;

import com.alibaba.cloud.ai.dashscope.api.DashScopeApi;
import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatModel;
import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatOptions;
import com.alibaba.cloud.ai.graph.NodeOutput;
import com.alibaba.cloud.ai.graph.agent.ReactAgent;
import com.alibaba.cloud.ai.graph.streaming.OutputType;
import com.alibaba.cloud.ai.graph.streaming.StreamingOutput;
import org.example.auth.AuthContext;
import org.example.auth.AuthUser;
import org.example.dto.chat.ChatRequest;
import org.example.dto.chat.ChatResponse;
import org.example.dto.chat.ClearRequest;
import org.example.dto.chat.SessionInfoResponse;
import org.example.dto.chat.SseMessage;
import org.example.dto.common.ApiResponse;
import org.example.service.AiOpsService;
import org.example.service.ChatService;
import org.example.service.session.ChatSession;
import org.example.service.session.ChatSessionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import reactor.core.publisher.Flux;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 统一 API 控制器
 * 适配前端接口需求，负责三类能力：
 * 1) 普通问答（/chat）
 * 2) 流式问答（/chat_stream）
 * 3) AIOps 多 Agent 分析（/ai_ops）
 */
@RestController
@RequestMapping("/api")
public class ChatController {

    private static final Logger logger = LoggerFactory.getLogger(ChatController.class);

    @Autowired
    private AiOpsService aiOpsService;

    @Autowired
    private ChatService chatService;

    @Autowired
    private ToolCallbackProvider tools;

    @Autowired
    private ChatSessionService chatSessionService;

    private final ExecutorService executor = Executors.newCachedThreadPool();

    /**
     * 普通对话接口（支持工具调用）
     * 与 /chat_react 逻辑一致，但直接返回完整结果而非流式输出
     */
    @PostMapping("/chat")
    public ResponseEntity<ApiResponse<ChatResponse>> chat(@RequestBody ChatRequest request) {
        try {
            logger.info("收到对话请求 - SessionId: {}, Question: {}", request.getId(), request.getQuestion());
            Long userId = getCurrentUserId();

            // Step 1: 参数校验，避免空问题触发模型调用
            if (request.getQuestion() == null || request.getQuestion().trim().isEmpty()) {
                logger.warn("问题内容为空");
                return ResponseEntity.ok(ApiResponse.success(ChatResponse.error("问题内容不能为空")));
            }

            // Step 2: 读取会话历史，提供多轮上下文
            ChatSession session = chatSessionService.getOrCreateSession(userId, request.getId());
            List<Map<String, String>> history = chatSessionService.getHistory(userId, session);
            logger.info("会话历史消息对数: {}", history.size() / 2);

            // Step 3: 创建模型与 Agent（包含工具与系统提示词）
            DashScopeApi dashScopeApi = chatService.createDashScopeApi();
            DashScopeChatModel chatModel = chatService.createStandardChatModel(dashScopeApi);
            chatService.logAvailableTools();
            logger.info("开始 ReactAgent 对话（支持自动工具调用）");
            // 构建系统提示词（包含历史消息）
            String systemPrompt = chatService.buildSystemPrompt(history);
            // 创建 ReactAgent
            ReactAgent agent = chatService.createReactAgent(chatModel, systemPrompt);
            // 执行对话
            String fullAnswer = chatService.executeChat(agent, request.getQuestion());
            // Step 4: 回写会话历史并返回结果
            chatSessionService.addMessage(userId, session, request.getQuestion(), fullAnswer);
            logger.info("已更新会话历史 - SessionId: {}", session.getSessionId());
            return ResponseEntity.ok(ApiResponse.success(ChatResponse.success(fullAnswer)));

        } catch (Exception e) {
            logger.error("对话失败", e);
            return ResponseEntity.ok(ApiResponse.success(ChatResponse.error(e.getMessage())));
        }
    }

    /**
     * 清空会话历史
     */
    @PostMapping("/chat/clear")
    public ResponseEntity<ApiResponse<String>> clearChatHistory(@RequestBody ClearRequest request) {
        try {
            logger.info("收到清空会话历史请求 - SessionId: {}", request.getId());
            Long userId = getCurrentUserId();

            if (request.getId() == null || request.getId().isEmpty()) {
                return ResponseEntity.ok(ApiResponse.error("会话ID不能为空"));
            }

            if (chatSessionService.clearHistory(userId, request.getId())) {
                return ResponseEntity.ok(ApiResponse.success("会话历史已清空"));
            } else {
                return ResponseEntity.ok(ApiResponse.error("会话不存在"));
            }

        } catch (Exception e) {
            logger.error("清空会话历史失败", e);
            return ResponseEntity.ok(ApiResponse.error(e.getMessage()));
        }
    }

    /**
     * 保存前端“近期对话”列表（用于跨浏览器共享）
     */
    @PostMapping("/chat/histories")
    public ResponseEntity<ApiResponse<String>> saveChatHistories(@RequestBody List<Map<String, Object>> histories) {
        try {
            Long userId = getCurrentUserId();
            chatSessionService.saveUiChatHistories(userId, histories);
            return ResponseEntity.ok(ApiResponse.success("历史对话已保存"));
        } catch (Exception e) {
            logger.error("保存历史对话列表失败", e);
            return ResponseEntity.ok(ApiResponse.error(e.getMessage()));
        }
    }

    /**
     * 获取前端“近期对话”列表（用于跨浏览器读取）
     */
    @GetMapping("/chat/histories")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> getChatHistories() {
        try {
            Long userId = getCurrentUserId();
            List<Map<String, Object>> histories = chatSessionService.loadUiChatHistories(userId);
            return ResponseEntity.ok(ApiResponse.success(histories));
        } catch (Exception e) {
            logger.error("获取历史对话列表失败", e);
            return ResponseEntity.ok(ApiResponse.error(e.getMessage()));
        }
    }

    /**
     * 获取指定会话的完整消息历史（用于前端会话详情展示）
     */
    @GetMapping("/chat/session/messages/{sessionId}")
    public ResponseEntity<ApiResponse<List<Map<String, String>>>> getSessionMessages(@PathVariable String sessionId) {
        try {
            Long userId = getCurrentUserId();
            Optional<List<Map<String, String>>> messagesOptional = chatSessionService.getSessionMessages(userId, sessionId);
            if (messagesOptional.isPresent()) {
                return ResponseEntity.ok(ApiResponse.success(messagesOptional.get()));
            }
            return ResponseEntity.ok(ApiResponse.error("会话不存在"));
        } catch (Exception e) {
            logger.error("获取会话消息失败 - SessionId: {}", sessionId, e);
            return ResponseEntity.ok(ApiResponse.error(e.getMessage()));
        }
    }

    /**
     * ReactAgent 对话接口（SSE 流式模式，支持多轮对话，支持自动工具调用，例如获取当前时间，查询日志，告警等）
     * 支持 session 管理，保留对话历史
     */
    @PostMapping(value = "/chat_stream", produces = "text/event-stream;charset=UTF-8")
    public SseEmitter chatStream(@RequestBody ChatRequest request) {
        SseEmitter emitter = new SseEmitter(300000L);
        Long userId = getCurrentUserId();

        // Step 1: 请求前置校验
        if (request.getQuestion() == null || request.getQuestion().trim().isEmpty()) {
            logger.warn("问题内容为空");
            try {
                emitter.send(SseEmitter.event().name("message").data(SseMessage.error("问题内容不能为空"), MediaType.APPLICATION_JSON));
                emitter.complete();
            } catch (IOException e) {
                emitter.completeWithError(e);
            }
            return emitter;
        }

        executor.execute(() -> {
            try {
                logger.info("收到 ReactAgent 对话请求 - SessionId: {}, Question: {}", request.getId(), request.getQuestion());

                // Step 2: 构建上下文与 Agent
                ChatSession session = chatSessionService.getOrCreateSession(userId, request.getId());
                List<Map<String, String>> history = chatSessionService.getHistory(userId, session);
                logger.info("ReactAgent 会话历史消息对数: {}", history.size() / 2);
                // 创建 DashScope API 和 ChatModel
                DashScopeApi dashScopeApi = chatService.createDashScopeApi();
                DashScopeChatModel chatModel = chatService.createStandardChatModel(dashScopeApi);
                // 记录可用工具
                chatService.logAvailableTools();

                logger.info("开始 ReactAgent 流式对话（支持自动工具调用）");
                // 构建系统提示词（包含历史消息）
                String systemPrompt = chatService.buildSystemPrompt(history);
                // 创建 ReactAgent
                ReactAgent agent = chatService.createReactAgent(chatModel, systemPrompt);
                // 用于累积完整答案
                StringBuilder fullAnswerBuilder = new StringBuilder();
                Flux<NodeOutput> stream = agent.stream(request.getQuestion());
                // Step 3: 订阅流式结果并转发到前端 SSE
                stream.subscribe(
                        output -> {
                            try {
                                if (output instanceof StreamingOutput streamingOutput) {
                                    OutputType type = streamingOutput.getOutputType();
                                    // 处理模型推理的流式输出
                                    if (type == OutputType.AGENT_MODEL_STREAMING) {
                                        // 流式增量内容，逐步显示
                                        String chunk = streamingOutput.message().getText();
                                        if (chunk != null && !chunk.isEmpty()) {
                                            fullAnswerBuilder.append(chunk);
                                    // 实时发送到前端
                                            emitter.send(SseEmitter.event()
                                                    .name("message")
                                                    .data(SseMessage.content(chunk), MediaType.APPLICATION_JSON));

                                            logger.info("发送流式内容: {}", chunk);
                                        }
                                    } else if (type == OutputType.AGENT_MODEL_FINISHED) {
                                        logger.info("模型输出完成");
                                    } else if (type == OutputType.AGENT_TOOL_FINISHED) {
                                        logger.info("工具调用完成: {}", output.node());
                                    } else if (type == OutputType.AGENT_HOOK_FINISHED) {
                                        logger.debug("Hook 执行完成: {}", output.node());
                                    }
                                }
                            } catch (IOException e) {
                                logger.error("发送流式消息失败", e);
                                throw new RuntimeException(e);
                            }
                        },
                        error -> {
                            // 错误处理
                            logger.error("ReactAgent 流式对话失败", error);
                            try {
                                emitter.send(SseEmitter.event()
                                        .name("message")
                                        .data(SseMessage.error(error.getMessage()), MediaType.APPLICATION_JSON));
                            } catch (IOException ex) {
                                logger.error("发送错误消息失败", ex);
                            }
                            emitter.completeWithError(error);
                        },
                        () -> {
                            try {
                                String fullAnswer = fullAnswerBuilder.toString();
                                logger.info("ReactAgent 流式对话完成 - SessionId: {}, 答案长度: {}",
                                        session.getSessionId(), fullAnswer.length());

                                // Step 4: 流结束后统一回写会话
                                chatSessionService.addMessage(userId, session, request.getQuestion(), fullAnswer);
                                logger.info("已更新会话历史 - SessionId: {}", session.getSessionId());

                                emitter.send(SseEmitter.event()
                                        .name("message")
                                        .data(SseMessage.done(), MediaType.APPLICATION_JSON));
                                emitter.complete();
                            } catch (IOException e) {
                                logger.error("发送完成消息失败", e);
                                emitter.completeWithError(e);
                            }
                        }
                );

            } catch (Exception e) {
                logger.error("ReactAgent 对话初始化失败", e);
                try {
                    emitter.send(SseEmitter.event()
                            .name("message")
                            .data(SseMessage.error(e.getMessage()), MediaType.APPLICATION_JSON));
                } catch (IOException ex) {
                    logger.error("发送错误消息失败", ex);
                }
                emitter.completeWithError(e);
            }
        });

        return emitter;
    }

    /**
     * AI 智能运维接口（SSE 流式模式）- 自动分析告警并生成运维报告
     * 无需用户输入，自动执行告警分析流程
     */
    @PostMapping(value = "/ai_ops", produces = "text/event-stream;charset=UTF-8")
    public SseEmitter aiOps(@RequestBody(required = false) ChatRequest request) {
        // 10分钟超时（告警分析可能较慢）
        SseEmitter emitter = new SseEmitter(600000L);
        String requestSessionId = request != null ? request.getId() : null;
        Long userId = getCurrentUserId();

        executor.execute(() -> {
            try {
                ChatSession session = chatSessionService.getOrCreateSession(userId, requestSessionId);
                logger.info("收到 AI 智能运维请求 - SessionId: {}, 启动多 Agent 协作流程", session.getSessionId());

                // Step 1: 初始化 AIOps 专用模型参数
                DashScopeApi dashScopeApi = chatService.createDashScopeApi();
                DashScopeChatModel chatModel = DashScopeChatModel.builder()
                        .dashScopeApi(dashScopeApi)
                        .defaultOptions(DashScopeChatOptions.builder()
                                .withModel(DashScopeChatModel.DEFAULT_MODEL_NAME)
                                .withTemperature(0.3)
                                .withMaxToken(8000)
                                .withTopP(0.9)
                                .build())
                        .build();

                ToolCallback[] toolCallbacks = tools.getToolCallbacks();

                emitter.send(SseEmitter.event().name("message")
                        .data(SseMessage.progress("正在分析告警并生成报告，请稍候..."), MediaType.APPLICATION_JSON));

                // 采用与 /chat_stream 一致的流式推送模式
                ReactAgent aiOpsAgent = aiOpsService.createStreamingAiOpsAgent(chatModel, toolCallbacks);
                String taskPrompt = "你是企业级 SRE，接到了自动化告警排查任务。请结合工具调用并持续输出 Markdown 报告，要求边分析边输出，禁止编造。";
                StringBuilder fullReportBuilder = new StringBuilder();
                Flux<NodeOutput> stream = aiOpsAgent.stream(taskPrompt);

                stream.subscribe(
                        output -> {
                            try {
                                if (output instanceof StreamingOutput streamingOutput) {
                                    OutputType type = streamingOutput.getOutputType();

                                    if (type == OutputType.AGENT_MODEL_STREAMING) {
                                        String chunk = streamingOutput.message().getText();
                                        if (chunk != null && !chunk.isEmpty()) {
                                            fullReportBuilder.append(chunk);
                                            emitter.send(SseEmitter.event()
                                                    .name("message")
                                                    .data(SseMessage.content(chunk), MediaType.APPLICATION_JSON));
                                        }
                                    } else if (type == OutputType.AGENT_TOOL_FINISHED) {
                                        logger.info("AIOps 工具调用完成: {}", output.node());
                                        emitter.send(SseEmitter.event().name("message")
                                                .data(SseMessage.progress("工具执行中: " + output.node()), MediaType.APPLICATION_JSON));
                                    } else if (type == OutputType.AGENT_MODEL_FINISHED) {
                                        logger.info("AIOps 模型输出完成");
                                    }
                                }
                            } catch (IOException e) {
                                logger.error("发送 AIOps 流式消息失败", e);
                                throw new RuntimeException(e);
                            }
                        },
                        error -> {
                            logger.error("AIOps 流式分析失败", error);
                            try {
                                emitter.send(SseEmitter.event().name("message")
                                        .data(SseMessage.error("AI Ops 流程失败: " + error.getMessage()), MediaType.APPLICATION_JSON));
                            } catch (IOException ex) {
                                logger.error("发送错误消息失败", ex);
                            }
                            emitter.completeWithError(error);
                        },
                        () -> {
                            try {
                                String fullReport = fullReportBuilder.toString();
                                logger.info("AIOps 流式分析完成，报告长度: {}", fullReport.length());

                                // Step 2: 回写 AI Ops 完整报告到会话历史，供后续 chat/chat_stream 追问复用
                                chatSessionService.addAiOpsReport(userId, session, fullReport);
                                logger.info("AI Ops 完整报告已写入会话上下文 - SessionId: {}", session.getSessionId());

                                if (fullReport.isEmpty()) {
                                    emitter.send(SseEmitter.event().name("message")
                                            .data(SseMessage.content("⚠️ 流程已完成，但未生成有效报告内容。"), MediaType.APPLICATION_JSON));
                                }
                                emitter.send(SseEmitter.event().name("message")
                                        .data(SseMessage.done(), MediaType.APPLICATION_JSON));
                                emitter.complete();
                            } catch (IOException e) {
                                logger.error("发送完成消息失败", e);
                                emitter.completeWithError(e);
                            }
                        }
                );

            } catch (Exception e) {
                logger.error("AI Ops 多 Agent 协作失败", e);
                try {
                    emitter.send(SseEmitter.event().name("message")
                            .data(SseMessage.error("AI Ops 流程失败: " + e.getMessage()), MediaType.APPLICATION_JSON));
                } catch (IOException ex) {
                    logger.error("发送错误消息失败", ex);
                }
                emitter.completeWithError(e);
            }
        });

        return emitter;
    }

    /**
     * 获取会话信息
     */
    @GetMapping("/chat/session/{sessionId}")
    public ResponseEntity<ApiResponse<SessionInfoResponse>> getSessionInfo(@PathVariable String sessionId) {
        try {
            logger.info("收到获取会话信息请求 - SessionId: {}", sessionId);
            Long userId = getCurrentUserId();

            Optional<SessionInfoResponse> responseOptional = chatSessionService.getSessionInfo(userId, sessionId);
            if (responseOptional.isPresent()) {
                return ResponseEntity.ok(ApiResponse.success(responseOptional.get()));
            } else {
                return ResponseEntity.ok(ApiResponse.error("会话不存在"));
            }

        } catch (Exception e) {
            logger.error("获取会话信息失败", e);
            return ResponseEntity.ok(ApiResponse.error(e.getMessage()));
        }
    }

    private Long getCurrentUserId() {
        AuthUser authUser = AuthContext.getCurrentUser();
        if (authUser == null || authUser.getUserId() == null) {
            throw new IllegalStateException("未登录或登录已失效");
        }
        return authUser.getUserId();
    }
}
