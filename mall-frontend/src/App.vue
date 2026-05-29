<template>
  <div class="app-container">
    <!-- 登录页不显示 header -->
    <template v-if="$route.path !== '/login'">
      <header class="app-header">
        <div class="header-brand">
          <i class="el-icon-chat-dot-round" style="font-size:22px;color:#fff;"></i>
          <span class="brand-title">智能运维客服系统</span>
        </div>
        <nav class="header-nav">
          <span class="nav-link" :class="{ active: $route.path === '/' }" @click="goto('/')">
            <i class="el-icon-chat-line-round"></i>
            <span>用户客服</span>
          </span>
          <span class="nav-link" :class="{ active: $route.path === '/admin' }" @click="goto('/admin')">
            <i class="el-icon-setting"></i>
            <span>接口配置</span>
          </span>
        </nav>
        <div class="header-user">
          <el-dropdown v-if="user" trigger="click" @command="handleCommand">
            <span class="user-info">
              <el-avatar :size="28" class="user-avatar">{{ userInitial }}</el-avatar>
              <span class="user-name">{{ user.nickname || user.username }}</span>
              <i class="el-icon-arrow-down" style="font-size:12px;"></i>
            </span>
            <el-dropdown-menu slot="dropdown">
              <el-dropdown-item :disabled="true">
                <i class="el-icon-user"></i>
                {{ user.username }}
                <el-tag v-if="user.role === 'ADMIN'" size="mini" type="warning" style="margin-left:6px">管理员</el-tag>
              </el-dropdown-item>
              <el-dropdown-item divided command="logout">
                <i class="el-icon-switch-button"></i>
                退出登录
              </el-dropdown-item>
            </el-dropdown-menu>
          </el-dropdown>
        </div>
      </header>
    </template>
    <main class="app-main">
      <router-view />
    </main>
  </div>
</template>

<script>
import { getUser, logout } from './utils/auth.js'

export default {
  name: 'App',
  data() {
    return {
      user: getUser()
    }
  },
  computed: {
    userInitial() {
      const u = this.user
      if (!u) return '?'
      const name = u.nickname || u.username
      return name.charAt(0).toUpperCase()
    }
  },
  watch: {
    '$route.path'() {
      // 路由跳转后同步用户信息（localStorage 无响应性，监听路由变化手动刷新）
      this.user = getUser()
    }
  },
  methods: {
    goto(path) {
      if (this.$route.path !== path) {
        this.$router.push(path).catch(() => {})
      }
    },
    handleCommand(cmd) {
      if (cmd === 'logout') {
        this.$confirm('确定要退出登录吗？', '提示', {
          confirmButtonText: '退出',
          cancelButtonText: '取消',
          type: 'warning'
        }).then(() => {
          logout()
          this.$message.success('已退出登录')
          this.$router.push('/login').catch(() => {})
        }).catch(() => {})
      }
    }
  }
}
</script>

<style>
* {
  margin: 0;
  padding: 0;
  box-sizing: border-box;
}

body {
  font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', 'PingFang SC',
    'Hiragino Sans GB', 'Microsoft YaHei', sans-serif;
  background: #f0f2f5;
  color: #1a1a1a;
}

.app-container {
  display: flex;
  flex-direction: column;
  height: 100vh;
  overflow: hidden;
}

.app-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 24px;
  height: 56px;
  background: linear-gradient(135deg, #1677ff 0%, #0958d9 100%);
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.15);
  flex-shrink: 0;
  z-index: 100;
}

.header-brand {
  display: flex;
  align-items: center;
  gap: 10px;
  color: #fff;
}

.brand-title {
  font-size: 17px;
  font-weight: 600;
  letter-spacing: 0.5px;
}

.header-nav {
  display: flex;
  gap: 4px;
}

.nav-link {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 6px 16px;
  border-radius: 8px;
  color: rgba(255, 255, 255, 0.85);
  text-decoration: none;
  font-size: 14px;
  font-weight: 500;
  cursor: pointer;
  user-select: none;
  transition: all 0.2s;
}

.nav-link:hover {
  background: rgba(255, 255, 255, 0.15);
  color: #fff;
}

.nav-link.active {
  background: rgba(255, 255, 255, 0.25);
  color: #fff;
}

.header-user {
  display: flex;
  align-items: center;
}

.user-info {
  display: flex;
  align-items: center;
  gap: 8px;
  cursor: pointer;
  color: rgba(255, 255, 255, 0.9);
  font-size: 14px;
  padding: 4px 8px;
  border-radius: 8px;
  transition: background 0.2s;
  outline: none;
}

.user-info:hover {
  background: rgba(255, 255, 255, 0.15);
}

.user-avatar {
  background: rgba(255, 255, 255, 0.3) !important;
  color: #fff !important;
  font-size: 13px;
  font-weight: 600;
  flex-shrink: 0;
}

.user-name {
  max-width: 100px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.app-main {
  flex: 1;
  overflow: hidden;
  display: flex;
  flex-direction: column;
}
</style>
