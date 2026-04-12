<div align="center">
  <h1>Ops Mind Agent</h1>
  <p><strong>面向智能运维场景的 Agent 应用</strong></p>
  <p>智能运维 · 知识增强 · Skills 扩展 · AI Ops · 流式交互</p>
  <p>
    前端产品形态：<strong>小维助手</strong>
    <br />
    <a href="./README.en.md">英文版 README</a>
  </p>
</div>

## 项目概览

`Ops Mind Agent` 是一套面向智能运维场景设计的 Agent 系统。项目将 `Spring AI`、知识库检索、官方 `Skills` 机制、流式交互以及业务化会话管理整合在同一套工程中，用于承载运维问答、告警分析、知识检索与联网搜索等核心场景。

当前前端产品形态为 **小维助手**，同时保留更偏流程化分析的 `AI Ops` 入口，使项目既适合自然语言交互，也适合结构化运维分析。

## 核心亮点

| 能力 | 说明 |
| --- | --- |
| 流式对话 | 支持普通问答与 `SSE` 流式输出，响应过程可逐步展示 |
| AI Ops 工作流 | 支持 `/api/ai_ops` 流式生成分析过程与运维报告 |
| RAG 检索链路 | 支持 Markdown/TXT 文档上传、分片、向量化与 Milvus 检索增强 |
| Skills 集成 | 已接入 Spring AI 官方 Skills 机制，内置 `web-search` 搜索技能 |
| 会话持久化 | 用户、会话、消息持久化到 MySQL，Redis 用于高频上下文缓存与状态辅助 |
| 认证与隔离 | 支持注册、登录、验证码、JWT 鉴权与多用户会话隔离 |

## 产品形态

### 小维助手

主对话入口面向自然语言交互，适合承载日常运维咨询、知识问答和基于上下文的连续追问。

### AI Ops 接口

页面右下角的 `AI Ops` 按钮用于触发智能运维分析链路。当前接口支持流式输出，可逐步渲染分析过程和最终报告，并将分析结果并入当前会话上下文，便于后续继续追问。

### 知识增强

项目同时具备两类知识增强路径：

- 内部知识：基于 `aiops-docs` 与上传文档构建 RAG 检索能力
- 外部知识：基于 `web-search` Skill 调用公网搜索补充时效性信息

## 系统架构

系统由四个层次组成：

- 前端层：负责登录注册、对话交互、AI Ops 展示、近期会话管理
- 应用层：基于 `Spring Boot 3` 与 `Spring AI` 负责鉴权、上下文编排、Agent 执行与 SSE 推送
- 数据层：MySQL 持久化用户、会话、消息；Redis 负责验证码、登录限制与上下文窗口缓存
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

项目已接入 Spring AI 官方 Skills 机制，技能目录如下：

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

`docker-compose.yml` 当前包含以下服务：

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
- 告警分析与运维报告生成
- 企业内部知识问答与文档检索
- 具备联网搜索能力的 Agent 应用
- Spring AI / Skills / RAG 一体化实践项目
