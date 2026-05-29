<template>
  <div class="login-page">
    <div class="login-card">
      <div class="login-logo">
        <i class="el-icon-chat-dot-round" style="font-size:40px;color:#1677ff;"></i>
        <h1 class="login-title">智能运维客服系统</h1>
        <p class="login-subtitle">请登录以继续</p>
      </div>

      <el-tabs v-model="activeTab" class="login-tabs">
        <!-- 登录 Tab -->
        <el-tab-pane label="登录" name="login">
          <el-form
            ref="loginFormRef"
            :model="loginForm"
            :rules="loginRules"
            label-position="top"
            size="medium"
            @submit.native.prevent="handleLogin"
          >
            <el-form-item label="用户名" prop="username">
              <el-input
                v-model="loginForm.username"
                placeholder="请输入用户名"
                prefix-icon="el-icon-user"
                clearable
              />
            </el-form-item>
            <el-form-item label="密码" prop="password">
              <el-input
                v-model="loginForm.password"
                type="password"
                placeholder="请输入密码"
                prefix-icon="el-icon-lock"
                show-password
                @keyup.enter.native="handleLogin"
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

        <!-- 注册 Tab -->
        <el-tab-pane label="注册" name="register">
          <el-form
            ref="registerFormRef"
            :model="registerForm"
            :rules="registerRules"
            label-position="top"
            size="medium"
            @submit.native.prevent="handleRegister"
          >
            <el-form-item label="用户名" prop="username">
              <el-input
                v-model="registerForm.username"
                placeholder="请输入用户名（3-20个字符）"
                prefix-icon="el-icon-user"
                clearable
              />
            </el-form-item>
            <el-form-item label="昵称" prop="nickname">
              <el-input
                v-model="registerForm.nickname"
                placeholder="请输入昵称（可选）"
                prefix-icon="el-icon-edit"
                clearable
              />
            </el-form-item>
            <el-form-item label="密码" prop="password">
              <el-input
                v-model="registerForm.password"
                type="password"
                placeholder="请输入密码（至少6位）"
                prefix-icon="el-icon-lock"
                show-password
              />
            </el-form-item>
            <el-form-item label="确认密码" prop="confirmPassword">
              <el-input
                v-model="registerForm.confirmPassword"
                type="password"
                placeholder="请再次输入密码"
                prefix-icon="el-icon-lock"
                show-password
                @keyup.enter.native="handleRegister"
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
        <span style="font-size:12px;color:#909399;">默认账号：admin / admin123，user1 / user123</span>
      </div>
    </div>
  </div>
</template>

<script>
import { login, register } from '../api/index.js'
import { setToken, setUser } from '../utils/auth.js'

export default {
  name: 'Login',
  data() {
    return {
      activeTab: 'login',
      loading: false,
      loginForm: { username: '', password: '' },
      loginRules: {
        username: [{ required: true, message: '请输入用户名', trigger: 'blur' }],
        password: [{ required: true, message: '请输入密码', trigger: 'blur' }]
      },
      registerForm: { username: '', nickname: '', password: '', confirmPassword: '' }
    }
  },
  computed: {
    registerRules() {
      return {
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
              if (value !== this.registerForm.password) callback(new Error('两次密码不一致'))
              else callback()
            },
            trigger: 'blur'
          }
        ]
      }
    }
  },
  methods: {
    async handleLogin() {
      const valid = await this.$refs.loginFormRef.validate().catch(() => false)
      if (!valid) return
      this.loading = true
      try {
        const res = await login(this.loginForm.username, this.loginForm.password)
        setToken(res.data.token)
        const user = {
          id:       res.data.userId,
          username: res.data.username,
          nickname: res.data.nickname,
          role:     res.data.role
        }
        setUser(user)
        this.$message.success(`欢迎回来，${user.nickname || user.username}！`)
        const redirect = this.$route.query.redirect || '/'
        this.$router.push(redirect)
      } catch (err) {
        const msg = err.response?.data?.error || err.response?.data?.message || '登录失败，请检查用户名或密码'
        this.$message.error(msg)
      } finally {
        this.loading = false
      }
    },

    async handleRegister() {
      const valid = await this.$refs.registerFormRef.validate().catch(() => false)
      if (!valid) return
      this.loading = true
      try {
        const res = await register(
          this.registerForm.username,
          this.registerForm.password,
          this.registerForm.nickname
        )
        setToken(res.data.token)
        const user = {
          id:       res.data.userId,
          username: res.data.username,
          nickname: res.data.nickname,
          role:     res.data.role
        }
        setUser(user)
        this.$message.success('注册成功，已自动登录！')
        const redirect = this.$route.query.redirect || '/'
        this.$router.push(redirect)
      } catch (err) {
        const msg = err.response?.data?.error || err.response?.data?.message || '注册失败'
        this.$message.error(msg)
      } finally {
        this.loading = false
      }
    }
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
