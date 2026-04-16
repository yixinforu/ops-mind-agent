<div align="center">
  <p><strong>CN <a href="./README.zh-CN.md">中文</a> | US English</strong></p>
  <h1>Ops Mind Agent</h1>
  <p><strong>A production-oriented AI agent application for intelligent operations workflows</strong></p>
  <p>Streaming Chat · AI Ops · RAG · Skills · Multi-User Session Management</p>
  <p>Product-facing UI: <strong>Xiaowei Assistant</strong></p>
</div>

## Overview

`Ops Mind Agent` is a production-oriented agent system built for intelligent operations scenarios rather than a thin chat wrapper around an LLM. It combines conversational interaction, alert analysis, document retrieval, public web search, session persistence, and multi-user access control into a single deployable engineering baseline.

The project is designed to look and behave like a real product: users can sign in, continue prior conversations, trigger an `AI Ops` workflow from a dedicated entry point, upload knowledge documents, and ask follow-up questions against both internal and public sources.

## Product Value

This project is aimed at teams that need more than a playground demo. It provides a practical baseline for building an AI-powered operations assistant with clear product shape and extensible engineering foundations.

It is especially useful when you need to combine:

- conversational assistance for day-to-day operations work
- structured alert interpretation and report generation
- internal knowledge retrieval with vector search
- public web search for time-sensitive questions
- user isolation, session continuity, and deployable runtime support

## Key Capabilities

| Capability | Description |
| --- | --- |
| Streaming Conversation | Supports standard chat and `SSE`-based streaming output for a more responsive user experience |
| AI Ops Workflow | `/api/ai_ops` streams analysis progress and operations reports instead of returning a single static response |
| Context-Aware Sessions | Supports recent sessions, full message history retrieval, and contextual follow-up interactions |
| Knowledge-Augmented QA | Uploads Markdown/TXT files, chunks them, indexes them into Milvus, and uses retrieval for grounded answers |
| Skills-Based Extension | Integrates official Spring AI `Skills` with built-in `web-search` capability for public internet search |
| Authentication & Isolation | Supports registration, login, captcha, JWT authentication, and user-level session isolation |
| Persistent Data Layer | Persists users, sessions, and messages in MySQL, with Redis for high-frequency state and cache support |
| Deployment Flexibility | Supports both local development mode and server-side containerized deployment |

## Production-Oriented Design

Many LLM demos stop at a single chat box. This project goes further in several important ways:

- It has a user system instead of anonymous one-off usage
- It preserves session continuity rather than treating every question as isolated
- It provides a dedicated `AI Ops` path for alert analysis and operations reporting
- It combines RAG and Skills instead of depending on model memory alone
- It supports both engineering experimentation and actual deployment workflows

This makes the project suitable not only for demos, but also as a serious starting point for an internal operations assistant.

## Product Surface

### Xiaowei Assistant

The main product-facing interface is **Xiaowei Assistant**, designed for natural language interaction. It is suitable for routine operations Q&A, contextual troubleshooting, knowledge lookup, and continued follow-up conversations.

### AI Ops Entry

The floating `AI Ops` button provides a dedicated workflow-oriented entry point for alert analysis. Instead of acting like generic chat, it focuses on structured operational reasoning, report generation, and flowing the result back into the same session context.

### Knowledge and Search Layer

The project combines two complementary augmentation paths:

- Internal knowledge: RAG retrieval from `aiops-docs` and uploaded documents
- External knowledge: public internet search powered by the `web-search` Skill

This gives the assistant both enterprise memory and external freshness.

## Product Features

### Conversation Assistant

- normal chat and streaming chat modes
- contextual follow-up questions in the same session
- recent conversations list and full message retrieval
- natural continuation after `AI Ops` execution

### AI Ops Analysis

- dedicated entry separate from general chat
- streaming operational reasoning output
- alert-oriented analysis flow
- final report generation and session integration

### Knowledge-Augmented Experience

- upload `txt` and `md` files
- automatic file persistence and overwrite by original filename
- automatic vector indexing after upload
- Milvus-backed semantic retrieval for grounded responses

### Public Search Capability

- built-in `web-search` Skill
- designed for latest, current, recent, and public information lookups
- supports time-sensitive search handling and source-aware answers
- complements internal knowledge instead of replacing it

### Multi-User Product Experience

- registration and login flow
- captcha verification
- JWT-based authentication
- user-specific sessions and message history
- recent conversations behavior closer to a real product than a sandbox demo

## Architecture

The system is organized into four layers:

- Frontend layer: login, registration, chat interaction, AI Ops display, recent-session management
- Application layer: `Spring Boot 3` + `Spring AI` for authentication, session orchestration, agent execution, tool routing, and SSE delivery
- Data layer: MySQL for users, sessions, and messages; Redis for captcha, login restrictions, session-related cache, and high-frequency state
- Retrieval and extension layer: Milvus for vector search, Tavily for public web search, and Skills for capability registration

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
- the `app` service is enabled through the `--profile app` profile
- the container runs on `eclipse-temurin:17-jre`, so no host-level JDK is required

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

### Knowledge Upload

| Endpoint | Description |
| --- | --- |
| `POST /api/upload` | Upload documents and trigger vector indexing |

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

- supports `txt` and `md`
- keeps original filenames and allows overwrite on duplicate upload
- automatically builds vector indexes after upload
- useful for bootstrapping operations documents and testing RAG behavior

To re-import directory documents:

```bash
make upload
```

## Skills

The project integrates the official Spring AI Skills mechanism:

```text
src/main/resources/skills/
└── web-search/
    └── SKILL.md
```

Currently enabled skill:

- `web-search`: invokes `searchWeb` to fetch public web information and inject it into the generation pipeline

This gives the system two complementary sources of augmentation: internal document retrieval and external real-time information.

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

- override model and search secrets via environment variables or private configuration
- replace the default JWT secret
- tune timeout and retry settings based on deployment resources

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

This project is a strong starting point for:

- AI-driven operations assistants
- alert interpretation and troubleshooting assistance
- internal knowledge Q&A and document retrieval
- public-news and official-announcement lookups
- Spring AI / Skills / RAG integrated engineering practice
- deployable internal copilots with session continuity and user isolation