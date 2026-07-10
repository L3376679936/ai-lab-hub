<template>
  <div class="dashboard-page">
    <!-- 欢迎栏与搜索条 -->
    <div class="dashboard-header glass-panel">
      <div class="welcome-text">
        <h3>欢迎使用 AI 工具箱 👋</h3>
        <p>在这里，您可以集中运行和调优您沉淀的所有 AI 自动化工作流与脚本。</p>
      </div>
      
      <div class="search-box">
        <Search class="search-icon" />
        <input 
          type="text" 
          v-model="searchQuery" 
          placeholder="搜索您想运行的 AI 小工具..."
        />
      </div>
    </div>

    <!-- 过滤器与分类 -->
    <div class="filter-bar">
      <div class="category-tags">
        <button 
          v-for="cat in categories" 
          :key="cat.value" 
          @click="activeCategory = cat.value"
          :class="['tag-btn', { active: activeCategory === cat.value }]"
        >
          {{ cat.label }}
        </button>
      </div>
      <div class="tool-count">
        共加载了 <span>{{ filteredTools.length }}</span> 个效率工具
      </div>
    </div>

    <!-- 工具卡片网格 -->
    <div class="tools-grid">
      <div 
        v-for="tool in filteredTools" 
        :key="tool.code" 
        class="tool-card glass-panel hover-card"
        :class="{ 'status-offline': tool.status === 0 }"
      >
        <div class="card-glow"></div>
        <div class="tool-icon-wrapper" :style="{ background: tool.color + '15', color: tool.color }">
          <component :is="tool.iconComponent" class="tool-card-icon" />
        </div>
        
        <div class="tool-info">
          <div class="title-row">
            <h4>{{ tool.name }}</h4>
            <span :class="['badge', tool.status === 1 ? 'badge-online' : 'badge-offline']">
              {{ tool.status === 1 ? '已上线' : '建设中' }}
            </span>
          </div>
          <p>{{ tool.description }}</p>
        </div>
        
        <div class="tool-actions">
          <router-link 
            v-if="tool.status === 1" 
            :to="'/tool/' + tool.code" 
            class="btn-primary start-btn"
          >
            <span>运行工作流</span>
            <ArrowRight class="action-arrow" />
          </router-link>
          
          <button 
            v-else 
            disabled 
            class="btn-secondary disabled-btn"
          >
            <span>开发中</span>
          </button>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, shallowRef } from 'vue';
import { 
  Search, 
  ArrowRight, 
  FileText, 
  BookOpen, 
  Database,
  Cpu
} from 'lucide-vue-next';

// 搜索与分类状态
const searchQuery = ref('');
const activeCategory = ref('all');

const categories = [
  { label: '全部小工具', value: 'all' },
  { label: '日常办公', value: 'office' },
  { label: '开发辅助', value: 'dev' }
];

// 工具列表原始数据
const tools = [
  {
    code: 'labGenWord',
    name: 'Word 自动生成',
    description: '通过输入文档大纲或需求，调用大模型生成章节并全自动导出为排版精美的 Word 报告。',
    category: 'office',
    status: 1,
    iconComponent: shallowRef(FileText),
    color: '#6366f1' // 靛蓝
  },
  {
    code: 'labPdfParser',
    name: 'PDF 智能文档解析',
    description: '上传 PDF 扫描件或电子书，AI 自动提取关键实体、翻译章节并自动整理成 Markdown 笔记。',
    category: 'office',
    status: 0,
    iconComponent: shallowRef(BookOpen),
    color: '#ec4899' // 粉红
  },
  {
    code: 'labSqlWriter',
    name: 'SQL 自动编写',
    description: '输入自然语言描述或数据库 Schema，AI 自动生成优化的 DDL 和复杂查询 SQL 语句。',
    category: 'dev',
    status: 0,
    iconComponent: shallowRef(Database),
    color: '#10b981' // 翡翠绿
  },
  {
    code: 'labMcpCenter',
    name: 'MCP 连接测试中心',
    description: '调试和验证本地拉起的各种 Model Context Protocol 服务连接，测试其 Tool Call 的响应延迟。',
    category: 'dev',
    status: 0,
    iconComponent: shallowRef(Cpu),
    color: '#f59e0b' // 琥珀黄
  }
];

