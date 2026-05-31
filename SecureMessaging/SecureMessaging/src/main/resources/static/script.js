// =============================================================================
// SecureChat — script.js
// Handles both index.html (login/register) and chat.html (main chat)
// Backend: http://localhost:8082
// =============================================================================

// ─────────────────────────────────────────────────────────────────────────────
// AUTH HELPERS  (global — used by both pages)
// ─────────────────────────────────────────────────────────────────────────────
const API_BASE = 'http://localhost:8082';

// sessionStorage is tab-isolated — each browser tab gets its own login session.
// This means you can open two tabs and log in as two different users simultaneously.
// (localStorage is shared across all tabs of the same origin, which caused the conflict.)
function getToken()    { return sessionStorage.getItem('secureChatToken'); }
function getUsername() { return sessionStorage.getItem('secureChatUser'); }

function authHeaders() {
    return {
        'Content-Type': 'application/json',
        'Authorization': `Bearer ${getToken()}`
    };
}

function authHeadersMultipart() {
    return { 'Authorization': `Bearer ${getToken()}` };
}

function handleLogout() {
    sessionStorage.removeItem('secureChatToken');
    sessionStorage.removeItem('secureChatUser');
    window.location.href = 'index.html';
}

// =============================================================================
// INDEX PAGE  (index.html) — Login / Register
// =============================================================================
(function initIndexPage() {
    const path = window.location.pathname;
    const isIndex = path.endsWith('index.html') ||
                    path.endsWith('/') ||
                    path === '' ||
                    path.endsWith('/static/');

    if (!isIndex && !document.getElementById('loginForm')) return;
    if (!document.getElementById('loginForm')) return;

    // If already authenticated, go straight to chat
    if (getToken()) {
        window.location.href = 'chat.html';
        return;
    }

    // ── DOM refs ──────────────────────────────────────────────────────────
    const loginTab     = document.getElementById('loginTab');
    const registerTab  = document.getElementById('registerTab');
    const loginForm    = document.getElementById('loginForm');
    const registerForm = document.getElementById('registerForm');
    const tabIndicator = document.getElementById('tabIndicator');
    const loginMsg     = document.getElementById('loginMessage');
    const registerMsg  = document.getElementById('registerMessage');

    // ── Helpers ───────────────────────────────────────────────────────────
    function showMsg(el, text, type) {
        el.textContent = text;
        el.className = `auth-message ${type}`;
    }

    function clearMessages() {
        loginMsg.textContent    = '';
        loginMsg.className      = 'auth-message';
        registerMsg.textContent = '';
        registerMsg.className   = 'auth-message';
    }

    // ── Tab switching ─────────────────────────────────────────────────────
    function showLoginTab() {
        loginTab.classList.add('active');
        registerTab.classList.remove('active');
        loginForm.classList.add('active-form');
        registerForm.classList.remove('active-form');
        tabIndicator.style.left = '3px';
        loginTab.setAttribute('aria-selected', 'true');
        registerTab.setAttribute('aria-selected', 'false');
        clearMessages();
    }

    function showRegisterTab() {
        registerTab.classList.add('active');
        loginTab.classList.remove('active');
        registerForm.classList.add('active-form');
        loginForm.classList.remove('active-form');
        tabIndicator.style.left = 'calc(50% + 1px)';
        registerTab.setAttribute('aria-selected', 'true');
        loginTab.setAttribute('aria-selected', 'false');
        clearMessages();
    }

    loginTab.addEventListener('click', showLoginTab);
    registerTab.addEventListener('click', showRegisterTab);

    // ── Password visibility toggles ───────────────────────────────────────
    function setupPasswordToggle(toggleBtnId, inputId) {
        const btn   = document.getElementById(toggleBtnId);
        const input = document.getElementById(inputId);
        if (!btn || !input) return;
        btn.addEventListener('click', () => {
            if (input.type === 'password') {
                input.type    = 'text';
                btn.textContent = '🙈';
            } else {
                input.type    = 'password';
                btn.textContent = '👁';
            }
        });
    }

    setupPasswordToggle('toggleLoginPwd', 'loginPassword');
    setupPasswordToggle('toggleRegPwd',   'registerPassword');

    // ── Login form submit ─────────────────────────────────────────────────
    loginForm.addEventListener('submit', async (e) => {
        e.preventDefault();
        const username = document.getElementById('loginUsername').value.trim();
        const password = document.getElementById('loginPassword').value;
        if (!username || !password) {
            showMsg(loginMsg, 'Please fill in all fields.', 'error');
            return;
        }
        try {
            const res = await fetch(`${API_BASE}/auth/login`, {
                method:  'POST',
                headers: { 'Content-Type': 'application/json' },
                body:    JSON.stringify({ username, password })
            });
            if (res.ok) {
                const data = await res.json();
                sessionStorage.setItem('secureChatToken', data.token);
                sessionStorage.setItem('secureChatUser',  data.username || username);
                showMsg(loginMsg, 'Login successful! Redirecting…', 'success');
                setTimeout(() => { window.location.href = 'chat.html'; }, 800);
            } else {
                const errText = await res.text();
                showMsg(loginMsg, errText || 'Login failed. Check your credentials.', 'error');
            }
        } catch {
            showMsg(loginMsg, 'Network error. Is the server running?', 'error');
        }
    });

    // ── Register form submit ──────────────────────────────────────────────
    registerForm.addEventListener('submit', async (e) => {
        e.preventDefault();
        const username = document.getElementById('registerUsername').value.trim();
        const password = document.getElementById('registerPassword').value;
        if (!username || !password) {
            showMsg(registerMsg, 'Please fill in all fields.', 'error');
            return;
        }
        try {
            const res = await fetch(`${API_BASE}/auth/register`, {
                method:  'POST',
                headers: { 'Content-Type': 'application/json' },
                body:    JSON.stringify({ username, password })
            });
            if (res.ok) {
                showMsg(registerMsg, 'Account created! Switching to login…', 'success');
                setTimeout(() => showLoginTab(), 1500);
            } else {
                const errText = await res.text();
                showMsg(registerMsg, errText || 'Registration failed.', 'error');
            }
        } catch {
            showMsg(registerMsg, 'Network error. Is the server running?', 'error');
        }
    });
})();

