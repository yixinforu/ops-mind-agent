// SuperBizAgent 前端应用
class SuperBizAgentApp {
    constructor() {
        this.apiBaseUrl = '/api';
        this.currentMode = 'quick'; // 'quick' 或 'stream'
        this.authToken = localStorage.getItem('authToken') || '';
        this.currentUser = null;
        this.loginCaptchaId = '';
        this.registerCaptchaId = '';
        this.sessionId = this.generateSessionId();
        this.isStreaming = false;
        this.currentChatHistory = []; // 当前对话的消息历史
        this.chatHistories = []; // 所有历史对话
        this.isCurrentChatFromHistory = false; // 标记当前对话是否是从历史记录加载的
        this.historySyncTimer = null;
        
        this.initializeElements();
        this.bindEvents();
        this.updateUI();
        this.initMarkdown();
        this.checkAndSetCentered();
        this.renderChatHistory();
        this.initializeAuthFlow();
    }

    // 初始化Markdown配置
    initMarkdown() {
        // 等待 marked 库加载完成
        const checkMarked = () => {
            if (typeof marked !== 'undefined') {
                try {
                    // 配置marked选项
                    marked.setOptions({
                        breaks: true,  // 支持GFM换行
                        gfm: true,     // 启用GitHub风格的Markdown
                        headerIds: false,
                        mangle: false
                    });

                    // 配置代码高亮
                    if (typeof hljs !== 'undefined') {
                        marked.setOptions({
                            highlight: function(code, lang) {
                                if (lang && hljs.getLanguage(lang)) {
                                    try {
                                        return hljs.highlight(code, { language: lang }).value;
                                    } catch (err) {
                                        console.error('代码高亮失败:', err);
                                    }
                                }
                                return code;
                            }
                        });
                    }
                    console.log('Markdown 渲染库初始化成功');
                } catch (e) {
                    console.error('Markdown 配置失败:', e);
                }
            } else {
                // 如果 marked 还没加载，等待一段时间后重试
                setTimeout(checkMarked, 100);
            }
        };
        checkMarked();
    }

    // 安全地渲染 Markdown
    renderMarkdown(content) {
        if (!content) return '';
        
        // 检查 marked 是否可用
        if (typeof marked === 'undefined') {
            console.warn('marked 库未加载，使用纯文本显示');
            return this.escapeHtml(content);
        }
        
        try {
            const html = marked.parse(content);
            return html;
        } catch (e) {
            console.error('Markdown 渲染失败:', e);
            return this.escapeHtml(content);
        }
    }

    // 高亮代码块
    highlightCodeBlocks(container) {
        if (typeof hljs !== 'undefined' && container) {
            try {
                container.querySelectorAll('pre code').forEach((block) => {
                    if (!block.classList.contains('hljs')) {
                        hljs.highlightElement(block);
                    }
                });
            } catch (e) {
                console.error('代码高亮失败:', e);
            }
        }
    }

    // 初始化DOM元素
    initializeElements() {
        // 侧边栏元素
        this.sidebar = document.querySelector('.sidebar');
        this.newChatBtn = document.getElementById('newChatBtn');
        this.aiOpsSidebarBtn = document.getElementById('aiOpsSidebarBtn');
        
        // 输入区域元素
        this.messageInput = document.getElementById('messageInput');
        this.sendButton = document.getElementById('sendButton');
        this.toolsBtn = document.getElementById('toolsBtn');
        this.toolsMenu = document.getElementById('toolsMenu');
        this.uploadFileItem = document.getElementById('uploadFileItem');
        this.modeSelectorBtn = document.getElementById('modeSelectorBtn');
        this.modeDropdown = document.getElementById('modeDropdown');
        this.currentModeText = document.getElementById('currentModeText');
        this.fileInput = document.getElementById('fileInput');
        
        // 聊天区域元素
        this.chatMessages = document.getElementById('chatMessages');
        this.loadingOverlay = document.getElementById('loadingOverlay');
        this.chatContainer = document.querySelector('.chat-container');
        this.welcomeGreeting = document.getElementById('welcomeGreeting');
        this.chatHistoryList = document.getElementById('chatHistoryList');
        this.sidebarUserInfo = document.getElementById('sidebarUserInfo');
        this.sidebarUsername = document.getElementById('sidebarUsername');
        this.sidebarUserAvatar = document.getElementById('sidebarUserAvatar');
        this.logoutBtn = document.getElementById('logoutBtn');

        // 认证层元素
        this.authOverlay = document.getElementById('authOverlay');
        this.authTabLogin = document.getElementById('authTabLogin');
        this.authTabRegister = document.getElementById('authTabRegister');
        this.authLoginPanel = document.getElementById('authLoginPanel');
        this.authRegisterPanel = document.getElementById('authRegisterPanel');
        this.loginUsernameInput = document.getElementById('loginUsername');
        this.loginPasswordInput = document.getElementById('loginPassword');
        this.loginCaptchaInput = document.getElementById('loginCaptchaInput');
        this.loginCaptchaQuestion = document.getElementById('loginCaptchaQuestion');
        this.refreshLoginCaptchaBtn = document.getElementById('refreshLoginCaptchaBtn');
        this.loginSubmitBtn = document.getElementById('loginSubmitBtn');
        this.registerUsernameInput = document.getElementById('registerUsername');
        this.registerPasswordInput = document.getElementById('registerPassword');
        this.registerConfirmPasswordInput = document.getElementById('registerConfirmPassword');
        this.registerCaptchaInput = document.getElementById('registerCaptchaInput');
        this.registerCaptchaQuestion = document.getElementById('registerCaptchaQuestion');
        this.refreshRegisterCaptchaBtn = document.getElementById('refreshRegisterCaptchaBtn');
        this.registerSubmitBtn = document.getElementById('registerSubmitBtn');
        
        // 初始化时检查是否需要居中
        this.checkAndSetCentered();
    }

    // 绑定事件监听器
    bindEvents() {
        // 新建对话
        if (this.newChatBtn) {
            this.newChatBtn.addEventListener('click', () => this.newChat());
        }
        
        // AI Ops按钮
        if (this.aiOpsSidebarBtn) {
            this.aiOpsSidebarBtn.addEventListener('click', () => this.triggerAIOps());
        }
        
        // 模式选择下拉菜单
        if (this.modeSelectorBtn) {
            this.modeSelectorBtn.addEventListener('click', (e) => {
                e.stopPropagation();
                this.toggleModeDropdown();
            });
        }
        
        // 下拉菜单项点击
        const dropdownItems = document.querySelectorAll('.dropdown-item');
        dropdownItems.forEach(item => {
            item.addEventListener('click', (e) => {
                const mode = item.getAttribute('data-mode');
                this.selectMode(mode);
                this.closeModeDropdown();
            });
        });
        
        // 点击外部关闭下拉菜单
        document.addEventListener('click', (e) => {
            if (this.modeSelectorBtn && this.modeDropdown &&
                !this.modeSelectorBtn.contains(e.target) && 
                !this.modeDropdown.contains(e.target)) {
                this.closeModeDropdown();
            }
        });
        
        // 发送消息
        if (this.sendButton) {
            this.sendButton.addEventListener('click', () => this.sendMessage());
        }
        
        if (this.messageInput) {
            this.messageInput.addEventListener('keypress', (e) => {
                if (e.key === 'Enter' && !e.shiftKey) {
                    e.preventDefault();
                    this.sendMessage();
                }
            });
        }
        
        // 工具按钮和菜单
        if (this.toolsBtn) {
            this.toolsBtn.addEventListener('click', (e) => {
                e.stopPropagation();
                this.toggleToolsMenu();
            });
        }
        
        // 工具菜单项点击事件
        if (this.uploadFileItem) {
            this.uploadFileItem.addEventListener('click', () => {
                if (this.fileInput) {
                    this.fileInput.click();
                }
                this.closeToolsMenu();
            });
        }
        
        // 点击外部关闭工具菜单
        document.addEventListener('click', (e) => {
            if (this.toolsBtn && this.toolsMenu && 
                !this.toolsBtn.contains(e.target) && 
                !this.toolsMenu.contains(e.target)) {
                this.closeToolsMenu();
            }
        });
        
        if (this.fileInput) {
            this.fileInput.addEventListener('change', (e) => this.handleFileSelect(e));
        }

        // 认证相关事件
        if (this.authTabLogin) {
            this.authTabLogin.addEventListener('click', () => this.switchAuthTab('login'));
        }
        if (this.authTabRegister) {
            this.authTabRegister.addEventListener('click', () => this.switchAuthTab('register'));
        }
        if (this.refreshLoginCaptchaBtn) {
            this.refreshLoginCaptchaBtn.addEventListener('click', () => this.fetchCaptcha('login'));
        }
        if (this.refreshRegisterCaptchaBtn) {
            this.refreshRegisterCaptchaBtn.addEventListener('click', () => this.fetchCaptcha('register'));
        }
        if (this.loginSubmitBtn) {
            this.loginSubmitBtn.addEventListener('click', () => this.submitLogin());
        }
        if (this.registerSubmitBtn) {
            this.registerSubmitBtn.addEventListener('click', () => this.submitRegister());
        }
        if (this.logoutBtn) {
            this.logoutBtn.addEventListener('click', () => this.handleLogout());
        }

        [this.loginUsernameInput, this.loginPasswordInput, this.loginCaptchaInput].forEach((el) => {
            if (!el) return;
            el.addEventListener('keypress', (e) => {
                if (e.key === 'Enter') {
                    e.preventDefault();
                    this.submitLogin();
                }
            });
        });
        [this.registerUsernameInput, this.registerPasswordInput, this.registerConfirmPasswordInput, this.registerCaptchaInput].forEach((el) => {
            if (!el) return;
            el.addEventListener('keypress', (e) => {
                if (e.key === 'Enter') {
                    e.preventDefault();
                    this.submitRegister();
                }
            });
        });
    }

