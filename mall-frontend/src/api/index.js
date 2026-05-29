import axios from 'axios'
import { getToken, logout } from '../utils/auth.js'
import router from '../router/index.js'

const http = axios.create({
  baseURL: '',
  timeout: 60000,
  headers: { 'Content-Type': 'application/json' }
})

// 请求拦截器：自动携带 JWT
http.interceptors.request.use(config => {
  const token = getToken()
  if (token) config.headers.Authorization = `Bearer ${token}`
  return config
})

// 响应拦截器：401 自动跳转登录
http.interceptors.response.use(
  res => res,
  err => {
    if (err.response?.status === 401) {
      logout()
      // Vue Router v3: currentRoute is the route object directly (no .value)
      const redirect = router.currentRoute ? router.currentRoute.fullPath : '/'
      router.push({ path: '/login', query: { redirect } }).catch(() => {})
    }
    return Promise.reject(err)
  }
)

// ====================================================================
// 认证接口
// ====================================================================

export function login(username, password) {
  return http.post('/api/auth/login', { username, password })
}

export function register(username, password, nickname) {
  return http.post('/api/auth/register', { username, password, nickname })
}

export function getMe() {
  return http.get('/api/auth/me')
}

// ====================================================================
// 用户端：聊天接口
// ====================================================================

export function sendMessage(message, sessionId) {
  return http.post('/api/chat', { message, sessionId })
}

/** 流式聊天 - 返回 fetch Response（调用方自行处理 ReadableStream） */
export async function sendMessageStream(message, sessionId) {
  const token = getToken()
  return fetch('/api/chat/stream', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      ...(token ? { Authorization: `Bearer ${token}` } : {})
    },
    body: JSON.stringify({ message, sessionId })
  })
}

/** 获取会话历史 */
export function getChatHistory(sessionId) {
  return http.get(`/api/chat/history/${sessionId}`)
}

/** 获取历史会话列表 */
export function getChatSessions() {
  return http.get('/api/chat/sessions')
}

// ====================================================================
// 管理端：Mock 接口配置
// ====================================================================

export function getAllConfigs() {
  return http.get('/api/mock-config')
}

export function getConfig(operation) {
  return http.get(`/api/mock-config/${operation}`)
}

export function saveConfig(operation, config) {
  return http.post(`/api/mock-config/${operation}`, config)
}

export function deleteConfig(operation) {
  return http.delete(`/api/mock-config/${operation}`)
}

export function testConfig(operation, params) {
  return http.post(`/api/mock-config/${operation}/test`, params)
}

export function getOperationMetas() {
  return http.get('/api/mock-config/operations')
}
