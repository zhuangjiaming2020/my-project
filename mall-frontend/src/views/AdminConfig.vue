<template>
  <div class="admin-page">
    <!-- 左侧：操作列表（类似 Postman Collection） -->
    <aside class="admin-sidebar">
      <div class="sidebar-title">
        <el-icon><Files /></el-icon>
        <span>接口配置</span>
      </div>
      <div
        v-for="op in operationList"
        :key="op.key"
        class="op-item"
        :class="{ active: selectedOp === op.key }"
        @click="selectOperation(op.key)"
      >
        <div class="op-item-left">
          <span class="method-tag" :class="getMethodClass(configs[op.key]?.method || 'GET')">
            {{ configs[op.key]?.method || 'GET' }}
          </span>
          <div class="op-item-info">
            <div class="op-item-name">{{ op.label }}</div>
            <div class="op-item-url">{{ configs[op.key]?.url || '未配置 URL' }}</div>
          </div>
        </div>
        <el-tag
          :type="configs[op.key]?.enabled ? 'success' : 'info'"
          size="small"
          effect="light"
        >
          {{ configs[op.key]?.enabled ? '启用' : '关闭' }}
        </el-tag>
      </div>

      <div class="sidebar-info">
        <el-icon><InfoFilled /></el-icon>
        <span>配置后端接口，客服机器人将调用真实 API 替代 Mock 数据</span>
      </div>
    </aside>

    <!-- 右侧：配置面板（类似 Postman 主面板） -->
    <div class="admin-main" v-if="selectedOp && currentConfig">
      <!-- URL 栏 -->
      <div class="url-bar">
        <el-select v-model="currentConfig.method" class="method-select" @change="dirty = true">
          <el-option label="GET" value="GET" />
          <el-option label="POST" value="POST" />
          <el-option label="PUT" value="PUT" />
          <el-option label="DELETE" value="DELETE" />
        </el-select>
        <el-input
          v-model="currentConfig.url"
          placeholder="输入接口 URL，如 http://your-mall.com/api/orders/{orderId}/status"
          class="url-input"
          @input="dirty = true"
        >
          <template #prefix>
            <el-icon color="#999"><Link /></el-icon>
          </template>
        </el-input>
        <el-button
          type="primary"
          :loading="testing"
          :disabled="!currentConfig.url"
          @click="runTest"
          class="send-btn"
        >
          <el-icon><CaretRight /></el-icon>
          Send
        </el-button>
      </div>

      <!-- 配置 Tabs -->
      <div class="config-tabs-container">
        <el-tabs v-model="activeTab" class="config-tabs">
          <!-- Tab 1: 基本信息 -->
          <el-tab-pane label="基本信息" name="basic">
            <div class="tab-content">
              <el-form :model="currentConfig" label-width="100px" label-position="left">
                <el-form-item label="接口名称">
                  <el-input v-model="currentConfig.description" @input="dirty = true"
                    placeholder="如：查询订单状态接口" />
                </el-form-item>
                <el-form-item label="是否启用">
                  <div class="enable-row">
                    <el-switch
                      v-model="currentConfig.enabled"
                      active-text="启用（调用外部 API）"
                      inactive-text="关闭（使用 Mock 数据）"
                      @change="dirty = true"
                    />
                    <el-alert
                      v-if="currentConfig.enabled"
                      type="warning"
                      :closable="false"
                      show-icon
                      class="enable-alert"
                    >
                      <template #title>
                        已启用外部 API —— 客服机器人将调用上方 URL 获取真实数据
                      </template>
                    </el-alert>
                    <el-alert
                      v-else
                      type="info"
                      :closable="false"
                      show-icon
                      class="enable-alert"
                    >
                      <template #title>
                        当前使用内置 Mock 数据，配置并保存后可切换到外部 API
                      </template>
                    </el-alert>
                  </div>
                </el-form-item>
              </el-form>

              <el-divider content-position="left">
                <span class="divider-title">URL 占位符说明</span>
              </el-divider>
              <div class="placeholder-help">
                <div v-for="p in currentMeta?.params || []" :key="p" class="placeholder-item">
                  <code class="code-inline">{{ '{' + p + '}' }}</code>
                  <span>URL 路径参数：如 /orders/<code>{{ '{' + p + '}' }}</code>/status</span>
                </div>
                <div v-for="p in currentMeta?.params || []" :key="'body-' + p" class="placeholder-item">
                  <code class="code-inline">{{ '{' + '{' + p + '}' + '}' }}</code>
                  <span>请求体模板变量（POST Body 中使用）</span>
                </div>
              </div>
            </div>
          </el-tab-pane>

          <!-- Tab 2: 请求头 -->
          <el-tab-pane name="headers">
            <template #label>
              <span>
                Headers
                <el-badge
                  v-if="headerCount > 0"
                  :value="headerCount"
                  class="tab-badge"
                  type="primary"
                />
              </span>
            </template>
            <div class="tab-content">
              <div class="headers-toolbar">
                <span class="section-hint">配置请求头（如 Authorization、Content-Type）</span>
                <el-button size="small" @click="addHeader" type="primary" plain>
                  <el-icon><Plus /></el-icon> 添加
                </el-button>
              </div>
              <div class="headers-table">
                <div class="headers-row headers-row-head">
                  <span class="col-key">Key</span>
                  <span class="col-val">Value</span>
                  <span class="col-action"></span>
                </div>
                <div
                  v-for="(header, idx) in headerList"
                  :key="idx"
                  class="headers-row"
                >
                  <el-input
                    v-model="header.key"
                    placeholder="Header 名称"
                    class="col-key"
                    size="small"
                    @input="dirty = true"
                  />
                  <el-input
                    v-model="header.value"
                    placeholder="Header 值"
                    class="col-val"
                    size="small"
                    @input="dirty = true"
                  />
                  <el-button
                    text
                    type="danger"
                    size="small"
                    class="col-action"
                    @click="removeHeader(idx)"
                  >
                    <el-icon><Delete /></el-icon>
                  </el-button>
                </div>
                <div v-if="headerList.length === 0" class="empty-tip">
                  暂无请求头，点击「添加」按钮新增
                </div>
              </div>
            </div>
          </el-tab-pane>

          <!-- Tab 3: 请求体（POST 时显示） -->
          <el-tab-pane label="Body" name="body">
            <div class="tab-content">
              <div class="body-toolbar">
                <span class="section-hint">POST 请求体模板（JSON），支持占位符替换</span>
                <el-tag type="info" size="small" effect="light">application/json</el-tag>
              </div>
              <div
                v-if="currentConfig.method !== 'POST' && currentConfig.method !== 'PUT'"
                class="body-disabled"
              >
                <el-icon color="#ccc" size="32"><Warning /></el-icon>
                <span>当前请求方法（{{ currentConfig.method }}）无请求体</span>
              </div>
              <el-input
                v-else
                v-model="currentConfig.requestBody"
                type="textarea"
                :rows="10"
                placeholder='{"orderId": "{{orderId}}", "rating": {{rating}}, "content": "{{content}}"}'
                class="body-editor"
                @input="dirty = true"
              />
              <div class="body-hint" v-if="currentConfig.method === 'POST' || currentConfig.method === 'PUT'">
                <p>示例模板（提交评价）：</p>
                <code v-pre>{"orderId": "{{orderId}}", "rating": {{rating}}, "content": "{{content}}"}</code>
              </div>
            </div>
          </el-tab-pane>
        </el-tabs>

        <!-- 保存按钮 -->
        <div class="save-bar" :class="{ dirty }">
          <span v-if="dirty" class="dirty-hint">⚠ 有未保存的修改</span>
          <el-button @click="resetForm">重置</el-button>
          <el-button type="primary" :loading="saving" @click="saveForm">
            <el-icon><Check /></el-icon> 保存配置
          </el-button>
        </div>
      </div>

      <!-- 测试响应区 -->
      <div class="response-panel" v-if="testResult !== null || testing">
        <div class="response-header">
          <span class="response-title">Response</span>
          <div class="response-meta" v-if="testResult">
            <el-tag :type="testResult.success ? 'success' : 'danger'" size="small" effect="light">
              {{ testResult.success ? '200 OK' : 'Error' }}
            </el-tag>
            <span class="elapsed">{{ testResult.elapsedMs }} ms</span>
          </div>
        </div>

        <div v-if="testing" class="response-loading">
          <el-icon class="rotating"><Loading /></el-icon>
          <span>正在发送请求...</span>
        </div>

        <div v-else-if="testResult">
          <div v-if="testResult.success" class="response-body">
            <pre>{{ formatJson(testResult.responseBody) }}</pre>
          </div>
          <div v-else class="response-error">
            <el-icon><CircleCloseFilled /></el-icon>
            <span>{{ testResult.errorMessage }}</span>
          </div>
        </div>
      </div>

      <!-- 测试参数（浮动面板） -->
      <el-dialog
        v-model="showTestDialog"
        title="填写测试参数"
        width="420px"
        :close-on-click-modal="false"
      >
        <el-form :model="testParams" label-width="80px">
          <el-form-item label="订单号" v-if="currentMeta?.params.includes('orderId')">
            <el-input v-model="testParams.orderId" placeholder="如 ORD001" />
          </el-form-item>
          <el-form-item label="评分" v-if="currentMeta?.params.includes('rating')">
            <el-rate v-model="testParams.rating" show-text />
          </el-form-item>
          <el-form-item label="评价内容" v-if="currentMeta?.params.includes('content')">
            <el-input v-model="testParams.content" type="textarea" :rows="3"
              placeholder="评价内容（可选，留空自动填充）" />
          </el-form-item>
        </el-form>
        <template #footer>
          <el-button @click="showTestDialog = false">取消</el-button>
          <el-button type="primary" @click="confirmTest">发送</el-button>
        </template>
      </el-dialog>
    </div>

    <!-- 未选中时的空状态 -->
    <div class="admin-main empty-main" v-else>
      <el-empty description="请从左侧选择一个接口进行配置" :image-size="120" />
    </div>
  </div>
