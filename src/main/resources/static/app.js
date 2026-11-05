const state = {
    sessions: [],
    skills: [],
    currentSessionId: null,
    loading: false,
    mode: 'SKILL',
    agentAvailable: false
};

const elements = {
    sidebar: document.querySelector('#sidebar'),
    sidebarScrim: document.querySelector('#sidebarScrim'),
    openSidebar: document.querySelector('#openSidebar'),
    closeSidebar: document.querySelector('#closeSidebar'),
    newChat: document.querySelector('#newChat'),
    sessionList: document.querySelector('#sessionList'),
    modelDot: document.querySelector('#modelDot'),
    modelStatus: document.querySelector('#modelStatus'),
    skillSelect: document.querySelector('#skillSelect'),
    conversation: document.querySelector('#conversation'),
    emptyState: document.querySelector('#emptyState'),
    messageList: document.querySelector('#messageList'),
    composer: document.querySelector('#composer'),
    input: document.querySelector('#messageInput'),
    sendButton: document.querySelector('#sendButton'),
    errorBanner: document.querySelector('#errorBanner')
    ,skillMode: document.querySelector('#skillMode')
    ,agentMode: document.querySelector('#agentMode')
    ,agentOptions: document.querySelector('#agentOptions')
    ,repoUrl: document.querySelector('#repoUrl')
    ,startingRef: document.querySelector('#startingRef')
    ,autoCreatePr: document.querySelector('#autoCreatePr')
};

async function request(url, options = {}) {
    const response = await fetch(url, {
        headers: {'Content-Type': 'application/json', ...(options.headers || {})},
        ...options
    });
    if (!response.ok) {
        let message = `请求失败 (${response.status})`;
        try {
            const payload = await response.json();
            message = payload.message || message;
        } catch (_) {
            // Keep the HTTP status message when the response is not JSON.
        }
        throw new Error(message);
    }
    return response.status === 204 ? null : response.json();
}

async function bootstrap() {
    try {
        const payload = await request('/api/bootstrap');
        state.sessions = payload.sessions || [];
        state.skills = payload.skills || [];
        state.agentAvailable = Boolean(payload.agentAvailable);
        renderSessions();
        renderSkills();
        elements.modelDot.className = `status-dot ${payload.modelConfigured ? 'ready' : 'error'}`;
        elements.modelStatus.textContent = payload.modelConfigured ? '模型已连接' : '模型未配置';
        elements.agentMode.disabled = !state.agentAvailable;
        elements.agentMode.title = state.agentAvailable
            ? '交给 Cursor Cloud Agent 执行研发任务'
            : '服务端尚未配置 CURSOR_API_KEY';
    } catch (error) {
        elements.modelDot.className = 'status-dot error';
        elements.modelStatus.textContent = '服务不可用';
        showError(error.message);
    }
}

function renderSkills() {
    const currentValue = elements.skillSelect.value;
    elements.skillSelect.replaceChildren(new Option('自动匹配', ''));
    state.skills.forEach(skill => {
        elements.skillSelect.add(new Option(skill.name, skill.id));
    });
    if (state.skills.some(skill => skill.id === currentValue)) {
        elements.skillSelect.value = currentValue;
    }
}

function renderSessions() {
    elements.sessionList.replaceChildren();
    state.sessions.forEach(session => {
        const wrapper = document.createElement('div');
        wrapper.className = `session-item${session.id === state.currentSessionId ? ' active' : ''}`;

        const openButton = document.createElement('button');
        openButton.type = 'button';
        openButton.className = 'session-open';
        openButton.textContent = session.title;
        openButton.title = session.title;
        openButton.addEventListener('click', () => openSession(session.id));

        const deleteButton = document.createElement('button');
        deleteButton.type = 'button';
        deleteButton.className = 'session-delete';
        deleteButton.textContent = '×';
        deleteButton.title = '删除会话';
        deleteButton.setAttribute('aria-label', `删除 ${session.title}`);
        deleteButton.addEventListener('click', () => deleteSession(session.id));

        wrapper.append(openButton, deleteButton);
        elements.sessionList.append(wrapper);
    });
}

