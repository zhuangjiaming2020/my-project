<template>
  <div class="chat-page">
    <!-- ═══════════════ 左侧边栏 ═══════════════ -->
    <aside class="chat-sidebar">
      <!-- 新建会话 -->
      <div class="sidebar-top">
        <el-button type="primary" size="small" class="new-session-btn" @click="createNewSession">
          <el-icon><Plus /></el-icon>
          新建会话
        </el-button>
      </div>

      <!-- 历史会话列表 -->
      <div class="session-list-wrapper">
        <div class="sidebar-label-row">
          <span class="sidebar-label">历史会话</span>
          <el-tooltip content="刷新列表" placement="right">
            <el-button text size="small" @click="loadSessions" :loading="sessionsLoading">
              <el-icon><Refresh /></el-icon>
            </el-button>
          </el-tooltip>
        </div>

        <div v-if="sessionsLoading" class="sessions-state">
          <el-icon class="rotating"><Loading /></el-icon>
        </div>
        <div v-else-if="sessions.length === 0" class="sessions-state sessions-empty">
          暂无历史会话
        </div>
        <div v-else class="sessions-list">
          <div
            v-for="s in sessions"
            :key="s.sessionId"
            class="session-item"
            :class="{ active: s.sessionId === sessionId }"
            @click="switchSession(s.sessionId)"
          >
            <div class="session-title">{{ truncate(s.content, 26) }}</div>
            <div class="session-time">{{ relativeTime(s.createdAt) }}</div>
          </div>
        </div>
      </div>

      <!-- 发送模式 -->
      <div class="sidebar-section">
        <div class="sidebar-label">发送模式</div>
        <el-radio-group v-model="useStream" size="small" class="mode-radio">
          <el-radio-button :value="false">同步</el-radio-button>
          <el-radio-button :value="true">流式</el-radio-button>
        </el-radio-group>
        <div class="mode-desc">
          {{ useStream ? '流式：实时显示处理进度' : '同步：等待完整回复' }}
        </div>
      </div>

      <!-- 快速提问 -->
      <div class="sidebar-section">
        <div class="sidebar-label">快速提问</div>
        <div class="quick-questions">
          <el-button
            v-for="q in quickQuestions"
            :key="q"
            size="small"
            plain
            class="quick-btn"
            @click="fillQuestion(q)"
          >{{ q }}</el-button>
        </div>
      </div>
    </aside>

    <!-- ═══════════════ 主聊天区 ═══════════════ -->
    <div class="chat-main">
      <!-- 顶部会话信息栏 -->
      <div class="chat-header">
        <span class="chat-header-title">
          {{ currentSessionTitle || '新建会话' }}
        </span>
        <span class="chat-header-sub" v-if="sessionId">
          {{ shortSessionId }}
        </span>
        <el-tooltip v-if="sessionId" content="复制会话ID">
          <el-button text size="small" @click="copySessionId">
            <el-icon><CopyDocument /></el-icon>
          </el-button>
        </el-tooltip>
      </div>

      <!-- 消息列表 -->
      <div class="messages-container" ref="messagesRef" v-loading="historyLoading">
        <!-- 欢迎区 -->
        <div v-if="messages.length === 0 && !historyLoading" class="welcome-area">
          <div class="welcome-icon">🤖</div>
          <div class="welcome-title">您好！我是智能客服助手</div>
          <div class="welcome-subtitle">我可以帮您查询订单状态、物流信息，以及提交商品评价</div>
          <div class="welcome-hints">
            <span v-for="hint in welcomeHints" :key="hint" class="hint-tag" @click="fillQuestion(hint)">
              {{ hint }}
            </span>
          </div>
        </div>

        <!-- 消息气泡 -->
        <div
          v-for="(msg, index) in messages"
          :key="index"
          class="message-row"
          :class="msg.role"
        >
          <div v-if="msg.role === 'assistant'" class="avatar bot-avatar">🤖</div>

          <div class="bubble-wrapper">
            <div v-if="msg.progress && !msg.content" class="progress-bubble">
              <el-icon class="rotating"><Loading /></el-icon>
              <span>{{ msg.progress }}</span>
            </div>
            <div v-else-if="msg.isLoading && !msg.content && !msg.progress" class="loading-bubble">
              <span class="dot" /><span class="dot" /><span class="dot" />
            </div>
            <div v-else-if="msg.content" class="bubble" :class="msg.role">
              <pre class="bubble-text">{{ msg.content }}</pre>
            </div>
            <div class="bubble-time">{{ formatTime(msg.time) }}</div>
          </div>

          <div v-if="msg.role === 'user'" class="avatar user-avatar">👤</div>
        </div>
      </div>

      <!-- 输入区 -->
      <div class="input-area">
        <div class="input-wrapper">
          <el-input
            v-model="inputText"
            type="textarea"
            :rows="3"
            placeholder="请输入您的问题（Ctrl+Enter 发送）"
            resize="none"
            :disabled="isLoading"
            @keydown.ctrl.enter.prevent="sendMessage"
            class="msg-input"
          />
          <div class="input-actions">
            <span class="input-hint">Ctrl+Enter 发送</span>
            <el-button
              type="primary"
              :loading="isLoading"
              :disabled="!inputText.trim()"
              @click="sendMessage"
              class="send-btn"
            >
              <el-icon v-if="!isLoading"><Promotion /></el-icon>
              发送
            </el-button>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, nextTick, computed, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import {
  sendMessage as apiSendMessage,
  sendMessageStream,
  getChatHistory,
  getChatSessions
} from '../api/index.js'

