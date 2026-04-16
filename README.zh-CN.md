<div align="center">
  <p><strong>CN 中文 | US <a href="./README.md">English</a></strong></p>
  <h1>Ops Mind Agent</h1>
  <p><strong>面向智能运维场景的产品化 Agent 应用</strong></p>
  <p>流式对话 · AI Ops · RAG 检索增强 · Skills 扩展 · 多用户会话管理</p>
  <p>前端产品形态：<strong>小维助手</strong></p>
</div>

## 项目定位

`Ops Mind Agent` 不是简单把大模型接到聊天框上的演示项目，而是一套面向智能运维场景设计的产品化 Agent 系统。项目将 `Spring AI`、检索增强、官方 `Skills`、会话管理、用户认证、流式交互和容器化部署整合在同一套工程中，用于承载运维问答、告警分析、知识检索、联网搜索和会话追溯等核心场景。

当前前端产品形态为 **小维助手**，同时提供独立的 `AI Ops` 工作流入口，使项目既能满足自然语言交互，也能承担更偏结构化的运维分析任务。

## 产品价值

这个项目的重点，不只是“能接大模型”，而是“已经具备产品轮廓和可落地的工程基础”。它适合用作以下场景的基础底座：

- 团队内部智能运维助手
- 智能化告警解读与运维报告生成
- 企业知识问答与文档增强检索
- 需要联网搜索能力的时效性问答系统
- 既支持本地开发，又支持服务器部署的 Agent 应用模板

## 核心能力

| 能力 | 说明 |
| --- | --- |
| 流式对话 | 支持普通问答与 `SSE` 流式输出，降低等待感并提升交互体验 |
| AI Ops 工作流 | 支持 `/api/ai_ops` 流式分析过程与运维报告输出 |
| 上下文会话 | 支持会话切换、最近对话、完整消息历史查看和持续追问 |
| RAG 检索链路 | 支持 Markdown/TXT 文档上传、分片、向量化与 Milvus 检索增强 |
| Skills 集成 | 已接入 Spring AI 官方 `Skills` 机制，内置 `web-search` 搜索技能 |
| 用户认证与隔离 | 支持注册、登录、验证码、JWT 鉴权和多用户会话隔离 |
| 数据持久化 | MySQL 持久化用户、会话、消息，Redis 承担高频状态与缓存 |
| 双模式部署 | 支持本地开发模式和服务器容器化部署模式 |

## 产品化设计特征

很多大模型项目只停留在一个聊天框和一次性对话上，而这个项目已经具备更完整的产品和工程特征：

- 有注册登录，而不是匿名单轮问答
- 有近期会话和完整历史，而不是只展示当前屏幕内容
- 有独立 `AI Ops` 入口，而不是所有问题都混在一个聊天窗口中
- 有知识库和 Skills，而不是完全依赖模型记忆
- 有本地开发模式与服务器部署模式，具备实际交付能力

因此，这个项目更适合作为企业内部智能运维入口的基础工程，而不只是概念验证。

## 产品形态

### 小维助手

主对话入口面向自然语言交互，适合承载日常运维咨询、知识问答、平台使用说明查询和基于上下文的连续追问。

### AI Ops 工作流

页面右下角的 `AI Ops` 按钮用于触发独立的智能运维分析链路。接口支持流式输出，可逐步展示分析过程和最终报告，并将结果并入当前会话上下文，便于后续继续追问。

### 知识增强与公网搜索

项目同时具备两类知识增强能力：

- 内部知识：基于 `aiops-docs` 与上传文档构建 RAG 检索能力
- 外部知识：基于 `web-search` Skill 调用公网搜索补充时效性信息

这意味着系统既能回答企业内部问题，也能处理“今天 / 最新 / 官方公告”这类需要外部信息的请求。

## 产品功能拆解

### 对话助手能力

- 普通问答与流式问答双模式
- 多轮上下文承接
- 会话切换与继续追问
- `AI Ops` 输出结果并入同一上下文

### AI Ops 分析能力

- 独立入口触发
- 面向告警与运维场景
- 支持分析过程可视化输出
- 支持生成结构化运维报告

### 知识库增强能力

- 上传 `txt`、`md` 文档
- 自动保存并按原始文件名覆盖
- 上传后自动构建向量索引
- 基于 Milvus 做语义检索增强

### 联网搜索能力