async function openSession(sessionId) {
    if (state.loading) return;
    hideError();
    try {
        const messages = await request(`/api/chat/sessions/${sessionId}/messages`);
        state.currentSessionId = sessionId;
        elements.messageList.replaceChildren();
        messages.forEach(message => appendMessage(message.role, message.content, skillName(message.capabilityId)));
        updateConversationState();
        renderSessions();
        closeSidebar();
        scrollToBottom();
    } catch (error) {
        showError(error.message);
    }
}

async function deleteSession(sessionId) {
    if (!window.confirm('确定删除这条会话吗？')) return;
    try {
        await request(`/api/chat/sessions/${sessionId}`, {method: 'DELETE'});
        state.sessions = state.sessions.filter(session => session.id !== sessionId);
        if (state.currentSessionId === sessionId) {
            startNewChat();
        } else {
            renderSessions();
        }
    } catch (error) {
        showError(error.message);
    }
}

function startNewChat() {
    state.currentSessionId = null;
    elements.messageList.replaceChildren();
    elements.skillSelect.value = '';
    elements.input.value = '';
    resizeInput();
    updateConversationState();
    renderSessions();
    hideError();
    closeSidebar();
    elements.input.focus();
}

async function sendMessage(text) {
    const message = text.trim();
    if (!message || state.loading) return;

    state.loading = true;
    elements.sendButton.disabled = true;
    hideError();
    appendMessage('user', message);
    const typingNode = appendTyping();
    elements.input.value = '';
    resizeInput();
    updateConversationState();
    scrollToBottom();

    try {
        if (state.mode === 'AGENT') {
            typingNode.remove();
            await sendAgentTask(message);
            return;
        }
        const reply = await request('/api/chat/messages', {
            method: 'POST',
            body: JSON.stringify({
                sessionId: state.currentSessionId,
                message,
                mode: 'SKILL',
                capabilityId: elements.skillSelect.value || null
            })
        });
        typingNode.remove();
        state.currentSessionId = reply.sessionId;
        appendMessage('assistant', reply.content, reply.capabilityName);
        await refreshSessions();
        scrollToBottom();
    } catch (error) {
        typingNode.remove();
        showError(error.message);
    } finally {
        state.loading = false;
        elements.sendButton.disabled = false;
        elements.input.focus();
    }
}

async function sendAgentTask(message) {
    const task = await request('/api/agent/tasks', {
        method: 'POST',
        body: JSON.stringify({
            sessionId: state.currentSessionId,
            message,
            repoUrl: elements.repoUrl.value.trim() || null,
            startingRef: elements.startingRef.value.trim() || null,
            autoCreatePr: elements.autoCreatePr.checked
        })
    });
    appendMessage('assistant', `已提交 Cursor Cloud Agent 任务。\n状态：${task.status}\nRun：${task.runId}`, 'Cursor Agent');
    await pollAgentTask(task.agentId, task.runId);
    await refreshSessions();
    scrollToBottom();
}

async function pollAgentTask(agentId, runId) {
    const terminalStatuses = new Set(['FINISHED', 'ERROR', 'CANCELLED', 'EXPIRED']);
    for (let attempt = 0; attempt < 180; attempt += 1) {
        await new Promise(resolve => window.setTimeout(resolve, attempt === 0 ? 1800 : 4000));
        const task = await request(`/api/agent/tasks/${encodeURIComponent(agentId)}/runs/${encodeURIComponent(runId)}`);
        if (!terminalStatuses.has(task.status)) continue;

        const lines = [`Cursor Cloud Agent 已结束：${task.status}`];
        if (task.result) lines.push(task.result);
        if (task.branch) lines.push(`分支：${task.branch}`);
        if (task.pullRequestUrl) lines.push(`Pull Request：${task.pullRequestUrl}`);
        if (task.agentUrl) lines.push(`Agent：${task.agentUrl}`);
        appendMessage('assistant', lines.join('\n'), 'Cursor Agent');
        return task;
    }
    throw new Error('任务运行时间较长，请稍后重新打开页面查看 Cursor Agent 状态。');
}

async function refreshSessions() {
    try {
        const payload = await request('/api/bootstrap');
        state.sessions = payload.sessions || [];
        renderSessions();
    } catch (_) {
        // A completed reply remains usable even if refreshing the sidebar fails.
    }
}

