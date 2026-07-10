<template>
  <div class="tool-page">
    <router-link to="/" class="back-link">
      <ArrowLeft class="back-icon" />
      <span>返回工具箱列表</span>
    </router-link>

    <div class="tool-layout">
      <!-- 左侧输入表单 -->
      <section class="form-section glass-panel">
        <div class="section-header">
          <FileText class="header-icon" />
          <h3>文档生成配置</h3>
        </div>

        <form @submit.prevent="startMockGeneration" class="tool-form">
          <div class="form-group">
            <label for="docTitle">报告/文档标题</label>
            <input 
              id="docTitle" 
              type="text" 
              v-model="config.title" 
              placeholder="例如：AI-Lab-Hub 项目建设可行性报告"
              required
              :disabled="running"
            />
          </div>

          <div class="form-group">
            <label for="docOutline">主要段落大纲 / 提示词要求</label>
            <textarea 
              id="docOutline" 
              v-model="config.outline" 
              rows="6"
              placeholder="请输入您希望文档包含的核心内容或大纲。&#10;例如：&#10;1. 项目背景与痛点分析&#10;2. 前后端技术栈方案抉择&#10;3. AI网关与连接池设计细节..."
              required
              :disabled="running"
            ></textarea>
          </div>

          <!-- 折叠局部 AI 配置区，突出优先级概念 -->
          <div class="config-accordion">
            <button 
              type="button" 
              @click="showLocalAiConfig = !showLocalAiConfig" 
              class="accordion-trigger"
            >
              <div class="trigger-label">
                <Cpu class="trigger-icon" />
                <span>工具级独立 AI 配置 (覆盖优先级高)</span>
              </div>
              <ChevronDown :class="['arrow-icon', { rotated: showLocalAiConfig }]" />
            </button>
            
            <transition name="slide-fade">
              <div v-show="showLocalAiConfig" class="accordion-content">
                <div class="form-group">
                  <label for="apiKey">大模型 API Key</label>
                  <input 
                    id="apiKey" 
                    type="password" 
                    v-model="config.apiKey" 
                    placeholder="若不填，默认使用底座全局 API Key"
                    :disabled="running"
                  />
                </div>
                <div class="form-group">
                  <label for="endpoint">请求代理端点 (Endpoint)</label>
                  <input 
                    id="endpoint" 
                    type="text" 
                    v-model="config.endpoint" 
                    placeholder="例如：https://api.anthropic.com"
                    :disabled="running"
                  />
                </div>
                <div class="form-group">
                  <label for="model">指定模型版本 (Model)</label>
                  <select id="model" v-model="config.model" :disabled="running">
                    <option value="">跟随底座全局默认模型</option>
                    <option value="claude-3-5-sonnet">Claude 3.5 Sonnet</option>
                    <option value="gpt-4o">GPT-4o</option>
                    <option value="deepseek-coder">DeepSeek Coder</option>
                  </select>
                </div>
              </div>
            </transition>
          </div>

          <div class="form-group">
            <label for="themeStyle">Word 渲染模板主题</label>
            <select id="themeStyle" v-model="config.theme" :disabled="running">
              <option value="tech">科技冷青 (Tech Cyan)</option>
              <option value="business">商务深蓝 (Business Navy)</option>
              <option value="minimalist">极简灰白 (Minimalist Light)</option>
            </select>
          </div>

          <button type="submit" class="btn-primary start-btn" :disabled="running">
            <Play class="btn-icon" />
            <span>开始一键流式生成</span>
          </button>
        </form>
      </section>

      <!-- 右侧生成终端控制台 -->
      <section class="console-section glass-panel">
        <div class="section-header">
          <Terminal class="header-icon" />
          <h3>AI 实时流式控制台</h3>
          <span :class="['status-badge', 'status-' + status]">
            {{ statusText }}
          </span>
        </div>

        <div class="console-box" ref="consoleRef">
          <div v-for="(log, idx) in logs" :key="idx" :class="['log-line', 'log-' + log.type]">
            <span class="log-time">[{{ log.time }}]</span>
            <span class="log-content">{{ log.text }}</span>
          </div>
          <!-- 光标动效 -->
          <div v-if="status === 'generating'" class="typing-cursor"></div>
        </div>

        <div class="console-footer">
          <button 
            @click="downloadMockFile" 
            class="btn-primary download-btn" 
            :disabled="status !== 'success'"
          >
            <Download class="btn-icon" />
            <span>下载自动生成报告 (Word)</span>
          </button>
        </div>
      </section>
    </div>
  </div>
