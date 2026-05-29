<template>
  <div class="admin-page">
    <!-- ── 左侧操作列表 ── -->
    <aside class="admin-sidebar">
      <div class="sidebar-title">
        <i class="el-icon-files"></i>
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
          <span class="method-tag" :class="getMethodClass(configs[op.key] && configs[op.key].method || 'GET')">
            {{ configs[op.key] && configs[op.key].method || 'GET' }}
          </span>
          <div class="op-item-info">
            <div class="op-item-name">{{ op.label }}</div>
            <div class="op-item-url">{{ configs[op.key] && configs[op.key].url || '未配置 URL' }}</div>
          </div>
        </div>
        <el-tag
          :type="configs[op.key] && configs[op.key].enabled ? 'success' : 'info'"
          size="mini"
          effect="light"
        >
          {{ configs[op.key] && configs[op.key].enabled ? '启用' : '关闭' }}
        </el-tag>
      </div>

      <div class="sidebar-info">
        <i class="el-icon-info"></i>
        <span>配置后端接口，客服机器人将调用真实 API 替代 Mock 数据</span>
      </div>
    </aside>

    <!-- ── 右侧配置面板 ── -->
    <div class="admin-main" v-if="selectedOp && currentConfig">
      <!-- URL 栏 -->
      <div class="url-bar">
        <el-select v-model="currentConfig.method" class="method-select" @change="dirty = true">
          <el-option label="GET"    value="GET" />
          <el-option label="POST"   value="POST" />
          <el-option label="PUT"    value="PUT" />
          <el-option label="DELETE" value="DELETE" />
        </el-select>
        <el-input
          v-model="currentConfig.url"
          placeholder="输入接口 URL，如 http://your-mall.com/api/orders/{orderId}/status"
          class="url-input"
          @input="dirty = true"
        >
          <i slot="prefix" class="el-icon-link" style="color:#999;"></i>
        </el-input>
        <el-button
          type="primary"
          :loading="testing"
          :disabled="!currentConfig.url"
          @click="runTest"
          class="send-btn"
        >
          <i class="el-icon-caret-right"></i>
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
                      title="已启用外部 API —— 客服机器人将调用上方 URL 获取真实数据"
                      type="warning"
                      :closable="false"
                      show-icon
                      class="enable-alert"
                    />
                    <el-alert
                      v-else
                      title="当前使用内置 Mock 数据，配置并保存后可切换到外部 API"
                      type="info"
                      :closable="false"
                      show-icon
                      class="enable-alert"
                    />
                  </div>
                </el-form-item>
              </el-form>

              <el-divider content-position="left">
                <span class="divider-title">URL 占位符说明</span>
              </el-divider>
              <div class="placeholder-help">
                <div v-for="p in (currentMeta && currentMeta.params || [])" :key="p" class="placeholder-item">
                  <code class="code-inline">{{ '{' + p + '}' }}</code>
                  <span>URL 路径参数：如 /orders/<code>{{ '{' + p + '}' }}</code>/status</span>
                </div>
                <div v-for="p in (currentMeta && currentMeta.params || [])" :key="'body-' + p" class="placeholder-item">
                  <code class="code-inline">{{ '{' + '{' + p + '}' + '}' }}</code>
                  <span>请求体模板变量（POST Body 中使用）</span>
                </div>
              </div>
            </div>
          </el-tab-pane>

          <!-- Tab 2: 请求头 -->
          <el-tab-pane name="headers">
            <span slot="label">
              Headers
              <el-badge
                v-if="headerCount > 0"
                :value="headerCount"
                class="tab-badge"
                type="primary"
              />
            </span>
            <div class="tab-content">
              <div class="headers-toolbar">
                <span class="section-hint">配置请求头（如 Authorization、Content-Type）</span>
                <el-button size="small" @click="addHeader" type="primary" plain>
                  <i class="el-icon-plus"></i> 添加
                </el-button>
              </div>
              <div class="headers-table">
                <div class="headers-row headers-row-head">
                  <span class="col-key">Key</span>
                  <span class="col-val">Value</span>
                  <span class="col-action"></span>
                </div>
                <div v-for="(header, idx) in headerList" :key="idx" class="headers-row">
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
                    type="text"
                    size="small"
                    class="col-action del-btn"
                    @click="removeHeader(idx)"
                  >
                    <i class="el-icon-delete" style="color:#f56c6c;"></i>
                  </el-button>
                </div>
                <div v-if="headerList.length === 0" class="empty-tip">
                  暂无请求头，点击「添加」按钮新增
                </div>
              </div>
            </div>
          </el-tab-pane>

          <!-- Tab 3: 请求体 -->
          <el-tab-pane label="Body" name="body">
            <div class="tab-content">
              <div class="body-toolbar">
                <span class="section-hint">POST 请求体模板（JSON），支持占位符替换</span>
                <div class="body-toolbar-right">
                  <el-tooltip content="格式化 JSON" placement="top">
                    <el-button
                      size="small"
                      plain
                      class="beautify-btn"
                      :disabled="!currentConfig.requestBody"
                      @click="beautifyBody"
                    >
                      <i class="el-icon-magic-stick" style="margin-right:4px;"></i>
                      美化
                    </el-button>
                  </el-tooltip>
                  <el-tag type="info" size="mini" effect="light">application/json</el-tag>
                </div>
              </div>
              <div
                v-if="currentConfig.method !== 'POST' && currentConfig.method !== 'PUT'"
                class="body-disabled"
              >
                <i class="el-icon-warning" style="font-size:32px;color:#ccc;"></i>
                <span>当前请求方法（{{ currentConfig.method }}）无请求体</span>
              </div>
              <el-input
                v-else
                v-model="currentConfig.requestBody"
                type="textarea"
                :rows="10"
                :placeholder="bodyPlaceholder"
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

        <!-- 保存栏 -->
        <div class="save-bar" :class="{ dirty: dirty }">
          <span v-if="dirty" class="dirty-hint">⚠ 有未保存的修改</span>
          <el-button @click="resetForm">重置</el-button>
          <el-button type="primary" :loading="saving" @click="saveForm">
            <i class="el-icon-check"></i> 保存配置
          </el-button>
        </div>
      </div>

      <!-- 测试响应区 -->
      <div
        class="response-panel"
        v-if="testResult !== null || testing"
        :style="{ height: responseHeight + 'px' }"
      >
        <div class="resize-handle" @mousedown="startResize" title="拖动调整高度">
          <div class="resize-dots"></div>
        </div>

        <div class="response-header">
          <span class="response-title">RESPONSE</span>
          <div class="response-meta" v-if="testResult">
            <el-tag :type="testResult.success ? 'success' : 'danger'" size="mini" effect="light">
              {{ testResult.success ? '200 OK' : 'Error' }}
            </el-tag>
            <span class="elapsed">{{ testResult.elapsedMs }} ms</span>
            <el-tooltip content="复制响应内容" placement="top">
              <el-button type="text" size="mini" @click="copyResponse">
                <i class="el-icon-copy-document" style="color:#666;"></i>
              </el-button>
            </el-tooltip>
            <el-tooltip :content="responseExpanded ? '收起' : '最大化'" placement="top">
              <el-button type="text" size="mini" @click="toggleExpand">
                <i :class="responseExpanded ? 'el-icon-minus' : 'el-icon-full-screen'" style="color:#666;"></i>
              </el-button>
            </el-tooltip>
            <el-tooltip content="关闭" placement="top">
              <el-button type="text" size="mini" @click="testResult = null">
                <i class="el-icon-close" style="color:#666;"></i>
              </el-button>
            </el-tooltip>
          </div>
        </div>

        <div v-if="testing" class="response-loading">
          <i class="el-icon-loading rotating"></i>
          <span>正在发送请求...</span>
        </div>

        <div v-else-if="testResult" class="response-content">
          <div v-if="testResult.success" class="response-body">
            <pre>{{ formatJson(testResult.responseBody) }}</pre>
          </div>
          <div v-else class="response-error">
            <i class="el-icon-circle-close-filled"></i>
            <span>{{ testResult.errorMessage }}</span>
          </div>
        </div>
      </div>

      <!-- 测试参数弹窗 -->
      <el-dialog
        :visible.sync="showTestDialog"
        title="填写测试参数"
        width="420px"
        :close-on-click-modal="false"
      >
        <el-form :model="testParams" label-width="80px">
          <el-form-item label="订单号" v-if="currentMeta && currentMeta.params.includes('orderId')">
            <el-input v-model="testParams.orderId" placeholder="如 ORD001" />
          </el-form-item>
          <el-form-item label="评分" v-if="currentMeta && currentMeta.params.includes('rating')">
            <el-rate v-model="testParams.rating" show-text />
          </el-form-item>
          <el-form-item label="评价内容" v-if="currentMeta && currentMeta.params.includes('content')">
            <el-input v-model="testParams.content" type="textarea" :rows="3"
              placeholder="评价内容（可选，留空自动填充）" />
          </el-form-item>
        </el-form>
        <span slot="footer" class="dialog-footer">
          <el-button @click="showTestDialog = false">取消</el-button>
          <el-button type="primary" @click="confirmTest">发送</el-button>
        </span>
      </el-dialog>
    </div>

    <!-- 未选中空状态 -->
    <div class="admin-main empty-main" v-else>
      <el-empty description="请从左侧选择一个接口进行配置" :image-size="120" />
    </div>
  </div>
