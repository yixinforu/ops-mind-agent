package com.ops.controller;

import com.alibaba.cloud.ai.dashscope.api.DashScopeApi;
import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatModel;
import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatOptions;
import com.alibaba.cloud.ai.graph.NodeOutput;
import com.alibaba.cloud.ai.graph.agent.ReactAgent;
import com.alibaba.cloud.ai.graph.streaming.OutputType;
import com.alibaba.cloud.ai.graph.streaming.StreamingOutput;
import com.ops.auth.AuthContext;
import com.ops.auth.AuthUser;
import com.ops.dto.chat.ChatRequest;
import com.ops.dto.chat.ChatResponse;
import com.ops.dto.chat.ClearRequest;
import com.ops.dto.chat.SessionInfoResponse;
import com.ops.dto.chat.SseMessage;
import com.ops.dto.common.ApiResponse;
import com.ops.service.AiOpsService;
import com.ops.service.ChatService;
import com.ops.service.session.ChatSession;
import com.ops.service.session.ChatSessionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import reactor.core.publisher.Flux;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * 统一 API 控制器。
 * 负责普通问答、流式问答以及 AI Ops 自动分析三类能力的接口编排，
 * 并在控制层统一处理会话读取、SSE 消息转发和异常返回。
 */
@RestController
@RequestMapping("/api")
public class ChatController {

    private static final Logger logger = LoggerFactory.getLogger(ChatController.class);
    private static final String SSE_EVENT_NAME = "message";
    private static final String STREAM_BUSY_MESSAGE = "当前请求较多，请稍后再试";

    @Autowired
    private AiOpsService aiOpsService;

    @Autowired
    private ChatService chatService;

    @Autowired
    private ToolCallbackProvider tools;

    @Autowired
    private ChatSessionService chatSessionService;

    @Autowired
    @Qualifier("chatStreamTaskExecutor")
    private ThreadPoolTaskExecutor chatStreamTaskExecutor;

    /**
     * 普通对话接口（支持工具调用）。
     * 与流式接口复用同一套会话上下文，但以一次性返回完整答案为主。
     */
    @PostMapping("/chat")
    public ResponseEntity<ApiResponse<ChatResponse>> chat(@RequestBody ChatRequest request) {
        try {
            logger.info("收到对话请求 - SessionId: {}, Question: {}", request.getId(), request.getQuestion());
            Long userId = getCurrentUserId();

            // Step 1: 参数校验，避免空问题触发模型调用。
            if (request.getQuestion() == null || request.getQuestion().trim().isEmpty()) {
                logger.warn("问题内容为空");
                return ResponseEntity.ok(ApiResponse.success(ChatResponse.error("问题内容不能为空")));
            }

            // Step 2: 读取会话历史，提供多轮上下文。
            ChatSession session = chatSessionService.getOrCreateSession(userId, request.getId());
            List<Map<String, String>> history = chatSessionService.getHistory(userId, session);
            logger.info("会话历史消息对数: {}", history.size() / 2);

            // Step 3: 创建模型与 Agent（包含工具与系统提示词）。
            DashScopeApi dashScopeApi = chatService.createDashScopeApi();
            DashScopeChatModel chatModel = chatService.createStandardChatModel(dashScopeApi);
            chatService.logAvailableTools();
            logger.info("开始 ReactAgent 对话（支持自动工具调用）");
            String systemPrompt = chatService.buildSystemPrompt(history);
            ReactAgent agent = chatService.createReactAgent(chatModel, systemPrompt);
            String fullAnswer = chatService.executeChat(agent, request.getQuestion());

            // Step 4: 回写会话历史并返回结果。
            chatSessionService.addMessage(userId, session, request.getQuestion(), fullAnswer);
            logger.info("已更新会话历史 - SessionId: {}", session.getSessionId());
            return ResponseEntity.ok(ApiResponse.success(ChatResponse.success(fullAnswer)));
        } catch (Exception e) {
            logger.error("对话失败", e);
            return ResponseEntity.ok(ApiResponse.success(ChatResponse.error(e.getMessage())));
        }
    }

