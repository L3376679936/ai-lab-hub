<template>
  <div class="app-layout">
    <!-- 左侧侧边栏 -->
    <aside class="sidebar glass-panel">
      <div class="sidebar-brand">
        <div class="logo-glow"></div>
        <span class="brand-text gradient-text">AI-Lab-Hub</span>
      </div>
      
      <nav class="sidebar-menu">
        <router-link to="/" class="menu-item" active-class="active">
          <LayoutDashboard class="menu-icon" />
          <span>工具箱主页</span>
        </router-link>
        
        <div class="menu-divider">
          <span>AI 效率工具</span>
        </div>
        
        <router-link to="/tool/labGenWord" class="menu-item" active-class="active">
          <FileText class="menu-icon" />
          <span>Word 自动生成</span>
        </router-link>
        
        <!-- 占位 Mock 菜单，强化系统感 -->
        <a href="javascript:void(0)" @click="showToast('敬请期待：PDF 智能文档解析')" class="menu-item disabled">
          <BookOpen class="menu-icon" />
          <span>PDF 智能解析</span>
        </a>
        <a href="javascript:void(0)" @click="showToast('敬请期待：SQL 脚本智编器')" class="menu-item disabled">
          <Database class="menu-icon" />
          <span>SQL 自动编写</span>
        </a>
      </nav>
      
      <div class="sidebar-footer">
        <a href="javascript:void(0)" @click="showToast('文档建设中...')" class="footer-link">
          <HelpCircle class="footer-icon" />
          <span>使用手册</span>
        </a>
      </div>
    </aside>

    <!-- 右侧内容区域 -->
    <div class="main-container">
      <!-- 顶部导航栏 -->
      <header class="topbar glass-panel">
        <div class="page-title">
          <h2>{{ currentRouteTitle }}</h2>
        </div>
        
        <div class="topbar-actions">
          <!-- 主题切换按钮 -->
          <button @click="themeStore.toggleTheme" class="theme-toggle" :title="themeStore.theme === 'dark' ? '切换到明亮模式' : '切换到暗黑模式'">
            <transition name="theme-icon-transition" mode="out-in">
              <Sun v-if="themeStore.theme === 'dark'" class="theme-icon" />
              <Moon v-else class="theme-icon" />
            </transition>
          </button>

          <!-- 系统全局设置按钮 -->
          <button @click="router.push('/settings')" class="theme-toggle" title="系统全局设置">
            <Settings class="theme-icon" />
          </button>
          
          <div class="user-profile">
            <div class="avatar">管</div>
            <span class="user-name">系统管理员</span>
          </div>
          
          <button @click="handleLogout" class="btn-logout" title="退出登录">
            <LogOut class="btn-logout-icon" />
          </button>
        </div>
      </header>

      <!-- 主要页面内容 -->
      <main class="page-content">
        <router-view v-slot="{ Component }">
          <transition name="fade-slide" mode="out-in">
            <component :is={Component} />
          </transition>
        </router-view>
      </main>
    </div>

    <!-- 全局提示框 (Toast) -->
    <transition name="toast-fade">
      <div v-if="toast.visible" class="toast-message glass-panel">
        <Info class="toast-icon" />
        <span>{{ toast.text }}</span>
      </div>
    </transition>
  </div>
</template>

<script setup>
import { computed, ref, reactive } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { useThemeStore } from '../store/themeStore';
import { 
  LayoutDashboard, 
  FileText, 
  BookOpen, 
  Database, 
  HelpCircle, 
  Sun, 
  Moon, 
  LogOut, 
  Info,
  Settings
} from 'lucide-vue-next';

const themeStore = useThemeStore();
const route = useRoute();
const router = useRouter();

// 根据当前路由动态显示标题
const currentRouteTitle = computed(() => {
  if (route.path === '/') return 'AI 工具箱控制台';
  if (route.path === '/tool/labGenWord') return 'Word 自动生成工作流';
  if (route.path === '/settings') return '系统全局设置';
  return 'AI-Lab-Hub';
});

// Toast 提示逻辑
const toast = reactive({
  visible: false,
  text: ''
});
let toastTimer = null;

const showToast = (text) => {
  if (toastTimer) clearTimeout(toastTimer);
  toast.text = text;
  toast.visible = true;
  toastTimer = setTimeout(() => {
    toast.visible = false;
  }, 2500);
};

// 退出登录
const handleLogout = () => {
  localStorage.removeItem('isAuthenticated');
  showToast('正在退出登录...');
  setTimeout(() => {
    router.push('/login');
  }, 1000);
};
</script>

<style scoped>
.app-layout {
  display: flex;
  min-height: 100vh;
  padding: 16px;
  gap: 16px;
  background-color: var(--bg-color);
}

/* 侧边栏样式 */
.sidebar {
  width: 260px;
  flex-shrink: 0;
  display: flex;
  flex-direction: column;
  padding: 24px 16px;
  background: var(--sidebar-bg);
  border-radius: var(--radius-lg) !important;
  z-index: 10;
}

.sidebar-brand {
  position: relative;
  display: flex;
  align-items: center;
  justify-content: center;
  padding-bottom: 24px;
  margin-bottom: 16px;
  border-bottom: 1px solid var(--border-color);
}

.logo-glow {
  position: absolute;
  width: 40px;
  height: 40px;
  background: var(--primary-color);
  filter: blur(20px);
  border-radius: 50%;
  opacity: 0.5;
  left: 20px;
}