</template>

<script setup>
import { reactive, ref, computed, nextTick } from 'vue';
import { 
  ArrowLeft, 
  FileText, 
  Cpu, 
  ChevronDown, 
  Terminal, 
  Play, 
  Download 
} from 'lucide-vue-next';

// 表单与配置
const showLocalAiConfig = ref(false);
const running = ref(false);
const status = ref('idle'); // idle, connecting, generating, building, success
const consoleRef = ref(null);

const config = reactive({
  title: '',
  outline: '',
  apiKey: '',
  endpoint: '',
  model: '',
  theme: 'tech'
});

// 控制台日志记录
const logs = ref([
  { time: getNowTime(), type: 'system', text: 'AI-Lab-Hub 控制台就绪。等待用户提交配置...' }
]);

const statusText = computed(() => {
  if (status.value === 'idle') return '等待运行';
  if (status.value === 'connecting') return '大模型请求中...';
  if (status.value === 'generating') return 'AI 流式章节生成中...';
  if (status.value === 'building') return 'POI Word排版中...';
  if (status.value === 'success') return '生成成功';
  return '未就绪';
});

function getNowTime() {
  const d = new Date();
  return d.toTimeString().split(' ')[0];
}

// 追加日志并自动滚动到底部
const addLog = (text, type = 'info') => {
  logs.value.push({
    time: getNowTime(),
    type,
    text
  });
  nextTick(() => {
    if (consoleRef.value) {
      consoleRef.value.scrollTop = consoleRef.value.scrollHeight;
    }
  });
};

// 预设生成的大模型文章内容（模拟 SSE 流式打字）
const mockLlmResponse = 
`# AI-Lab-Hub 平台建设报告

## 一、 系统架构设计与多模块隔离
AI-Lab-Hub 采用 Maven 多模块架构进行组织。底座模块封装了安全认证、AI网关、定时文件清理等共性能力。具体的小工具则以独立的 Sub-Module 进行物理隔离，这完美地解决了多个 AI 业务脚本相互缠绕和开发耦合的痛点。

## 二、 统一大模型网关设计 (AiGatewayService)
网关支持多级 Key 配置覆盖。当独立工具配置了专属 API Key 时，网关会自动加载组件级 Key，否则安全地退回到全局公共 Key，兼顾了共享便捷性与个例安全性。网关引入本地 ConcurrentHashMap 缓存池，并在配置更新时基于 Spring Event 触发失效机制。

## 三、 文件生命周期安全清理 (FileCleanupTask)
系统引入了路径 CanonicalPath 规范化验证以及 SymbolicLink 软链接检测，每天凌晨 3:00 执行过期 24 小时的临时文件物理清除，在保证服务器存储容量上限的同时，彻底规避了目录穿越误删宿主机系统文件带来的致命安全漏洞。

## 四、 结论与后续演进
目前项目已打通前后端骨架。下一步将继续完善对 MCP 客户端连接测试中心的开发，丰富本地 Skill 注解反射调用机制，以实现更具想象力的本地 AI 自动化执行。`;

