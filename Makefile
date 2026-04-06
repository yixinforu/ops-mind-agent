# SuperBizAgent Makefile
# 用于自动化项目初始化和文档向量化

# 配置变量
SERVER_URL = http://localhost:9900
UPLOAD_API = $(SERVER_URL)/api/upload
DOCS_DIR = aiops-docs
HEALTH_CHECK_API = $(SERVER_URL)/milvus/health
DOCKER_COMPOSE_FILE = vector-database.yml
DOCKER_COMPOSE_CMD := $(shell if command -v docker-compose >/dev/null 2>&1; then echo docker-compose; else echo "docker compose"; fi)
MILVUS_CONTAINER = milvus-standalone

# 颜色输出
GREEN = \033[0;32m
YELLOW = \033[0;33m
RED = \033[0;31m
NC = \033[0m # No Color

.PHONY: help init start stop restart check upload clean up down status wait

# 默认目标：显示帮助信息
help:
	@echo "$(GREEN)SuperBizAgent Makefile$(NC)"
	@echo ""
	@echo "可用命令："
	@echo "  $(YELLOW)make init$(NC)    - 🚀 一键初始化（启动Docker → 启动服务 → 上传文档）"
	@echo "  $(YELLOW)make up$(NC)      - 启动 Docker Compose（Milvus 向量数据库）"
	@echo "  $(YELLOW)make down$(NC)    - 停止 Docker Compose"
	@echo "  $(YELLOW)make status$(NC)  - 查看 Docker 容器状态"
	@echo "  $(YELLOW)make start$(NC)   - 启动 Spring Boot 服务（后台运行）"
	@echo "  $(YELLOW)make stop$(NC)    - 停止 Spring Boot 服务"
	@echo "  $(YELLOW)make restart$(NC) - 重启 Spring Boot 服务"
	@echo "  $(YELLOW)make check$(NC)   - 检查服务器是否运行"
	@echo "  $(YELLOW)make upload$(NC)  - 上传 aiops-docs 目录下的所有文档"
	@echo "  $(YELLOW)make clean$(NC)   - 清理临时文件"
	@echo ""
	@echo "使用示例："
	@echo "  1. 一键初始化: make init"
	@echo "  2. 手动启动: make up && make start && make upload"
	@echo "  3. 停止服务: make stop && make down"

# 一键初始化：启动Docker → 启动服务 → 检查服务 → 上传文档
init:
	@echo "$(GREEN)🚀 开始一键初始化 SuperBizAgent...$(NC)"
	@echo ""
	@echo "$(YELLOW)步骤 1/4: 启动 Docker Compose（Milvus 向量数据库）$(NC)"
	@$(MAKE) up
	@echo ""
	@echo "$(YELLOW)步骤 2/4: 启动 Spring Boot 服务$(NC)"
	@$(MAKE) start
	@echo ""
	@echo "$(YELLOW)步骤 3/4: 等待服务就绪$(NC)"
	@$(MAKE) wait
	@echo ""
	@echo "$(YELLOW)步骤 4/4: 上传 AIOps 文档到向量数据库$(NC)"
	@$(MAKE) upload
	@echo ""
	@echo "$(GREEN)═══════════════════════════════════════════════════════$(NC)"
	@echo "$(GREEN)✅ 初始化完成！所有文档已成功向量化存储到数据库$(NC)"
	@echo "$(GREEN)═══════════════════════════════════════════════════════$(NC)"
	@echo ""
	@echo "$(GREEN)🌐 服务访问地址:$(NC)"
	@echo "   API 服务: $(SERVER_URL)"
	@echo "   Attu (Web UI): http://localhost:8000"
	@echo ""
	@echo "$(YELLOW)💡 提示: 服务正在后台运行，查看日志: tail -f server.log$(NC)"

# 启动 Spring Boot 服务（后台运行）
start:
	@echo "$(YELLOW)🚀 启动 Spring Boot 服务...$(NC)"
	@if curl -s -f $(HEALTH_CHECK_API) > /dev/null 2>&1; then \
		echo "$(GREEN)✅ 服务已经在运行中 ($(SERVER_URL))$(NC)"; \
	else \
		echo "$(YELLOW)📦 正在启动服务（后台运行）...$(NC)"; \
		nohup mvn spring-boot:run > server.log 2>&1 & \
		echo $$! > server.pid; \
		echo "$(GREEN)✅ 服务启动命令已执行$(NC)"; \
		echo "$(YELLOW)   PID: $$(cat server.pid)$(NC)"; \
		echo "$(YELLOW)   日志文件: server.log$(NC)"; \
	fi

# 等待服务器就绪（最多等待 60 秒）
wait:
	@echo "$(YELLOW)⏳ 等待服务器就绪...$(NC)"
	@max_attempts=60; \
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
	echo "$(YELLOW)请检查日志: tail -f server.log$(NC)"; \
	exit 1

# 检查服务器是否运行
check:
	@echo "$(YELLOW)🔍 检查服务器状态...$(NC)"
	@if curl -s -f $(HEALTH_CHECK_API) > /dev/null 2>&1; then \
		echo "$(GREEN)✅ 服务器运行正常 ($(SERVER_URL))$(NC)"; \
	else \
		echo "$(RED)❌ 服务器未运行或无法连接！$(NC)"; \
		echo "$(YELLOW)请先启动项目: mvn spring-boot:run$(NC)"; \
		exit 1; \
	fi

# 上传所有文档
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

# 停止 Spring Boot 服务
stop:
	@echo "$(YELLOW)🛑 停止 Spring Boot 服务...$(NC)"
	@if [ -f server.pid ]; then \
		pid=$$(cat server.pid); \
		if ps -p $$pid > /dev/null 2>&1; then \
			kill $$pid; \
			echo "$(GREEN)✅ 服务已停止 (PID: $$pid)$(NC)"; \
		else \
			echo "$(YELLOW)⚠️  进程不存在 (PID: $$pid)$(NC)"; \
		fi; \
		rm -f server.pid; \
	else \
		echo "$(YELLOW)⚠️  未找到 server.pid 文件$(NC)"; \
		pkill -f "spring-boot:run" && echo "$(GREEN)✅ 已停止所有 spring-boot 进程$(NC)" || echo "$(YELLOW)⚠️  没有运行中的 spring-boot 进程$(NC)"; \
	fi

# 重启 Spring Boot 服务
restart:
	@echo "$(YELLOW)🔄 重启 Spring Boot 服务...$(NC)"
	@echo ""
	@echo "$(YELLOW)步骤 1/2: 停止服务$(NC)"
	@$(MAKE) stop
	@echo ""
	@echo "$(YELLOW)步骤 2/2: 启动服务$(NC)"
	@$(MAKE) start
	@echo ""
	@$(MAKE) wait
	@echo ""
	@echo "$(GREEN)✅ 服务重启完成！$(NC)"

# 清理临时文件
clean:
	@echo "$(YELLOW)🧹 清理临时文件...$(NC)"
	@rm -rf uploads/*.tmp
	@rm -f server.pid server.log
	@echo "$(GREEN)✅ 清理完成$(NC)"

# 显示文档列表
list-docs:
	@echo "$(YELLOW)📚 $(DOCS_DIR) 目录下的文档:$(NC)"
	@if [ -d "$(DOCS_DIR)" ]; then \
		ls -lh $(DOCS_DIR)/*.md 2>/dev/null || echo "$(RED)没有找到 .md 文件$(NC)"; \
	else \
		echo "$(RED)目录 $(DOCS_DIR) 不存在$(NC)"; \
	fi

# 测试单个文件上传
test-upload:
	@echo "$(YELLOW)🧪 测试上传单个文件...$(NC)"
	@if [ -f "$(DOCS_DIR)/cpu_high_usage.md" ]; then \
		curl -X POST $(UPLOAD_API) \
			-F "file=@$(DOCS_DIR)/cpu_high_usage.md" \
			-H "Accept: application/json" | jq .; \
	else \
		echo "$(RED)测试文件不存在$(NC)"; \
	fi

# 启动 Docker Compose（智能检测，避免重复启动）
up:
	@echo "$(YELLOW)🐳 检查 Docker 容器状态...$(NC)"
	@if [ ! -f "$(DOCKER_COMPOSE_FILE)" ]; then \
		echo "$(RED)❌ Docker Compose 文件不存在: $(DOCKER_COMPOSE_FILE)$(NC)"; \
		exit 1; \
	fi
	@if docker ps --format '{{.Names}}' | grep -q "^$(MILVUS_CONTAINER)$$"; then \
		echo "$(GREEN)✅ Milvus 容器已经在运行中$(NC)"; \
		echo "$(YELLOW)📋 当前运行的容器:$(NC)"; \
		docker ps --filter "name=milvus" --format "table {{.Names}}\t{{.Status}}\t{{.Ports}}"; \
	else \
		echo "$(YELLOW)🚀 启动 Docker Compose...$(NC)"; \
		if ! $(DOCKER_COMPOSE_CMD) -f $(DOCKER_COMPOSE_FILE) up -d; then \
			echo ""; \
			echo "$(RED)❌ Docker Compose 启动命令执行失败$(NC)"; \
			echo "$(YELLOW)请直接查看上面的报错信息，或执行: $(DOCKER_COMPOSE_CMD) -f $(DOCKER_COMPOSE_FILE) logs --tail=200$(NC)"; \
			exit 1; \
		fi; \
		echo ""; \
		echo "$(YELLOW)⏳ 等待容器启动...$(NC)"; \
		sleep 5; \
		if docker ps --format '{{.Names}}' | grep -q "^$(MILVUS_CONTAINER)$$"; then \
			echo "$(GREEN)✅ Docker Compose 启动成功！$(NC)"; \
			echo ""; \
			echo "$(GREEN)📋 运行中的容器:$(NC)"; \
			docker ps --filter "name=milvus" --format "table {{.Names}}\t{{.Status}}\t{{.Ports}}"; \
			echo ""; \
			echo "$(GREEN)🌐 服务访问地址:$(NC)"; \
			echo "   Milvus: localhost:19530"; \
			echo "   Attu (Web UI): http://localhost:8000"; \
			echo "   MinIO: http://localhost:9001 (admin/minioadmin)"; \
		else \
			echo "$(RED)❌ 容器启动失败，当前状态如下:$(NC)"; \
			$(DOCKER_COMPOSE_CMD) -f $(DOCKER_COMPOSE_FILE) ps; \
			echo ""; \
			echo "$(YELLOW)最近日志（最后 100 行）:$(NC)"; \
			$(DOCKER_COMPOSE_CMD) -f $(DOCKER_COMPOSE_FILE) logs --tail=100; \
			exit 1; \
		fi; \
	fi

# 停止 Docker Compose
down:
	@echo "$(YELLOW)🛑 停止 Docker Compose...$(NC)"
	@if [ ! -f "$(DOCKER_COMPOSE_FILE)" ]; then \
		echo "$(RED)❌ Docker Compose 文件不存在: $(DOCKER_COMPOSE_FILE)$(NC)"; \
		exit 1; \
	fi
	@if docker ps --format '{{.Names}}' | grep -q "milvus"; then \
		$(DOCKER_COMPOSE_CMD) -f $(DOCKER_COMPOSE_FILE) down; \
		echo "$(GREEN)✅ Docker Compose 已停止$(NC)"; \
	else \
		echo "$(YELLOW)⚠️  没有运行中的 Milvus 容器$(NC)"; \
	fi

# 查看 Docker 容器状态
status:
	@echo "$(YELLOW)📊 Docker 容器状态:$(NC)"
	@echo ""
	@if docker ps -a --format '{{.Names}}' | grep -q "milvus"; then \
		docker ps -a --filter "name=milvus" --format "table {{.Names}}\t{{.Status}}\t{{.Ports}}"; \
		echo ""; \
		running=$$(docker ps --filter "name=milvus" --format '{{.Names}}' | wc -l | tr -d ' '); \
		total=$$(docker ps -a --filter "name=milvus" --format '{{.Names}}' | wc -l | tr -d ' '); \
		echo "$(GREEN)运行中: $$running / $$total$(NC)"; \
	else \
		echo "$(YELLOW)⚠️  没有找到 Milvus 相关容器$(NC)"; \
		echo "$(YELLOW)提示: 运行 'make up' 启动容器$(NC)"; \
	fi
