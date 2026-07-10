<template>
  <div class="login-page">
    <!-- 炫丽渐变发光球，提供背景深度感 -->
    <div class="glow-sphere sphere-1"></div>
    <div class="glow-sphere sphere-2"></div>
    
    <!-- 磨砂玻璃登录卡片 -->
    <div class="login-card glass-panel">
      <div class="login-header">
        <h1 class="gradient-text">AI-Lab-Hub</h1>
        <p>个人专属 AI 自动化工具枢纽</p>
      </div>

      <form @submit.prevent="handleLogin" class="login-form">
        <div class="form-group">
          <label for="username">管理员账号</label>
          <div class="input-wrapper">
            <User class="input-icon" />
            <input 
              id="username" 
              type="text" 
              v-model="form.username" 
              placeholder="请输入管理员账号"
              required
            />
          </div>
        </div>

        <div class="form-group">
          <label for="password">安全密码</label>
          <div class="input-wrapper">
            <Lock class="input-icon" />
            <input 
              id="password" 
              :type="showPassword ? 'text' : 'password'" 
              v-model="form.password" 
              placeholder="请输入登录密码"
              required
            />
            <button 
              type="button" 
              @click="showPassword = !showPassword" 
              class="password-toggle"
            >
              <Eye v-if="!showPassword" class="eye-icon" />
              <EyeOff v-else class="eye-icon" />
            </button>
          </div>
        </div>

        <div class="form-tip">
          <span>提示：演示环境，可输入任意账号密码登录</span>
        </div>

        <button type="submit" class="btn-primary btn-submit" :disabled="loading">
          <span v-if="!loading">确认登录</span>
          <span v-else class="loading-wrapper">
            <Loader2 class="loading-icon" />
            <span>安全验签中...</span>
          </span>
        </button>
      </form>
    </div>
  </div>
</template>

<script setup>
import { reactive, ref } from 'vue';
import { useRouter } from 'vue-router';
import { User, Lock, Eye, EyeOff, Loader2 } from 'lucide-vue-next';

const router = useRouter();
const showPassword = ref(false);
const loading = ref(false);

const form = reactive({
  username: '',
  password: ''
});

const handleLogin = async () => {
  loading.value = true;
  try {
    const response = await fetch('/ai-lab-hub-api/auth/login', {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json'
      },
      body: JSON.stringify({
        username: form.username,
        password: form.password
      })
    });
    
    const result = await response.json();
    if (result.code === 200) {
      localStorage.setItem('isAuthenticated', 'true');
      localStorage.setItem('token', result.data); // 保存后端生成的 JWT Token
      router.push('/');
    } else {
      alert(result.message || '账号或密码错误');
    }
  } catch (err) {
    console.error(err);
    alert('连接后端底座服务失败，请检查服务是否运行或已被 Nginx 代理');
  } finally {
    loading.value = false;
  }
};
</script>

<style scoped>
.login-page {
  position: relative;
  min-height: 100vh;
  width: 100vw;
  display: flex;
  align-items: center;
  justify-content: center;
  background-color: var(--bg-color);
  overflow: hidden;
  padding: 20px;
}

/* 动感炫彩背景气泡 */
.glow-sphere {
  position: absolute;
  border-radius: 50%;
  filter: blur(100px);
  opacity: 0.35;
  z-index: 1;
  animation: float-around 20s infinite alternate ease-in-out;
}

.sphere-1 {
  width: 400px;
  height: 400px;
  background: radial-gradient(circle, var(--primary-color) 0%, rgba(139, 92, 246, 0.4) 100%);
  top: -100px;
  left: -100px;
}

.sphere-2 {
  width: 500px;
  height: 500px;
  background: radial-gradient(circle, #c084fc 0%, rgba(99, 102, 241, 0.3) 100%);
  bottom: -150px;
  right: -100px;
  animation-delay: -10s;
}

@keyframes float-around {
  0% {
    transform: translate(0, 0) scale(1);
  }
  100% {
    transform: translate(80px, 50px) scale(1.1);
  }
}

/* 登录面板 */
.login-card {
  width: 100%;
  max-width: 440px;
  padding: 40px;
  position: relative;
  z-index: 5;
  background: var(--surface-color);
  border-radius: var(--radius-xl) !important;
  box-shadow: var(--card-shadow);
  border: 1px solid var(--border-color);
}

.login-header {
  text-align: center;
  margin-bottom: 32px;
}

.login-header h1 {
  font-size: 32px;
  font-weight: 800;
  letter-spacing: 0.5px;
  margin-bottom: 8px;
}

.login-header p {
  color: var(--text-secondary);
  font-size: 14px;
}

.login-form {
  display: flex;
  flex-direction: column;
  gap: 20px;
}

.form-group {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.form-group label {
  font-size: 13px;
  font-weight: 600;
  color: var(--text-secondary);
  padding-left: 4px;
}

.input-wrapper {
  position: relative;
  display: flex;
  align-items: center;
}

.input-icon {
  position: absolute;
  left: 14px;
  width: 18px;
  height: 18px;
  color: var(--text-muted);
  pointer-events: none;
}

.input-wrapper input {
  width: 100%;
  height: 48px;
  padding: 0 44px;
  border-radius: var(--radius-md);
  border: 1px solid var(--border-color);
  background: rgba(var(--bg-color), 0.5);
  color: var(--text-primary);
  transition: all var(--transition-fast);
}

.input-wrapper input:focus {
  border-color: var(--border-focus);
  box-shadow: 0 0 0 4px var(--primary-glow);
  background: var(--surface-solid);
}

/* 密码显示切换 */
.password-toggle {
  position: absolute;
  right: 14px;
  background: transparent;
  border: none;
  color: var(--text-muted);
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
}

.password-toggle:hover {
  color: var(--text-secondary);
}

.eye-icon {
  width: 18px;
  height: 18px;
}

.form-tip {
  font-size: 12px;
  color: var(--text-muted);
  text-align: center;
  margin-top: -4px;
}

/* 提交按钮 */
.btn-submit {
  width: 100%;
  height: 48px;
  justify-content: center;
  margin-top: 12px;
  font-size: 16px;
  border-radius: var(--radius-md) !important;
}

.loading-wrapper {
  display: flex;
  align-items: center;
  gap: 8px;
}

.loading-icon {
  width: 18px;
  height: 18px;
  animation: spin 1s linear infinite;
}

@keyframes spin {
  from {
    transform: rotate(0deg);
  }
  to {
    transform: rotate(360deg);
  }
}
</style>
