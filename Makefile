# SuperBizAgent Makefile
# 用于自动化项目启动、健康检查与文档向量化

# 配置变量
SERVER_URL = http://localhost:8600
UPLOAD_API = $(SERVER_URL)/api/upload
DOCS_DIR = aiops-docs
HEALTH_CHECK_API = $(SERVER_URL)/milvus/health
DOCKER_COMPOSE_FILE = docker-compose.yml
DOCKER_COMPOSE_CMD := $(shell if command -v docker-compose >/dev/null 2>&1; then echo docker-compose; else echo "docker compose"; fi)
APP_SERVICE = app
APP_PROFILE = app
LOCAL_LOG_FILE = server.log
LOCAL_PID_FILE = server.pid

# 颜色输出
GREEN = \033[0;32m
YELLOW = \033[0;33m
RED = \033[0;31m
NC = \033[0m # No Color

.PHONY: help init init-local start start-local stop stop-local restart restart-local check upload clean up up-local down status wait logs logs-local

help:
	@echo "$(GREEN)SuperBizAgent Makefile$(NC)"
	@echo ""
	@echo "可用命令："
	@echo "  $(YELLOW)make init$(NC)         - 服务器一键初始化（启动全部容器 → 等待服务 → 上传文档）"
	@echo "  $(YELLOW)make init-local$(NC)   - 本地一键初始化（启动中间件 → 本地启动应用 → 等待服务 → 上传文档）"
	@echo "  $(YELLOW)make up$(NC)           - 服务器启动全部容器（含 app）"
	@echo "  $(YELLOW)make up-local$(NC)     - 本地仅启动中间件容器"
	@echo "  $(YELLOW)make start$(NC)        - 服务器启动应用容器（含 app profile）"
	@echo "  $(YELLOW)make start-local$(NC)  - 本地按旧方式启动 Spring Boot 服务"
	@echo "  $(YELLOW)make stop$(NC)         - 停止应用容器"
	@echo "  $(YELLOW)make stop-local$(NC)   - 停止本地 Spring Boot 服务"
	@echo "  $(YELLOW)make restart$(NC)      - 重启应用容器"
	@echo "  $(YELLOW)make restart-local$(NC)- 重启本地 Spring Boot 服务"
	@echo "  $(YELLOW)make wait$(NC)         - 等待应用健康检查通过"
	@echo "  $(YELLOW)make check$(NC)        - 检查应用是否运行正常"
	@echo "  $(YELLOW)make upload$(NC)       - 上传 aiops-docs 目录下的所有文档"
	@echo "  $(YELLOW)make logs$(NC)         - 查看应用容器日志"
	@echo "  $(YELLOW)make logs-local$(NC)   - 查看本地 Spring Boot 日志"
	@echo "  $(YELLOW)make status$(NC)       - 查看 Docker 容器状态"
	@echo "  $(YELLOW)make down$(NC)         - 停止全部 Docker Compose 服务"
	@echo "  $(YELLOW)make clean$(NC)        - 清理临时文件"
	@echo ""
	@echo "使用示例："
	@echo "  1. 服务器部署: make init"
	@echo "  2. 本地开发: make init-local"
	@echo "  3. 更新服务器 jar 后重启: make restart"

# 服务器一键初始化：包含 app 容器
init:
	@echo "$(GREEN)🚀 开始服务器初始化 SuperBizAgent...$(NC)"
	@echo ""
	@echo "$(YELLOW)步骤 1/3: 启动全部容器$(NC)"
	@$(MAKE) up
	@echo ""
	@echo "$(YELLOW)步骤 2/3: 等待服务就绪$(NC)"
	@$(MAKE) wait
	@echo ""
	@echo "$(YELLOW)步骤 3/3: 上传 AIOps 文档到向量数据库$(NC)"
	@$(MAKE) upload
	@echo "$(GREEN)🚀 启动并初始化成功"

# 本地初始化：中间件走 Docker，应用仍按旧方式启动
init-local:
	@echo "$(GREEN)🚀 开始本地初始化 SuperBizAgent...$(NC)"
	@echo ""
	@echo "$(YELLOW)步骤 1/4: 启动中间件容器$(NC)"
	@$(MAKE) up-local
	@echo ""
	@echo "$(YELLOW)步骤 2/4: 本地启动 Spring Boot 服务$(NC)"
	@$(MAKE) start-local
	@echo ""
	@echo "$(YELLOW)步骤 3/4: 等待服务就绪$(NC)"
	@$(MAKE) wait
	@echo ""
	@echo "$(YELLOW)步骤 4/4: 上传 AIOps 文档到向量数据库$(NC)"
	@$(MAKE) upload
	@echo "$(GREEN)🚀 启动并初始化成功"

# 服务器部署：启动全部容器（含 app profile）
up:
	@echo "$(YELLOW)🐳 启动全部 Docker Compose 服务（含 app）...$(NC)"
	@if [ ! -f "$(DOCKER_COMPOSE_FILE)" ]; then \
		echo "$(RED)❌ Docker Compose 文件不存在: $(DOCKER_COMPOSE_FILE)$(NC)"; \
		exit 1; \
	fi
	@$(DOCKER_COMPOSE_CMD) -f $(DOCKER_COMPOSE_FILE) --profile $(APP_PROFILE) up -d
	@echo ""
	@echo "$(GREEN)✅ 全量容器启动命令已执行$(NC)"
	@$(DOCKER_COMPOSE_CMD) -f $(DOCKER_COMPOSE_FILE) --profile $(APP_PROFILE) ps

# 本地开发：仅启动中间件，不启动 app 容器
up-local:
	@echo "$(YELLOW)🐳 启动中间件 Docker Compose 服务...$(NC)"
	@if [ ! -f "$(DOCKER_COMPOSE_FILE)" ]; then \
		echo "$(RED)❌ Docker Compose 文件不存在: $(DOCKER_COMPOSE_FILE)$(NC)"; \
		exit 1; \
	fi
	@$(DOCKER_COMPOSE_CMD) -f $(DOCKER_COMPOSE_FILE) up -d
	@echo ""
	@echo "$(GREEN)✅ 中间件容器启动命令已执行（app 容器未启动）$(NC)"
	@$(DOCKER_COMPOSE_CMD) -f $(DOCKER_COMPOSE_FILE) ps

# 服务器启动应用容器
start:
	@echo "$(YELLOW)🚀 启动应用容器...$(NC)"
	@if [ ! -f "$(DOCKER_COMPOSE_FILE)" ]; then \
		echo "$(RED)❌ Docker Compose 文件不存在: $(DOCKER_COMPOSE_FILE)$(NC)"; \
		exit 1; \
	fi
	@$(DOCKER_COMPOSE_CMD) -f $(DOCKER_COMPOSE_FILE) --profile $(APP_PROFILE) up -d $(APP_SERVICE)
	@echo "$(GREEN)✅ 应用容器启动命令已执行$(NC)"

# 本地按旧方式启动 Spring Boot 服务
start-local:
	@echo "$(YELLOW)🚀 本地启动 Spring Boot 服务...$(NC)"
	@if curl -s -f $(HEALTH_CHECK_API) > /dev/null 2>&1; then \
		echo "$(GREEN)✅ 服务已经在运行中 ($(SERVER_URL))$(NC)"; \
	else \
		echo "$(YELLOW)📦 正在启动服务（后台运行）...$(NC)"; \
		nohup mvn spring-boot:run > $(LOCAL_LOG_FILE) 2>&1 & \
		echo $$! > $(LOCAL_PID_FILE); \
		echo "$(GREEN)✅ 服务启动命令已执行$(NC)"; \
		echo "$(YELLOW)   PID: $$(cat $(LOCAL_PID_FILE))$(NC)"; \
		echo "$(YELLOW)   日志文件: $(LOCAL_LOG_FILE)$(NC)"; \
	fi

wait:
	@echo "$(YELLOW)⏳ 等待服务器就绪...$(NC)"
	@max_attempts=120; \
	attempt=0; \
	while [ $$attempt -lt $$max_attempts ]; do \
		if curl -s -f $(HEALTH_CHECK_API) > /dev/null 2>&1; then \
			echo "$(GREEN)✅ 服务器已就绪！($(SERVER_URL))$(NC)"; \
			exit 0; \
		fi; \
		attempt=$$((attempt + 1)); \
		printf "$(YELLOW)   等待中... [$$attempt/$$max_attempts]$(NC)\r"; \
		sleep 1; \
	done; \
	echo ""; \
	echo "$(RED)❌ 服务器启动超时！$(NC)"; \
	echo "$(YELLOW)服务器请执行 make logs，本地请执行 make logs-local$(NC)"; \
	$(DOCKER_COMPOSE_CMD) -f $(DOCKER_COMPOSE_FILE) --profile $(APP_PROFILE) ps; \
	exit 1

check:
	@echo "$(YELLOW)🔍 检查服务器状态...$(NC)"
	@if curl -s -f $(HEALTH_CHECK_API) > /dev/null 2>&1; then \
		echo "$(GREEN)✅ 服务器运行正常 ($(SERVER_URL))$(NC)"; \
	else \
		echo "$(RED)❌ 服务器未运行或无法连接！$(NC)"; \
		echo "$(YELLOW)服务器请执行 make start，本地请执行 make start-local$(NC)"; \
		exit 1; \
	fi

upload:
	@echo "$(YELLOW)📤 开始上传 $(DOCS_DIR) 目录下的文档...$(NC)"
	@if [ ! -d "$(DOCS_DIR)" ]; then \
		echo "$(RED)❌ 目录 $(DOCS_DIR) 不存在！$(NC)"; \
		exit 1; \
	fi
	@count=0; \
	success=0; \
	failed=0; \
	for file in $(DOCS_DIR)/*.md; do \
		if [ -f "$$file" ]; then \
			count=$$((count + 1)); \
			filename=$$(basename "$$file"); \
			echo "$(YELLOW)  [$$count] 上传文件: $$filename$(NC)"; \
			response=$$(curl -s -w "\n%{http_code}" -X POST $(UPLOAD_API) \
				-F "file=@$$file" \
				-H "Accept: application/json"); \
			http_code=$$(echo "$$response" | tail -n1); \
			body=$$(echo "$$response" | sed '$$d'); \
			if [ "$$http_code" = "200" ]; then \
				echo "$(GREEN)      ✅ 成功: $$filename$(NC)"; \
				success=$$((success + 1)); \
			else \
				echo "$(RED)      ❌ 失败: $$filename (HTTP $$http_code)$(NC)"; \
				echo "$$body" | head -n 3; \
				failed=$$((failed + 1)); \
			fi; \
			sleep 1; \
		fi; \
	done; \
	echo ""; \
	echo "$(GREEN)📊 上传统计:$(NC)"; \
	echo "   总计: $$count 个文件"; \
	echo "   $(GREEN)成功: $$success$(NC)"; \
	if [ $$failed -gt 0 ]; then \
		echo "   $(RED)失败: $$failed$(NC)"; \
	fi

stop:
	@echo "$(YELLOW)🛑 停止应用容器...$(NC)"
	@if [ ! -f "$(DOCKER_COMPOSE_FILE)" ]; then \
		echo "$(RED)❌ Docker Compose 文件不存在: $(DOCKER_COMPOSE_FILE)$(NC)"; \
		exit 1; \
	fi
	@$(DOCKER_COMPOSE_CMD) -f $(DOCKER_COMPOSE_FILE) --profile $(APP_PROFILE) stop $(APP_SERVICE)
	@echo "$(GREEN)✅ 应用容器已停止$(NC)"

stop-local:
	@echo "$(YELLOW)🛑 停止本地 Spring Boot 服务...$(NC)"
	@if [ -f $(LOCAL_PID_FILE) ]; then \
		pid=$$(cat $(LOCAL_PID_FILE)); \
		if ps -p $$pid > /dev/null 2>&1; then \
			kill $$pid; \
			echo "$(GREEN)✅ 服务已停止 (PID: $$pid)$(NC)"; \
		else \
			echo "$(YELLOW)⚠️  进程不存在 (PID: $$pid)$(NC)"; \
		fi; \
		rm -f $(LOCAL_PID_FILE); \
	else \
		echo "$(YELLOW)⚠️  未找到 $(LOCAL_PID_FILE) 文件$(NC)"; \
		pkill -f "spring-boot:run" && echo "$(GREEN)✅ 已停止所有 spring-boot 进程$(NC)" || echo "$(YELLOW)⚠️  没有运行中的 spring-boot 进程$(NC)"; \
	fi

restart:
	@echo "$(YELLOW)🔄 重启应用容器...$(NC)"
	@$(DOCKER_COMPOSE_CMD) -f $(DOCKER_COMPOSE_FILE) --profile $(APP_PROFILE) up -d --force-recreate $(APP_SERVICE)
	@$(MAKE) wait
	@echo "$(GREEN)✅ 应用重启完成！$(NC)"

restart-local:
	@echo "$(YELLOW)🔄 重启本地 Spring Boot 服务...$(NC)"
	@$(MAKE) stop-local
	@$(MAKE) start-local
	@$(MAKE) wait
	@echo "$(GREEN)✅ 本地应用重启完成！$(NC)"

logs:
	@$(DOCKER_COMPOSE_CMD) -f $(DOCKER_COMPOSE_FILE) --profile $(APP_PROFILE) logs -f --tail=200 $(APP_SERVICE)

logs-local:
	@tail -f $(LOCAL_LOG_FILE)

clean:
	@echo "$(YELLOW)🧹 清理临时文件...$(NC)"
	@rm -rf uploads/*.tmp
	@rm -f $(LOCAL_PID_FILE)
	@echo "$(GREEN)✅ 清理完成$(NC)"

down:
	@echo "$(YELLOW)🛑 停止 Docker Compose...$(NC)"
	@if [ ! -f "$(DOCKER_COMPOSE_FILE)" ]; then \
		echo "$(RED)❌ Docker Compose 文件不存在: $(DOCKER_COMPOSE_FILE)$(NC)"; \
		exit 1; \
	fi
	@$(DOCKER_COMPOSE_CMD) -f $(DOCKER_COMPOSE_FILE) --profile $(APP_PROFILE) down
	@echo "$(GREEN)✅ Docker Compose 已停止$(NC)"

status:
	@echo "$(YELLOW)📊 Docker 容器状态:$(NC)"
	@echo ""
	@if [ ! -f "$(DOCKER_COMPOSE_FILE)" ]; then \
		echo "$(RED)❌ Docker Compose 文件不存在: $(DOCKER_COMPOSE_FILE)$(NC)"; \
		exit 1; \
	fi
	@$(DOCKER_COMPOSE_CMD) -f $(DOCKER_COMPOSE_FILE) --profile $(APP_PROFILE) ps
