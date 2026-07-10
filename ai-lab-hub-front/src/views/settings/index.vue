<template>
  <div class="settings-page glass-panel">
    <div class="settings-header">
      <SettingsIcon class="header-icon" />
      <h3>系统全局设置</h3>
    </div>

    <form @submit.prevent="saveSettings" class="settings-form">
      <div class="form-section">
        <h4 class="section-title">
          <Cpu class="title-icon" />
          <span>全局 AI 大模型服务配置</span>
        </h4>
        
        <div class="form-group">
          <label for="globalApiKey">全局 API Key</label>
          <input 
            id="globalApiKey" 
            type="password" 
            v-model="settings.globalApiKey" 
            placeholder="请输入全局默认的大模型 API Key"
          />
          <span class="input-tip">注：敏感密钥在保存时将通过 AES-256 算法加密存储于数据库的 sys_config 表中。</span>
        </div>

        <div class="form-group">
          <label for="globalEndpoint">全局 API 端点 (Endpoint)</label>
          <input 
            id="globalEndpoint" 
            type="text" 
            v-model="settings.globalEndpoint" 
            placeholder="例如：https://api.anthropic.com"
          />
        </div>

        <div class="form-group">
          <label for="globalModel">全局默认大模型 (Model)</label>
          <select id="globalModel" v-model="settings.globalModel">
            <option value="claude-3-5-sonnet">Claude 3.5 Sonnet (默认推荐)</option>
            <option value="gpt-4o">GPT-4o</option>
            <option value="deepseek-coder">DeepSeek Coder</option>
            <option value="deepseek-chat">DeepSeek Chat</option>
          </select>
        </div>
      </div>

      <div class="form-section">
        <h4 class="section-title">
          <ShieldAlert class="title-icon" />
          <span>Key 优先级与继承逻辑说明</span>
        </h4>
        <div class="logic-card">
          <div class="logic-step">
            <div class="step-num">1</div>
            <div class="step-desc">
              <strong>局部组件配置 (最高优先)</strong>
              <p>各具体小工具内独立配置的 API Key 和模型，仅对该小工具自身生效。</p>
            </div>
          </div>
          <div class="logic-arrow">↓</div>
          <div class="logic-step">
            <div class="step-num">2</div>
            <div class="step-desc">
              <strong>页面全局配置 (中等优先)</strong>
              <p>即本页面配置的参数，对所有未单独配置 Key 的小工具生效（即刻写入数据库，无需重启服务）。</p>
            </div>
          </div>
          <div class="logic-arrow">↓</div>
          <div class="logic-step">
            <div class="step-num">3</div>
            <div class="step-desc">
              <strong>本地配置文件 (兜底优先)</strong>
              <p>后端 application.yml 静态配置的参数，作为系统最底层的兜底。如果上述两层均未设置，则使用此配置。</p>
            </div>
          </div>
        </div>
      </div>

      <div class="settings-actions">
        <button type="submit" class="btn-primary save-btn" :disabled="saving">
          <Save v-if="!saving" class="btn-icon" />
          <Loader2 v-else class="btn-icon spinning" />
          <span>{{ saving ? '正在保存到数据库...' : '保存系统全局配置' }}</span>
        </button>
      </div>
    </form>

    <div class="settings-divider"></div>

    <!-- 管理员安全密码修改卡片 -->
    <div class="form-section password-section">
      <h4 class="section-title">
        <Lock class="title-icon" />
        <span>管理员安全密码修改</span>
      </h4>
      
      <form @submit.prevent="changePassword" class="password-form">
        <div class="form-group">
          <label for="oldPassword">当前安全密码</label>
          <input 
            id="oldPassword" 
            type="password" 
            v-model="pwdForm.oldPassword" 
            placeholder="请输入当前正在使用的安全密码"
            required
          />
        </div>

        <div class="form-group">
          <label for="newPassword">新安全密码</label>
          <input 
            id="newPassword" 
            type="password" 
            v-model="pwdForm.newPassword" 
            placeholder="请输入长度不小于 6 位的新安全密码"
            required
          />
        </div>

        <div class="form-group">
          <label for="confirmPassword">确认新安全密码</label>
          <input 
            id="confirmPassword" 
            type="password" 
            v-model="pwdForm.confirmPassword" 
            placeholder="请再次输入新安全密码以确认"
            required
          />
        </div>

        <div class="settings-actions">
          <button type="submit" class="btn-primary save-btn" :disabled="pwdSaving">
            <Key v-if="!pwdSaving" class="btn-icon" />
            <Loader2 v-else class="btn-icon spinning" />
            <span>{{ pwdSaving ? '正在修改中...' : '确认修改安全密码' }}</span>
          </button>
        </div>
      </form>
    </div>

    <!-- 局部 Toast 提示 -->
    <transition name="fade">
      <div v-if="savedTip" class="save-success-toast glass-panel">
        配置保存成功！已加密存入 sys_config 表。
      </div>
    </transition>
  </div>