.brand-text {
  font-size: 22px;
  font-weight: 800;
  letter-spacing: 0.5px;
}

.sidebar-menu {
  display: flex;
  flex-direction: column;
  gap: 6px;
  flex-grow: 1;
}

.menu-divider {
  font-size: 11px;
  font-weight: 700;
  text-transform: uppercase;
  color: var(--text-muted);
  padding: 18px 12px 6px;
  letter-spacing: 1px;
}

.menu-item {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 12px 16px;
  border-radius: var(--radius-md);
  color: var(--text-secondary);
  font-weight: 500;
  transition: all var(--transition-fast);
}

.menu-item:hover:not(.disabled) {
  background: var(--surface-hover);
  color: var(--text-primary);
  transform: translateX(4px);
}

.menu-item.active {
  background: var(--primary-gradient);
  color: #ffffff;
  box-shadow: 0 4px 12px var(--primary-glow);
}

.menu-icon {
  width: 18px;
  height: 18px;
}

.menu-item.disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.sidebar-footer {
  padding-top: 16px;
  border-top: 1px solid var(--border-color);
}

.footer-link {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 10px 12px;
  color: var(--text-muted);
  font-size: 14px;
  transition: color var(--transition-fast);
}

.footer-link:hover {
  color: var(--text-primary);
}

.footer-icon {
  width: 16px;
  height: 16px;
}

/* 右侧主区域 */
.main-container {
  flex-grow: 1;
  display: flex;
  flex-direction: column;
  gap: 16px;
  min-width: 0; /* 防止子元素溢出 */
}

/* 顶部顶栏 */
.topbar {
  height: 70px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 24px;
  background: var(--topbar-bg);
  border-radius: var(--radius-lg) !important;
}

.page-title h2 {
  font-size: 18px;
  font-weight: 700;
  color: var(--text-primary);
}

.topbar-actions {
  display: flex;
  align-items: center;
  gap: 16px;
}

/* 主题切换动效 */
.theme-toggle {
  background: transparent;
  border: 1px solid var(--border-color);
  width: 40px;
  height: 40px;
  border-radius: var(--radius-md);
  display: flex;
  align-items: center;
  justify-content: center;
  color: var(--text-secondary);
  cursor: pointer;
  transition: all var(--transition-fast);
}

.theme-toggle:hover {
  background: var(--surface-hover);
  color: var(--text-primary);
  border-color: var(--text-muted);
  transform: scale(1.05);
}

.theme-icon {
  width: 18px;
  height: 18px;
}

/* 头像 & 用户名 */
.user-profile {
  display: flex;
  align-items: center;
  gap: 10px;
  padding-left: 16px;
  border-left: 1px solid var(--border-color);
}

.avatar {
  width: 32px;
  height: 32px;
  border-radius: 50%;
  background: var(--primary-gradient);
  color: #ffffff;
  display: flex;
  align-items: center;
  justify-content: center;
  font-weight: 700;
  font-size: 14px;
  box-shadow: 0 2px 8px var(--primary-glow);
}

.user-name {
  font-size: 14px;
  font-weight: 600;
  color: var(--text-secondary);
}

/* 退出登录 */
.btn-logout {
  background: transparent;
  border: 1px solid var(--border-color);
  width: 40px;
  height: 40px;
  border-radius: var(--radius-md);
  display: flex;
  align-items: center;
  justify-content: center;
  color: var(--text-muted);
  cursor: pointer;
  transition: all var(--transition-fast);
}

.btn-logout:hover {
  background: rgba(ef, 68, 68, 0.1);
  color: #ef4444;
  border-color: rgba(ef, 68, 68, 0.3);
  transform: scale(1.05);
}

.btn-logout-icon {
  width: 18px;
  height: 18px;
}

/* 页面内容区 */
.page-content {
  flex-grow: 1;
  min-height: 0;
  position: relative;
}

/* 全局 Toast */
.toast-message {
  position: fixed;
  top: 32px;
  right: 32px;
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 14px 20px;
  z-index: 1000;
  border-left: 4px solid var(--primary-color) !important;
  font-size: 14px;
  font-weight: 600;
}

.toast-icon {
  width: 16px;
  height: 16px;
  color: var(--primary-color);
}

/* 动画定义 */
.theme-icon-transition-enter-active,
.theme-icon-transition-leave-active {
  transition: transform 0.25s cubic-bezier(0.4, 0, 0.2, 1), opacity 0.2s;
}

.theme-icon-transition-enter-from {
  transform: rotate(-90deg) scale(0.5);
  opacity: 0;
}

.theme-icon-transition-leave-to {
  transform: rotate(90deg) scale(0.5);
  opacity: 0;
}

/* 页面切换动画 */
.fade-slide-enter-active,
.fade-slide-leave-active {
  transition: all 0.25s cubic-bezier(0.4, 0, 0.2, 1);
}

.fade-slide-enter-from {
  opacity: 0;
  transform: translateY(8px);
}

.fade-slide-leave-to {
  opacity: 0;
  transform: translateY(-8px);
}

/* Toast 动画 */
.toast-fade-enter-active,
.toast-fade-leave-active {
  transition: all 0.3s cubic-bezier(0.18, 0.89, 0.32, 1.28);
}

.toast-fade-enter-from {
  opacity: 0;
  transform: translateY(-20px) scale(0.9);
}

.toast-fade-leave-to {
  opacity: 0;
  transform: translateY(-10px) scale(0.95);
}
</style>
