<div align="center">
  <h1>Ops Mind Agent</h1>
  <p><strong>A production-oriented agent stack for intelligent operations workflows</strong></p>
  <p>Spring AI · Retrieval · Skills · AI Ops · Streaming UX</p>
  <p>
    Product-facing UI: <strong>Xiaowei Assistant</strong>
    <br />
    <a href="./README.md">中文 README</a>
  </p>
</div>

## Overview

`Ops Mind Agent` is a production-oriented agent system built for operations scenarios rather than a thin chat wrapper around an LLM. It combines `Spring AI`, retrieval-augmented generation, official `Skills`, streaming interaction, and session-aware workflow design in a single engineering baseline.

The current product-facing UI is **Xiaowei Assistant**, while `AI Ops` remains available as a more workflow-centric entry for alert analysis, operational reasoning, and report generation.

## Highlights

| Capability | Description |
| --- | --- |
| Streaming Chat | Supports standard chat and `SSE`-based streaming responses |
| AI Ops Workflow | `/api/ai_ops` streams analysis steps and final operations reports |
| RAG Pipeline | Supports Markdown/TXT upload, chunking, vectorization, and Milvus retrieval |
| Skills Integration | Official Spring AI Skills support with built-in `web-search` |
| Session Persistence | Users, sessions, and messages are persisted in MySQL; Redis assists with high-frequency context state |
| Auth & Isolation | Registration, login, captcha, JWT authentication, and user-level conversation isolation |

## Product Surface

### Xiaowei Assistant

The main chat entry is designed for natural interaction, making it suitable for day-to-day operations Q&A, knowledge lookup, and contextual follow-up conversations.

### AI Ops

The `AI Ops` button is placed at the bottom-right corner of the page. It triggers the operations analysis workflow and streams both intermediate reasoning output and the final report. The result is merged back into the current conversation context so the user can continue asking follow-up questions naturally.

### Knowledge + Search

The system combines two complementary augmentation paths:

- Internal knowledge: RAG retrieval powered by `aiops-docs` and uploaded documents
- External knowledge: public web search powered by the `web-search` Skill

## Architecture

The system is organized into four layers:

- Frontend: login, registration, chat interaction, AI Ops display, recent sessions
- Application: `Spring Boot 3` + `Spring AI` for authentication, session orchestration, agent execution, and SSE delivery
- Data: MySQL for users, sessions, and messages; Redis for captcha, login restrictions, and context-window caching
- Retrieval and extension: Milvus for vector retrieval, Tavily for web search, Skills for capability registration

Typical request flow:

```text
Browser
  -> Auth / Chat / AI Ops API
  -> Session Context Assembly
  -> Tools / Skills / RAG Retrieval
  -> DashScope LLM
  -> SSE Stream or JSON Response
```

## Tech Stack

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

## Quick Start

### Mode A: Local Development

This mode is intended for coding and debugging. Infrastructure runs in Docker, while the Spring Boot application runs as a local process.

```bash
make init-local
```

This command will:

1. Start MySQL, Redis, Milvus, and other infrastructure containers
2. Start the Spring Boot service locally in the background
3. Wait for the health check to pass
4. Upload Markdown documents from `aiops-docs` into the knowledge base

Common commands:

```bash
make up-local
make start-local
make restart-local
make logs-local
make upload
```

### Mode B: Server Deployment

This mode is intended for remote deployment, with Docker managing both the application and its dependencies.

```bash
make init
```

This command will:

1. Start the full Docker Compose stack
2. Wait until MySQL, Redis, Milvus, and the application are ready
3. Upload Markdown documents from `aiops-docs` into the knowledge base

Common commands:

```bash
make up
make start
make restart
make logs
```

Notes for server mode:

- `ops-mind-agent.jar` must be placed in the same directory as `docker-compose.yml`
- The `app` service is enabled through the `--profile app` profile
- The container runs on `eclipse-temurin:17-jre`, so no host-level JDK is required

## Runtime Commands