</template>

<script setup>
import { ref, reactive, computed, watch, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getAllConfigs, saveConfig, deleteConfig, testConfig, getOperationMetas } from '../api/index.js'

// ====================================================================
// 静态操作定义
// ====================================================================

const operationList = [
  { key: 'order_status',   label: '查询订单状态',   icon: '📋' },
  { key: 'logistics_info', label: '查询物流信息',   icon: '📦' },
  { key: 'submit_review',  label: '提交订单评价',   icon: '⭐' }
]

// ====================================================================
// 状态
// ====================================================================

const selectedOp = ref(null)
const configs = reactive({})
const operationMetas = ref([])
const activeTab = ref('basic')
const dirty = ref(false)
const saving = ref(false)
const testing = ref(false)
const testResult = ref(null)
const showTestDialog = ref(false)

const currentConfig = ref(null)
const headerList = ref([])

const testParams = reactive({
  orderId: 'ORD001',
  rating: 5,
  content: ''
})

// ====================================================================
// 计算属性
// ====================================================================

const currentMeta = computed(() =>
  operationMetas.value.find(m => m.operation === selectedOp.value)
)

const headerCount = computed(() =>
  headerList.value.filter(h => h.key.trim()).length
)

// ====================================================================
// 生命周期
// ====================================================================