    async initializeAuthFlow() {
        try {
            await this.fetchCaptcha('login');
            await this.fetchCaptcha('register');
        } catch (e) {
            console.warn('初始化验证码失败:', e);
        }

        if (!this.authToken) {
            this.showAuthOverlay(true);
            this.updateSidebarUserInfo();
            return;
        }

        try {
            const response = await this.authFetch(`${this.apiBaseUrl}/auth/me`, { method: 'GET' }, true);
            if (!response.ok) {
                this.handleAuthExpired(false);
                return;
            }
            const data = await response.json();
            if (!data || (data.code !== 200 && data.message !== 'success') || !data.data) {
                this.handleAuthExpired(false);
                return;
            }

            this.currentUser = {
                userId: data.data.userId,
                username: data.data.username
            };
            this.showAuthOverlay(false);
            this.updateSidebarUserInfo();
            this.chatHistories = this.loadChatHistories();
            this.renderChatHistory();
            this.syncChatHistoriesFromServer();
        } catch (e) {
            console.warn('初始化登录态失败:', e);
            this.handleAuthExpired(false);
        }
    }

    showAuthOverlay(show) {
        if (!this.authOverlay) {
            return;
        }
        if (show) {
            this.authOverlay.classList.add('show');
        } else {
            this.authOverlay.classList.remove('show');
        }
    }

    switchAuthTab(tab) {
        const isLogin = tab === 'login';
        if (this.authTabLogin) {
            this.authTabLogin.classList.toggle('active', isLogin);
            this.authTabLogin.setAttribute('aria-selected', isLogin ? 'true' : 'false');
        }
        if (this.authTabRegister) {
            this.authTabRegister.classList.toggle('active', !isLogin);
            this.authTabRegister.setAttribute('aria-selected', isLogin ? 'false' : 'true');
        }
        if (this.authLoginPanel) {
            this.authLoginPanel.classList.toggle('active', isLogin);
            this.authLoginPanel.setAttribute('aria-hidden', isLogin ? 'false' : 'true');
        }
        if (this.authRegisterPanel) {
            this.authRegisterPanel.classList.toggle('active', !isLogin);
            this.authRegisterPanel.setAttribute('aria-hidden', isLogin ? 'true' : 'false');
        }
    }

    async fetchCaptcha(mode) {
        const response = await fetch(`${this.apiBaseUrl}/auth/captcha`, {
            method: 'GET',
            headers: {
                'Content-Type': 'application/json'
            }
        });

        if (!response.ok) {
            throw new Error('验证码获取失败');
        }

        const data = await response.json();
        if (!data || (data.code !== 200 && data.message !== 'success') || !data.data) {
            throw new Error(data?.message || '验证码获取失败');
        }

        if (mode === 'login') {
            this.loginCaptchaId = data.data.captchaId;
            if (this.loginCaptchaQuestion) {
                this.loginCaptchaQuestion.textContent = data.data.question || '--';
            }
        } else {
            this.registerCaptchaId = data.data.captchaId;
            if (this.registerCaptchaQuestion) {
                this.registerCaptchaQuestion.textContent = data.data.question || '--';
            }
        }
    }

    async submitLogin() {
        const username = this.loginUsernameInput ? this.loginUsernameInput.value.trim() : '';
        const password = this.loginPasswordInput ? this.loginPasswordInput.value : '';
        const captchaAnswer = this.loginCaptchaInput ? this.loginCaptchaInput.value.trim() : '';

        if (!username || !password || !captchaAnswer) {
            this.showNotification('请完整填写登录信息', 'warning');
            return;
        }
        if (!/^[A-Za-z0-9]{1,10}$/.test(username)) {
            this.showNotification('用户名只能包含英文和数字，且长度不能超过10位', 'warning');
            return;
        }

        try {
            const response = await fetch(`${this.apiBaseUrl}/auth/login`, {
                method: 'POST',
                headers: {
                    'Content-Type': 'application/json'
                },
                body: JSON.stringify({
                    username: username,
                    password: password,
                    captchaId: this.loginCaptchaId,
                    captchaAnswer: captchaAnswer
                })
            });
            const data = await response.json();
            if (!response.ok || !data || (data.code !== 200 && data.message !== 'success') || !data.data) {
                this.showNotification(data?.message || '登录失败', 'error');
                await this.fetchCaptcha('login').catch(() => {});
                if (this.loginCaptchaInput) {
                    this.loginCaptchaInput.value = '';
                }
                return;
            }
            this.finishLogin(data.data, true);
        } catch (e) {
            this.showNotification('登录失败: ' + e.message, 'error');
            await this.fetchCaptcha('login').catch(() => {});
        }
    }

    async submitRegister() {
        const username = this.registerUsernameInput ? this.registerUsernameInput.value.trim() : '';
        const password = this.registerPasswordInput ? this.registerPasswordInput.value : '';
        const confirmPassword = this.registerConfirmPasswordInput ? this.registerConfirmPasswordInput.value : '';
        const captchaAnswer = this.registerCaptchaInput ? this.registerCaptchaInput.value.trim() : '';

        if (!username || !password || !confirmPassword || !captchaAnswer) {
            this.showNotification('请完整填写注册信息', 'warning');
            return;
        }
        if (!/^[A-Za-z0-9]{1,10}$/.test(username)) {
            this.showNotification('用户名只能包含英文和数字，且长度不能超过10位', 'warning');
            return;
        }
        if (password.length < 5) {
            this.showNotification('密码长度不能低于5位', 'warning');
            return;
        }
        if (password !== confirmPassword) {
            this.showNotification('两次输入的密码不一致', 'warning');
            return;
        }

        try {
            const response = await fetch(`${this.apiBaseUrl}/auth/register`, {
                method: 'POST',
                headers: {
                    'Content-Type': 'application/json'
                },
                body: JSON.stringify({
                    username: username,
                    password: password,
                    confirmPassword: confirmPassword,
                    captchaId: this.registerCaptchaId,
                    captchaAnswer: captchaAnswer
                })
            });
            const data = await response.json();
            if (!response.ok || !data || (data.code !== 200 && data.message !== 'success') || !data.data) {
                this.showNotification(data?.message || '注册失败', 'error');
                await this.fetchCaptcha('register').catch(() => {});
                if (this.registerCaptchaInput) {
                    this.registerCaptchaInput.value = '';
                }
                return;
            }
            this.finishLogin(data.data, true);
        } catch (e) {
            this.showNotification('注册失败: ' + e.message, 'error');
            await this.fetchCaptcha('register').catch(() => {});
        }
    }

    finishLogin(authData, showSuccessNotification) {
        this.authToken = authData.token || '';
        if (!this.authToken) {
            this.showNotification('登录态异常，请重试', 'error');
            return;
        }

        localStorage.setItem('authToken', this.authToken);
        this.currentUser = {
            userId: authData.userId,
            username: authData.username
        };

        if (this.loginPasswordInput) this.loginPasswordInput.value = '';
        if (this.loginCaptchaInput) this.loginCaptchaInput.value = '';
        if (this.registerPasswordInput) this.registerPasswordInput.value = '';
        if (this.registerConfirmPasswordInput) this.registerConfirmPasswordInput.value = '';
        if (this.registerCaptchaInput) this.registerCaptchaInput.value = '';

        // 登录后重置会话状态，加载当前用户隔离数据
        this.currentChatHistory = [];
        this.chatHistories = this.loadChatHistories();
        this.sessionId = this.generateSessionId();
        this.isCurrentChatFromHistory = false;
        if (this.chatMessages) {
            this.chatMessages.innerHTML = '';
        }
        this.checkAndSetCentered();
        this.renderChatHistory();
        this.updateSidebarUserInfo();
        this.showAuthOverlay(false);
        this.syncChatHistoriesFromServer();

        if (showSuccessNotification) {
            this.showNotification('登录成功', 'success');
        }
    }

    async handleLogout() {
        if (!this.isAuthenticated()) {
            return;
        }

        const confirmed = await this.showConfirmDialog({
            title: '退出登录',
            message: '确认退出当前账号吗？',
            confirmText: '退出',
            cancelText: '取消'
        });
        if (!confirmed) {
            return;
        }

        try {
            await this.authFetch(`${this.apiBaseUrl}/auth/logout`, {
                method: 'POST',
                headers: {
                    'Content-Type': 'application/json'
                }
            }, true);
        } catch (e) {
            console.warn('退出登录请求失败:', e);
        }

        this.handleAuthExpired(false);
        this.showNotification('已退出登录', 'info');
    }

    handleAuthExpired(showToast = true) {
        this.authToken = '';
        this.currentUser = null;
        localStorage.removeItem('authToken');

        this.chatHistories = [];
        this.currentChatHistory = [];
        this.isCurrentChatFromHistory = false;
        this.sessionId = this.generateSessionId();

        if (this.chatMessages) {
            this.chatMessages.innerHTML = '';
        }
        this.renderChatHistory();
        this.checkAndSetCentered();
        this.updateSidebarUserInfo();
        this.showAuthOverlay(true);
        this.switchAuthTab('login');
        this.fetchCaptcha('login').catch(() => {});
        this.fetchCaptcha('register').catch(() => {});

        if (showToast) {
            this.showNotification('登录已失效，请重新登录', 'warning');
        }
    }

    isAuthenticated() {
        return !!(this.authToken && this.currentUser && this.currentUser.userId);
    }

    updateSidebarUserInfo() {
        if (!this.sidebarUserInfo) {
            return;
        }

        if (!this.currentUser || !this.currentUser.username) {
            this.sidebarUserInfo.classList.remove('show');
            if (this.sidebarUsername) {
                this.sidebarUsername.textContent = '-';
            }
            if (this.sidebarUserAvatar) {
                this.sidebarUserAvatar.textContent = 'U';
            }
            return;
        }

        this.sidebarUserInfo.classList.add('show');
        if (this.sidebarUsername) {
            this.sidebarUsername.textContent = this.currentUser.username;
        }
        if (this.sidebarUserAvatar) {
            this.sidebarUserAvatar.textContent = this.currentUser.username.substring(0, 1).toUpperCase();
        }
    }