// 触发 Mock 自动化流式生成过程
const startMockGeneration = () => {
  if (running.value) return;
  running.value = true;
  logs.value = []; // 清空日志
  
  // 1. 模拟连接大模型网关
  status.value = 'connecting';
  addLog('正在读取 AI 配置参数...', 'system');
  
  setTimeout(() => {
    if (config.apiKey) {
      addLog(`检测到独立配置，优先使用工具级独立 API Key (覆盖全局默认配置)`, 'warning');
      addLog(`使用局部模型版本: ${config.model || '跟随底座'}`, 'info');
    } else {
      addLog(`未配置工具级 Key，继承底座全局默认 AI 配置进行调用`, 'info');
      addLog(`调用全局模型版本: claude-3-5-sonnet`, 'info');
    }
    addLog('正在向大模型网关发起连接请求 (SSE 协议)...', 'system');
  }, 600);

  // 2. 模拟 SSE 握手成功，开始流式传输
  setTimeout(() => {
    status.value = 'generating';
    addLog('连接建立成功，开始流式接收大模型生成章节...', 'system');
    
    let charIdx = 0;
    const typingInterval = setInterval(() => {
      // 每次吐出 6 个字，加快演示节奏
      const chunk = mockLlmResponse.substring(charIdx, charIdx + 6);
      logs.value.push({
        time: getNowTime(),
        type: 'llm',
        text: chunk
      });
      charIdx += 6;
      
      // 滚动到底部
      if (consoleRef.value) {
        consoleRef.value.scrollTop = consoleRef.value.scrollHeight;
      }

      if (charIdx >= mockLlmResponse.length) {
        clearInterval(typingInterval);
        
        // 3. 进入 POI 排版阶段
        setTimeout(() => {
          status.value = 'building';
          addLog('大模型流式输出完毕，关闭 SSE 通道', 'system');
          addLog(`开始调用底座 POI 模版渲染器，应用主题: [${config.theme === 'tech' ? '科技冷青' : config.theme === 'business' ? '商务深蓝' : '极简灰白'}]`, 'system');
          addLog('正在生成封面、目录、图表并应用段落间距与中文字体...', 'info');
        }, 500);

        // 4. 生成文件落盘成功
        setTimeout(() => {
          status.value = 'success';
          running.value = false;
          addLog('Word 文档排版并拼接成功！', 'system');
          addLog('已生成临时文件路径：/var/data/ailab/temp/AI-Lab-Hub_Generated_Report.docx', 'success');
          addLog('请点击下方按钮下载您的 Word 报告！', 'success');
        }, 1800);
      }
    }, 40);
  }, 1800);
};

// 触发下载 Mock Word 报告（写了文字说明的文档）
const downloadMockFile = () => {
  const fileContent = `==================================================\nAI-LAB-HUB WORD GENERATOR MOCK REPORT\n==================================================\n\n文档标题：${config.title}\n排版模板：${config.theme === 'tech' ? '科技冷青' : config.theme === 'business' ? '商务深蓝' : '极简灰白'}\n\n大纲内容：\n${config.outline}\n\n--------------------------------------------------\n以下为 AI 自动流式生成的报告正文内容：\n--------------------------------------------------\n\n${mockLlmResponse}`;
  
  const blob = new Blob([fileContent], { type: 'text/plain;charset=utf-8' });
  const url = URL.createObjectURL(blob);
  const link = document.createElement('a');
  link.href = url;
  
  // 命名为 .docx (实际内容为文本，但能演示出下载文件的流程效果)
  link.download = `${config.title || 'AI-Lab-Hub自动生成报告'}.docx`;
  document.body.appendChild(link);
  link.click();
  document.body.removeChild(link);
  URL.revokeObjectURL(url);
  
  addLog('文件下载指令发送成功，请注意检查浏览器下载文件夹。', 'success');
};
</script>