- 内置 `web-search` Skill
- 面向公开互联网信息查询
- 适合官方公告、新闻、版本变更、时效性问题
- 与内部知识库互补，而不是替代关系

### 多用户产品能力

- 注册 / 登录 / 验证码流程
- JWT 鉴权
- 用户隔离的会话体系
- 近期会话列表与历史消息追溯
- 更接近真实产品而不是实验型沙盒

## 系统架构

系统由四个层次组成：

- 前端层：负责登录注册、对话交互、AI Ops 展示、近期会话管理
- 应用层：基于 `Spring Boot 3` 与 `Spring AI` 负责鉴权、上下文编排、Agent 执行、工具路由与 SSE 推送
- 数据层：MySQL 持久化用户、会话、消息；Redis 负责验证码、登录限制、会话相关缓存和高频状态
- 检索与扩展层：Milvus 提供向量检索，Tavily 提供联网搜索能力，Skills 负责能力挂载

典型调用链路：

```text
浏览器
  -> 认证 / 对话 / AI Ops 接口
  -> 会话上下文组装
  -> 工具 / Skills / RAG 检索
  -> DashScope 大模型
  -> SSE 流式输出或 JSON 响应
```

## 技术栈

- `Java 17`
- `Spring Boot 3.5.0`
- `Spring AI 1.1.2`
- `Spring AI Alibaba / DashScope`
- `MySQL 8`
- `Redis 7`
- `Milvus 2.5.x`
- `Docker / Docker Compose`
- `JWT`
- `Tavily Search`

## 快速开始

### 模式 A：本地开发

适合本地联调和代码开发，依赖组件由 Docker 承载，应用本身以本地进程运行。

```bash
make init-local
```

该命令会完成：

1. 启动 MySQL、Redis、Milvus 等中间件容器
2. 本地后台启动 Spring Boot 服务
3. 等待健康检查通过
4. 自动上传 `aiops-docs` 中的 Markdown 文档到知识库

常用命令：

```bash
make up-local
make start-local
make restart-local
make logs-local
make upload
```

### 模式 B：服务器部署

适合远程服务器部署，由 Docker 统一托管应用和依赖服务。

```bash
make init
```

该命令会完成：

1. 启动完整 Docker Compose 服务
2. 等待 MySQL、Redis、Milvus 与应用服务就绪
3. 自动上传 `aiops-docs` 中的 Markdown 文档到知识库

常用命令：

```bash
make up
make start
make restart
make logs
```

服务器模式下需要注意：

- `ops-mind-agent.jar` 需要与 `docker-compose.yml` 位于同一级目录
- `app` 服务通过 `--profile app` 启用
- 容器内使用 `eclipse-temurin:17-jre` 启动应用，无需宿主机单独安装 JDK

## 运行命令

| 命令 | 说明 |
| --- | --- |
| `make init` | 服务器模式一键初始化，启动全部容器并上传文档 |
| `make up` | 服务器模式启动全部容器，包含 `app` 容器 |
| `make start` | 启动应用容器 |
| `make stop` | 停止应用容器 |
| `make restart` | 重启应用容器 |
| `make logs` | 查看应用容器日志 |
| `make init-local` | 本地模式一键初始化，中间件容器 + 本地 Spring Boot 服务 |
| `make up-local` | 仅启动本地依赖中间件 |
| `make start-local` | 本地启动 Spring Boot 服务 |
| `make stop-local` | 停止本地 Spring Boot 服务 |
| `make restart-local` | 重启本地 Spring Boot 服务 |
| `make logs-local` | 查看本地服务日志 |
| `make upload` | 上传 `aiops-docs` 目录下文档并写入向量库 |
| `make down` | 停止 Docker Compose 服务 |
| `make status` | 查看容器状态 |

## 接口概览

### 认证接口

| 接口 | 说明 |
| --- | --- |
| `GET /api/auth/captcha` | 获取验证码 |
| `POST /api/auth/register` | 注册并自动登录 |
| `POST /api/auth/login` | 用户登录 |
| `POST /api/auth/logout` | 退出登录 |
| `GET /api/auth/me` | 获取当前用户信息 |

### 对话接口

| 接口 | 说明 |
| --- | --- |
| `POST /api/chat` | 普通问答接口 |
| `POST /api/chat_stream` | SSE 流式对话接口 |
| `POST /api/chat/clear` | 清空指定会话历史 |
| `POST /api/chat/histories` | 保存前端近期对话列表 |
| `GET /api/chat/histories` | 获取前端近期对话列表 |
| `GET /api/chat/session/messages/{sessionId}` | 获取指定会话完整消息历史 |
| `GET /api/chat/session/{sessionId}` | 获取指定会话信息 |