function appendMessage(role, content, capabilityName = '') {
    const article = document.createElement('article');
    article.className = `message ${role}`;

    const avatar = document.createElement('div');
    avatar.className = 'message-avatar';
    avatar.textContent = role === 'assistant' ? 'L' : '你';

    const body = document.createElement('div');
    const head = document.createElement('div');
    head.className = 'message-head';

    const roleName = document.createElement('span');
    roleName.className = 'message-role';
    roleName.textContent = role === 'assistant' ? 'LDD' : '你';
    head.append(roleName);

    if (role === 'assistant' && capabilityName) {
        const badge = document.createElement('span');
        badge.className = 'skill-badge';
        badge.textContent = capabilityName;
        head.append(badge);
    }

    const contentNode = document.createElement('div');
    contentNode.className = 'message-content';
    contentNode.textContent = content;
    body.append(head, contentNode);
    article.append(avatar, body);
    elements.messageList.append(article);
    return article;
}

function appendTyping() {
    const article = document.createElement('article');
    article.className = 'message assistant';
    const avatar = document.createElement('div');
    avatar.className = 'message-avatar';
    avatar.textContent = 'L';
    const dots = document.createElement('div');
    dots.className = 'typing-dots';
    dots.append(document.createElement('span'), document.createElement('span'), document.createElement('span'));
    article.append(avatar, dots);
    elements.messageList.append(article);
    return article;
}

function updateConversationState() {
    const isEmpty = elements.messageList.childElementCount === 0;
    elements.emptyState.hidden = !isEmpty;
    elements.messageList.hidden = isEmpty;
}

function skillName(id) {
    return state.skills.find(skill => skill.id === id)?.name || id || '';
}

function showError(message) {
    elements.errorBanner.textContent = message;
    elements.errorBanner.hidden = false;
}

function hideError() {
    elements.errorBanner.hidden = true;
    elements.errorBanner.textContent = '';
}

function resizeInput() {
    elements.input.style.height = 'auto';
    elements.input.style.height = `${Math.min(elements.input.scrollHeight, 180)}px`;
}

function scrollToBottom() {
    requestAnimationFrame(() => {
        elements.conversation.scrollTop = elements.conversation.scrollHeight;
    });
}

function openSidebar() {
    elements.sidebar.classList.add('open');
    elements.sidebarScrim.classList.add('open');
}

function closeSidebar() {
    elements.sidebar.classList.remove('open');
    elements.sidebarScrim.classList.remove('open');
}

function setMode(mode) {
    if (mode === 'AGENT' && !state.agentAvailable) {
        showError('Cursor Agent 尚未配置，请在服务端设置 CURSOR_API_KEY。');
        return;
    }
    state.mode = mode;
    const agent = mode === 'AGENT';
    elements.skillMode.classList.toggle('active', !agent);
    elements.agentMode.classList.toggle('active', agent);
    elements.skillMode.setAttribute('aria-pressed', String(!agent));
    elements.agentMode.setAttribute('aria-pressed', String(agent));
    elements.skillSelect.disabled = agent;
    elements.agentOptions.hidden = !agent;
    hideError();
    elements.input.placeholder = agent ? '描述需要 Cursor 在仓库中完成的研发任务' : '输入消息';
    elements.input.focus();
}

elements.composer.addEventListener('submit', event => {
    event.preventDefault();
    sendMessage(elements.input.value);
});

elements.input.addEventListener('input', resizeInput);
elements.input.addEventListener('keydown', event => {
    if (event.key === 'Enter' && !event.shiftKey && !event.isComposing) {
        event.preventDefault();
        elements.composer.requestSubmit();
    }
});

elements.newChat.addEventListener('click', startNewChat);
elements.openSidebar.addEventListener('click', openSidebar);
elements.closeSidebar.addEventListener('click', closeSidebar);
elements.sidebarScrim.addEventListener('click', closeSidebar);
elements.skillMode.addEventListener('click', () => setMode('SKILL'));
elements.agentMode.addEventListener('click', () => setMode('AGENT'));
document.querySelectorAll('.prompt-chip').forEach(button => {
    button.addEventListener('click', () => {
        elements.input.value = button.textContent;
        resizeInput();
        elements.input.focus();
    });
});

updateConversationState();
bootstrap();