| Command | Description |
| --- | --- |
| `make init` | Full server-side initialization, including containers and document upload |
| `make up` | Start the full container stack, including the app container |
| `make start` | Start the app container |
| `make stop` | Stop the app container |
| `make restart` | Restart the app container |
| `make logs` | View app container logs |
| `make init-local` | Full local initialization with infra containers and local Spring Boot runtime |
| `make up-local` | Start only local infrastructure containers |
| `make start-local` | Start the Spring Boot service locally |
| `make stop-local` | Stop the local Spring Boot service |
| `make restart-local` | Restart the local Spring Boot service |
| `make logs-local` | View local service logs |
| `make upload` | Upload files in `aiops-docs` into the vector knowledge base |
| `make down` | Stop Docker Compose services |
| `make status` | Check container status |

## APIs

### Auth

| Endpoint | Description |
| --- | --- |
| `GET /api/auth/captcha` | Get captcha |
| `POST /api/auth/register` | Register and auto-login |
| `POST /api/auth/login` | Login |
| `POST /api/auth/logout` | Logout |
| `GET /api/auth/me` | Get current user information |

### Chat

| Endpoint | Description |
| --- | --- |
| `POST /api/chat` | Standard chat API |
| `POST /api/chat_stream` | SSE streaming chat API |
| `POST /api/chat/clear` | Clear a specific conversation history |
| `POST /api/chat/histories` | Save frontend recent-session list |
| `GET /api/chat/histories` | Get frontend recent-session list |
| `GET /api/chat/session/messages/{sessionId}` | Get full message history of a session |
| `GET /api/chat/session/{sessionId}` | Get session metadata |

### AI Ops

| Endpoint | Description |
| --- | --- |
| `POST /api/ai_ops` | Streaming operations analysis and report generation |

### Health

| Endpoint | Description |
| --- | --- |
| `GET /milvus/health` | Health check for the application and vector services |

## Knowledge Base

The default initial document directory is:

```text
aiops-docs/
```

Upload API:

```http
POST /api/upload
```

Document import behavior:

- Supports `txt` and `md`
- Keeps original filenames and allows overwrite on duplicate upload
- Automatically builds vector indexes after upload
- Useful for bootstrapping operations documents and testing RAG behavior

To re-import directory documents:

```bash
make upload
```

## Skills

The project already integrates the official Spring AI Skills mechanism:

```text
src/main/resources/skills/
└── web-search/
    └── SKILL.md
```

Currently enabled skill:

- `web-search`: invokes `searchWeb` to fetch public web information and inject it into the generation pipeline

This gives the system two sources of augmentation: internal document retrieval and external real-time information.

## Configuration

Primary runtime configuration lives in `src/main/resources/application.yml`.

Important settings include:

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

Production recommendations:

- Override model and search secrets via environment variables or private configuration
- Replace the default JWT secret
- Tune timeout and retry settings based on deployment resources

Suggested environment variables:

```bash
export DASHSCOPE_API_KEY=your-dashscope-key
export TAVILY_API_KEY=your-tavily-key
```

## Docker Compose Layout

The current `docker-compose.yml` includes:

- `mysql`
- `redis`
- `etcd`
- `minio`
- `standalone` (Milvus)
- `attu`
- `app` (enabled via the `app` profile)

Port mapping:

- `mysql -> 3390`
- `redis -> 6379`
- `milvus -> 19530`
- `app -> 8600`

The `app` service waits for `mysql`, `redis`, and `standalone` to become healthy before starting, which makes it suitable for full containerized deployment.

## Project Layout

```text
ops-mind-agent/
├── aiops-docs/                      # Operations knowledge documents
├── mysql-init/                      # MySQL initialization scripts
├── docker-compose.yml               # Infra and app container orchestration
├── Makefile                         # Unified entry for local and server startup
├── src/main/java/com/ops/
│   ├── agent/                       # Agent tools and capability wrappers
│   ├── auth/                        # Authentication components
│   ├── config/                      # Spring configuration and Skills wiring
│   ├── controller/                  # Web API layer
│   ├── dto/                         # Request and response objects
│   ├── entity/                      # MySQL persistence entities
│   ├── repository/                  # JPA repositories
│   ├── service/                     # Core business services
│   ├── service/session/             # Session management and context handling
│   └── tool/                        # Business tool definitions
└── src/main/resources/
    ├── application.yml              # Runtime configuration
    ├── skills/                      # Official Skills directory
    └── static/                      # Frontend static assets
```

## Use Cases

This project is a solid base for:

- AI-driven operations assistants
- Alert analysis and report generation
- Internal knowledge Q&A and document retrieval
- Agent systems with web search augmentation
- Integrated Spring AI / Skills / RAG engineering practice

