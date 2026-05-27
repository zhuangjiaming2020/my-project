import { createRouter, createWebHistory } from 'vue-router'
import { isLoggedIn } from '../utils/auth.js'

const routes = [
  {
    path: '/login',
    name: 'Login',
    component: () => import('../views/Login.vue'),
    meta: { title: '登录', public: true }
  },
  {
    path: '/',
    name: 'UserChat',
    component: () => import('../views/UserChat.vue'),
    meta: { title: '智能客服' }
  },
  {
    path: '/admin',
    name: 'AdminConfig',
    component: () => import('../views/AdminConfig.vue'),
    meta: { title: '接口配置管理' }
  }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

router.beforeEach((to, from, next) => {
  document.title = to.meta.title ? `${to.meta.title} - 智能运维客服` : '智能运维客服'
  if (to.meta.public) {
    next()
  } else if (!isLoggedIn()) {
    next({ path: '/login', query: { redirect: to.fullPath } })
  } else {
    next()
  }
})

export default router