// ====================================================================
// 常量 & 状态
// ====================================================================

const SESSION_KEY = 'mall_chat_session_id'

const messages       = ref([])
const inputText      = ref('')
const isLoading      = ref(false)
const useStream      = ref(false)
const messagesRef    = ref(null)
const sessions       = ref([])
const sessionsLoading = ref(false)
const historyLoading  = ref(false)
const sessionId       = ref(localStorage.getItem(SESSION_KEY) || '')

const quickQuestions = [
  '查询 ORD001 订单状态',
  '查询 ORD002 快递物流',
  '给 ORD001 提交五星好评',
  '查询 ORD003 发货情况'
]

const welcomeHints = ['📦 查物流', '📋 查订单', '⭐ 提交评价', '💬 咨询问题']

// ====================================================================
// 计算属性
// ====================================================================

const shortSessionId = computed(() =>
  sessionId.value ? sessionId.value.substring(0, 8) + '...' : ''
)

/** 当前会话在历史列表中的标题（第一条用户提问） */
const currentSessionTitle = computed(() => {
  if (!sessionId.value) return ''
  const s = sessions.value.find(x => x.sessionId === sessionId.value)
  return s ? truncate(s.content, 32) : ''
})

// ====================================================================
// 工具函数
// ====================================================================

function truncate(str, len) {
  if (!str) return ''
  return str.length > len ? str.substring(0, len) + '…' : str
}

function formatTime(date) {
  if (!date) return ''
  const d = new Date(date)
  return `${String(d.getHours()).padStart(2, '0')}:${String(d.getMinutes()).padStart(2, '0')}`
}

function relativeTime(dt) {
  if (!dt) return ''
  const date = new Date(dt)
  const now  = new Date()
  const diff = now - date          // ms
  const mins = Math.floor(diff / 60000)
  if (mins < 1)   return '刚刚'
  if (mins < 60)  return `${mins}分钟前`
  const hrs = Math.floor(mins / 60)
  if (hrs < 24)   return `${hrs}小时前`
  const days = Math.floor(hrs / 24)
  if (days < 7)   return `${days}天前`
  return `${date.getMonth() + 1}月${date.getDate()}日`
}

function generateUUID() {
  return 'xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx'.replace(/[xy]/g, c => {
    const r = (Math.random() * 16) | 0
    return (c === 'x' ? r : (r & 0x3) | 0x8).toString(16)
  })
}

function fillQuestion(q) { inputText.value = q }

function copySessionId() {
  if (sessionId.value) {
    navigator.clipboard.writeText(sessionId.value)
    ElMessage.success('会话ID已复制')
  }
}

async function scrollToBottom() {
  await nextTick()
  if (messagesRef.value) messagesRef.value.scrollTop = messagesRef.value.scrollHeight
}

// ====================================================================
// 会话列表
// ====================================================================

async function loadSessions() {
  sessionsLoading.value = true
  try {
    const { data } = await getChatSessions()
    sessions.value = Array.isArray(data) ? data : []
  } catch {
    // 静默失败，不影响主功能
  } finally {
    sessionsLoading.value = false
  }
}

// ====================================================================
// 切换 / 新建会话
// ====================================================================