onMounted(async () => {
  await loadConfigs()
  await loadMetas()
  selectOperation(operationList[0].key)
})

// ====================================================================
// 方法
// ====================================================================

async function loadConfigs() {
  try {
    const { data } = await getAllConfigs()
    data.forEach(cfg => {
      configs[cfg.operation] = cfg
    })
  } catch (e) {
    ElMessage.warning('无法连接后端服务，请确认后端（8080 端口）已启动')
  }
}

async function loadMetas() {
  try {
    const { data } = await getOperationMetas()
    operationMetas.value = data
  } catch (e) {
    // 降级处理
    operationMetas.value = [
      { operation: 'order_status',   params: ['orderId'] },
      { operation: 'logistics_info', params: ['orderId'] },
      { operation: 'submit_review',  params: ['orderId', 'rating', 'content'] }
    ]
  }
}

function selectOperation(opKey) {
  if (dirty.value && selectedOp.value !== opKey) {
    // 简单切换，不提示（可改为弹窗确认）
  }
  selectedOp.value = opKey
  const cfg = configs[opKey]
  if (cfg) {
    currentConfig.value = JSON.parse(JSON.stringify(cfg))
    headerList.value = Object.entries(cfg.headers || {})
      .map(([key, value]) => ({ key, value }))
  } else {
    currentConfig.value = {
      operation: opKey,
      enabled: false,
      description: '',
      url: '',
      method: 'GET',
      headers: {},
      requestBody: ''
    }
    headerList.value = []
  }
  dirty.value = false
  testResult.value = null
  activeTab.value = 'basic'
}

function getMethodClass(method) {
  const map = { GET: 'method-get', POST: 'method-post', PUT: 'method-put', DELETE: 'method-del' }
  return map[method?.toUpperCase()] || 'method-get'
}