### AI Ops 接口

| 接口 | 说明 |
| --- | --- |
| `POST /api/ai_ops` | AI Ops 流式分析与报告输出 |

### 知识导入接口

| 接口 | 说明 |
| --- | --- |
| `POST /api/upload` | 上传文档并触发向量索引构建 |

### 健康检查

| 接口 | 说明 |
| --- | --- |
| `GET /milvus/health` | 应用与向量能力健康检查 |

## 知识库

项目默认将 `aiops-docs` 作为初始知识文档目录：

```text
aiops-docs/
```

当前上传接口：

```http
POST /api/upload
```

文档导入特性：

- 支持 `txt`、`md` 文件上传
- 按原始文件名保存，可覆盖同名文件
- 上传成功后自动构建向量索引
- 适用于初始化运维文档、测试 RAG 与向量搜索效果

如需重新导入目录文档：

```bash
make upload
```

## Skills 扩展

项目已接入 Spring AI 官方 `Skills` 机制，技能目录如下：

```text
src/main/resources/skills/
└── web-search/
    └── SKILL.md
```

当前已启用技能：

- `web-search`：通过 `searchWeb` 工具执行联网搜索，并将结果纳入生成链路

这使得项目同时具备内部知识检索和外部实时信息补充两种能力来源。

## 配置说明

主要运行配置位于 `src/main/resources/application.yml`。

关键项包括：

- `server.port=8600`
- `spring.datasource.url=jdbc:mysql://localhost:3390/aiops_agent...`
- `spring.data.redis.host=localhost`
- `milvus.host=localhost`
- `milvus.port=19530`
- `chat.session.ttl-minutes=10080`
- `chat.session.max-window-size=6`
- `chat.session.context-cache.ttl-minutes=60`
- `auth.jwt.expire-minutes=1440`
- `auth.login.max-retry=5`
- `auth.login.lock-minutes=60`
- `rag.top-k=3`
- `rag.model=qwen3-max`
- `search.enabled=true`
- `search.provider=tavily`

生产环境建议：

- 使用环境变量或私有配置覆盖模型与搜索密钥
- 替换默认 JWT Secret
- 根据部署资源调优超时和重试参数

推荐环境变量示例：

```bash
export DASHSCOPE_API_KEY=your-dashscope-key
export TAVILY_API_KEY=your-tavily-key
```

## Docker Compose 组成

当前 `docker-compose.yml` 包含以下服务：

- `mysql`
- `redis`
- `etcd`
- `minio`
- `standalone`（Milvus）
- `attu`
- `app`（通过 `app profile` 启用）

端口映射：

- `mysql -> 3390`
- `redis -> 6379`
- `milvus -> 19530`
- `app -> 8600`

`app` 服务依赖 `mysql`、`redis`、`standalone` 健康检查通过后再启动，适合完整容器化部署。

## 项目结构

```text
ops-mind-agent/
├── aiops-docs/                      # 运维知识文档目录
├── mysql-init/                      # MySQL 初始化脚本
├── docker-compose.yml               # 中间件与应用容器编排
├── Makefile                         # 本地/服务器统一启动入口
├── src/main/java/com/ops/
│   ├── agent/                       # Agent 工具与能力封装
│   ├── auth/                        # 认证相关组件
│   ├── config/                      # Spring 配置与 Skills 装配
│   ├── controller/                  # Web API 入口
│   ├── dto/                         # 请求与响应对象
│   ├── entity/                      # MySQL 持久化实体
│   ├── repository/                  # JPA 数据访问层
│   ├── service/                     # 核心业务服务
│   ├── service/session/             # 会话管理与上下文处理
│   └── tool/                        # 业务工具定义
└── src/main/resources/
    ├── application.yml              # 运行配置
    ├── skills/                      # 官方 Skills 目录
    └── static/                      # 前端静态页面
```

## 适用场景

这个项目适合用于以下方向的原型验证或工程基础建设：

- 智能运维问答助手
- 告警解读与排障辅助
- 企业内部知识问答与文档检索
- 公开资讯、官方公告、版本动态查询
- Spring AI / Skills / RAG 一体化实践项目
- 具备用户隔离与会话连续性的内部智能助手产品