    getChatHistoryStorageKey() {
        if (!this.currentUser || !this.currentUser.userId) {
            return 'chatHistories';
        }
        return `chatHistories:${this.currentUser.userId}`;
    }

    async authFetch(url, options = {}, skipAuthExpiredHandling = false) {
        const requestOptions = {
            ...options,
            headers: {
                ...(options.headers || {})
            }
        };

        if (this.authToken) {
            requestOptions.headers['Authorization'] = `Bearer ${this.authToken}`;
        }

        const response = await fetch(url, requestOptions);
        if (response.status === 401 && !skipAuthExpiredHandling) {
            this.handleAuthExpired(true);
        }
        return response;
    }

    // 切换工具菜单显示/隐藏
    toggleToolsMenu() {
        if (this.toolsMenu && this.toolsBtn) {
            const wrapper = this.toolsBtn.closest('.tools-btn-wrapper');
            if (wrapper) {
                wrapper.classList.toggle('active');
            }
        }
    }

    // 关闭工具菜单
    closeToolsMenu() {
        if (this.toolsMenu && this.toolsBtn) {
            const wrapper = this.toolsBtn.closest('.tools-btn-wrapper');
            if (wrapper) {
                wrapper.classList.remove('active');
            }
        }
    }

    // 新建对话
    newChat() {
        if (!this.isAuthenticated()) {
            this.showAuthOverlay(true);
            this.showNotification('请先登录后再开始对话', 'warning');
            return;
        }

        if (this.isStreaming) {
            this.showNotification('请等待当前对话完成后再新建对话', 'warning');
            return;
        }
        
        // 如果当前有对话内容，且不是从历史记录加载的，才保存为新的历史对话
        // 如果是从历史记录加载的，只需要更新该历史记录
        if (this.currentChatHistory.length > 0) {
            if (this.isCurrentChatFromHistory) {
                // 当前对话是从历史记录加载的，更新该历史记录
                this.updateCurrentChatHistory();
            } else {
                // 当前对话是新对话，保存为新的历史对话
                this.saveCurrentChat();
            }
        }
        
        // 停止所有进行中的操作
        this.isStreaming = false;
        
        // 清空输入框
        if (this.messageInput) {
            this.messageInput.value = '';
        }
        
        // 清空当前对话历史
        this.currentChatHistory = [];
        
        // 重置标记
        this.isCurrentChatFromHistory = false;
        
        // 清空聊天记录
        if (this.chatMessages) {
            this.chatMessages.innerHTML = '';
        }
        
        // 生成新的会话ID
        this.sessionId = this.generateSessionId();
        
        // 重置模式为快速
        this.currentMode = 'quick';
        this.updateUI();
        
        // 重新设置居中样式（确保对话框居中显示）
        this.checkAndSetCentered();
        
        // 确保容器有过渡动画
        if (this.chatContainer) {
            this.chatContainer.style.transition = 'all 0.5s ease';
        }
        
        // 更新历史对话列表
        this.renderChatHistory();
    }
    
    // 保存当前对话到历史记录（新建）
    saveCurrentChat() {
        if (!this.isAuthenticated()) {
            return;
        }
        if (this.currentChatHistory.length === 0) {
            return;
        }
        
        // 检查是否已存在相同ID的历史记录
        const existingIndex = this.chatHistories.findIndex(h => h.id === this.sessionId);
        if (existingIndex !== -1) {
            // 如果已存在，更新而不是新建
            this.updateCurrentChatHistory();
            return;
        }
        
        // 获取对话标题（使用第一条用户消息的前30个字符）
        const firstUserMessage = this.currentChatHistory.find(msg => msg.type === 'user');
        const title = firstUserMessage ? 
            (firstUserMessage.content.substring(0, 30) + (firstUserMessage.content.length > 30 ? '...' : '')) : 
            '新对话';
        
        const chatHistory = {
            id: this.sessionId,
            title: title,
            createdAt: new Date().toISOString(),
            updatedAt: new Date().toISOString()
        };
        
        // 添加到历史记录列表的开头
        this.chatHistories.unshift(chatHistory);
        
        // 限制历史记录数量（最多保存50条）
        if (this.chatHistories.length > 50) {
            this.chatHistories = this.chatHistories.slice(0, 50);
        }
        
        // 保存到localStorage
        this.saveChatHistories();
    }
    
    // 更新当前对话的历史记录
    updateCurrentChatHistory() {
        if (!this.isAuthenticated()) {
            return;
        }
        if (this.currentChatHistory.length === 0) {
            return;
        }
        
        const existingIndex = this.chatHistories.findIndex(h => h.id === this.sessionId);
        if (existingIndex === -1) {
            // 如果不存在，调用保存方法
            this.saveCurrentChat();
            return;
        }
        
        // 更新现有的历史记录
        const history = this.chatHistories[existingIndex];
        history.updatedAt = new Date().toISOString();
        
        // 如果标题需要更新（第一条消息改变了）
        const firstUserMessage = this.currentChatHistory.find(msg => msg.type === 'user');
        if (firstUserMessage) {
            const newTitle = firstUserMessage.content.substring(0, 30) + (firstUserMessage.content.length > 30 ? '...' : '');
            if (history.title !== newTitle) {
                history.title = newTitle;
            }
        }
        
        // 保存到localStorage
        this.saveChatHistories();
    }

    // 统一持久化当前会话到 localStorage，并刷新左侧近期对话列表
    persistCurrentChatHistory() {
        if (!this.isAuthenticated()) {
            return;
        }
        if (this.currentChatHistory.length === 0) {
            return;
        }

        if (this.isCurrentChatFromHistory) {
            this.updateCurrentChatHistory();
        } else {
            this.saveCurrentChat();
        }
        this.renderChatHistory();
    }
    
    // 加载历史对话列表
    loadChatHistories() {
        if (!this.isAuthenticated()) {
            return [];
        }
        try {
            const stored = localStorage.getItem(this.getChatHistoryStorageKey());
            const parsed = stored ? JSON.parse(stored) : [];
            return this.normalizeChatHistoryIndexList(parsed);
        } catch (e) {
            console.error('加载历史对话失败:', e);
            return [];
        }
    }
    
    // 保存历史对话列表到localStorage
    saveChatHistories(syncServer = true) {
        if (!this.isAuthenticated()) {
            return;
        }
        try {
            this.chatHistories = this.normalizeChatHistoryIndexList(this.chatHistories);
            localStorage.setItem(this.getChatHistoryStorageKey(), JSON.stringify(this.chatHistories));
            if (syncServer) {
                this.scheduleSyncChatHistoriesToServer();
            }
        } catch (e) {
            console.error('保存历史对话失败:', e);
        }
    }

    // 延迟同步，避免高频写入本地时反复触发后端请求
    scheduleSyncChatHistoriesToServer() {
        if (!this.isAuthenticated()) {
            return;
        }
        if (this.historySyncTimer) {
            clearTimeout(this.historySyncTimer);
        }
        this.historySyncTimer = setTimeout(() => {
            this.syncChatHistoriesToServer();
        }, 300);
    }

    // 将近期对话列表同步到后端（跨浏览器共享）
    async syncChatHistoriesToServer() {
        if (!this.isAuthenticated()) {
            return;
        }
        try {
            const lightweightHistories = this.normalizeChatHistoryIndexList(this.chatHistories);
            await this.authFetch(`${this.apiBaseUrl}/chat/histories`, {
                method: 'POST',
                headers: {
                    'Content-Type': 'application/json',
                },
                body: JSON.stringify(lightweightHistories || [])
            });
        } catch (e) {
            console.warn('同步历史对话到服务端失败:', e);
        }
    }

    // 从后端拉取历史记录并与本地合并，解决跨浏览器历史不共享问题
    async syncChatHistoriesFromServer() {
        if (!this.isAuthenticated()) {
            return;
        }
        try {
            const response = await this.authFetch(`${this.apiBaseUrl}/chat/histories`, {
                method: 'GET',
                headers: {
                    'Content-Type': 'application/json',
                }
            });
            if (!response.ok) {
                return;
            }

            const data = await response.json();
            if (!data || (data.code !== 200 && data.message !== 'success')) {
                return;
            }

            const serverHistories = Array.isArray(data.data) ? data.data : [];
            if (serverHistories.length === 0) {
                // 服务端为空但本地有数据，回推一次避免首次使用时出现空白
                if (this.chatHistories.length > 0) {
                    this.syncChatHistoriesToServer();
                }
                return;
            }

            const mergedHistories = this.mergeChatHistories(this.chatHistories, serverHistories);
            this.chatHistories = mergedHistories;
            this.saveChatHistories(false);
            this.renderChatHistory();
        } catch (e) {
            console.warn('从服务端同步历史对话失败:', e);
        }
    }

    // 合并本地与服务端历史，按 updatedAt 降序去重（同 id 取更新时间更近的一条）
    mergeChatHistories(localHistories, serverHistories) {
        const mergedMap = new Map();
        const source = [
            ...(Array.isArray(localHistories) ? localHistories : []),
            ...(Array.isArray(serverHistories) ? serverHistories : [])
        ];

        source.forEach((history) => {
            const normalizedHistory = this.normalizeChatHistoryIndex(history);
            if (!normalizedHistory) {
                return;
            }
            const existing = mergedMap.get(normalizedHistory.id);
            if (!existing) {
                mergedMap.set(normalizedHistory.id, normalizedHistory);
                return;
            }

            const existingTs = new Date(existing.updatedAt || existing.createdAt || 0).getTime();
            const currentTs = new Date(normalizedHistory.updatedAt || normalizedHistory.createdAt || 0).getTime();
            if (currentTs >= existingTs) {
                mergedMap.set(normalizedHistory.id, normalizedHistory);
            }
        });

        return Array.from(mergedMap.values())
            .sort((a, b) => new Date(b.updatedAt || b.createdAt || 0).getTime() - new Date(a.updatedAt || a.createdAt || 0).getTime())
            .slice(0, 50);
    }