</template>

<script setup>
import { reactive, ref, onMounted } from 'vue';
import { 
  Settings as SettingsIcon, 
  Cpu, 
  ShieldAlert, 
  Save, 
  Loader2,
  Lock,
  Key
} from 'lucide-vue-next';

const saving = ref(false);
const savedTip = ref(false);

const settings = reactive({
  globalApiKey: '',
  globalEndpoint: '',
  globalModel: 'claude-3-5-sonnet'
});

// 密码表单与状态
const pwdSaving = ref(false);
const pwdForm = reactive({
  oldPassword: '',
  newPassword: '',
  confirmPassword: ''
});

// 初始化时从本地加载模拟数据
onMounted(() => {
  settings.globalApiKey = localStorage.getItem('global_api_key') || 'sk-ant-************';
  settings.globalEndpoint = localStorage.getItem('global_endpoint') || 'https://api.anthropic.com';
  settings.globalModel = localStorage.getItem('global_model') || 'claude-3-5-sonnet';
});

const saveSettings = () => {
  saving.value = true;
  
  // 模拟请求写入
  setTimeout(() => {
    saving.value = false;
    localStorage.setItem('global_api_key', settings.globalApiKey);
    localStorage.setItem('global_endpoint', settings.globalEndpoint);
    localStorage.setItem('global_model', settings.globalModel);
    
    savedTip.value = true;
    setTimeout(() => {
      savedTip.value = false;
    }, 2500);
  }, 1000);
};

// 提交密码修改请求
const changePassword = async () => {
  if (pwdForm.newPassword.length < 6) {
    alert('新密码长度不能少于 6 位');
    return;
  }
  if (pwdForm.newPassword !== pwdForm.confirmPassword) {
    alert('两次输入的新密码不一致，请重新核对');
    return;
  }

  pwdSaving.value = true;
  try {
    const response = await fetch('/ai-lab-hub-api/auth/change-password', {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        'Authorization': 'Bearer ' + (localStorage.getItem('token') || '')
      },
      body: JSON.stringify({
        oldPassword: pwdForm.oldPassword,
        newPassword: pwdForm.newPassword
      })
    });

    const result = await response.json();
    if (result.code === 200) {
      alert('安全密码修改成功！请用新密码重新登录。');
      // 修改成功后清除登录态，迫使重新登录以验证新密码
      localStorage.removeItem('isAuthenticated');
      localStorage.removeItem('token');
      window.location.reload();
    } else {
      alert(result.message || '原安全密码校验失败');
    }
  } catch (err) {
    console.error(err);
    alert('网络异常，无法连接后端底座服务');
  } finally {
    pwdSaving.value = false;
  }
};
</script>

<style scoped>
.settings-page {
  padding: 28px;
  background: var(--surface-color);
  max-width: 800px;
  margin: 0 auto;
}

.settings-header {
  display: flex;
  align-items: center;
  gap: 12px;
  padding-bottom: 18px;
  margin-bottom: 24px;
  border-bottom: 1px solid var(--border-color);
}

