<template>
  <div class="login-page">
    <div class="login-card">
      <div class="login-logo">
        <el-icon size="40" color="#1677ff"><ChatDotRound /></el-icon>
        <h1 class="login-title">智能运维客服系统</h1>
        <p class="login-subtitle">请登录以继续</p>
      </div>

      <el-tabs v-model="activeTab" class="login-tabs">
        <el-tab-pane label="登录" name="login">
          <el-form
            ref="loginFormRef"
            :model="loginForm"
            :rules="loginRules"
            label-position="top"
            size="large"
            @submit.prevent="handleLogin"
          >
            <el-form-item label="用户名" prop="username">
              <el-input
                v-model="loginForm.username"
                placeholder="请输入用户名"
                prefix-icon="User"
                clearable
                autofocus
              />
            </el-form-item>
            <el-form-item label="密码" prop="password">
              <el-input
                v-model="loginForm.password"
                type="password"
                placeholder="请输入密码"
                prefix-icon="Lock"
                show-password
                @keyup.enter="handleLogin"
              />
            </el-form-item>
            <el-button
              type="primary"
              class="login-btn"
              :loading="loading"
              @click="handleLogin"
            >
              登录
            </el-button>
          </el-form>
        </el-tab-pane>

        <el-tab-pane label="注册" name="register">
          <el-form
            ref="registerFormRef"
            :model="registerForm"
            :rules="registerRules"
            label-position="top"
            size="large"
            @submit.prevent="handleRegister"
          >
            <el-form-item label="用户名" prop="username">
              <el-input
                v-model="registerForm.username"
                placeholder="请输入用户名（3-20个字符）"
                prefix-icon="User"
                clearable
              />
            </el-form-item>
            <el-form-item label="昵称" prop="nickname">
              <el-input
                v-model="registerForm.nickname"
                placeholder="请输入昵称（可选）"
                prefix-icon="Avatar"
                clearable
              />
            </el-form-item>
            <el-form-item label="密码" prop="password">
              <el-input
                v-model="registerForm.password"
                type="password"
                placeholder="请输入密码（至少6位）"
                prefix-icon="Lock"
                show-password
              />
            </el-form-item>
            <el-form-item label="确认密码" prop="confirmPassword">
              <el-input
                v-model="registerForm.confirmPassword"
                type="password"
                placeholder="请再次输入密码"
                prefix-icon="Lock"
                show-password
                @keyup.enter="handleRegister"
              />
            </el-form-item>
            <el-button
              type="primary"
              class="login-btn"
              :loading="loading"
              @click="handleRegister"
            >
              注册
            </el-button>
          </el-form>
        </el-tab-pane>
      </el-tabs>

      <div class="login-hint">
        <el-text size="small" type="info">
          默认账号：admin / admin123，user1 / user123
        </el-text>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { login, register } from '../api/index.js'
import { setToken, setUser } from '../utils/auth.js'

const router = useRouter()
const activeTab = ref('login')
const loading = ref(false)

// ---------- 登录 ----------
const loginFormRef = ref()
const loginForm = reactive({ username: '', password: '' })
const loginRules = {
  username: [{ required: true, message: '请输入用户名', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }]
}

async function handleLogin() {
  const valid = await loginFormRef.value?.validate().catch(() => false)
  if (!valid) return
  loading.value = true
  try {
    const res = await login(loginForm.username, loginForm.password)
    setToken(res.data.token)
    const user = {
      id:       res.data.userId,
      username: res.data.username,
      nickname: res.data.nickname,
      role:     res.data.role
    }
    setUser(user)
    ElMessage.success(`欢迎回来，${user.nickname || user.username}！`)
    const redirect = router.currentRoute.value.query.redirect || '/'
    router.push(redirect)
  } catch (err) {
    const msg = err.response?.data?.error || err.response?.data?.message || '登录失败，请检查用户名或密码'
    ElMessage.error(msg)
  } finally {
    loading.value = false
  }
}

// ---------- 注册 ----------
const registerFormRef = ref()
const registerForm = reactive({ username: '', nickname: '', password: '', confirmPassword: '' })
const registerRules = {
  username: [
    { required: true, message: '请输入用户名', trigger: 'blur' },
    { min: 3, max: 20, message: '用户名长度为 3-20 个字符', trigger: 'blur' }
  ],
  password: [
    { required: true, message: '请输入密码', trigger: 'blur' },
    { min: 6, message: '密码至少 6 位', trigger: 'blur' }
  ],
  confirmPassword: [
    { required: true, message: '请确认密码', trigger: 'blur' },
    {
      validator: (rule, value, callback) => {
        if (value !== registerForm.password) callback(new Error('两次密码不一致'))
        else callback()
      },
      trigger: 'blur'
    }
  ]
}

async function handleRegister() {
  const valid = await registerFormRef.value?.validate().catch(() => false)
  if (!valid) return
  loading.value = true
  try {
    const res = await register(registerForm.username, registerForm.password, registerForm.nickname)
    setToken(res.data.token)
    const user = {
      id:       res.data.userId,
      username: res.data.username,
      nickname: res.data.nickname,
      role:     res.data.role
    }
    setUser(user)
    ElMessage.success('注册成功，已自动登录！')
    const redirect = router.currentRoute.value.query.redirect || '/'
    router.push(redirect)
  } catch (err) {
    const msg = err.response?.data?.error || err.response?.data?.message || '注册失败'
    ElMessage.error(msg)
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.login-page {
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  background: linear-gradient(135deg, #e6f0ff 0%, #f0f2f5 50%, #e8f5e9 100%);
}

.login-card {
  width: 420px;
  background: #fff;
  border-radius: 16px;
  box-shadow: 0 8px 40px rgba(0, 0, 0, 0.12);
  padding: 40px 40px 32px;
}

.login-logo {
  text-align: center;
  margin-bottom: 28px;
}

.login-title {
  font-size: 20px;
  font-weight: 700;
  color: #1a1a1a;
  margin: 12px 0 6px;
}

.login-subtitle {
  font-size: 14px;
  color: #888;
}

.login-tabs {
  margin-bottom: 8px;
}

.login-btn {
  width: 100%;
  margin-top: 8px;
  height: 44px;
  font-size: 16px;
  font-weight: 500;
  border-radius: 8px;
}

.login-hint {
  text-align: center;
  margin-top: 20px;
  padding-top: 16px;
  border-top: 1px solid #f0f0f0;
}
</style>