    // 归一化左侧“近期对话”索引项（只保留轻量字段）
    normalizeChatHistoryIndex(history) {
        if (!history || !history.id) {
            return null;
        }

        const nowIso = new Date().toISOString();
        const createdAt = history.createdAt || nowIso;
        const updatedAt = history.updatedAt || createdAt;

        return {
            id: String(history.id),
            title: history.title ? String(history.title) : '新对话',
            createdAt: String(createdAt),
            updatedAt: String(updatedAt)
        };
    }

    normalizeChatHistoryIndexList(histories) {
        if (!Array.isArray(histories)) {
            return [];
        }

        return histories
            .map((history) => this.normalizeChatHistoryIndex(history))
            .filter((history) => history !== null)
            .sort((a, b) => new Date(b.updatedAt || b.createdAt || 0).getTime() - new Date(a.updatedAt || a.createdAt || 0).getTime())
            .slice(0, 50);
    }
    
    // 渲染历史对话列表
    renderChatHistory() {
        if (!this.chatHistoryList) {
            return;
        }
        
        this.chatHistoryList.innerHTML = '';
        
        if (this.chatHistories.length === 0) {
            return;
        }
        
        this.chatHistories.forEach((history, index) => {
            const historyItem = document.createElement('div');
            historyItem.className = 'history-item';
            historyItem.dataset.historyId = history.id;
            
            historyItem.innerHTML = `
                <div class="history-item-content">
                    <span class="history-item-title">${this.escapeHtml(history.title)}</span>
                </div>
                <button class="history-item-delete" data-history-id="${history.id}" title="删除">
                    <svg viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg">
                        <path d="M18 6L6 18M6 6L18 18" stroke="currentColor" stroke-width="2" stroke-linecap="round"/>
                    </svg>
                </button>
            `;
            
            // 点击历史项加载对话
            historyItem.addEventListener('click', (e) => {
                if (!e.target.closest('.history-item-delete')) {
                    this.loadChatHistory(history.id);
                }
            });
            
            // 删除历史对话
            const deleteBtn = historyItem.querySelector('.history-item-delete');
            deleteBtn.addEventListener('click', (e) => {
                e.stopPropagation();
                this.deleteChatHistory(history.id);
            });
            
            this.chatHistoryList.appendChild(historyItem);
        });
    }
    
    // 将后端会话消息（role/content）转换为前端消息结构（type/content）
    normalizeSessionMessages(messages) {
        if (!Array.isArray(messages)) {
            return [];
        }

        return messages
            .map((message) => {
                if (!message) {
                    return null;
                }
                const role = (message.role || '').toString().toLowerCase();
                const type = role === 'user' ? 'user' : 'assistant';
                const content = message.content == null ? '' : String(message.content);
                return {
                    type: type,
                    content: content,
                    timestamp: new Date().toISOString()
                };
            })
            .filter((message) => message !== null);
    }

    // 兼容旧版本本地历史结构（包含 messages 字段）
    normalizeLegacyHistoryMessages(messages) {
        if (!Array.isArray(messages)) {
            return [];
        }

        return messages
            .map((message) => {
                if (!message) {
                    return null;
                }
                const type = message.type === 'user' ? 'user' : 'assistant';
                const content = message.content == null ? '' : String(message.content);
                return {
                    type: type,
                    content: content,
                    timestamp: message.timestamp || new Date().toISOString()
                };
            })
            .filter((message) => message !== null);
    }

    // 删除本地会话索引（可选同步到后端索引存储）
    removeChatHistoryIndex(historyId, syncServer = true) {
        const beforeSize = this.chatHistories.length;
        this.chatHistories = this.chatHistories.filter(h => h.id !== historyId);
        const changed = this.chatHistories.length !== beforeSize;
        if (changed) {
            this.saveChatHistories(syncServer);
            this.renderChatHistory();
        }
        return changed;
    }

    // 删除后端会话存储（chat:session:*）
    async clearSessionOnServer(sessionId) {
        if (!this.isAuthenticated() || !sessionId) {
            return true;
        }

        try {
            const response = await this.authFetch(`${this.apiBaseUrl}/chat/clear`, {
                method: 'POST',
                headers: {
                    'Content-Type': 'application/json',
                },
                body: JSON.stringify({
                    Id: sessionId
                })
            });

            if (!response.ok) {
                return false;
            }

            const data = await response.json();
            if (data && (data.code === 200 || data.message === 'success')) {
                return true;
            }

            // 会话已不存在时按“已清理”处理，避免影响前端删除体验
            if (data && typeof data.message === 'string' && data.message.includes('会话不存在')) {
                return true;
            }
        } catch (e) {
            console.warn('删除后端会话存储失败:', e);
            return false;
        }

        return false;
    }

    // 加载历史对话
    async loadChatHistory(historyId) {
        if (!this.isAuthenticated()) {
            this.showAuthOverlay(true);
            return;
        }

        const history = this.chatHistories.find(h => h.id === historyId);
        if (!history) {
            return;
        }
        
        // 切换当前会话
        this.sessionId = history.id;
        this.isCurrentChatFromHistory = true; // 标记为从历史记录加载

        let messages = [];
        let loadedFromServer = false;

        let serverMessage = '';
        try {
            const response = await this.authFetch(`${this.apiBaseUrl}/chat/session/messages/${encodeURIComponent(history.id)}`, {
                method: 'GET',
                headers: {
                    'Content-Type': 'application/json'
                }
            });

            if (response.ok) {
                const data = await response.json();
                if (data && (data.code === 200 || data.message === 'success') && Array.isArray(data.data)) {
                    messages = this.normalizeSessionMessages(data.data);
                    loadedFromServer = true;
                } else if (data && typeof data.message === 'string') {
                    serverMessage = data.message;
                }
            }
        } catch (e) {
            console.warn('加载会话消息失败，尝试兼容本地旧缓存:', e);
        }

        // 兼容旧本地缓存：如果后端读取失败且本地仍有 messages 字段，使用旧数据兜底
        if (!loadedFromServer && Array.isArray(history.messages)) {
            messages = this.normalizeLegacyHistoryMessages(history.messages);
        }

        if (!loadedFromServer && !Array.isArray(history.messages)) {
            // 会话不存在时自动从左侧索引移除，减少脏数据残留
            const notFound = !serverMessage || serverMessage.includes('会话不存在');
            if (notFound) {
                this.removeChatHistoryIndex(history.id, true);
            }
            this.currentChatHistory = [];
            this.isCurrentChatFromHistory = false;
            this.sessionId = this.generateSessionId();
            if (this.chatMessages) {
                this.chatMessages.innerHTML = '';
            }
            this.checkAndSetCentered();
            this.showNotification('该会话消息不存在或已过期，已从近期对话移除', 'warning');
            return;
        }

        this.currentChatHistory = [...messages];

        // 清空并重新渲染消息
        if (this.chatMessages) {
            this.chatMessages.innerHTML = '';
            messages.forEach(msg => {
                this.addMessage(msg.type, msg.content, false, false); // false表示不是流式，false表示不保存到历史（因为已经存在）
            });
        }

        // 更新UI
        this.checkAndSetCentered();
        this.renderChatHistory();
    }
    
    // 删除历史对话
    async deleteChatHistory(historyId) {
        if (!this.isAuthenticated()) {
            this.showAuthOverlay(true);
            return;
        }

        const confirmed = await this.showConfirmDialog({
            title: '删除会话',
            message: '确认删除该会话吗？删除后无法恢复。',
            confirmText: '删除',
            cancelText: '取消'
        });
        if (!confirmed) {
            return;
        }

        const removed = this.removeChatHistoryIndex(historyId, true);

        // 如果删除的是当前对话，清空当前对话
        if (this.sessionId === historyId) {
            this.currentChatHistory = [];
            this.isCurrentChatFromHistory = false;
            if (this.chatMessages) {
                this.chatMessages.innerHTML = '';
            }
            this.sessionId = this.generateSessionId();
            this.checkAndSetCentered();
        }

        const cleared = await this.clearSessionOnServer(historyId);
        if (removed && !cleared) {
            this.showNotification('已删除本地会话，后端会话删除失败', 'warning');
        }
    }

    // 切换模式下拉菜单
    toggleModeDropdown() {
        if (this.modeSelectorBtn && this.modeDropdown) {
            const wrapper = this.modeSelectorBtn.closest('.mode-selector-wrapper');
            if (wrapper) {
                wrapper.classList.toggle('active');
            }
        }
    }

    // 关闭模式下拉菜单
    closeModeDropdown() {
        if (this.modeSelectorBtn && this.modeDropdown) {
            const wrapper = this.modeSelectorBtn.closest('.mode-selector-wrapper');
            if (wrapper) {
                wrapper.classList.remove('active');
            }
        }
    }

    // 选择模式
    selectMode(mode) {
        if (this.isStreaming) {
            this.showNotification('请等待当前对话完成后再切换模式', 'warning');
            return;
        }
        
        this.currentMode = mode;
        this.updateUI();
        
        const modeNames = {
            'quick': '快速',
            'stream': '流式'
        };
        
        this.showNotification(`已切换到${modeNames[mode]}模式`, 'info');
    }