// =============================================================================
// CHAT PAGE  (chat.html) — Main Chat
// =============================================================================
(function initChatPage() {
    if (!document.getElementById('messagesArea')) return;

    // ── Auth guard ────────────────────────────────────────────────────────
    const currentUser = getUsername();
    if (!currentUser || !getToken()) {
        window.location.href = 'index.html';
        return;
    }

    // ── DOM refs ──────────────────────────────────────────────────────────
    const loggedInUserEl  = document.getElementById('loggedInUser');
    const userAvatarEl    = document.getElementById('userAvatar');
    const dmTabBtn        = document.getElementById('dmTabBtn');
    const groupTabBtn     = document.getElementById('groupTabBtn');
    const dmPanel         = document.getElementById('dmPanel');
    const groupPanel      = document.getElementById('groupPanel');
    const receiverInput   = document.getElementById('receiverInput');
    const loadChatBtn     = document.getElementById('loadChatBtn');
    const recentChatsList = document.getElementById('recentChatsList');
    const groupList       = document.getElementById('groupList');
    const chatEmpty       = document.getElementById('chatEmpty');
    const chatView        = document.getElementById('chatView');
    const chatAvatar      = document.getElementById('chatAvatar');
    const chatHeaderTitle = document.getElementById('chatHeaderTitle');
    const chatHeaderSub   = document.getElementById('chatHeaderSub');
    const groupActions    = document.getElementById('groupActions');
    const messagesArea    = document.getElementById('messagesArea');
    const messageInput    = document.getElementById('messageInput');
    const sendBtn         = document.getElementById('sendBtn');
    const imageInput      = document.getElementById('imageInput');
    const imagePreview    = document.getElementById('imagePreview');
    const previewImg      = document.getElementById('previewImg');
    const cancelImage     = document.getElementById('cancelImage');
    const imgPreviewName  = document.getElementById('imgPreviewName');

    // ── State ─────────────────────────────────────────────────────────────
    let currentMode      = 'dm';   // 'dm' | 'group'
    let currentReceiver  = null;   // DM: username string
    let currentGroupId   = null;   // Group: group id number
    let currentGroupData = null;   // Group: full group object
    let pollingInterval  = null;
    let selectedImage    = null;

    // ── Init ──────────────────────────────────────────────────────────────
    loggedInUserEl.textContent = currentUser;
    userAvatarEl.textContent   = currentUser.charAt(0).toUpperCase();

    updateRecentChatsUI();
    loadGroupList();

    // ── Sidebar tab switch ────────────────────────────────────────────────
    window.switchSidebarTab = function (tab) {
        currentMode = tab;
        dmPanel.style.display    = tab === 'dm'    ? 'flex' : 'none';
        groupPanel.style.display = tab === 'group' ? 'flex' : 'none';
        dmTabBtn.classList.toggle('active',    tab === 'dm');
        groupTabBtn.classList.toggle('active', tab === 'group');
    };

    // ── Open DM chat ──────────────────────────────────────────────────────
    async function openDMChat(receiver) {
        stopPolling();
        currentMode      = 'dm';
        currentReceiver  = receiver;
        currentGroupId   = null;
        currentGroupData = null;

        // Show chat view
        chatEmpty.style.display = 'none';
        chatView.style.display  = 'flex';

        // Header
        chatAvatar.textContent      = receiver.charAt(0).toUpperCase();
        chatHeaderTitle.textContent = receiver;
        chatHeaderSub.textContent   = '🔒 Encrypted';
        groupActions.style.display  = 'none';

        enableInput();
        messagesArea.innerHTML = '<div class="loading">Loading…</div>';

        try {
            const res = await fetch(
                `${API_BASE}/messages/chat?user1=${encodeURIComponent(currentUser)}&user2=${encodeURIComponent(receiver)}`,
                { headers: authHeaders() }
            );
            if (res.status === 401) { handleLogout(); return; }
            if (res.ok) {
                const messages = await res.json();
                displayMessages(messages, 'dm');
                saveRecentChat(receiver);
                updateRecentChatsUI();
                startPolling(() => pollDM(receiver));
            } else {
                messagesArea.innerHTML = '<div class="error">Failed to load conversation.</div>';
            }
        } catch {
            messagesArea.innerHTML = '<div class="error">Network error.</div>';
        }
    }

    async function pollDM(receiver) {
        if (currentMode !== 'dm' || currentReceiver !== receiver) return;
        try {
            const res = await fetch(
                `${API_BASE}/messages/chat?user1=${encodeURIComponent(currentUser)}&user2=${encodeURIComponent(receiver)}`,
                { headers: authHeaders() }
            );
            if (res.status === 401) { handleLogout(); return; }
            if (res.ok) displayMessages(await res.json(), 'dm');
        } catch { /* silent poll failure */ }
    }

    // ── Open group chat ───────────────────────────────────────────────────
    async function openGroupChat(group) {
        stopPolling();
        currentMode      = 'group';
        currentGroupId   = group.id;
        currentGroupData = group;
        currentReceiver  = null;

        // Show chat view
        chatEmpty.style.display = 'none';
        chatView.style.display  = 'flex';

        // Header
        chatAvatar.textContent = group.name.charAt(0).toUpperCase();
        chatHeaderTitle.textContent = group.name;
        const memberCount = group.members ? group.members.length : 0;
        chatHeaderSub.textContent = `${memberCount} member${memberCount !== 1 ? 's' : ''} · 🔒 Encrypted`;
        groupActions.style.display = 'flex';

        enableInput();
        messagesArea.innerHTML = '<div class="loading">Loading…</div>';

        try {
            const res = await fetch(
                `${API_BASE}/groups/${group.id}/messages`,
                { headers: authHeaders() }
            );
            if (res.status === 401) { handleLogout(); return; }
            if (res.ok) {
                displayMessages(await res.json(), 'group');
                startPolling(() => pollGroup(group.id));
            } else {
                messagesArea.innerHTML = '<div class="error">Failed to load messages.</div>';
            }
        } catch {
            messagesArea.innerHTML = '<div class="error">Network error.</div>';
        }
    }

    async function pollGroup(groupId) {
        if (currentMode !== 'group' || currentGroupId !== groupId) return;
        try {
            const res = await fetch(
                `${API_BASE}/groups/${groupId}/messages`,
                { headers: authHeaders() }
            );
            if (res.status === 401) { handleLogout(); return; }
            if (res.ok) displayMessages(await res.json(), 'group');
        } catch { /* silent poll failure */ }
    }

    // ── Load & render group list ──────────────────────────────────────────
    async function loadGroupList() {
        try {
            const res = await fetch(`${API_BASE}/groups`, { headers: authHeaders() });
            if (!res.ok) return;
            renderGroupList(await res.json());
        } catch { /* silent */ }
    }

    function renderGroupList(groups) {
        groupList.innerHTML = '';
        if (!groups || groups.length === 0) {
            const empty = document.createElement('div');
            empty.className = 'empty-state';
            empty.innerHTML = '<span class="empty-icon">👥</span><span>No groups yet</span>';
            groupList.appendChild(empty);
            return;
        }
        groups.forEach(g => {
            const memberCount = g.members ? g.members.length : 0;

            const item = document.createElement('div');
            item.className = 'conv-item';
            if (currentGroupId === g.id) item.classList.add('active');

            const avatar = document.createElement('div');
            avatar.className = 'conv-avatar';
            avatar.textContent = g.name.charAt(0).toUpperCase();

            const info = document.createElement('div');
            info.className = 'conv-info';

            const name = document.createElement('span');
            name.className = 'conv-name';
            name.textContent = g.name;

            const preview = document.createElement('span');
            preview.className = 'conv-preview';
            preview.textContent = `${memberCount} member${memberCount !== 1 ? 's' : ''}`;

            info.appendChild(name);
            info.appendChild(preview);
            item.appendChild(avatar);
            item.appendChild(info);
            item.addEventListener('click', () => openGroupChat(g));
            groupList.appendChild(item);
        });
    }

    // ── Send message ──────────────────────────────────────────────────────
    async function sendMessage() {
        const content = messageInput.value.trim();
        if (!content && !selectedImage) return;

        sendBtn.disabled = true;
        const origHTML = sendBtn.innerHTML;
        sendBtn.innerHTML = '<svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5"><circle cx="12" cy="12" r="10"/><polyline points="12 6 12 12 16 14"/></svg>';

        try {
            let res;
            if (currentMode === 'group') {
                if (!currentGroupId) return;
                if (selectedImage) {
                    const fd = new FormData();
                    fd.append('image', selectedImage);
                    res = await fetch(`${API_BASE}/groups/${currentGroupId}/messages/image`, {
                        method: 'POST', headers: authHeadersMultipart(), body: fd
                    });
                } else {
                    res = await fetch(`${API_BASE}/groups/${currentGroupId}/messages`, {
                        method: 'POST', headers: authHeaders(),
                        body: JSON.stringify({ content })
                    });
                }
                if (res.status === 401) { handleLogout(); return; }
                if (res.ok) {
                    messageInput.value = '';
                    clearImageSelection();
                    await openGroupChat(currentGroupData);
                } else {
                    alert(`Failed to send: ${await res.text()}`);
                }
            } else {
                if (!currentReceiver) return;
                if (selectedImage) {
                    const fd = new FormData();
                    fd.append('receiver', currentReceiver);
                    fd.append('image', selectedImage);
                    res = await fetch(`${API_BASE}/messages/send-image`, {
                        method: 'POST', headers: authHeadersMultipart(), body: fd
                    });
                } else {
                    res = await fetch(`${API_BASE}/messages/send`, {
                        method: 'POST', headers: authHeaders(),
                        body: JSON.stringify({ receiver: currentReceiver, content })
                    });
                }
                if (res.status === 401) { handleLogout(); return; }
                if (res.ok) {
                    messageInput.value = '';
                    clearImageSelection();
                    await openDMChat(currentReceiver);
                } else {
                    alert(`Failed to send: ${await res.text()}`);
                }
            }
        } catch {
            alert('Network error. Could not send message.');
        } finally {
            sendBtn.disabled = false;
            sendBtn.innerHTML = origHTML;
        }
    }

    // ── Display messages ──────────────────────────────────────────────────
    function displayMessages(messages, mode) {
        messagesArea.innerHTML = '';
        if (!messages || messages.length === 0) {
            const empty = document.createElement('div');
            empty.className = 'no-messages';
            empty.textContent = 'No messages yet. Say hello! 👋';
            messagesArea.appendChild(empty);
            return;
        }

        messages.forEach(msg => {
            const isSent = msg.sender === currentUser;

            const bubble = document.createElement('div');
            bubble.className = `message-bubble ${isSent ? 'sent' : 'received'}`;

            // Sender label (received messages only)
            if (!isSent) {
                const senderEl = document.createElement('div');
                senderEl.className = 'message-sender';
                senderEl.textContent = msg.sender;
                bubble.appendChild(senderEl);
            }

            // Content
            const contentEl = document.createElement('div');
            contentEl.className = 'message-content';

            if (msg.contentType === 'IMAGE') {
                const img = document.createElement('img');
                const mime = msg.mimeType || 'image/jpeg';
                img.src = `data:${mime};base64,${msg.content}`;
                img.className = 'message-image';
                img.alt = 'Shared image';
                img.onerror = function () {
                    if (!this.dataset.fallback) {
                        this.dataset.fallback = 'true';
                        this.src = `data:image/png;base64,${msg.content}`;
                    } else {
                        this.style.display = 'none';
                    }
                };
                img.onclick = () => openImageInNewTab(img.src);
                contentEl.appendChild(img);
            } else {
                // Use textContent (NOT innerHTML) to prevent XSS
                contentEl.textContent = msg.content;
            }

            // Timestamp
            const timeEl = document.createElement('div');
            timeEl.className = 'message-time';
            timeEl.textContent = msg.timestamp ? formatTimestamp(msg.timestamp) : '';

            bubble.appendChild(contentEl);
            bubble.appendChild(timeEl);
            messagesArea.appendChild(bubble);
        });

        scrollToBottom();
    }

    // ── Image handling ────────────────────────────────────────────────────
    function handleImageSelect(e) {
        const file = e.target.files[0];
        if (!file) return;
        if (!file.type.startsWith('image/')) {
            alert('Please select an image file.');
            return;
        }
        if (file.size > 5 * 1024 * 1024) {
            alert('Image must be less than 5 MB.');
            return;
        }
        selectedImage = file;
        imgPreviewName.textContent = file.name;
        imagePreview.style.display = 'flex';
        const reader = new FileReader();
        reader.onload = ev => { previewImg.src = ev.target.result; };
        reader.readAsDataURL(file);
    }

    function clearImageSelection() {
        selectedImage = null;
        imageInput.value = '';
        imagePreview.style.display = 'none';
        previewImg.src = '';
    }

    // ── Recent DM chats (localStorage) ───────────────────────────────────
    function getRecentChats() {
        const raw = localStorage.getItem(`recentChats_${currentUser}`);
        return raw ? JSON.parse(raw) : [];
    }

    function saveRecentChat(receiver) {
        let chats = getRecentChats().filter(c => c.username !== receiver);
        chats.unshift({ username: receiver, timestamp: Date.now() });
        localStorage.setItem(
            `recentChats_${currentUser}`,
            JSON.stringify(chats.slice(0, 10))
        );
    }

    function updateRecentChatsUI() {
        const chats = getRecentChats();
        recentChatsList.innerHTML = '';
        if (chats.length === 0) {
            const empty = document.createElement('div');
            empty.className = 'empty-state';
            empty.innerHTML = '<span class="empty-icon">💬</span><span>No chats yet</span>';
            recentChatsList.appendChild(empty);
            return;
        }
        chats.forEach(chat => {
            const item = document.createElement('div');
            item.className = 'conv-item';
            if (currentMode === 'dm' && currentReceiver === chat.username) {
                item.classList.add('active');
            }

            const avatar = document.createElement('div');
            avatar.className = 'conv-avatar';
            avatar.textContent = chat.username.charAt(0).toUpperCase();

            const info = document.createElement('div');
            info.className = 'conv-info';

            const name = document.createElement('span');
            name.className = 'conv-name';
            name.textContent = chat.username;

            const time = document.createElement('span');
            time.className = 'conv-time';
            time.textContent = formatTime(new Date(chat.timestamp));

            info.appendChild(name);
            info.appendChild(time);
            item.appendChild(avatar);
            item.appendChild(info);
            item.addEventListener('click', () => {
                receiverInput.value = chat.username;
                openDMChat(chat.username);
            });
            recentChatsList.appendChild(item);
        });
    }

    // ── Polling ───────────────────────────────────────────────────────────
    function startPolling(fn) {
        stopPolling();
        pollingInterval = setInterval(fn, 3000);
    }

    function stopPolling() {
        if (pollingInterval) {
            clearInterval(pollingInterval);
            pollingInterval = null;
        }
    }

    // ── Utilities ─────────────────────────────────────────────────────────
    function enableInput() {
        messageInput.disabled = false;
        sendBtn.disabled      = false;
        messageInput.focus();
    }

    function disableInput() {
        messageInput.disabled = true;
        sendBtn.disabled      = true;
    }

    function scrollToBottom() {
        messagesArea.scrollTop = messagesArea.scrollHeight;
    }

    function openImageInNewTab(src) {
        const w = window.open('', '_blank');
        if (!w) return;
        w.document.write(
            '<!DOCTYPE html><html><head><title>Image</title></head>' +
            '<body style="margin:0;background:#111;display:flex;justify-content:center;' +
            'align-items:center;min-height:100vh;">' +
            '<img src="' + src + '" style="max-width:95vw;max-height:95vh;' +
            'object-fit:contain;border-radius:8px;"/></body></html>'
        );
        w.document.close();
    }

    function showModalError(el, msg) {
        el.textContent    = msg;
        el.style.display  = 'block';
    }

    /**
     * formatTime — relative time label for recent-chats list
     * @param {Date} date
     */
    function formatTime(date) {
        const diff = Date.now() - date.getTime();
        const m    = Math.floor(diff / 60000);
        const h    = Math.floor(diff / 3600000);
        const d    = Math.floor(diff / 86400000);
        if (m < 1)  return 'Just now';
        if (m < 60) return `${m}m ago`;
        if (h < 24) return `${h}h ago`;
        if (d < 7)  return `${d}d ago`;
        return date.toLocaleDateString();
    }

    /**
     * formatTimestamp — HH:MM from ISO timestamp string
     * @param {string} isoString
     */
    function formatTimestamp(isoString) {
        try {
            const d = new Date(isoString);
            return d.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' });
        } catch {
            return '';
        }
    }

    // ── Event listeners ───────────────────────────────────────────────────
    loadChatBtn.addEventListener('click', () => {
        const receiver = receiverInput.value.trim();
        if (!receiver) { alert('Please enter a username.'); return; }
        if (receiver === currentUser) { alert('You cannot chat with yourself.'); return; }
        openDMChat(receiver);
    });

    receiverInput.addEventListener('keypress', e => {
        if (e.key === 'Enter') loadChatBtn.click();
    });

    sendBtn.addEventListener('click', sendMessage);

    messageInput.addEventListener('keypress', e => {
        if (e.key === 'Enter') sendMessage();
    });

    imageInput.addEventListener('change', handleImageSelect);
    cancelImage.addEventListener('click', clearImageSelection);

    window.addEventListener('beforeunload', stopPolling);

    // ── Modal: close on overlay click ─────────────────────────────────────
    document.querySelectorAll('.modal-overlay').forEach(overlay => {
        overlay.addEventListener('click', e => {
            if (e.target === overlay) overlay.style.display = 'none';
        });
    });

    // ── Modal: Enter key shortcuts ────────────────────────────────────────
    document.getElementById('newGroupName').addEventListener('keypress', e => {
        if (e.key === 'Enter') window.createGroup();
    });
    document.getElementById('addMemberUsername').addEventListener('keypress', e => {
        if (e.key === 'Enter') window.addMember();
    });

    // =========================================================================
    // GROUP MODALS  (window.* so HTML onclick attributes can call them)
    // =========================================================================

    window.openCreateGroupModal = function () {
        document.getElementById('newGroupName').value = '';
        document.getElementById('createGroupError').style.display = 'none';
        document.getElementById('createGroupModal').style.display = 'flex';
        setTimeout(() => document.getElementById('newGroupName').focus(), 100);
    };

    window.createGroup = async function () {
        const name  = document.getElementById('newGroupName').value.trim();
        const errEl = document.getElementById('createGroupError');
        if (!name) { showModalError(errEl, 'Group name is required.'); return; }
        try {
            const res = await fetch(`${API_BASE}/groups`, {
                method: 'POST', headers: authHeaders(),
                body: JSON.stringify({ name })
            });
            if (res.ok) {
                const group = await res.json();
                window.closeModal('createGroupModal');
                await loadGroupList();
                window.switchSidebarTab('group');
                openGroupChat(group);
            } else {
                showModalError(errEl, await res.text() || 'Failed to create group.');
            }
        } catch {
            showModalError(errEl, 'Network error.');
        }
    };

    window.openAddMemberModal = function () {
        document.getElementById('addMemberUsername').value = '';
        document.getElementById('addMemberError').style.display = 'none';
        document.getElementById('addMemberModal').style.display = 'flex';
        setTimeout(() => document.getElementById('addMemberUsername').focus(), 100);
    };

    window.addMember = async function () {
        const username = document.getElementById('addMemberUsername').value.trim();
        const errEl    = document.getElementById('addMemberError');
        if (!username) { showModalError(errEl, 'Username is required.'); return; }
        try {
            const res = await fetch(`${API_BASE}/groups/${currentGroupId}/members`, {
                method: 'POST', headers: authHeaders(),
                body: JSON.stringify({ username })
            });
            if (res.ok) {
                currentGroupData = await res.json();
                window.closeModal('addMemberModal');
                await loadGroupList();
                // Re-render the group list with fresh data
                const listRes = await fetch(`${API_BASE}/groups`, { headers: authHeaders() });
                if (listRes.ok) renderGroupList(await listRes.json());
            } else {
                showModalError(errEl, await res.text() || 'Failed to add member.');
            }
        } catch {
            showModalError(errEl, 'Network error.');
        }
    };

    window.openGroupInfoModal = async function () {
        if (!currentGroupId) return;
        try {
            const res = await fetch(`${API_BASE}/groups/${currentGroupId}`, { headers: authHeaders() });
            if (!res.ok) return;
            const group = await res.json();
            currentGroupData = group;

            document.getElementById('groupInfoTitle').textContent = `👥 ${group.name}`;
            const membersList = document.getElementById('membersList');
            membersList.innerHTML = '';

            const isAdmin = group.members &&
                group.members.some(m => m.username === currentUser && m.role === 'ADMIN');

            (group.members || []).forEach(m => {
                const row = document.createElement('div');
                row.className = 'member-row';

                // Left: avatar + name + role
                const info = document.createElement('div');
                info.className = 'member-info';

                const avatar = document.createElement('div');
                avatar.className = 'member-avatar';
                avatar.textContent = m.username.charAt(0).toUpperCase();

                const nameEl = document.createElement('span');
                nameEl.className = 'member-name';
                nameEl.textContent = m.username + (m.username === currentUser ? ' (you)' : '');

                const roleEl = document.createElement('span');
                roleEl.className = `member-role ${m.role === 'ADMIN' ? 'role-admin' : 'role-member'}`;
                roleEl.textContent = m.role === 'ADMIN' ? 'ADMIN' : 'MEMBER';

                info.appendChild(avatar);
                info.appendChild(nameEl);
                info.appendChild(roleEl);
                row.appendChild(info);

                // Right: admin action buttons (only if current user is admin and target is not self)
                if (isAdmin && m.username !== currentUser) {
                    const actions = document.createElement('div');
                    actions.className = 'member-actions';

                    if (m.role !== 'ADMIN') {
                        const promoteBtn = document.createElement('button');
                        promoteBtn.className = 'btn-member-action btn-promote';
                        promoteBtn.textContent = '⬆ Make Admin';
                        promoteBtn.onclick = () => promoteMember(m.username);
                        actions.appendChild(promoteBtn);
                    }

                    const removeBtn = document.createElement('button');
                    removeBtn.className = 'btn-member-action btn-remove';
                    removeBtn.textContent = '✕ Remove';
                    removeBtn.onclick = () => removeMember(m.username);
                    actions.appendChild(removeBtn);

                    row.appendChild(actions);
                }

                membersList.appendChild(row);
            });

            document.getElementById('groupInfoModal').style.display = 'flex';
        } catch {
            alert('Failed to load group info.');
        }
    };

    async function promoteMember(username) {
        try {
            const res = await fetch(
                `${API_BASE}/groups/${currentGroupId}/members/${encodeURIComponent(username)}/promote`,
                { method: 'POST', headers: authHeaders() }
            );
            if (res.ok) {
                window.closeModal('groupInfoModal');
                window.openGroupInfoModal();
            } else {
                alert(await res.text() || 'Failed to promote member.');
            }
        } catch {
            alert('Network error.');
        }
    }

    async function removeMember(username) {
        if (!confirm(`Remove ${username} from the group?`)) return;
        try {
            const res = await fetch(
                `${API_BASE}/groups/${currentGroupId}/members/${encodeURIComponent(username)}`,
                { method: 'DELETE', headers: authHeaders() }
            );
            if (res.ok) {
                currentGroupData = await res.json();
                window.closeModal('groupInfoModal');
                window.openGroupInfoModal();
                await loadGroupList();
            } else {
                alert(await res.text() || 'Failed to remove member.');
            }
        } catch {
            alert('Network error.');
        }
    }

    window.leaveGroup = async function () {
        if (!confirm('Leave this group?')) return;
        try {
            const res = await fetch(
                `${API_BASE}/groups/${currentGroupId}/members/${encodeURIComponent(currentUser)}`,
                { method: 'DELETE', headers: authHeaders() }
            );
            if (res.ok) {
                // Reset state
                currentGroupId   = null;
                currentGroupData = null;
                currentReceiver  = null;
                stopPolling();

                // Show empty state
                chatView.style.display  = 'none';
                chatEmpty.style.display = 'flex';
                disableInput();

                await loadGroupList();
                window.switchSidebarTab('group');
            } else {
                alert(await res.text() || 'Failed to leave group.');
            }
        } catch {
            alert('Network error.');
        }
    };

    window.closeModal = function (id) {
        const el = document.getElementById(id);
        if (el) el.style.display = 'none';
    };

})();