.header-icon {
  width: 22px;
  height: 22px;
  color: var(--primary-color);
}

.settings-header h3 {
  font-size: 18px;
  font-weight: 700;
  color: var(--text-primary);
}

.settings-form {
  display: flex;
  flex-direction: column;
  gap: 28px;
}

.form-section {
  display: flex;
  flex-direction: column;
  gap: 18px;
}

.section-title {
  display: flex;
  align-items: center;
  gap: 10px;
  font-size: 15px;
  font-weight: 700;
  color: var(--text-primary);
  border-left: 3px solid var(--primary-color);
  padding-left: 10px;
}

.title-icon {
  width: 16px;
  height: 16px;
  color: var(--text-secondary);
}

.form-group {
  display: flex;
  flex-direction: column;
  gap: 8px;
  padding-left: 14px;
}

.form-group label {
  font-size: 13px;
  font-weight: 600;
  color: var(--text-secondary);
}

.form-group input,
.form-group select {
  width: 100%;
  padding: 10px 14px;
  border: 1px solid var(--border-color);
  background: rgba(var(--bg-color), 0.3);
  color: var(--text-primary);
  border-radius: var(--radius-md);
  transition: all var(--transition-fast);
}

.form-group input:focus,
.form-group select:focus {
  border-color: var(--border-focus);
  box-shadow: 0 0 0 3px var(--primary-glow);
  background: var(--surface-solid);
}

.input-tip {
  font-size: 12px;
  color: var(--text-muted);
}

/* 优先级展示卡片 */
.logic-card {
  background: rgba(0, 0, 0, 0.12);
  border-radius: var(--radius-md);
  padding: 20px;
  border: 1px solid var(--border-color);
  display: flex;
  flex-direction: column;
  gap: 10px;
  margin-left: 14px;
}

.logic-step {
  display: flex;
  align-items: flex-start;
  gap: 14px;
}

.step-num {
  width: 24px;
  height: 24px;
  background: var(--primary-gradient);
  color: #ffffff;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  font-weight: 700;
  font-size: 12px;
  flex-shrink: 0;
  margin-top: 2px;
}

.step-desc strong {
  font-size: 13px;
  color: var(--text-primary);
  display: block;
  margin-bottom: 2px;
}

.step-desc p {
  font-size: 12px;
  color: var(--text-secondary);
  line-height: 1.4;
}

.logic-arrow {
  text-align: center;
  color: var(--text-muted);
  font-size: 14px;
  font-weight: 700;
  margin: -4px 0;
  padding-left: 28px;
}

.settings-actions {
  display: flex;
  justify-content: flex-end;
  padding-top: 16px;
  border-top: 1px solid var(--border-color);
}

.save-btn {
  height: 44px;
  padding: 0 24px;
}

.btn-icon {
  width: 16px;
  height: 16px;
}

.btn-icon.spinning {
  animation: spin 1s linear infinite;
}

@keyframes spin {
  from { transform: rotate(0deg); }
  to { transform: rotate(360deg); }
}

/* Toast */
.save-success-toast {
  position: fixed;
  bottom: 40px;
  left: 50%;
  transform: translateX(-50%);
  background: rgba(16, 185, 129, 0.9);
  color: #ffffff;
  border: 1px solid rgba(16, 185, 129, 0.3);
  padding: 12px 24px;
  border-radius: var(--radius-md);
  font-weight: 600;
  font-size: 14px;
  box-shadow: 0 10px 25px -5px rgba(16, 185, 129, 0.4);
  z-index: 2000;
}

.settings-divider {
  height: 1px;
  background: linear-gradient(to right, transparent, var(--border-color), transparent);
  margin: 20px 0;
  width: 100%;
}

.fade-enter-active,
.fade-leave-active {
  transition: all 0.25s ease;
}

.fade-enter-from,
.fade-leave-to {
  opacity: 0;
  transform: translate(-50%, 10px);
}
</style>