    // 更新UI
    updateUI() {
        // 更新模式选择器显示
        if (this.currentModeText) {
            const modeNames = {
                'quick': '快速',
                'stream': '流式'
            };
            this.currentModeText.textContent = modeNames[this.currentMode] || '快速';
        }
        
        // 更新下拉菜单选中状态
        const dropdownItems = document.querySelectorAll('.dropdown-item');
        dropdownItems.forEach(item => {
            const mode = item.getAttribute('data-mode');
            if (mode === this.currentMode) {
                item.classList.add('active');
            } else {
                item.classList.remove('active');
            }
        });
        
        // 更新发送按钮状态
        if (this.sendButton) {
            this.sendButton.disabled = this.isStreaming;
        }
        
        // 更新输入框状态
        if (this.messageInput) {
            this.messageInput.disabled = this.isStreaming;
            this.messageInput.placeholder = '问问智能运维助手';
        }
    }

    // 生成随机会话ID
    generateSessionId() {
        return 'session_' + Math.random().toString(36).substr(2, 9) + '_' + Date.now();
    }

    // 发送消息
    async sendMessage() {
        if (!this.isAuthenticated()) {
            this.showAuthOverlay(true);
            this.showNotification('请先登录后再发送消息', 'warning');
            return;
        }

        let message = '';
        if (this.messageInput) {
            message = this.messageInput.value.trim();
        }
        
        if (!message) {
            this.showNotification('请输入消息内容', 'warning');
            return;
        }

        if (this.isStreaming) {
            this.showNotification('请等待当前对话完成', 'warning');
            return;
        }

        // 显示用户消息
        this.addMessage('user', message);
        // 先落盘一次，避免用户刷新页面时连用户问题都丢失
        this.persistCurrentChatHistory();
        
        // 清空输入框
        if (this.messageInput) {
            this.messageInput.value = '';
        }

        // 设置发送状态
        this.isStreaming = true;
        this.updateUI();

        try {
            if (this.currentMode === 'quick') {
                await this.sendQuickMessage(message);
            } else if (this.currentMode === 'stream') {
                await this.sendStreamMessage(message);
            }
        } catch (error) {
            console.error('发送消息失败:', error);
            this.addMessage('assistant', '抱歉，发送消息时出现错误：' + error.message);
        } finally {
            this.isStreaming = false;
            this.updateUI();
            // 每次请求完成后都持久化当前会话，保证刷新后近期对话不丢失
            this.persistCurrentChatHistory();
        }
    }

    // 发送快速消息（普通对话）
    async sendQuickMessage(message) {
        // 添加等待提示消息
        const loadingMessage = this.addLoadingMessage('正在思考...');
        
        try {
            const response = await this.authFetch(`${this.apiBaseUrl}/chat`, {
                method: 'POST',
                headers: {
                    'Content-Type': 'application/json',
                },
                body: JSON.stringify({
                    Id: this.sessionId,
                    Question: message
                })
            });

            if (!response.ok) {
                throw new Error(`HTTP错误: ${response.status}`);
            }

            const data = await response.json();
            console.log('[sendQuickMessage] 响应数据:', JSON.stringify(data));
            
            // 移除等待提示消息
            if (loadingMessage && loadingMessage.parentNode) {
                loadingMessage.parentNode.removeChild(loadingMessage);
            }
            
            // 统一响应格式：检查 data.code 或 data.message 判断请求是否成功
            if (data.code === 200 || data.message === 'success') {
                // data.data 是 ChatResponse 对象
                const chatResponse = data.data;
                
                if (chatResponse && chatResponse.success) {
                    // 成功：添加实际响应消息（即使 answer 为空也显示）
                    const answer = chatResponse.answer || '（无回复内容）';
                    this.addMessage('assistant', answer);
                } else if (chatResponse && chatResponse.errorMessage) {
                    // 业务错误
                    throw new Error(chatResponse.errorMessage);
                } else {
                    // 兜底：尝试显示任何可用内容
                    const fallbackAnswer = chatResponse?.answer || chatResponse?.errorMessage || '服务返回了空内容';
                    this.addMessage('assistant', fallbackAnswer);
                }
            } else {
                // HTTP 成功但业务失败
                throw new Error(data.message || '请求失败');
            }
        } catch (error) {
            // 出错时也要移除等待提示消息
            if (loadingMessage && loadingMessage.parentNode) {
                loadingMessage.parentNode.removeChild(loadingMessage);
            }
            throw error;
        }
    }

    // 发送流式消息
    async sendStreamMessage(message) {
        try {
            const response = await this.authFetch(`${this.apiBaseUrl}/chat_stream`, {
                method: 'POST',
                headers: {
                    'Content-Type': 'application/json',
                },
                body: JSON.stringify({
                    Id: this.sessionId,
                    Question: message
                })
            });

            if (!response.ok) {
                throw new Error(`HTTP错误: ${response.status}`);
            }
            
            // 创建助手消息元素
            const assistantMessageElement = this.addMessage('assistant', '', true);
            let fullResponse = '';

            // 处理流式响应
            const reader = response.body.getReader();
            const decoder = new TextDecoder();
            let buffer = '';
            let currentEvent = '';

            try {
                while (true) {
                    const { done, value } = await reader.read();
                    
                    if (done) {
                        // 流结束，使用统一的处理方法
                        this.handleStreamComplete(assistantMessageElement, fullResponse);
                        break;
                    }

                    // 解码数据并添加到缓冲区
                    buffer += decoder.decode(value, { stream: true });
                    
                    // 按行分割处理
                    const lines = buffer.split('\n');
                    // 保留最后一行（可能不完整）
                    buffer = lines.pop() || '';
                    
                    for (const line of lines) {
                        if (line.trim() === '') continue;
                        
                        console.log('[SSE调试] 收到行:', line);
                        
                        // 解析SSE格式
                        if (line.startsWith('id:')) {
                            console.log('[SSE调试] 解析到ID');
                            continue;
                        } else if (line.startsWith('event:')) {
                            // 兼容 "event:message" 和 "event: message" 两种格式
                            currentEvent = line.substring(6).trim();
                            console.log('[SSE调试] 解析到事件类型:', currentEvent);
                            // 注意：后端统一使用 "message" 事件名，真正的类型在 data 的 JSON 中
                            continue;
                        } else if (line.startsWith('data:')) {
                            // 兼容 "data:xxx" 和 "data: xxx" 两种格式
                            const rawData = line.substring(5).trim();
                            console.log('[SSE调试] 解析到数据, currentEvent:', currentEvent, ', rawData:', rawData);
                            
                            // 兼容旧格式 [DONE] 标记
                            if (rawData === '[DONE]') {
                                // 流结束标记，将内容转换为Markdown渲染
                                this.handleStreamComplete(assistantMessageElement, fullResponse);
                                return;
                            }
                            
                            // 处理 SSE 数据
                            try {
                                // 尝试解析为 SseMessage 格式的 JSON
                                const sseMessage = JSON.parse(rawData);
                                console.log('[SSE调试] 解析JSON成功:', sseMessage);
                                
                                if (sseMessage && typeof sseMessage.type === 'string') {
                                    if (sseMessage.type === 'content') {
                                        const content = sseMessage.data || '';
                                        fullResponse += content;
                                        console.log('[SSE调试] 添加内容:', content);
                                        
                                        // 实时渲染 Markdown
                                        if (assistantMessageElement) {
                                            const messageContent = assistantMessageElement.querySelector('.message-content');
                                            messageContent.innerHTML = this.renderMarkdown(fullResponse);
                                            // 高亮代码块
                                            this.highlightCodeBlocks(messageContent);
                                            this.scrollToBottom();
                                        }
                                    } else if (sseMessage.type === 'done') {
                                        console.log('[SSE调试] 收到done标记，流结束');
                                        this.handleStreamComplete(assistantMessageElement, fullResponse);
                                        return;
                                    } else if (sseMessage.type === 'error') {
                                        console.error('[SSE调试] 收到错误:', sseMessage.data);
                                        if (assistantMessageElement) {
                                            const messageContent = assistantMessageElement.querySelector('.message-content');
                                            messageContent.innerHTML = this.renderMarkdown('错误: ' + (sseMessage.data || '未知错误'));
                                        }
                                        return;
                                    }
                                } else {
                                    // 不是标准 SseMessage 格式，尝试兼容处理
                                    console.log('[SSE调试] 非标准格式，尝试兼容处理');
                                    fullResponse += rawData;
                                    if (assistantMessageElement) {
                                        const messageContent = assistantMessageElement.querySelector('.message-content');
                                        messageContent.innerHTML = this.renderMarkdown(fullResponse);
                                        this.highlightCodeBlocks(messageContent);
                                        this.scrollToBottom();
                                    }
                                }
                            } catch (e) {
                                // JSON 解析失败，尝试兼容旧格式
                                console.log('[SSE调试] JSON解析失败，使用兼容模式:', e.message);
                                if (rawData === '') {
                                    fullResponse += '\n';
                                } else {
                                    fullResponse += rawData;
                                }
                                
                                if (assistantMessageElement) {
                                    const messageContent = assistantMessageElement.querySelector('.message-content');
                                    messageContent.innerHTML = this.renderMarkdown(fullResponse);
                                    this.highlightCodeBlocks(messageContent);
                                    this.scrollToBottom();
                                }
                            }
                        }
                    }
                }
            } finally {
                reader.releaseLock();
            }
        } catch (error) {
            throw error;
        }
    }