<style scoped>
.tool-page {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.back-link {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  color: var(--text-secondary);
  font-weight: 600;
  transition: color var(--transition-fast);
  align-self: flex-start;
  padding: 4px 8px;
  border-radius: var(--radius-sm);
}

.back-link:hover {
  color: var(--text-primary);
  background: var(--surface-color);
}

.back-icon {
  width: 16px;
  height: 16px;
}

.tool-layout {
  display: grid;
  grid-template-columns: 400px 1fr;
  gap: 20px;
  align-items: start;
}

/* 统一卡片头部 */
.section-header {
  display: flex;
  align-items: center;
  gap: 10px;
  padding-bottom: 16px;
  margin-bottom: 20px;
  border-bottom: 1px solid var(--border-color);
}

.header-icon {
  width: 20px;
  height: 20px;
  color: var(--primary-color);
}

.section-header h3 {
  font-size: 16px;
  font-weight: 700;
  flex-grow: 1;
}

/* 左侧配置栏 */
.form-section {
  padding: 24px;
  background: var(--surface-color);
}

.tool-form {
  display: flex;
  flex-direction: column;
  gap: 18px;
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
  padding-left: 2px;
}

.form-group input, 
.form-group select, 
.form-group textarea {
  width: 100%;
  padding: 10px 14px;
  border: 1px solid var(--border-color);
  background: rgba(var(--bg-color), 0.3);
  color: var(--text-primary);
  border-radius: var(--radius-md);
  transition: all var(--transition-fast);
}

.form-group input:focus, 
.form-group select:focus, 
.form-group textarea:focus {
  border-color: var(--border-focus);
  box-shadow: 0 0 0 3px var(--primary-glow);
  background: var(--surface-solid);
}

/* 折叠面板 (AI配置) */
.config-accordion {
  border: 1px solid var(--border-color);
  border-radius: var(--radius-md);
  overflow: hidden;
}

.accordion-trigger {
  width: 100%;
  padding: 12px 14px;
  background: rgba(var(--bg-color), 0.2);
  border: none;
  display: flex;
  justify-content: space-between;
  align-items: center;
  cursor: pointer;
  color: var(--text-secondary);
  transition: background var(--transition-fast);
}

.accordion-trigger:hover {
  background: rgba(var(--bg-color), 0.4);
}

.trigger-label {
  display: flex;
  align-items: center;
  gap: 10px;
  font-size: 12px;
  font-weight: 600;
}

.trigger-icon {
  width: 14px;
  height: 14px;
}

.arrow-icon {
  width: 14px;
  height: 14px;
  transition: transform var(--transition-fast);
}

.arrow-icon.rotated {
  transform: rotate(180deg);
}

.accordion-content {
  padding: 14px;
  display: flex;
  flex-direction: column;
  gap: 12px;
  border-top: 1px solid var(--border-color);
  background: rgba(0, 0, 0, 0.05);
}

.start-btn {
  width: 100%;
  height: 44px;
  justify-content: center;
  font-size: 15px;
}

.btn-icon {
  width: 16px;
  height: 16px;
}

/* 右侧控制台 */
.console-section {
  padding: 24px;
  background: var(--surface-color);
  display: flex;
  flex-direction: column;
  height: 600px;
}

.status-badge {
  font-size: 11px;
  font-weight: 700;
  padding: 3px 10px;
  border-radius: 20px;
}

.status-idle { background: var(--border-color); color: var(--text-muted); }
.status-connecting { background: rgba(99, 102, 241, 0.15); color: #818cf8; }
.status-generating { background: rgba(168, 85, 247, 0.15); color: #c084fc; }
.status-building { background: rgba(245, 158, 11, 0.15); color: #fbbf24; }
.status-success { background: rgba(16, 185, 129, 0.15); color: #34d399; }

/* 终端框 */
.console-box {
  flex-grow: 1;
  background: #070913;
  border: 1px solid #1a2035;
  border-radius: var(--radius-md);
  padding: 16px;
  font-family: 'Courier New', Courier, monospace;
  font-size: 13px;
  overflow-y: auto;
  display: flex;
  flex-direction: column;
  gap: 6px;
  line-height: 1.5;
  box-shadow: inset 0 2px 8px rgba(0,0,0,0.8);
}

.log-line {
  word-break: break-all;
}

.log-time {
  color: #4b5563;
  margin-right: 8px;
}

/* 控制台行颜色 */
.log-system { color: #38bdf8; font-weight: 600; } /* 蓝 */
.log-warning { color: #facc15; } /* 黄 */
.log-info { color: #94a3b8; } /* 灰 */
.log-success { color: #4ade80; font-weight: 600; } /* 绿 */
.log-llm { color: #a78bfa; white-space: pre-wrap; } /* 紫色打字 */

/* 打字光标动画 */
.typing-cursor {
  display: inline-block;
  width: 8px;
  height: 15px;
  background: #a78bfa;
  margin-left: 4px;
  animation: blink 0.8s infinite;
  align-self: flex-start;
}

@keyframes blink {
  0%, 100% { opacity: 0; }
  50% { opacity: 1; }
}

.console-footer {
  margin-top: 16px;
}

.download-btn {
  width: 100%;
  height: 46px;
  justify-content: center;
  font-size: 15px;
}

/* 折叠动画 */
.slide-fade-enter-active,
.slide-fade-leave-active {
  transition: all 0.25s ease-out;
}

.slide-fade-enter-from,
.slide-fade-leave-to {
  opacity: 0;
  transform: translateY(-8px);
}
</style>