function createNewSession() {
  sessionId.value = ''
  localStorage.removeItem(SESSION_KEY)
  messages.value  = []
}

async function switchSession(sid) {
  if (sid === sessionId.value) return

  sessionId.value = sid
  localStorage.setItem(SESSION_KEY, sid)
  messages.value  = []
  historyLoading.value = true

  try {
    const { data } = await getChatHistory(sid)
    messages.value = (Array.isArray(data) ? data : []).map(h => ({
      role:    h.role.toLowerCase(),
      content: h.content || '',
      time:    h.createdAt ? new Date(h.createdAt) : new Date()
    }))
    await scrollToBottom()
  } catch {
    ElMessage.error('加载历史记录失败')
  } finally {
    historyLoading.value = false
  }
}

// ====================================================================
// 初始化：页面加载
// ====================================================================

onMounted(async () => {
  await loadSessions()
  // 若 localStorage 有历史 sessionId，自动加载其聊天记录
  if (sessionId.value) {
    historyLoading.value = true
    try {
      const { data } = await getChatHistory(sessionId.value)
      messages.value = (Array.isArray(data) ? data : []).map(h => ({
        role:    h.role.toLowerCase(),
        content: h.content || '',
        time:    h.createdAt ? new Date(h.createdAt) : new Date()
      }))
      await scrollToBottom()
    } catch {
      // 历史加载失败时不影响正常使用
    } finally {
      historyLoading.value = false
    }
  }
})

// ====================================================================
// 发送消息
// ====================================================================

async function sendMessage() {
  const text = inputText.value.trim()
  if (!text || isLoading.value) return

  messages.value.push({ role: 'user', content: text, time: new Date() })
  inputText.value = ''
  await scrollToBottom()

  if (useStream.value) {
    await handleStreamSend(text)
  } else {
    await handleSyncSend(text)
  }
}

async function handleSyncSend(text) {
  isLoading.value = true
  messages.value.push({ role: 'assistant', content: '', isLoading: true, time: new Date() })
  const idx = messages.value.length - 1
  await scrollToBottom()

  try {
    const { data } = await apiSendMessage(text, sessionId.value)
    sessionId.value = data.sessionId
    localStorage.setItem(SESSION_KEY, data.sessionId)
    messages.value[idx].content   = data.success ? data.answer : (data.answer || data.errorMessage || '请求失败')
    messages.value[idx].isLoading = false
  } catch {
    messages.value[idx].content   = '网络请求失败，请检查后端服务是否已启动（端口 8080）。'
    messages.value[idx].isLoading = false
  } finally {
    isLoading.value = false
    await scrollToBottom()
    loadSessions()    // 刷新会话列表（不等待）
  }
}

async function handleStreamSend(text) {
  // 流式模式下前端主动生成 sessionId，避免后端生成的 ID 无法同步回前端
  if (!sessionId.value) {
    sessionId.value = generateUUID()
    localStorage.setItem(SESSION_KEY, sessionId.value)
  }

  isLoading.value = true
  messages.value.push({ role: 'assistant', content: '', isLoading: true, progress: '', time: new Date() })
  const idx = messages.value.length - 1
  await scrollToBottom()

  try {
    const response = await sendMessageStream(text, sessionId.value)
    if (!response.ok) {
      messages.value[idx].content   = `请求失败，状态码: ${response.status}`
      messages.value[idx].isLoading = false
      return
    }

    const reader  = response.body.getReader()
    const decoder = new TextDecoder()
    let buffer      = ''
    let answerLines = []
    let inAnswer    = false

    while (true) {
      const { done, value } = await reader.read()
      if (done) break

      buffer += decoder.decode(value, { stream: true })
      const lines = buffer.split('\n')
      buffer = lines.pop() ?? ''

      for (const rawLine of lines) {
        // 统一剥去 Spring SSE 封装的 "data: " 前缀后再处理
        const line = rawLine.startsWith('data: ') ? rawLine.slice(6)
                   : rawLine.startsWith('data:')  ? rawLine.slice(5)
                   : rawLine

        if (!line.trim()) {
          if (inAnswer) answerLines.push('')
          continue
        }

        if (line === '[ANSWER]') {
          inAnswer    = true
          answerLines = []
        } else if (line.startsWith('[PROGRESS] ')) {
          messages.value[idx].progress = line.slice(11)
        } else if (line.startsWith('[ERROR]')) {
          messages.value[idx].content   = line
          messages.value[idx].isLoading = false
        } else if (inAnswer) {
          answerLines.push(line)
          messages.value[idx].content   = answerLines.join('\n').trimEnd()
          messages.value[idx].progress  = ''
          messages.value[idx].isLoading = false
        }
      }
      await scrollToBottom()
    }

    // 流结束兜底
    if (!messages.value[idx].content && messages.value[idx].progress) {
      messages.value[idx].content = messages.value[idx].progress
    }
    messages.value[idx].isLoading = false

  } catch {
    messages.value[idx].content   = '流式请求失败，请检查后端服务。'
    messages.value[idx].isLoading = false
  } finally {
    isLoading.value = false
    await scrollToBottom()
    loadSessions()    // 刷新会话列表（不等待）
  }
}
</script>