function addHeader() {
  headerList.value.push({ key: '', value: '' })
  dirty.value = true
}

function removeHeader(idx) {
  headerList.value.splice(idx, 1)
  dirty.value = true
}

function buildHeadersMap() {
  const map = {}
  headerList.value.forEach(h => {
    if (h.key.trim()) map[h.key.trim()] = h.value
  })
  return map
}

function resetForm() {
  selectOperation(selectedOp.value)
}

async function saveForm() {
  if (!currentConfig.value.url?.trim()) {
    ElMessage.warning('请填写接口 URL')
    return
  }
  saving.value = true
  try {
    currentConfig.value.headers = buildHeadersMap()
    const { data } = await saveConfig(selectedOp.value, currentConfig.value)
    configs[selectedOp.value] = data
    currentConfig.value = JSON.parse(JSON.stringify(data))
    dirty.value = false
    ElMessage.success('配置已保存')
  } catch (e) {
    ElMessage.error('保存失败：' + (e.response?.data?.error || e.message))
  } finally {
    saving.value = false
  }
}

function runTest() {
  if (!currentConfig.value.url?.trim()) {
    ElMessage.warning('请先填写接口 URL')
    return
  }
  showTestDialog.value = true
}

async function confirmTest() {
  showTestDialog.value = false
  testing.value = true
  testResult.value = null

  try {
    // 先临时保存当前配置（不改变 enabled 状态）
    const tempConfig = {
      ...currentConfig.value,
      headers: buildHeadersMap()
    }
    await saveConfig(selectedOp.value, tempConfig)

    const { data } = await testConfig(selectedOp.value, {
      orderId: testParams.orderId || 'ORD001',
      rating: testParams.rating || 5,
      content: testParams.content || ''
    })
    testResult.value = data
    configs[selectedOp.value] = { ...configs[selectedOp.value], ...tempConfig }
  } catch (e) {
    testResult.value = {
      success: false,
      errorMessage: e.response?.data?.error || e.message,
      elapsedMs: 0
    }
  } finally {
    testing.value = false
  }
}

function formatJson(str) {
  if (!str) return ''
  try {
    return JSON.stringify(JSON.parse(str), null, 2)
  } catch {
    return str
  }
}
</script>

<style scoped>
.admin-page {
  display: flex;
  height: 100%;
  background: #1e1e1e;
  color: #d4d4d4;
}

/* ── 侧边栏 ── */
.admin-sidebar {
  width: 260px;
  background: #252526;
  border-right: 1px solid #3c3c3c;
  display: flex;
  flex-direction: column;
  overflow-y: auto;
  flex-shrink: 0;
}

.sidebar-title {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 14px 16px;
  font-size: 13px;
  font-weight: 600;
  color: #ccc;
  border-bottom: 1px solid #3c3c3c;
  text-transform: uppercase;
  letter-spacing: 0.5px;
}

.op-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px 14px;
  cursor: pointer;
  border-left: 3px solid transparent;
  transition: all 0.15s;
  gap: 10px;
}

.op-item:hover {
  background: #2a2d2e;
}

.op-item.active {
  background: #37373d;
  border-left-color: #1677ff;
}

.op-item-left {
  display: flex;
  align-items: center;
  gap: 10px;
  min-width: 0;
  flex: 1;
}

.method-tag {
  font-family: monospace;
  font-size: 10px;
  font-weight: 700;
  padding: 2px 6px;
  border-radius: 3px;
  flex-shrink: 0;
}