    // 添加消息到聊天界面
    addMessage(type, content, isStreaming = false, saveToHistory = true) {
        // 检查是否是第一条消息，如果是则移除居中样式
        const isFirstMessage = this.chatMessages && this.chatMessages.querySelectorAll('.message').length === 0;
        
        // 保存消息到当前对话历史（如果不是流式消息且需要保存）
        if (!isStreaming && saveToHistory && content) {
            this.currentChatHistory.push({
                type: type,
                content: content,
                timestamp: new Date().toISOString()
            });
        }
        
        const messageDiv = document.createElement('div');
        messageDiv.className = `message ${type}${isStreaming ? ' streaming' : ''}`;

        // 如果是assistant消息，添加头像图标
        if (type === 'assistant') {
            const messageAvatar = document.createElement('div');
            messageAvatar.className = 'message-avatar';
            messageAvatar.innerHTML = `
                <svg width="20" height="20" viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg">
                    <path d="M12 2L15.09 8.26L22 9.27L17 14.14L18.18 21.02L12 17.77L5.82 21.02L7 14.14L2 9.27L8.91 8.26L12 2Z" fill="white"/>
                </svg>
            `;
            messageDiv.appendChild(messageAvatar);
        }

        // 创建消息内容包装器
        const messageContentWrapper = document.createElement('div');
        messageContentWrapper.className = 'message-content-wrapper';

        const messageContent = document.createElement('div');
        messageContent.className = 'message-content';
        
        // 如果是assistant消息且不是流式消息，使用Markdown渲染
        if (type === 'assistant' && !isStreaming) {
            messageContent.innerHTML = this.renderMarkdown(content);
            // 高亮代码块
            this.highlightCodeBlocks(messageContent);
        } else {
            // 用户消息或流式消息使用纯文本
            messageContent.textContent = content;
        }

        messageContentWrapper.appendChild(messageContent);
        messageDiv.appendChild(messageContentWrapper);

        if (this.chatMessages) {
            this.chatMessages.appendChild(messageDiv);
            
            // 如果是第一条消息，移除居中样式并添加动画
            if (isFirstMessage && this.chatContainer) {
                this.chatContainer.classList.remove('centered');
                // 添加动画类
                this.chatContainer.style.transition = 'all 0.5s ease';
            }
            
            this.scrollToBottom();
        }

        return messageDiv;
    }

    // 添加带加载动画的消息
    addLoadingMessage(content) {
        const messageDiv = document.createElement('div');
        messageDiv.className = 'message assistant';

        // 添加头像图标
        const messageAvatar = document.createElement('div');
        messageAvatar.className = 'message-avatar';
        messageAvatar.innerHTML = `
            <svg width="20" height="20" viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg">
                <path d="M12 2L15.09 8.26L22 9.27L17 14.14L18.18 21.02L12 17.77L5.82 21.02L7 14.14L2 9.27L8.91 8.26L12 2Z" fill="white"/>
            </svg>
        `;
        messageDiv.appendChild(messageAvatar);

        // 创建消息内容包装器
        const messageContentWrapper = document.createElement('div');
        messageContentWrapper.className = 'message-content-wrapper';

        const messageContent = document.createElement('div');
        messageContent.className = 'message-content loading-message-content';
        
        // 创建文本和动画容器
        const textSpan = document.createElement('span');
        textSpan.textContent = content;
        
        // 创建旋转动画图标
        const loadingIcon = document.createElement('span');
        loadingIcon.className = 'loading-spinner-icon';
        loadingIcon.innerHTML = `
            <svg width="16" height="16" viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg">
                <path d="M12 2C6.48 2 2 6.48 2 12s4.48 10 10 10 10-4.48 10-10S17.52 2 12 2zm0 18c-4.41 0-8-3.59-8-8s3.59-8 8-8 8 3.59 8 8-3.59 8-8 8z" fill="currentColor" opacity="0.2"/>
                <path d="M12 2C6.48 2 2 6.48 2 12s4.48 10 10 10c1.54 0 3-.36 4.28-1l-1.5-2.6C13.64 19.62 12.84 20 12 20c-4.41 0-8-3.59-8-8s3.59-8 8-8c.84 0 1.64.38 2.18 1l1.5-2.6C13 2.36 12.54 2 12 2z" fill="currentColor"/>
            </svg>
        `;
        
        messageContent.appendChild(textSpan);
        messageContent.appendChild(loadingIcon);
        messageContentWrapper.appendChild(messageContent);
        messageDiv.appendChild(messageContentWrapper);

        if (this.chatMessages) {
            this.chatMessages.appendChild(messageDiv);
            
            // 如果是第一条消息，移除居中样式
            const isFirstMessage = this.chatMessages.querySelectorAll('.message').length === 1;
            if (isFirstMessage && this.chatContainer) {
                this.chatContainer.classList.remove('centered');
                this.chatContainer.style.transition = 'all 0.5s ease';
            }
            
            this.scrollToBottom();
        }

        return messageDiv;
    }
    
    // 检查并设置居中样式
    checkAndSetCentered() {
        if (this.chatMessages && this.chatContainer) {
            const hasMessages = this.chatMessages.querySelectorAll('.message').length > 0;
            if (!hasMessages) {
                this.chatContainer.classList.add('centered');
            } else {
                this.chatContainer.classList.remove('centered');
            }
        }
    }

    // 滚动到底部
    scrollToBottom() {
        if (this.chatMessages) {
            this.chatMessages.scrollTop = this.chatMessages.scrollHeight;
        }
    }

    // 处理流式传输完成
    handleStreamComplete(assistantMessageElement, fullResponse) {
        if (assistantMessageElement) {
            assistantMessageElement.classList.remove('streaming');
            const messageContent = assistantMessageElement.querySelector('.message-content');
            if (messageContent) {
                messageContent.innerHTML = this.renderMarkdown(fullResponse);
                // 高亮代码块
                this.highlightCodeBlocks(messageContent);
            }
        }
        // 保存流式消息到历史记录
        if (fullResponse) {
            this.currentChatHistory.push({
                type: 'assistant',
                content: fullResponse,
                timestamp: new Date().toISOString()
            });
            this.persistCurrentChatHistory();
        }
    }

    // 自定义确认弹窗（替代原生 confirm，保证风格一致）
    showConfirmDialog(options = {}) {
        const title = options.title || '确认操作';
        const message = options.message || '请确认是否继续';
        const confirmText = options.confirmText || '确认';
        const cancelText = options.cancelText || '取消';

        return new Promise((resolve) => {
            const overlay = document.createElement('div');
            overlay.className = 'confirm-dialog-overlay';
            overlay.innerHTML = `
                <div class="confirm-dialog" role="dialog" aria-modal="true" aria-labelledby="confirmDialogTitle" tabindex="-1">
                    <div class="confirm-dialog-header">
                        <div class="confirm-dialog-icon" aria-hidden="true">
                            <svg viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg">
                                <path d="M12 8V13M12 16.5V17M10.29 3.86L1.82 18A2 2 0 0 0 3.53 21H20.47A2 2 0 0 0 22.18 18L13.71 3.86A2 2 0 0 0 10.29 3.86Z" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"/>
                            </svg>
                        </div>
                        <h3 class="confirm-dialog-title" id="confirmDialogTitle">${this.escapeHtml(title)}</h3>
                    </div>
                    <p class="confirm-dialog-message">${this.escapeHtml(message)}</p>
                    <div class="confirm-dialog-actions">
                        <button type="button" class="confirm-dialog-btn cancel">${this.escapeHtml(cancelText)}</button>
                        <button type="button" class="confirm-dialog-btn danger">${this.escapeHtml(confirmText)}</button>
                    </div>
                </div>
            `;

            document.body.appendChild(overlay);

            const dialog = overlay.querySelector('.confirm-dialog');
            const cancelBtn = overlay.querySelector('.confirm-dialog-btn.cancel');
            const confirmBtn = overlay.querySelector('.confirm-dialog-btn.danger');
            let closed = false;

            const cleanup = (result) => {
                if (closed) {
                    return;
                }
                closed = true;
                document.removeEventListener('keydown', onKeyDown);
                overlay.classList.remove('show');
                window.setTimeout(() => {
                    if (overlay.parentNode) {
                        overlay.parentNode.removeChild(overlay);
                    }
                    resolve(result);
                }, 180);
            };

            const onKeyDown = (event) => {
                if (event.key === 'Escape') {
                    cleanup(false);
                }
            };

            overlay.addEventListener('click', (event) => {
                if (event.target === overlay) {
                    cleanup(false);
                }
            });
            cancelBtn.addEventListener('click', () => cleanup(false));
            confirmBtn.addEventListener('click', () => cleanup(true));
            document.addEventListener('keydown', onKeyDown);

            requestAnimationFrame(() => {
                overlay.classList.add('show');
                if (dialog) {
                    dialog.focus?.();
                }
            });
        });
    }

    // 显示通知
    showNotification(message, type = 'info') {
        const notification = document.createElement('div');
        notification.className = `notification ${type}`;
        notification.textContent = message;
        notification.setAttribute('role', 'status');
        notification.setAttribute('aria-live', 'polite');

        document.body.appendChild(notification);

        setTimeout(() => {
            notification.style.animation = 'notificationOut 0.24s ease';
            setTimeout(() => {
                if (notification.parentNode) {
                    notification.parentNode.removeChild(notification);
                }
            }, 240);
        }, 3000);
    }

    // 处理文件选择
    handleFileSelect(event) {
        const file = event.target.files[0];
        if (file) {
            // 验证文件格式
            if (!this.validateFileType(file)) {
                this.showNotification('只支持上传 TXT 或 Markdown (.md) 格式的文件', 'error');
                this.fileInput.value = '';
                return;
            }
            this.uploadFile(file);
        }
    }

    // 验证文件类型
    validateFileType(file) {
        const fileName = file.name.toLowerCase();
        const allowedExtensions = ['.txt', '.md', '.markdown'];
        return allowedExtensions.some(ext => fileName.endsWith(ext));
    }