<style scoped>
/* ═══════════════════════════════════════════════════
   整体布局
══════════════════════════════════════════════════ */
.chat-page {
  display: flex;
  height: 100%;
  background: #f0f2f5;
  overflow: hidden;
}

/* ═══════════════════════════════════════════════════
   左侧边栏
══════════════════════════════════════════════════ */
.chat-sidebar {
  width: 240px;
  background: #fff;
  border-right: 1px solid #e8e8e8;
  display: flex;
  flex-direction: column;
  flex-shrink: 0;
  overflow: hidden;
}

/* 新建会话按钮 */
.sidebar-top {
  padding: 14px 12px 10px;
  border-bottom: 1px solid #f0f0f0;
}

.new-session-btn {
  width: 100%;
  justify-content: center;
}

/* 历史会话列表区块 */
.session-list-wrapper {
  flex: 1;
  overflow-y: auto;
  padding: 10px 8px 6px;
  border-bottom: 1px solid #f0f0f0;
  min-height: 0;
}

.sidebar-label-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 6px;
  padding: 0 4px;
}

.sidebar-label {
  font-size: 11px;
  font-weight: 600;
  color: #999;
  text-transform: uppercase;
  letter-spacing: 0.5px;
}

.sessions-state {
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 24px 0;
  color: #bbb;
  font-size: 12px;
}