</template>

<script>
import { getAllConfigs, saveConfig, testConfig, getOperationMetas } from '../api/index.js'

export default {
  name: 'AdminConfig',
  data() {
    return {
      operationList: [
        { key: 'order_status',   label: '查询订单状态', icon: '📋' },
        { key: 'logistics_info', label: '查询物流信息', icon: '📦' },
        { key: 'submit_review',  label: '提交订单评价', icon: '⭐' }
      ],
      selectedOp:      null,
      configs:         {},
      operationMetas:  [],
      activeTab:       'basic',
      dirty:           false,
      saving:          false,
      testing:         false,
      testResult:      null,
      showTestDialog:  false,
      currentConfig:   null,
      headerList:      [],
      testParams:      { orderId: 'ORD001', rating: 5, content: '' },
      // placeholder 中含有 {{...}} 字面量，不能直接写在模板属性里（Vue 2 会当插值处理）
      bodyPlaceholder: '{"orderId": "{{orderId}}", "rating": {{rating}}, "content": "{{content}}"}',
      // 响应面板高度（拖拽）
      responseHeight:  300,
      responseExpanded: false,
      prevHeight:      300,
      resizing:        false,
      startY:          0,
      startH:          0
    }
  },
  computed: {
    currentMeta() {
      return this.operationMetas.find(m => m.operation === this.selectedOp)
    },
    headerCount() {
      return this.headerList.filter(h => h.key.trim()).length
    }
  },
  async mounted() {
    document.addEventListener('mousemove', this.onMouseMove)
    document.addEventListener('mouseup',   this.onMouseUp)
    await this.loadConfigs()
    await this.loadMetas()
    this.selectOperation(this.operationList[0].key)
  },
  beforeDestroy() {
    document.removeEventListener('mousemove', this.onMouseMove)
    document.removeEventListener('mouseup',   this.onMouseUp)
  },
  methods: {
    // ── 数据加载 ──────────────────────────────
    async loadConfigs() {
      try {
        const { data } = await getAllConfigs()
        data.forEach(cfg => {
          this.$set(this.configs, cfg.operation, cfg)
        })
      } catch {
        this.$message.warning('无法连接后端服务，请确认后端（8080 端口）已启动')
      }
    },
    async loadMetas() {
      try {
        const { data } = await getOperationMetas()
        this.operationMetas = data
      } catch {
        this.operationMetas = [
          { operation: 'order_status',   params: ['orderId'] },
          { operation: 'logistics_info', params: ['orderId'] },
          { operation: 'submit_review',  params: ['orderId', 'rating', 'content'] }
        ]
      }
    },

    // ── 操作选择 ──────────────────────────────
    selectOperation(opKey) {
      this.selectedOp = opKey
      const cfg = this.configs[opKey]
      if (cfg) {
        this.currentConfig = JSON.parse(JSON.stringify(cfg))
        this.headerList = Object.entries(cfg.headers || {}).map(([key, value]) => ({ key, value }))
      } else {
        this.currentConfig = { operation: opKey, enabled: false, description: '', url: '', method: 'GET', headers: {}, requestBody: '' }
        this.headerList = []
      }
      this.dirty = false
      this.testResult = null
      this.activeTab = 'basic'
    },
    getMethodClass(method) {
      const map = { GET: 'method-get', POST: 'method-post', PUT: 'method-put', DELETE: 'method-del' }
      return map[method && method.toUpperCase()] || 'method-get'
    },

    // ── Headers ───────────────────────────────
    addHeader() {
      this.headerList.push({ key: '', value: '' })
      this.dirty = true
    },
    removeHeader(idx) {
      this.headerList.splice(idx, 1)
      this.dirty = true
    },
    buildHeadersMap() {
      const map = {}
      this.headerList.forEach(h => { if (h.key.trim()) map[h.key.trim()] = h.value })
      return map
    },

    // ── 保存 / 重置 ───────────────────────────
    resetForm() { this.selectOperation(this.selectedOp) },
    async saveForm() {
      if (!this.currentConfig.url || !this.currentConfig.url.trim()) {
        this.$message.warning('请填写接口 URL')
        return
      }
      this.saving = true
      try {
        this.currentConfig.headers = this.buildHeadersMap()
        const { data } = await saveConfig(this.selectedOp, this.currentConfig)
        this.$set(this.configs, this.selectedOp, data)
        this.currentConfig = JSON.parse(JSON.stringify(data))
        this.dirty = false
        this.$message.success('配置已保存')
      } catch (e) {
        this.$message.error('保存失败：' + (e.response && e.response.data && e.response.data.error || e.message))
      } finally {
        this.saving = false
      }
    },

    // ── 测试 ──────────────────────────────────
    runTest() {
      if (!this.currentConfig.url || !this.currentConfig.url.trim()) {
        this.$message.warning('请先填写接口 URL')
        return
      }
      this.showTestDialog = true
    },
    async confirmTest() {
      this.showTestDialog = false
      this.testing = true
      this.testResult = null
      try {
        const tempConfig = { ...this.currentConfig, headers: this.buildHeadersMap() }
        await saveConfig(this.selectedOp, tempConfig)
        const { data } = await testConfig(this.selectedOp, {
          orderId: this.testParams.orderId || 'ORD001',
          rating:  this.testParams.rating  || 5,
          content: this.testParams.content || ''
        })
        this.testResult = data
        this.$set(this.configs, this.selectedOp, { ...this.configs[this.selectedOp], ...tempConfig })
      } catch (e) {
        this.testResult = {
          success: false,
          errorMessage: e.response && e.response.data && e.response.data.error || e.message,
          elapsedMs: 0
        }
      } finally {
        this.testing = false
      }
    },

    // ── JSON 美化 ─────────────────────────────
    beautifyBody() {
      const raw = this.currentConfig && this.currentConfig.requestBody
      if (!raw) return
      const PLACEHOLDER_RE = /\{\{(\w+)\}\}/g
      const placeholders = []
      const safe = raw.replace(PLACEHOLDER_RE, (m) => {
        placeholders.push(m)
        return `"__PH_${placeholders.length - 1}__"`
      })
      try {
        let pretty = JSON.stringify(JSON.parse(safe), null, 2)
        pretty = pretty.replace(/"__PH_(\d+)__"/g, (_, i) => placeholders[+i])
        this.currentConfig.requestBody = pretty
        this.dirty = true
        this.$message.success('JSON 已格式化')
      } catch {
        this.$message.warning('JSON 格式有误，无法美化')
      }
    },
    formatJson(str) {
      if (!str) return ''
      try { return JSON.stringify(JSON.parse(str), null, 2) } catch { return str }
    },

    // ── 响应面板工具 ──────────────────────────
    copyResponse() {
      const text = this.testResult && this.testResult.responseBody || ''
      navigator.clipboard.writeText(this.formatJson(text))
      this.$message.success('已复制到剪贴板')
    },
    toggleExpand() {
      if (this.responseExpanded) {
        this.responseHeight   = this.prevHeight
        this.responseExpanded = false
      } else {
        this.prevHeight       = this.responseHeight
        this.responseHeight   = window.innerHeight - 160
        this.responseExpanded = true
      }
    },

    // ── 拖拽调整响应面板高度 ──────────────────
    startResize(e) {
      this.resizing = true
      this.startY   = e.clientY
      this.startH   = this.responseHeight
      document.body.style.cursor     = 'row-resize'
      document.body.style.userSelect = 'none'
    },
    onMouseMove(e) {
      if (!this.resizing) return
      const delta = this.startY - e.clientY
      this.responseHeight   = Math.min(Math.max(this.startH + delta, 120), window.innerHeight - 200)
      this.responseExpanded = false
    },
    onMouseUp() {
      if (this.resizing) {
        this.resizing = false
        document.body.style.cursor     = ''
        document.body.style.userSelect = ''
      }
    }
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

.op-item:hover  { background: #2a2d2e; }
.op-item.active { background: #37373d; border-left-color: #1677ff; }

.op-item-left { display: flex; align-items: center; gap: 10px; min-width: 0; flex: 1; }

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

.op-item-info { min-width: 0; flex: 1; }

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

.empty-main { justify-content: center; align-items: center; }

/* ── URL 栏 ── */
.url-bar {
  display: flex;
  align-items: center;
  padding: 12px 16px;
  background: #252526;
  border-bottom: 1px solid #3c3c3c;
  gap: 8px;
}

.method-select { width: 110px; flex-shrink: 0; }

.method-select ::v-deep .el-input__inner {
  background: #2d2d30;
  border-color: #3c3c3c;
  color: #dce775;
  font-family: monospace;
  font-weight: 600;
  border-radius: 6px 0 0 6px;
}

.url-input { flex: 1; }

.url-input ::v-deep .el-input__inner {
  background: #2d2d30;
  border-color: #3c3c3c;
  color: #d4d4d4;
  font-family: monospace;
  font-size: 13px;
  border-radius: 0 6px 6px 0;
}

.url-input ::v-deep .el-input__inner:focus {
  border-color: #1677ff;
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

.config-tabs ::v-deep .el-tabs__header {
  background: #252526;
  margin: 0;
  border-bottom: 1px solid #3c3c3c;
  padding: 0 16px;
}

.config-tabs ::v-deep .el-tabs__nav-wrap::after { display: none; }

.config-tabs ::v-deep .el-tabs__item {
  color: #888;
  font-size: 13px;
  padding: 0 16px;
  height: 40px;
  line-height: 40px;
}

.config-tabs ::v-deep .el-tabs__item.is-active { color: #fff; }
.config-tabs ::v-deep .el-tabs__active-bar { background: #1677ff; }

.config-tabs ::v-deep .el-tabs__content {
  flex: 1;
  overflow-y: auto;
  background: #1e1e1e;
}

.tab-badge { margin-left: 4px; }
.tab-content { padding: 20px 24px; }

/* ── 基本信息 Tab ── */
.config-tabs ::v-deep .el-form-item__label { color: #bbb; font-size: 13px; }

.config-tabs ::v-deep .el-input__inner {
  background: #2d2d30;
  border-color: #3c3c3c;
  color: #d4d4d4;
}

.config-tabs ::v-deep .el-input__inner:focus { border-color: #1677ff; }

.enable-row { display: flex; flex-direction: column; gap: 12px; width: 100%; }
.enable-alert { max-width: 500px; }

.config-tabs ::v-deep .el-switch__label { color: #888; }
.config-tabs ::v-deep .el-switch__label.is-active { color: #fff; }

.placeholder-help {
  display: flex;
  flex-direction: column;
  gap: 10px;
  background: #252526;
  border-radius: 8px;
  padding: 14px;
  font-size: 13px;
}

.placeholder-item { display: flex; align-items: center; gap: 12px; color: #bbb; }

.code-inline {
  background: #1a3a4a;
  color: #4ec9b0;
  padding: 2px 8px;
  border-radius: 4px;
  font-family: monospace;
  font-size: 12px;
  white-space: nowrap;
}

.divider-title { color: #666; font-size: 12px; }

.config-tabs ::v-deep .el-divider__text { background: #1e1e1e; }
.config-tabs ::v-deep .el-divider { border-top-color: #3c3c3c; }

/* ── Headers Tab ── */
.headers-toolbar { display: flex; align-items: center; justify-content: space-between; margin-bottom: 12px; }
.section-hint { font-size: 12px; color: #666; }

.headers-table { border: 1px solid #3c3c3c; border-radius: 6px; overflow: hidden; }

.headers-row {
  display: flex;
  align-items: center;
  border-bottom: 1px solid #3c3c3c;
}

.headers-row:last-child { border-bottom: none; }

.headers-row-head {
  background: #252526;
  padding: 6px 12px;
  font-size: 11px;
  color: #666;
  font-weight: 600;
  text-transform: uppercase;
}

.col-key    { flex: 4; min-width: 0; }
.col-val    { flex: 6; min-width: 0; border-left: 1px solid #3c3c3c; }
.col-action { flex: 0 0 40px; display: flex; justify-content: center; border-left: 1px solid #3c3c3c; }

.headers-row ::v-deep .el-input__inner {
  border: none;
  background: transparent;
  color: #d4d4d4;
  border-radius: 0;
  box-shadow: none;
}

.empty-tip { padding: 20px; text-align: center; color: #555; font-size: 13px; }

/* ── Body Tab ── */
.body-toolbar { display: flex; align-items: center; justify-content: space-between; margin-bottom: 12px; }
.body-toolbar-right { display: flex; align-items: center; gap: 8px; }

.beautify-btn {
  background: #2d2d30 !important;
  border-color: #3c3c3c !important;
  color: #4ec9b0 !important;
  font-size: 12px;
}

.beautify-btn:hover { border-color: #4ec9b0 !important; }

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

.body-editor ::v-deep .el-textarea__inner {
  background: #1a1a1a;
  color: #4ec9b0;
  font-family: 'Consolas', 'Monaco', monospace;
  font-size: 13px;
  border-color: #3c3c3c;
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

.body-hint code { color: #4ec9b0; font-family: monospace; }

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

.dirty-hint { font-size: 12px; color: #f4a261; margin-right: auto; }

.save-bar ::v-deep .el-button--default {
  background: #2d2d30;
  border-color: #3c3c3c;
  color: #bbb;
}

/* ── Response 面板 ── */
.response-panel {
  background: #1a1a1a;
  border-top: 2px solid #3c3c3c;
  display: flex;
  flex-direction: column;
  flex-shrink: 0;
  min-height: 120px;
  position: relative;
}

.resize-handle {
  height: 8px;
  background: #252526;
  border-bottom: 1px solid #3c3c3c;
  cursor: row-resize;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  transition: background 0.15s;
}

.resize-handle:hover { background: #37373d; }

.resize-dots {
  width: 32px;
  height: 3px;
  border-radius: 2px;
  background: repeating-linear-gradient(
    90deg,
    #555 0px, #555 3px,
    transparent 3px, transparent 6px
  );
}

.response-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 6px 12px;
  background: #252526;
  border-bottom: 1px solid #3c3c3c;
  flex-shrink: 0;
}

.response-title { font-size: 11px; font-weight: 700; color: #888; letter-spacing: 1px; }
.response-meta  { display: flex; align-items: center; gap: 6px; }
.elapsed        { font-size: 12px; color: #666; font-family: monospace; }

.response-content { flex: 1; overflow-y: auto; min-height: 0; }

.response-body { height: 100%; padding: 12px 16px; }

.response-body pre {
  font-family: 'Consolas', 'Monaco', monospace;
  font-size: 12px;
  color: #4ec9b0;
  white-space: pre-wrap;
  word-break: break-all;
  margin: 0;
  line-height: 1.7;
}

.response-error {
  display: flex;
  align-items: flex-start;
  gap: 8px;
  padding: 16px;
  color: #f48771;
  font-size: 13px;
  line-height: 1.6;
  word-break: break-all;
}

.response-loading {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 20px 16px;
  color: #666;
  font-size: 13px;
  flex-shrink: 0;
}

.rotating { animation: spin 1s linear infinite; }

@keyframes spin {
  from { transform: rotate(0deg); }
  to   { transform: rotate(360deg); }
}

/* ── 弹窗暗色主题 ── */
::v-deep .el-dialog {
  background: #2d2d30;
  border: 1px solid #3c3c3c;
}

::v-deep .el-dialog__title { color: #d4d4d4; }
::v-deep .el-dialog__body  { color: #bbb; }

::v-deep .el-dialog .el-form-item__label { color: #bbb; }

::v-deep .el-dialog .el-input__inner {
  background: #1e1e1e;
  border-color: #3c3c3c;
  color: #d4d4d4;
}

::v-deep .el-dialog .el-textarea__inner {
  background: #1e1e1e;
  border-color: #3c3c3c;
  color: #d4d4d4;
}

::v-deep .el-dialog .el-button--default {
  background: #2d2d30;
  border-color: #3c3c3c;
  color: #bbb;
}

::v-deep .el-rate .el-rate__icon { color: #666; }
::v-deep .el-rate .el-rate__icon.is-active { color: #f4a261; }
</style>