    // 上传文件到知识库
    async uploadFile(file) {
        if (!this.isAuthenticated()) {
            this.showAuthOverlay(true);
            this.showNotification('请先登录后再上传文件', 'warning');
            return;
        }

        // 再次验证文件类型（双重保险）
        if (!this.validateFileType(file)) {
            this.showNotification('只支持上传 TXT 或 Markdown (.md) 格式的文件', 'error');
            return;
        }

        // 验证文件大小（限制为50MB）
        const maxSize = 50 * 1024 * 1024;
        if (file.size > maxSize) {
            this.showNotification('文件大小不能超过50MB', 'error');
            return;
        }

        // 锁定前端并显示上传遮罩层
        this.isStreaming = true;
        this.updateUI();
        this.showUploadOverlay(true, file.name);

        try {
            // 创建 FormData
            const formData = new FormData();
            formData.append('file', file);

            // 发送上传请求
            const response = await this.authFetch(`${this.apiBaseUrl}/upload`, {
                method: 'POST',
                body: formData
            });

            if (!response.ok) {
                throw new Error(`HTTP错误: ${response.status}`);
            }

            const data = await response.json();

            if ((data.code === 200 || data.message === 'success') && data.data) {
                // 在聊天界面显示上传成功消息
                const successMessage = `${file.name} 上传到知识库成功`;
                this.addMessage('assistant', successMessage, false, true);
            } else {
                throw new Error(data.message || '上传失败');
            }
        } catch (error) {
            console.error('文件上传失败:', error);
            this.showNotification('文件上传失败: ' + error.message, 'error');
        } finally {
            // 清空文件输入
            if (this.fileInput) {
                this.fileInput.value = '';
            }
            // 解锁前端
            this.isStreaming = false;
            this.showUploadOverlay(false);
            this.updateUI();
        }
    }

    // 格式化文件大小
    formatFileSize(bytes) {
        if (bytes === 0) return '0 Bytes';
        const k = 1024;
        const sizes = ['Bytes', 'KB', 'MB', 'GB'];
        const i = Math.floor(Math.log(bytes) / Math.log(k));
        return Math.round(bytes / Math.pow(k, i) * 100) / 100 + ' ' + sizes[i];
    }