.method-get  { background: #1a4a2a; color: #4ec9b0; }
.method-post { background: #2a3a1a; color: #dce775; }
.method-put  { background: #3a2a1a; color: #f4a261; }
.method-del  { background: #3a1a1a; color: #f48771; }

.op-item-info {
  min-width: 0;
  flex: 1;
}

.op-item-name {
  font-size: 13px;
  color: #ccc;
  font-weight: 500;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.op-item-url {
  font-size: 11px;
  color: #666;
  font-family: monospace;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  margin-top: 2px;
}

.sidebar-info {
  margin-top: auto;
  padding: 16px 14px;
  display: flex;
  gap: 8px;
  font-size: 11px;
  color: #555;
  border-top: 1px solid #3c3c3c;
  line-height: 1.5;
}

/* ── 主面板 ── */
.admin-main {
  flex: 1;
  display: flex;
  flex-direction: column;
  overflow: hidden;
  background: #1e1e1e;
}

.empty-main {
  justify-content: center;
  align-items: center;
}

/* ── URL 栏 ── */
.url-bar {
  display: flex;
  align-items: center;
  gap: 0;
  padding: 12px 16px;
  background: #252526;
  border-bottom: 1px solid #3c3c3c;
  gap: 8px;
}

.method-select {
  width: 110px;
  flex-shrink: 0;
}

.method-select :deep(.el-input__wrapper) {
  background: #2d2d30;
  border: 1px solid #3c3c3c;
  box-shadow: none;
  border-radius: 6px 0 0 6px;
}

.method-select :deep(.el-input__inner) {
  color: #dce775;
  font-family: monospace;
  font-weight: 600;
}

.url-input {
  flex: 1;
}

.url-input :deep(.el-input__wrapper) {
  background: #2d2d30;
  border: 1px solid #3c3c3c;
  box-shadow: none;
  border-radius: 0 6px 6px 0;
}

.url-input :deep(.el-input__inner) {
  color: #d4d4d4;
  font-family: monospace;
  font-size: 13px;
}

.send-btn {
  flex-shrink: 0;
  border-radius: 6px;
  font-weight: 600;
  background: #1677ff;
  border-color: #1677ff;
}

/* ── Tabs ── */
.config-tabs-container {
  flex: 1;
  display: flex;
  flex-direction: column;
  overflow: hidden;
  min-height: 0;
}

.config-tabs {
  flex: 1;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.config-tabs :deep(.el-tabs__header) {
  background: #252526;
  margin: 0;
  border-bottom: 1px solid #3c3c3c;
  padding: 0 16px;
}

.config-tabs :deep(.el-tabs__nav-wrap::after) {
  display: none;
}

.config-tabs :deep(.el-tabs__item) {
  color: #888;
  font-size: 13px;
  padding: 0 16px;
  height: 40px;
  line-height: 40px;
}

.config-tabs :deep(.el-tabs__item.is-active) {
  color: #fff;
}

.config-tabs :deep(.el-tabs__active-bar) {
  background: #1677ff;
}

.config-tabs :deep(.el-tabs__content) {
  flex: 1;
  overflow-y: auto;
  background: #1e1e1e;
}

.tab-badge {
  margin-left: 4px;
}

.tab-content {
  padding: 20px 24px;
}

/* ── 基本信息 Tab ── */
.config-tabs :deep(.el-form-item__label) {
  color: #bbb;
  font-size: 13px;
}

.config-tabs :deep(.el-input__wrapper) {
  background: #2d2d30;
  border: 1px solid #3c3c3c;
  box-shadow: none;
}

.config-tabs :deep(.el-input__inner) {
  color: #d4d4d4;
}

.config-tabs :deep(.el-input__wrapper:hover),
.config-tabs :deep(.el-input__wrapper.is-focus) {
  border-color: #1677ff;
}

.enable-row {
  display: flex;
  flex-direction: column;
  gap: 12px;
  width: 100%;
}

.enable-alert {
  max-width: 500px;
}

.config-tabs :deep(.el-switch__label) {
  color: #888;
}

.config-tabs :deep(.el-switch__label.is-active) {
  color: #fff;
}

.placeholder-help {
  display: flex;
  flex-direction: column;
  gap: 10px;
  background: #252526;
  border-radius: 8px;
  padding: 14px;
  font-size: 13px;
}

.placeholder-item {
  display: flex;
  align-items: center;
  gap: 12px;
  color: #bbb;
}

.code-inline {
  background: #1a3a4a;
  color: #4ec9b0;
  padding: 2px 8px;
  border-radius: 4px;
  font-family: monospace;
  font-size: 12px;
  white-space: nowrap;
}

.divider-title {
  color: #666;
  font-size: 12px;
}

.config-tabs :deep(.el-divider__text) {
  background: #1e1e1e;
}

.config-tabs :deep(.el-divider.el-divider--horizontal) {
  border-top-color: #3c3c3c;
}

/* ── Headers Tab ── */
.headers-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 12px;
}

.section-hint {
  font-size: 12px;
  color: #666;
}

.headers-table {
  border: 1px solid #3c3c3c;
  border-radius: 6px;
  overflow: hidden;
}

.headers-row {
  display: flex;
  align-items: center;
  gap: 0;
  border-bottom: 1px solid #3c3c3c;
}

.headers-row:last-child {
  border-bottom: none;
}

.headers-row-head {
  background: #252526;
  padding: 6px 12px;
  font-size: 11px;
  color: #666;
  font-weight: 600;
  text-transform: uppercase;
}

.col-key {
  flex: 4;
  min-width: 0;
}

.col-val {
  flex: 6;
  min-width: 0;
  border-left: 1px solid #3c3c3c;
}

.col-action {
  flex: 0 0 40px;
  display: flex;
  justify-content: center;
  border-left: 1px solid #3c3c3c;
}

.headers-row :deep(.el-input__wrapper) {
  border: none;
  background: transparent;
  box-shadow: none;
  border-radius: 0;
}

.empty-tip {
  padding: 20px;
  text-align: center;
  color: #555;
  font-size: 13px;
}

/* ── Body Tab ── */
.body-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 12px;
}

.body-disabled {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 12px;
  height: 160px;
  color: #555;
  font-size: 13px;
}

.body-editor :deep(.el-textarea__inner) {
  background: #1a1a1a;
  color: #4ec9b0;
  font-family: 'Consolas', 'Monaco', monospace;
  font-size: 13px;
  border: 1px solid #3c3c3c;
  border-radius: 6px;
  line-height: 1.6;
}

.body-hint {
  margin-top: 12px;
  padding: 12px 14px;
  background: #252526;
  border-radius: 6px;
  font-size: 12px;
  color: #666;
  line-height: 1.8;
}

.body-hint code {
  color: #4ec9b0;
  font-family: monospace;
}

/* ── 保存栏 ── */
.save-bar {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 10px;
  padding: 12px 24px;
  background: #252526;
  border-top: 1px solid #3c3c3c;
  flex-shrink: 0;
}

.dirty-hint {
  font-size: 12px;
  color: #f4a261;
  margin-right: auto;
}

.save-bar :deep(.el-button--default) {
  background: #2d2d30;
  border-color: #3c3c3c;
  color: #bbb;
}

/* ── Response 面板 ── */
.response-panel {
  background: #1a1a1a;
  border-top: 2px solid #3c3c3c;
  max-height: 280px;
  display: flex;
  flex-direction: column;
  flex-shrink: 0;
}

.response-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 8px 16px;
  background: #252526;
  border-bottom: 1px solid #3c3c3c;
}

.response-title {
  font-size: 12px;
  font-weight: 600;
  color: #888;
  text-transform: uppercase;
  letter-spacing: 0.5px;
}

.response-meta {
  display: flex;
  align-items: center;
  gap: 10px;
}

.elapsed {
  font-size: 12px;
  color: #666;
  font-family: monospace;
}

.response-body {
  flex: 1;
  overflow-y: auto;
  padding: 12px 16px;
}

.response-body pre {
  font-family: 'Consolas', 'Monaco', monospace;
  font-size: 12px;
  color: #4ec9b0;
  white-space: pre-wrap;
  word-break: break-all;
  margin: 0;
  line-height: 1.6;
}

.response-error {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 12px 16px;
  color: #f48771;
  font-size: 13px;
}

.response-loading {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 16px;
  color: #666;
  font-size: 13px;
}

.rotating {
  animation: spin 1s linear infinite;
}

@keyframes spin {
  from { transform: rotate(0deg); }
  to { transform: rotate(360deg); }
}

/* ── 暗色主题覆盖 ── */
:deep(.el-dialog) {
  background: #2d2d30;
  border: 1px solid #3c3c3c;
}

:deep(.el-dialog__title) {
  color: #d4d4d4;
}

:deep(.el-dialog__body) {
  color: #bbb;
}

:deep(.el-dialog .el-form-item__label) {
  color: #bbb;
}

:deep(.el-dialog .el-input__wrapper) {
  background: #1e1e1e;
  border: 1px solid #3c3c3c;
  box-shadow: none;
}

:deep(.el-dialog .el-input__inner) {
  color: #d4d4d4;
}

:deep(.el-dialog .el-textarea__inner) {
  background: #1e1e1e;
  border: 1px solid #3c3c3c;
  box-shadow: none;
  color: #d4d4d4;
}

:deep(.el-dialog .el-button--default) {
  background: #2d2d30;
  border-color: #3c3c3c;
  color: #bbb;
}

:deep(.el-rate .el-rate__icon) {
  color: #666;
}

:deep(.el-rate .el-rate__icon.is-active) {
  color: #f4a261;
}
</style>
