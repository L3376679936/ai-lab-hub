import { createRouter, createWebHistory } from 'vue-router';
import Layout from '../core/components/Layout.vue';
import Login from '../views/login/index.vue';
import Dashboard from '../views/dashboard/index.vue';

// 1. 基础静态路由
const routes = [
  {
    path: '/login',
    name: 'Login',
    component: Login,
    meta: { requiresAuth: false }
  },
  {
    path: '/',
    component: Layout,
    meta: { requiresAuth: true },
    children: [
      {
        path: '',
        name: 'Dashboard',
        component: Dashboard
      },
      {
        path: 'settings',
        name: 'Settings',
        component: () => import('../views/settings/index.vue')
      }
    ]
  }
];

// 2. 自动扫描模块组件路由 (插拔式)
// 扫描 src/views/modules/ 目录下所有小工具的 index.vue
const modules = import.meta.glob('../views/modules/*/index.vue');

Object.keys(modules).forEach((path) => {
  // 从路径中提取小工具名称，如 "../views/modules/labGenWord/index.vue" -> "labGenWord"
  const moduleName = path.match(/\/modules\/(.+?)\/index\.vue$/)[1];
  
  // 动态构造子路由并追加到 Layout 下
  routes[1].children.push({
    path: `tool/${moduleName}`,
    name: `Tool-${moduleName}`,
    component: modules[path]
  });
});

// 3. 创建路由实例
const router = createRouter({
  history: createWebHistory(),
  routes
});

// 4. 路由守卫：登录状态拦截与跳转
router.beforeEach((to, from, next) => {
  const isAuthenticated = localStorage.getItem('isAuthenticated') === 'true';

  if (to.matched.some(record => record.meta.requiresAuth)) {
    // 访问需要授权的页面
    if (!isAuthenticated) {
      next('/login');
    } else {
      next();
    }
  } else {
    // 访问不需要授权的页面 (如 Login)
    if (isAuthenticated && to.path === '/login') {
      next('/'); // 已登录直接去首页
    } else {
      next();
    }
  }
});

export default router;