    // 发送智能运维请求（SSE 流式模式）
    async sendAIOpsRequest(loadingMessageElement) {
        try {
            const response = await this.authFetch(`${this.apiBaseUrl}/ai_ops`, {
                method: 'POST',
                headers: {
                    'Content-Type': 'application/json',
                },
                // 绑定当前会话ID，确保 AI Ops 输出可并入同一会话上下文
                body: JSON.stringify({
                    Id: this.sessionId
                })
            });

            if (!response.ok) {
                throw new Error(`HTTP错误: ${response.status}`);
            }

            let fullResponse = '';

            // 处理 SSE 流式响应
            const reader = response.body.getReader();
            const decoder = new TextDecoder();
            let buffer = '';
            let currentEvent = 'message'; // 默认事件类型为 message

            try {
                while (true) {
                    const { done, value } = await reader.read();
                    
                    if (done) {
                        // 流结束，更新最终内容
                        if (fullResponse) {
                            console.log('AI Ops 流结束，更新最终内容，长度:', fullResponse.length);
                            this.updateAIOpsMessage(loadingMessageElement, fullResponse, []);
                        }
                        break;
                    }

                    // 解码数据并添加到缓冲区
                    buffer += decoder.decode(value, { stream: true });
                    
                    // 按行分割处理
                    const lines = buffer.split('\n');
                    // 保留最后一行（可能不完整）
                    buffer = lines.pop() || '';
                    
                    for (const line of lines) {
                        if (line.trim() === '') continue;
                        
                        console.log('[AI Ops SSE] 收到行:', line);
                        
                        // 解析 SSE 格式
                        if (line.startsWith('id:')) {
                            continue;
                        } else if (line.startsWith('event:')) {
                            currentEvent = line.substring(6).trim();
                            console.log('[AI Ops SSE] 事件类型:', currentEvent);
                            continue;
                        } else if (line.startsWith('data:')) {
                            // 不要 trim，避免丢失 Markdown 关键换行
                            let rawData = line.substring(5);
                            // 兼容 "data: xxx" 与 "data:xxx"
                            if (rawData.startsWith(' ')) {
                                rawData = rawData.substring(1);
                            }
                            console.log('[AI Ops SSE] 数据:', rawData, ', currentEvent:', currentEvent);
                            
                            // 解析可能包含多个JSON对象的数据
                            const processJsonMessages = (data) => {
                                const jsonPattern = /\{"type"\s*:\s*"[^"]+"\s*,\s*"data"\s*:\s*(?:"[^"]*"|null)\}/g;
                                const matches = data.match(jsonPattern);
                                
                                if (matches && matches.length > 0) {
                                    console.log('[AI Ops SSE] 匹配到', matches.length, '个JSON对象');
                                    for (const jsonStr of matches) {
                                        try {
                                            const sseMessage = JSON.parse(jsonStr);
                                            if (sseMessage.type === 'content') {
                                                fullResponse += sseMessage.data || '';
                                            } else if (sseMessage.type === 'progress') {
                                                this.updateAIOpsProgress(loadingMessageElement, sseMessage.data || '');
                                            } else if (sseMessage.type === 'done') {
                                                console.log('AI Ops 流完成，最终内容长度:', fullResponse.length);
                                                this.updateAIOpsMessage(loadingMessageElement, fullResponse, []);
                                                return true;
                                            } else if (sseMessage.type === 'error') {
                                                throw new Error(sseMessage.data || '智能运维分析失败');
                                            }
                                        } catch (e) {
                                            if (e.message.includes('智能运维')) throw e;
                                            console.log('[AI Ops SSE] 单个JSON解析失败:', jsonStr);
                                        }
                                    }
                                    if (loadingMessageElement) {
                                        this.updateAIOpsStreamContent(loadingMessageElement, fullResponse);
                                    }
                                    return false;
                                }
                                return null;
                            };
                            
                            const result = processJsonMessages(rawData);
                            if (result === true) {
                                return; // 流结束
                            } else if (result === null) {
                                // 没有匹配到多个JSON，尝试单个JSON解析
                                try {
                                    const sseMessage = JSON.parse(rawData);
                                    if (sseMessage && sseMessage.type) {
                                        if (sseMessage.type === 'content') {
                                            fullResponse += sseMessage.data || '';
                                            if (loadingMessageElement) {
                                                this.updateAIOpsStreamContent(loadingMessageElement, fullResponse);
                                            }
                                        } else if (sseMessage.type === 'progress') {
                                            this.updateAIOpsProgress(loadingMessageElement, sseMessage.data || '');
                                        } else if (sseMessage.type === 'done') {
                                            console.log('AI Ops 流完成，最终内容长度:', fullResponse.length);
                                            this.updateAIOpsMessage(loadingMessageElement, fullResponse, []);
                                            return;
                                        } else if (sseMessage.type === 'error') {
                                            throw new Error(sseMessage.data || '智能运维分析失败');
                                        }
                                    } else {
                                        fullResponse += rawData;
                                        if (loadingMessageElement) {
                                            this.updateAIOpsStreamContent(loadingMessageElement, fullResponse);
                                        }
                                    }
                                } catch (e) {
                                    if (e.message.includes('智能运维')) throw e;
                                    // 非 JSON 格式，直接追加原始数据
                                    fullResponse += rawData;
                                    if (loadingMessageElement) {
                                        this.updateAIOpsStreamContent(loadingMessageElement, fullResponse);
                                    }
                                }
                            }
                        }
                    }
                }
            } finally {
                reader.releaseLock();
            }
        } catch (error) {
            throw error;
        }
    }

    // 更新智能运维流式内容（实时显示）
    updateAIOpsStreamContent(messageElement, content) {
        if (!messageElement) return;
        
        // 添加 aiops-message 类
        messageElement.classList.add('aiops-message');
        
        const messageContentWrapper = messageElement.querySelector('.message-content-wrapper');
        if (messageContentWrapper) {
            let messageContent = messageContentWrapper.querySelector('.message-content');
            if (!messageContent) {
                messageContent = document.createElement('div');
                messageContent.className = 'message-content';
                messageContentWrapper.appendChild(messageContent);
            }
            // 进入真正流式内容后，移除加载态布局，避免 flex 导致 Markdown 在一行显示
            messageContent.classList.remove('loading-message-content');
            const loadingIcon = messageContent.querySelector('.loading-spinner-icon');
            if (loadingIcon) {
                loadingIcon.remove();
            }
            // 流式阶段也进行 Markdown 渲染，避免用户长时间看到不可读的纯文本
            const normalizedContent = this.normalizeAIOpsStreamingMarkdown(content);
            messageContent.innerHTML = this.renderMarkdown(normalizedContent);
            this.highlightCodeBlocks(messageContent);
            this.scrollToBottom();
        }
    }

    // 归一化 AIOps 流式 Markdown（仅用于前端预览，不修改后端原始内容）
    normalizeAIOpsStreamingMarkdown(content) {
        if (!content) return '';

        let normalized = content.replace(/\r\n/g, '\n');
        // 标题前缺少换行时，补齐为块级结构
        normalized = normalized.replace(/([^\n])\s+(#{1,6}\s)/g, '$1\n\n$2');
        // 常见无序列表（- **xxx**）前缺少换行时补齐
        normalized = normalized.replace(/([^\n])\s+(-\s+\*\*)/g, '$1\n$2');
        // 常见有序列表前缺少换行时补齐
        normalized = normalized.replace(/([^\n])\s+(\d+\.\s+)/g, '$1\n$2');

        return normalized;
    }

    // 更新 AIOps 阶段进度（与正文分离，避免破坏 Markdown 格式）
    updateAIOpsProgress(messageElement, progressText) {
        if (!messageElement || !progressText) return;

        const messageContentWrapper = messageElement.querySelector('.message-content-wrapper');
        if (!messageContentWrapper) return;

        let progressDiv = messageContentWrapper.querySelector('.aiops-progress');
        if (!progressDiv) {
            progressDiv = document.createElement('div');
            progressDiv.className = 'aiops-progress';
            progressDiv.style.cssText = 'font-size:12px;color:#5f6368;margin-bottom:8px;';
            messageContentWrapper.insertBefore(progressDiv, messageContentWrapper.firstChild);
        }

        progressDiv.textContent = progressText;
    }

    // 更新智能运维消息（带折叠详情）
    updateAIOpsMessage(messageElement, response, details) {
        console.log('updateAIOpsMessage 被调用');
        console.log('messageElement:', messageElement);
        console.log('response:', response);
        console.log('response length:', response ? response.length : 0);
        console.log('details:', details);
        
        if (!messageElement) {
            // 如果没有传入消息元素，则创建新消息
            console.log('messageElement 为空，创建新消息');
            return this.addAIOpsMessage(response, details);
        }

        // 添加aiops-message类
        messageElement.classList.add('aiops-message');

        // 获取消息内容包装器
        const messageContentWrapper = messageElement.querySelector('.message-content-wrapper');
        if (!messageContentWrapper) {
            console.error('未找到 message-content-wrapper');
            return;
        }

        // 清空现有内容（保留消息内容容器）
        const messageContent = messageContentWrapper.querySelector('.message-content');
        if (!messageContent) {
            console.error('未找到 message-content');
            return;
        }

        // 移除加载动画相关的类和内容
        messageContent.classList.remove('loading-message-content');
        messageContent.textContent = '';
        
        // 移除加载图标（如果存在）
        const loadingIcon = messageContent.querySelector('.loading-spinner-icon');
        if (loadingIcon) {
            loadingIcon.remove();
        }

        // 详情部分（可折叠）- 先显示
        if (details && details.length > 0) {
            // 检查是否已存在详情容器
            let detailsContainer = messageElement.querySelector('.aiops-details');
            if (!detailsContainer) {
                detailsContainer = document.createElement('div');
                detailsContainer.className = 'aiops-details';
                messageContentWrapper.insertBefore(detailsContainer, messageContent);
            } else {
                // 清空现有详情
                detailsContainer.innerHTML = '';
            }

            const detailsToggle = document.createElement('div');
            detailsToggle.className = 'details-toggle';
            detailsToggle.innerHTML = `
                <svg class="toggle-icon" viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg">
                    <path d="M9 18L15 12L9 6" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"/>
                </svg>
                <span>查看详细步骤 (${details.length}条)</span>
            `;

            const detailsContent = document.createElement('div');
            detailsContent.className = 'details-content';
            
            details.forEach((detail, index) => {
                const detailItem = document.createElement('div');
                detailItem.className = 'detail-item';
                detailItem.innerHTML = `<strong>步骤 ${index + 1}:</strong> ${this.escapeHtml(detail)}`;
                detailsContent.appendChild(detailItem);
            });

            // 点击切换折叠状态
            detailsToggle.addEventListener('click', () => {
                detailsContent.classList.toggle('expanded');
                detailsToggle.classList.toggle('expanded');
            });

            detailsContainer.appendChild(detailsToggle);
            detailsContainer.appendChild(detailsContent);
        }

        // 更新主要响应内容（使用Markdown渲染）
        console.log('开始渲染 Markdown');
        const renderedHtml = this.renderMarkdown(response);
        console.log('Markdown 渲染完成，HTML 长度:', renderedHtml ? renderedHtml.length : 0);
        messageContent.innerHTML = renderedHtml;
        console.log('innerHTML 已设置');
        // 高亮代码块
        this.highlightCodeBlocks(messageContent);
        console.log('代码块高亮完成');
        
        // 保存到历史记录
        this.currentChatHistory.push({
            type: 'assistant',
            content: response,
            timestamp: new Date().toISOString()
        });
        this.persistCurrentChatHistory();
        
        this.scrollToBottom();
        return messageElement;
    }

    // 添加智能运维消息（带折叠详情）- 保留用于兼容性
    addAIOpsMessage(response, details) {
        const messageDiv = document.createElement('div');
        messageDiv.className = 'message assistant aiops-message';

        // 添加头像图标
        const messageAvatar = document.createElement('div');
        messageAvatar.className = 'message-avatar';
        messageAvatar.innerHTML = `
            <svg width="20" height="20" viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg">
                <path d="M12 2L15.09 8.26L22 9.27L17 14.14L18.18 21.02L12 17.77L5.82 21.02L7 14.14L2 9.27L8.91 8.26L12 2Z" fill="white"/>
            </svg>
        `;
        messageDiv.appendChild(messageAvatar);

        // 创建消息内容包装器
        const messageContentWrapper = document.createElement('div');
        messageContentWrapper.className = 'message-content-wrapper';

        // 详情部分（可折叠）- 先显示
        if (details && details.length > 0) {
            const detailsContainer = document.createElement('div');
            detailsContainer.className = 'aiops-details';

            const detailsToggle = document.createElement('div');
            detailsToggle.className = 'details-toggle';
            detailsToggle.innerHTML = `
                <svg class="toggle-icon" viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg">
                    <path d="M9 18L15 12L9 6" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"/>
                </svg>
                <span>查看详细步骤 (${details.length}条)</span>
            `;

            const detailsContent = document.createElement('div');
            detailsContent.className = 'details-content';
            
            details.forEach((detail, index) => {
                const detailItem = document.createElement('div');
                detailItem.className = 'detail-item';
                detailItem.innerHTML = `<strong>步骤 ${index + 1}:</strong> ${this.escapeHtml(detail)}`;
                detailsContent.appendChild(detailItem);
            });

            // 点击切换折叠状态
            detailsToggle.addEventListener('click', () => {
                detailsContent.classList.toggle('expanded');
                detailsToggle.classList.toggle('expanded');
            });

            detailsContainer.appendChild(detailsToggle);
            detailsContainer.appendChild(detailsContent);
            messageContentWrapper.appendChild(detailsContainer);
        }

        // 主要响应内容 - 后显示（使用Markdown渲染）
        const messageContent = document.createElement('div');
        messageContent.className = 'message-content';
        messageContent.innerHTML = this.renderMarkdown(response);
        // 高亮代码块
        this.highlightCodeBlocks(messageContent);
        messageContentWrapper.appendChild(messageContent);
        messageDiv.appendChild(messageContentWrapper);
        
        if (this.chatMessages) {
            this.chatMessages.appendChild(messageDiv);
            this.scrollToBottom();
        }

        return messageDiv;
    }

    // HTML转义
    escapeHtml(text) {
        const div = document.createElement('div');
        div.textContent = text;
        return div.innerHTML;
    }

    // 触发智能运维（点击智能运维按钮时直接调用）
    async triggerAIOps() {
        if (!this.isAuthenticated()) {
            this.showAuthOverlay(true);
            this.showNotification('请先登录后再使用 AI Ops', 'warning');
            return;
        }

        if (this.isStreaming) {
            this.showNotification('请等待当前操作完成', 'warning');
            return;
        }

        // 新建对话
        this.newChat();
        
        // 添加"分析中..."的消息（带旋转动画）
        const loadingMessage = this.addLoadingMessage('分析中...');
        this.currentAIOpsMessage = loadingMessage; // 保存消息引用用于后续更新
        
        // 设置发送状态
        this.isStreaming = true;
        this.updateUI();

        try {
            await this.sendAIOpsRequest(loadingMessage);
        } catch (error) {
            console.error('智能运维分析失败:', error);
            // 更新消息为错误信息
            if (loadingMessage) {
                const messageContent = loadingMessage.querySelector('.message-content');
                if (messageContent) {
                    messageContent.textContent = '抱歉，智能运维分析时出现错误：' + error.message;
                }
            }
        } finally {
            this.isStreaming = false;
            this.currentAIOpsMessage = null;
            this.updateUI();
        }
    }

    // 显示/隐藏加载遮罩层
    showLoadingOverlay(show) {
        if (this.loadingOverlay) {
            if (show) {
                this.loadingOverlay.style.display = 'flex';
                // 更新文字为智能运维
                const loadingText = this.loadingOverlay.querySelector('.loading-text');
                const loadingSubtext = this.loadingOverlay.querySelector('.loading-subtext');
                if (loadingText) loadingText.textContent = '智能运维分析中，请稍候...';
                if (loadingSubtext) loadingSubtext.textContent = '后端正在处理，请耐心等待';
                // 防止页面滚动
                document.body.style.overflow = 'hidden';
            } else {
                this.loadingOverlay.style.display = 'none';
                // 恢复页面滚动
                document.body.style.overflow = '';
            }
        }
    }

    // 显示/隐藏上传遮罩层
    showUploadOverlay(show, fileName = '') {
        if (this.loadingOverlay) {
            if (show) {
                this.loadingOverlay.style.display = 'flex';
                // 更新文字为上传中
                const loadingText = this.loadingOverlay.querySelector('.loading-text');
                const loadingSubtext = this.loadingOverlay.querySelector('.loading-subtext');
                if (loadingText) loadingText.textContent = '正在上传文件...';
                if (loadingSubtext) loadingSubtext.textContent = fileName ? `上传: ${fileName}` : '请稍候';
                // 防止页面滚动
                document.body.style.overflow = 'hidden';
            } else {
                this.loadingOverlay.style.display = 'none';
                // 恢复页面滚动
                document.body.style.overflow = '';
            }
        }
    }
}

// 添加CSS动画
const style = document.createElement('style');
style.textContent = `
    @keyframes slideIn {
        from {
            transform: translateX(100%);
            opacity: 0;
        }
        to {
            transform: translateX(0);
            opacity: 1;
        }
    }
    
    @keyframes slideOut {
        from {
            transform: translateX(0);
            opacity: 1;
        }
        to {
            transform: translateX(100%);
            opacity: 0;
        }
    }
`;
document.head.appendChild(style);

// 初始化应用
document.addEventListener('DOMContentLoaded', () => {
    new SuperBizAgentApp();
});