// 混合过滤逻辑
const filteredTools = computed(() => {
  return tools.filter(tool => {
    const matchesSearch = tool.name.toLowerCase().includes(searchQuery.value.toLowerCase()) ||
                          tool.description.toLowerCase().includes(searchQuery.value.toLowerCase());
    
    const matchesCategory = activeCategory.value === 'all' || tool.category === activeCategory.value;
    
    return matchesSearch && matchesCategory;
  });
});
</script>

<style scoped>
.dashboard-page {
  display: flex;
  flex-direction: column;
  gap: 20px;
}

/* 顶部欢迎栏 */
.dashboard-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 24px;
  background: var(--surface-color);
}

.welcome-text h3 {
  font-size: 20px;
  font-weight: 700;
  margin-bottom: 6px;
  color: var(--text-primary);
}

.welcome-text p {
  font-size: 14px;
  color: var(--text-secondary);
}

/* 搜索框 */
.search-box {
  position: relative;
  width: 320px;
}

.search-icon {
  position: absolute;
  left: 14px;
  top: 50%;
  transform: translateY(-50%);
  width: 18px;
  height: 18px;
  color: var(--text-muted);
}

.search-box input {
  width: 100%;
  height: 42px;
  padding: 0 16px 0 42px;
  border-radius: var(--radius-md);
  border: 1px solid var(--border-color);
  background: rgba(var(--bg-color), 0.5);
  color: var(--text-primary);
  transition: all var(--transition-fast);
}

.search-box input:focus {
  border-color: var(--border-focus);
  box-shadow: 0 0 0 3px var(--primary-glow);
  background: var(--surface-solid);
}

/* 过滤栏 */
.filter-bar {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.category-tags {
  display: flex;
  gap: 8px;
}

.tag-btn {
  background: var(--surface-color);
  border: 1px solid var(--border-color);
  padding: 8px 16px;
  border-radius: var(--radius-sm);
  color: var(--text-secondary);
  font-weight: 600;
  cursor: pointer;
  transition: all var(--transition-fast);
}

.tag-btn:hover {
  background: var(--surface-hover);
  color: var(--text-primary);
}

.tag-btn.active {
  background: var(--primary-gradient);
  color: #ffffff;
  border-color: transparent;
  box-shadow: 0 2px 8px var(--primary-glow);
}

.tool-count {
  font-size: 13px;
  color: var(--text-secondary);
}

.tool-count span {
  font-weight: 700;
  color: var(--primary-color);
}

/* 卡片网格 */
.tools-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(320px, 1fr));
  gap: 20px;
}

.tool-card {
  position: relative;
  display: flex;
  flex-direction: column;
  padding: 24px;
  background: var(--surface-color);
  border-radius: var(--radius-lg) !important;
  overflow: hidden;
}

/* 卡片发光球 */
.card-glow {
  position: absolute;
  top: -60px;
  right: -60px;
  width: 120px;
  height: 120px;
  background: var(--primary-color);
  filter: blur(50px);
  border-radius: 50%;
  opacity: 0.05;
  transition: opacity var(--transition-normal);
}

.tool-card:hover .card-glow {
  opacity: 0.15;
}

.tool-icon-wrapper {
  width: 48px;
  height: 48px;
  border-radius: var(--radius-md);
  display: flex;
  align-items: center;
  justify-content: center;
  margin-bottom: 20px;
}

.tool-card-icon {
  width: 24px;
  height: 24px;
}

.tool-info {
  flex-grow: 1;
  margin-bottom: 24px;
}

.title-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 10px;
}

.title-row h4 {
  font-size: 16px;
  font-weight: 700;
  color: var(--text-primary);
}

.badge {
  font-size: 11px;
  font-weight: 700;
  padding: 3px 8px;
  border-radius: 20px;
}

.badge-online {
  background: rgba(16, 185, 129, 0.12);
  color: #10b981;
}

.badge-offline {
  background: var(--border-color);
  color: var(--text-muted);
}

.tool-info p {
  font-size: 13px;
  color: var(--text-secondary);
  line-height: 1.6;
}

.tool-actions {
  display: flex;
}

.start-btn {
  width: 100%;
  justify-content: center;
  height: 42px;
}

.action-arrow {
  width: 16px;
  height: 16px;
  transition: transform var(--transition-fast);
}

.start-btn:hover .action-arrow {
  transform: translateX(4px);
}

.disabled-btn {
  width: 100%;
  height: 42px;
  justify-content: center;
  cursor: not-allowed;
  opacity: 0.6;
}
</style>