    /**
     * 清空指定会话的历史消息。
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
            }
            return ResponseEntity.ok(ApiResponse.error("会话不存在"));
        } catch (Exception e) {
            logger.error("清空会话历史失败", e);
            return ResponseEntity.ok(ApiResponse.error(e.getMessage()));
        }
    }

    /**
     * 保存前端“近期对话”列表，便于跨刷新和跨浏览器同步展示。
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
     * 获取前端“近期对话”列表，供左侧会话导航回显。
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
     * 获取指定会话的完整消息历史，用于前端回显完整上下文。
     */
    @GetMapping("/chat/session/messages/{sessionId}")
    public ResponseEntity<ApiResponse<List<Map<String, String>>>> getSessionMessages(@PathVariable String sessionId) {
        try {
            Long userId = getCurrentUserId();
            Optional<List<Map<String, String>>> messagesOptional =
                    chatSessionService.getSessionMessages(userId, sessionId);
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
     * ReactAgent 对话接口（SSE 流式模式）。
     * 接口启动时只负责提交任务，真正的流式生成在有界线程池中执行，
     * 以避免长连接请求无限制占用新线程。
     */
    @PostMapping(value = "/chat_stream", produces = "text/event-stream;charset=UTF-8")
    public SseEmitter chatStream(@RequestBody ChatRequest request) {
        SseEmitter emitter = createEmitter(300000L);
        Long userId = getCurrentUserId();

        // Step 1: 请求前置校验。
        if (request.getQuestion() == null || request.getQuestion().trim().isEmpty()) {
            logger.warn("问题内容为空");
            sendSseErrorAndComplete(emitter, "问题内容不能为空", null);
            return emitter;
        }

        submitStreamingTask(emitter, "chat_stream", () -> {
            try {
                logger.info("收到 ReactAgent 对话请求 - SessionId: {}, Question: {}",
                        request.getId(), request.getQuestion());

                // Step 2: 构建上下文与 Agent。
                ChatSession session = chatSessionService.getOrCreateSession(userId, request.getId());
                List<Map<String, String>> history = chatSessionService.getHistory(userId, session);
                logger.info("ReactAgent 会话历史消息对数: {}", history.size() / 2);
                DashScopeApi dashScopeApi = chatService.createDashScopeApi();
                DashScopeChatModel chatModel = chatService.createStandardChatModel(dashScopeApi);
                chatService.logAvailableTools();

                logger.info("开始 ReactAgent 流式对话（支持自动工具调用）");
                String systemPrompt = chatService.buildSystemPrompt(history);
                ReactAgent agent = chatService.createReactAgent(chatModel, systemPrompt);
                StringBuilder fullAnswerBuilder = new StringBuilder();
                Flux<NodeOutput> stream = agent.stream(request.getQuestion());

                // Step 3: 订阅流式结果并转发到前端 SSE。
                stream.subscribe(
                        output -> {
                            try {
                                if (output instanceof StreamingOutput streamingOutput) {
                                    OutputType type = streamingOutput.getOutputType();
                                    if (type == OutputType.AGENT_MODEL_STREAMING) {
                                        String chunk = streamingOutput.message().getText();
                                        if (chunk != null && !chunk.isEmpty()) {
                                            fullAnswerBuilder.append(chunk);
                                            sendSseContent(emitter, chunk, "发送流式内容");
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
                            logger.error("ReactAgent 流式对话失败", error);
                            sendSseErrorAndComplete(emitter, error.getMessage(), error);
                        },
                        () -> {
                            try {
                                String fullAnswer = fullAnswerBuilder.toString();
                                logger.info("ReactAgent 流式对话完成 - SessionId: {}, 答案长度: {}",
                                        session.getSessionId(), fullAnswer.length());

                                // Step 4: 流结束后统一回写会话。
                                chatSessionService.addMessage(userId, session, request.getQuestion(), fullAnswer);
                                logger.info("已更新会话历史 - SessionId: {}", session.getSessionId());
                                sendSseDoneAndComplete(emitter);
                            } catch (IOException e) {
                                logger.error("发送完成消息失败", e);
                                emitter.completeWithError(e);
                            }
                        }
                );
            } catch (Exception e) {
                logger.error("ReactAgent 对话初始化失败", e);
                sendSseErrorAndComplete(emitter, e.getMessage(), e);
            }
        });

        return emitter;
    }

    /**
     * AI 智能运维接口（SSE 流式模式）。
     * 无需用户输入，自动执行告警分析流程，并将完整报告写入会话上下文。
     */
    @PostMapping(value = "/ai_ops", produces = "text/event-stream;charset=UTF-8")
    public SseEmitter aiOps(@RequestBody(required = false) ChatRequest request) {
        SseEmitter emitter = createEmitter(600000L);
        String requestSessionId = request != null ? request.getId() : null;
        Long userId = getCurrentUserId();

        submitStreamingTask(emitter, "ai_ops", () -> {
            try {
                ChatSession session = chatSessionService.getOrCreateSession(userId, requestSessionId);
                logger.info("收到 AI 智能运维请求 - SessionId: {}, 启动多 Agent 协作流程", session.getSessionId());

                // Step 1: 初始化 AIOps 专用模型参数。
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
                sendSseMessage(emitter, SseMessage.progress("正在分析告警并生成报告，请稍候..."));

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
                                            sendSseContent(emitter, chunk, null);
                                        }
                                    } else if (type == OutputType.AGENT_TOOL_FINISHED) {
                                        logger.info("AIOps 工具调用完成: {}", output.node());
                                        sendSseMessage(emitter, SseMessage.progress("工具执行中: " + output.node()));
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
                            sendSseErrorAndComplete(emitter, "AI Ops 流程失败: " + error.getMessage(), error);
                        },
                        () -> {
                            try {
                                String fullReport = fullReportBuilder.toString();
                                logger.info("AIOps 流式分析完成，报告长度: {}", fullReport.length());

                                // Step 2: 回写 AI Ops 完整报告到会话历史，供后续追问复用。
                                chatSessionService.addAiOpsReport(userId, session, fullReport);
                                logger.info("AI Ops 完整报告已写入会话上下文 - SessionId: {}", session.getSessionId());

                                if (fullReport.isEmpty()) {
                                    sendSseMessage(emitter, SseMessage.content("⚠️ 流程已完成，但未生成有效报告内容。"));
                                }
                                sendSseDoneAndComplete(emitter);
                            } catch (IOException e) {
                                logger.error("发送完成消息失败", e);
                                emitter.completeWithError(e);
                            }
                        }
                );
            } catch (Exception e) {
                logger.error("AI Ops 多 Agent 协作失败", e);
                sendSseErrorAndComplete(emitter, "AI Ops 流程失败: " + e.getMessage(), e);
            }
        });

        return emitter;
    }

    /**
     * 获取指定会话的基础信息。
     */
    @GetMapping("/chat/session/{sessionId}")
    public ResponseEntity<ApiResponse<SessionInfoResponse>> getSessionInfo(@PathVariable String sessionId) {
        try {
            logger.info("收到获取会话信息请求 - SessionId: {}", sessionId);
            Long userId = getCurrentUserId();

            Optional<SessionInfoResponse> responseOptional = chatSessionService.getSessionInfo(userId, sessionId);
            if (responseOptional.isPresent()) {
                return ResponseEntity.ok(ApiResponse.success(responseOptional.get()));
            }
            return ResponseEntity.ok(ApiResponse.error("会话不存在"));
        } catch (Exception e) {
            logger.error("获取会话信息失败", e);
            return ResponseEntity.ok(ApiResponse.error(e.getMessage()));
        }
    }

    /**
     * 创建 SSE 发射器。
     */
    private SseEmitter createEmitter(long timeoutMillis) {
        return new SseEmitter(timeoutMillis);
    }

    /**
     * 将流式任务提交到有界线程池。
     * 当线程池已满时，直接通过 SSE 返回拥塞提示，避免请求无限等待。
     */
    private void submitStreamingTask(SseEmitter emitter, String scene, Runnable task) {
        try {
            logExecutorStatus(scene);
            chatStreamTaskExecutor.execute(() -> {
                try {
                    task.run();
                } catch (Exception e) {
                    logger.error("流式任务执行异常 - Scene: {}", scene, e);
                    sendSseErrorAndComplete(emitter, "服务处理异常，请稍后再试", e);
                }
            });
        } catch (RejectedExecutionException e) {
            logger.warn("流式任务被线程池拒绝 - Scene: {}", scene, e);
            logExecutorStatus(scene + "-rejected");
            sendSseErrorAndComplete(emitter, STREAM_BUSY_MESSAGE, null);
        }
    }

    /**
     * 记录线程池当前状态，便于观察流式并发压力。
     */
    private void logExecutorStatus(String scene) {
        ThreadPoolExecutor executor = chatStreamTaskExecutor.getThreadPoolExecutor();
        if (executor == null) {
            logger.info("流式线程池未完成初始化 - Scene: {}", scene);
            return;
        }

        logger.info(
                "流式线程池状态 - Scene: {}, PoolSize: {}, Active: {}, QueueSize: {}, Completed: {}",
                scene,
                executor.getPoolSize(),
                executor.getActiveCount(),
                executor.getQueue().size(),
                executor.getCompletedTaskCount()
        );
    }

    /**
     * 发送通用 SSE 消息。
     */
    private void sendSseMessage(SseEmitter emitter, SseMessage message) throws IOException {
        emitter.send(SseEmitter.event()
                .name(SSE_EVENT_NAME)
                .data(message, MediaType.APPLICATION_JSON));
    }

    /**
     * 发送正文分片，并按需记录发送日志。
     */
    private void sendSseContent(SseEmitter emitter, String chunk, String logPrefix) throws IOException {
        sendSseMessage(emitter, SseMessage.content(chunk));
        if (logPrefix != null && !logPrefix.isBlank()) {
            logger.info("{}: {}", logPrefix, chunk);
        }
    }

    /**
     * 发送完成事件并关闭 SSE。
     */
    private void sendSseDoneAndComplete(SseEmitter emitter) throws IOException {
        sendSseMessage(emitter, SseMessage.done());
        emitter.complete();
    }

    /**
     * 发送错误事件并结束 SSE。
     */
    private void sendSseErrorAndComplete(SseEmitter emitter, String message, Throwable error) {
        try {
            sendSseMessage(emitter, SseMessage.error(message));
        } catch (IOException e) {
            logger.error("发送错误消息失败", e);
            if (error == null) {
                error = e;
            }
        }

        if (error == null) {
            emitter.complete();
        } else {
            emitter.completeWithError(error);
        }
    }

    /**
     * 获取当前登录用户 ID。
     */
    private Long getCurrentUserId() {
        AuthUser authUser = AuthContext.getCurrentUser();
        if (authUser == null || authUser.getUserId() == null) {
            throw new IllegalStateException("未登录或登录已失效");
        }
        return authUser.getUserId();
    }
}