.sessions-empty { color: #ccc; }

.sessions-list {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.session-item {
  padding: 9px 10px;
  border-radius: 8px;
  cursor: pointer;
  transition: background 0.15s;
}

.session-item:hover {
  background: #f5f5f5;
}

.session-item.active {
  background: #e8f4ff;
}

.session-title {
  font-size: 13px;
  color: #333;
  line-height: 1.4;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.session-item.active .session-title {
  color: #1677ff;
  font-weight: 500;
}

.session-time {
  font-size: 11px;
  color: #bbb;
  margin-top: 2px;
}

/* 底部固定区块 */
.sidebar-section {
  padding: 12px 12px 8px;
  border-bottom: 1px solid #f0f0f0;
  flex-shrink: 0;
}

.sidebar-section:last-child {
  border-bottom: none;
}

.mode-radio {
  display: flex;
  width: 100%;
  margin-bottom: 6px;
}

.mode-radio :deep(.el-radio-button) { flex: 1; }
.mode-radio :deep(.el-radio-button__inner) { width: 100%; padding: 5px 0; }

.mode-desc {
  font-size: 11px;
  color: #999;
  line-height: 1.4;
}

.quick-questions {
  display: flex;
  flex-direction: column;
  gap: 5px;
}

.quick-btn {
  text-align: left;
  justify-content: flex-start;
  white-space: normal;
  height: auto;
  padding: 6px 10px;
  line-height: 1.4;
  font-size: 12px;
}

/* ═══════════════════════════════════════════════════
   主聊天区
══════════════════════════════════════════════════ */
.chat-main {
  flex: 1;
  display: flex;
  flex-direction: column;
  overflow: hidden;
  min-width: 0;
}

/* 顶部信息栏 */
.chat-header {
  height: 48px;
  padding: 0 24px;
  display: flex;
  align-items: center;
  gap: 8px;
  background: #fff;
  border-bottom: 1px solid #e8e8e8;
  flex-shrink: 0;
}

.chat-header-title {
  font-size: 14px;
  font-weight: 500;
  color: #222;
  max-width: 320px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.chat-header-sub {
  font-size: 12px;
  color: #bbb;
  font-family: monospace;
}

/* 消息列表 */
.messages-container {
  flex: 1;
  overflow-y: auto;
  padding: 24px 40px;
  display: flex;
  flex-direction: column;
  gap: 16px;
}

/* 欢迎区 */
.welcome-area {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  flex: 1;
  gap: 12px;
  padding: 60px 20px;
  text-align: center;
}

.welcome-icon      { font-size: 56px; line-height: 1; }
.welcome-title     { font-size: 22px; font-weight: 600; color: #1a1a1a; }
.welcome-subtitle  { font-size: 14px; color: #888; max-width: 380px; }

.welcome-hints {
  display: flex;
  gap: 10px;
  flex-wrap: wrap;
  justify-content: center;
  margin-top: 8px;
}

.hint-tag {
  padding: 8px 16px;
  background: #e8f4ff;
  color: #1677ff;
  border-radius: 20px;
  font-size: 13px;
  cursor: pointer;
  border: 1px solid #bae0ff;
  transition: all 0.2s;
}

.hint-tag:hover {
  background: #1677ff;
  color: #fff;
  border-color: #1677ff;
}

/* 消息行 */
.message-row {
  display: flex;
  gap: 10px;
  align-items: flex-start;
  max-width: 80%;
}

.message-row.user      { flex-direction: row-reverse; align-self: flex-end; max-width: 70%; }
.message-row.assistant { align-self: flex-start; }

.avatar {
  width: 36px;
  height: 36px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 18px;
  flex-shrink: 0;
  background: #f0f0f0;
}

.bot-avatar  { background: linear-gradient(135deg, #e8f4ff, #bae0ff); }
.user-avatar { background: linear-gradient(135deg, #fff7e6, #ffd591); }

.bubble-wrapper { display: flex; flex-direction: column; gap: 4px; min-width: 0; }

/* 气泡 */
.bubble {
  padding: 12px 16px;
  border-radius: 12px;
  max-width: 100%;
  word-break: break-word;
}

.bubble.assistant {
  background: #fff;
  box-shadow: 0 1px 4px rgba(0,0,0,0.08);
  border-radius: 4px 12px 12px 12px;
}

.bubble.user {
  background: linear-gradient(135deg, #1677ff, #0958d9);
  color: #fff;
  border-radius: 12px 4px 12px 12px;
}

.bubble-text {
  font-family: inherit;
  font-size: 14px;
  line-height: 1.7;
  white-space: pre-wrap;
  margin: 0;
}

.bubble.user .bubble-text { color: #fff; }

.bubble-time { font-size: 11px; color: #bbb; }
.message-row.user .bubble-time { text-align: right; }

/* 加载气泡 */
.loading-bubble {
  background: #fff;
  padding: 14px 18px;
  border-radius: 4px 12px 12px 12px;
  box-shadow: 0 1px 4px rgba(0,0,0,0.08);
  display: flex;
  gap: 5px;
  align-items: center;
}

.dot {
  width: 7px;
  height: 7px;
  background: #1677ff;
  border-radius: 50%;
  animation: bounce 1.4s infinite ease-in-out;
}

.dot:nth-child(2) { animation-delay: 0.2s; }
.dot:nth-child(3) { animation-delay: 0.4s; }

@keyframes bounce {
  0%, 80%, 100% { transform: scale(0.6); opacity: 0.5; }
  40%            { transform: scale(1);   opacity: 1; }
}

/* 进度气泡 */
.progress-bubble {
  background: #f0f7ff;
  border: 1px solid #bae0ff;
  color: #1677ff;
  padding: 8px 14px;
  border-radius: 8px;
  font-size: 13px;
  display: flex;
  align-items: center;
  gap: 8px;
}

.rotating { animation: spin 1s linear infinite; }

@keyframes spin {
  from { transform: rotate(0deg); }
  to   { transform: rotate(360deg); }
}

/* 输入区 */
.input-area {
  border-top: 1px solid #e8e8e8;
  background: #fff;
  padding: 16px 40px;
}

.input-wrapper {
  display: flex;
  gap: 12px;
  align-items: flex-end;
}

.msg-input { flex: 1; }

.msg-input :deep(.el-textarea__inner) {
  font-size: 14px;
  line-height: 1.6;
  border-radius: 10px;
  resize: none;
  padding: 10px 14px;
}

.input-actions {
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  gap: 6px;
}

.input-hint { font-size: 11px; color: #bbb; white-space: nowrap; }

.send-btn {
  height: 40px;
  padding: 0 20px;
  font-size: 14px;
  border-radius: 8px;
}
</style>
