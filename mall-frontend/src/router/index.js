import { createRouter, createWebHistory } from 'vue-router'
import UserChat from '../views/UserChat.vue'
import AdminConfig from '../views/AdminConfig.vue'

const routes = [
  {
    path: '/',
    name: 'UserChat',
    component: UserChat,
    meta: { title: '智能客服' }
  },
  {
    path: '/admin',
    name: 'AdminConfig',
    component: AdminConfig,
    meta: { title: '接口配置管理' }
  }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

router.beforeEach((to) => {
  document.title = to.meta.title ? `${to.meta.title} - 智能运维客服` : '智能运维客服'
})

export default router
