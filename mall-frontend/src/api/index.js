import axios from 'axios'

const http = axios.create({
  baseURL: '',
  timeout: 60000,
  headers: { 'Content-Type': 'application/json' }
})

// ====================================================================
// 用户端：聊天接口
// ====================================================================

/** 同步聊天 */
export function sendMessage(message, sessionId) {
  return http.post('/api/chat', { message, sessionId })
}

/** 流式聊天 - 返回 fetch Response 对象（调用方自行处理 ReadableStream） */
export async function sendMessageStream(message, sessionId) {
  return fetch('/api/chat/stream', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ message, sessionId })
  })
}

// ====================================================================
// 管理端：Mock 接口配置
// ====================================================================

/** 获取所有操作的配置 */
export function getAllConfigs() {
  return http.get('/api/mock-config')
}

/** 获取单个操作的配置 */
export function getConfig(operation) {
  return http.get(`/api/mock-config/${operation}`)
}

/** 保存/更新配置 */
export function saveConfig(operation, config) {
  return http.post(`/api/mock-config/${operation}`, config)
}

/** 重置配置 */
export function deleteConfig(operation) {
  return http.delete(`/api/mock-config/${operation}`)
}

/** 测试外部 API */
export function testConfig(operation, params) {
  return http.post(`/api/mock-config/${operation}/test`, params)
}

/** 获取操作元信息 */
export function getOperationMetas() {
  return http.get('/api/mock-config/operations')
}
