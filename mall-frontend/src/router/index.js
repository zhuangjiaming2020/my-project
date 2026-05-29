import Vue from 'vue'
import VueRouter from 'vue-router'
import { isLoggedIn } from '../utils/auth.js'

Vue.use(VueRouter)

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

const router = new VueRouter({
  mode: 'history',
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
