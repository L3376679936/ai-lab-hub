<template>
  <div class="tool-page">
    <!-- 返回头部 -->
    <div class="page-header">
      <router-link to="/" class="back-link">
        <ArrowLeft class="back-icon" />
        <span>返回工具箱列表</span>
      </router-link>
      <div class="page-title">
        <h2>Word 自动生成工作流</h2>
        <span class="subtitle">针对单一接口设计说明场景，一键解析代码/Swagger并物理填充至指定规范Word模板</span>
      </div>
    </div>

    <!-- 方案 A：双卡片 Bento 分栏看板布局 -->
    <div class="bento-layout">
      <!-- 左栏：模板与资料输入 -->
      <div class="left-panel">
        <a-card :bordered="false" class="panel-card input-card">
          <template #title>
            <div class="card-title-box">
              <FileTextOutlined class="title-icon" />
              <span>1. 录入配置与原始资料</span>
            </div>
          </template>

          <a-form layout="vertical">
            <!-- 模板选择 -->
            <a-form-item label="文档格式模板选择">
              <a-radio-group v-model:value="config.templateType" class="full-width-radio">
                <a-radio-button value="nannan">河北南网接口文档格式 (内置默认)</a-radio-button>
                <a-radio-button value="custom">自定义上传模板 (.docx)</a-radio-button>
              </a-radio-group>
            </a-form-item>

            <!-- 自定义模板上传区 -->
            <div v-if="config.templateType === 'custom'" class="upload-container">
              <a-upload-dragger
                name="file"
                :multiple="false"
                action=""
                :beforeUpload="handleTemplateUpload"
              >
                <p class="ant-upload-drag-icon">
                  <InboxOutlined />
                </p>
                <p class="ant-upload-text">将您的 .docx 模板文件拖拽到此处，或点击浏览上传</p>
                <p class="ant-upload-hint">系统将自动分析并匹配模板中的动态替换标签</p>
              </a-upload-dragger>
            </div>

            <!-- 数据源输入 -->
            <a-form-item label="原始资料/数据源录入" class="margin-top-md">
              <a-textarea
                v-model:value="config.material"
                :rows="12"
                placeholder="在此黏贴您的零散资料。支持：&#10;1. Spring Boot Controller Java代码&#10;2. Swagger / OpenAPI JSON/YAML&#10;3. 原始 Markdown 接口说明或表格"
                class="code-textarea"
              />
            </a-form-item>

            <!-- 一键解析动作 -->
            <a-button 
              type="primary" 
              size="large" 
              block 
              :loading="parsing" 
              @click="startParsing"
              class="parse-btn"
            >
              <template #icon><ThunderboltOutlined /></template>
              <span>{{ parsing ? '大模型正在智能提取结构中...' : '智能提取接口数据' }}</span>
            </a-button>
          </a-form>
        </a-card>
      </div>

      <!-- 右栏：解析字段可视化微调 -->
      <div class="right-panel">
        <a-card :bordered="false" class="panel-card result-card">
          <template #title>
            <div class="card-title-box">
              <SlidersOutlined class="title-icon" />
              <span>2. 字段校对与智能补全</span>
            </div>
          </template>
          
          <!-- 未解析时的空状态 -->
          <div v-if="apiList.length === 0" class="empty-holder">
            <a-empty description="等待提取左侧原始资料...">
              <template #image>
                <img src="https://gw.alipayobjects.com/zos/antfincdn/ZHrcdLPrvN/empty.svg" alt="empty" />
              </template>
            </a-empty>
          </div>

          <!-- 解析出接口后的大纲编辑区 -->
          <div v-else class="api-editor-area">
            <div class="helper-bar">
              <span class="badge">已成功提取 {{ apiList.length }} 个接口大纲</span>
              <span class="tip">温馨提示：您可直接在下方修改任一数据，双击表格单元格即可直接编辑参数。</span>
            </div>

            <a-collapse v-model:activeKey="activeApiIndex" accordion class="api-collapse">
              <a-collapse-panel v-for="(api, idx) in apiList" :key="idx">
                <template #header>
                  <div class="panel-header-title">
                    <span :class="['method-tag', api.method.toLowerCase()]">{{ api.method }}</span>
                    <strong class="api-name-text">{{ api.name }}</strong>
                    <span class="api-url-text">{{ api.url }}</span>
                  </div>
                </template>

                <a-form layout="vertical" class="inner-form">
                  <div class="two-column-row">
                    <a-form-item label="接口中文名称" class="flex-1">
                      <a-input v-model:value="api.name" />
                    </a-form-item>
                    <a-form-item label="请求 URL" class="flex-1">
                      <a-input v-model:value="api.url" />
                    </a-form-item>
                  </div>

                  <a-form-item label="接口描述说明">
                    <a-textarea v-model:value="api.description" :rows="2" />
                  </a-form-item>

                  <!-- 请求参数表 -->
                  <div class="table-section">
                    <div class="table-title">
                      <span>请求参数说明 (Request Params)</span>
                      <a-button type="link" size="small" @click="addParam(api.requestParams)">
                        + 添加参数
                      </a-button>
                    </div>
                    <a-table 
                      :dataSource="api.requestParams" 
                      :columns="paramColumns" 
                      size="small" 
                      :pagination="false"
                      bordered
                    >
                      <template #bodyCell="{ column, record, index }">
                        <template v-if="column.key === 'name'">
                          <a-input v-model:value="record.name" size="small" />
                        </template>
                        <template v-if="column.key === 'required'">
                          <a-select v-model:value="record.required" size="small" class="select-required">
                            <a-select-option value="是">是</a-select-option>
                            <a-select-option value="否">否</a-select-option>
                          </a-select>
                        </template>
                        <template v-if="column.key === 'type'">
                          <a-input v-model:value="record.type" size="small" />
                        </template>
                        <template v-if="column.key === 'description'">
                          <a-input v-model:value="record.description" size="small" />
                        </template>
                        <template v-if="column.key === 'action'">
                          <a-button type="link" danger size="small" @click="deleteParam(api.requestParams, index)">
                            删除
                          </a-button>
                        </template>
                      </template>
                    </a-table>
                  </div>

                  <!-- 返回参数表 -->
                  <div class="table-section margin-top-md">
                    <div class="table-title">
                      <span>返回参数说明 (Response Params)</span>
                      <a-button type="link" size="small" @click="addParam(api.responseParams)">
                        + 添加参数
                      </a-button>
                    </div>
                    <a-table 
                      :dataSource="api.responseParams" 
                      :columns="responseColumns" 
                      size="small" 
                      :pagination="false"
                      bordered
                    >
                      <template #bodyCell="{ column, record, index }">
                        <template v-if="column.key === 'name'">
                          <a-input v-model:value="record.name" size="small" />
                        </template>
                        <template v-if="column.key === 'type'">
                          <a-input v-model:value="record.type" size="small" />
                        </template>
                        <template v-if="column.key === 'description'">
                          <a-input v-model:value="record.description" size="small" />
                        </template>
                        <template v-if="column.key === 'action'">
                          <a-button type="link" danger size="small" @click="deleteParam(api.responseParams, index)">
                            删除
                          </a-button>
                        </template>
                      </template>
                    </a-table>
                  </div>

                  <!-- 示例卡片 -->
                  <div class="two-column-row margin-top-md">
                    <a-form-item label="入参 JSON 示例" class="flex-1">
                      <a-textarea v-model:value="api.requestExample" :rows="4" class="font-mono" />
                    </a-form-item>
                    <a-form-item label="返回 JSON 示例" class="flex-1">
                      <a-textarea v-model:value="api.responseExample" :rows="4" class="font-mono" />
                    </a-form-item>
                  </div>

                  <!-- 智能补全 -->
                  <div class="action-bar">
                    <a-button 
                      type="dashed" 
                      :loading="api.completing" 
                      @click="autoCompleteApi(api)"
                      class="magic-btn"
                    >
                      <span>🪄 一键 AI 脑补字段含义与 JSON 示例</span>
                    </a-button>
                  </div>
                </a-form>
              </a-collapse-panel>
            </a-collapse>

            <!-- 导出阶段 -->
            <div class="export-section">
              <a-button 
                type="primary" 
                size="large" 
                block 
                :loading="exporting" 
                @click="exportToWord"
                class="export-btn"
              >
                <template #icon><DownloadOutlined /></template>
                <span>一键导出为规范 Word 文档 (.docx)</span>
              </a-button>
            </div>
          </div>
        </a-card>
      </div>
    </div>
  </div>
</template>

<script setup>
import { reactive, ref } from 'vue';
import { 
  ArrowLeft 
} from 'lucide-vue-next';
import { 
  FileTextOutlined,
  SlidersOutlined,
  ThunderboltOutlined,
  InboxOutlined,
  DownloadOutlined
} from '@ant-design/icons-vue';
import { message } from 'ant-design-vue';

// 配置表单
const config = reactive({
  templateType: 'nannan',
  material: ''
});

// 解析与导出状态
const parsing = ref(false);
const exporting = ref(false);
const activeApiIndex = ref(0);

// 解析结果接口列表
const apiList = ref([]);

// 字段定义
const paramColumns = [
  { title: '参数名称', dataIndex: 'name', key: 'name', width: '25%' },
  { title: '是否必填', dataIndex: 'required', key: 'required', width: '20%' },
  { title: '类型', dataIndex: 'type', key: 'type', width: '20%' },
  { title: '字段说明', dataIndex: 'description', key: 'description', width: '25%' },
  { title: '操作', key: 'action', width: '10%', align: 'center' }
];

const responseColumns = [
  { title: '参数名称', dataIndex: 'name', key: 'name', width: '30%' },
  { title: '类型', dataIndex: 'type', key: 'type', width: '25%' },
  { title: '字段说明', dataIndex: 'description', key: 'description', width: '35%' },
  { title: '操作', key: 'action', width: '10%', align: 'center' }
];

// 自定义模板上传拦截
const handleTemplateUpload = (file) => {
  message.success(`本地模板 "${file.name}" 上传成功，将作为排版底标进行字段插值。`);
  return false; // 阻断自动上传，保存在本地
};

// 1. 触发大模型提取数据
const startParsing = () => {
  if (!config.material.trim()) {
    message.warning('请先在左侧输入框贴入原始接口代码或资料！');
    return;
  }
  
  parsing.value = true;
  apiList.value = [];

  // 模拟大模型提取响应
  setTimeout(() => {
    parsing.value = false;
    message.success('AI 智能分析大纲抽取完成！已自动为您生成结构化字段。');
    
    // Mock 结构化抽取后的数据
    apiList.value = [
      {
        name: '首页大屏-容量分类',
        url: '/index/bigScreen/capacityCategory',
        method: 'GET',
        description: '按额定容量分档统计已启用且关联场站的机组数量',
        requestParams: [
          { name: 'status', required: '否', type: 'Int', description: '场站状态过滤 (1:启用, 0:停用)' }
        ],
        requestExample: '{\n  "status": 1\n}',
        responseExample: '{\n  "status": 200,\n  "message": "操作成功",\n  "data": [\n    { "type": "1000MW", "count": 12 },\n    { "type": "600MW", "count": 28 }\n  ]\n}',
        responseParams: [
          { name: 'status', type: 'Int', description: '响应代码 (200:成功)' },
          { name: 'message', type: 'String', description: '响应提示信息' },
          { name: 'data', type: 'List', description: '容量分档统计列表数据' }
        ],
        completing: false
      },
      {
        name: '机组涉网性能指标上报',
        url: '/performance/evaluate/submit',
        method: 'POST',
        description: '用于火力发电机组一次调频、AGC性能、励磁系统参数等涉网指标的数据申报',
        requestParams: [
          { name: 'unitId', required: '是', type: 'Long', description: '机组物理 ID' },
          { name: 'evaluateDate', required: '是', type: 'String', description: '评估周期时间 (yyyy-MM)' },
          { name: 'agcScore', required: '否', type: 'Float', description: 'AGC 调频综合评分' }
        ],
        requestExample: '{\n  "unitId": 9931,\n  "evaluateDate": "2025-02",\n  "agcScore": 94.5\n}',
        responseExample: '{\n  "status": 200,\n  "message": "申报上报成功"\n}',
        responseParams: [
          { name: 'status', type: 'Int', description: '操作状态' },
          { name: 'message', type: 'String', description: '处理回执信息' }
        ],
        completing: false
      }
    ];
    activeApiIndex.value = 0;
  }, 1500);
};

// 字段增加删除
const addParam = (paramsList) => {
  paramsList.push({
    name: '',
    required: '否',
    type: 'String',
    description: ''
  });
  message.info('已新增一行参数槽位，请完善定义');
};

const deleteParam = (paramsList, idx) => {
  paramsList.splice(idx, 1);
  message.info('参数已移除');
};

// 🪄 单接口 AI 补全
const autoCompleteApi = (api) => {
  api.completing = true;
  setTimeout(() => {
    api.completing = false;
    // 补全所有缺失的说明，并丰富 JSON
    api.requestParams.forEach(p => {
      if (!p.description) p.description = `AI自动生成的[${p.name}]属性业务含义说明`;
    });
    api.responseParams.forEach(p => {
      if (!p.description) p.description = `AI根据返回结构预测 of [${p.name}]字段说明`;
    });
    message.success(`[${api.name}] 缺漏字段说明及示例已由 AI 成功补齐润色！`);
  }, 1200);
};

// 2. 导出 Word 文档
const exportToWord = () => {
  exporting.value = true;
  message.loading({ content: '正在读取河北南网火电接口模板样式...', key: 'export' });

  setTimeout(() => {
    message.loading({ content: '正在物理插值克隆表格样式并写入段落...', key: 'export', duration: 1 });
    
    setTimeout(() => {
      exporting.value = false;
      message.success({ content: '河北南网火电机组涉网性能接口说明文档生成成功！文件已存入磁盘。', key: 'export', duration: 3 });
      
      // 弹出模拟下载提示
      const confirmDownload = confirm('文档已物理生成完毕，是否立即下载？\n文件名：河北南网火电机组涉网性能评估分析系统接口文档_Generated.docx');
      if (confirmDownload) {
        message.info('模拟开始下载 docx 报告中...');
      }
    }, 1500);
  }, 1200);
};
</script>

<style scoped>
.tool-page {
  padding: 24px;
  background: var(--bg-color);
  min-height: calc(100vh - 64px);
  color: var(--text-primary);
}

.page-header {
  display: flex;
  flex-direction: column;
  gap: 8px;
  margin-bottom: 24px;
}

.back-link {
  display: flex;
  align-items: center;
  gap: 6px;
  color: var(--text-secondary);
  font-size: 13px;
  text-decoration: none;
  transition: color var(--transition-fast);
}

.back-link:hover {
  color: var(--primary-color);
}

.back-icon {
  width: 14px;
  height: 14px;
}

.page-title h2 {
  font-size: 22px;
  font-weight: 700;
  color: var(--text-primary);
  margin: 0;
}

.subtitle {
  font-size: 13px;
  color: var(--text-muted);
}

/* Bento 双卡片布局 */
.bento-layout {
  display: grid;
  grid-template-columns: 1fr 1.2fr;
  gap: 24px;
  align-items: start;
}

.panel-card {
  background: var(--surface-color);
  border: 1px solid var(--border-color);
  border-radius: var(--radius-lg);
  box-shadow: 0 10px 30px -10px rgba(0, 0, 0, 0.3);
  backdrop-filter: blur(10px);
}

.card-title-box {
  display: flex;
  align-items: center;
  gap: 10px;
  font-size: 16px;
  font-weight: 700;
  color: var(--text-primary);
}

.title-icon {
  color: var(--primary-color);
  font-size: 18px;
}

.full-width-radio {
  width: 100%;
  display: flex;
}

.full-width-radio :deep(.ant-radio-button-wrapper) {
  flex: 1;
  text-align: center;
  background: rgba(255, 255, 255, 0.03);
  border-color: var(--border-color);
  color: var(--text-secondary);
}

.full-width-radio :deep(.ant-radio-button-wrapper-checked) {
  background: var(--primary-gradient) !important;
  color: #ffffff !important;
  border-color: transparent !important;
}

.upload-container {
  margin-top: 14px;
  background: rgba(0, 0, 0, 0.15);
  border-radius: var(--radius-md);
  padding: 8px;
}

:deep(.ant-upload-drag) {
  background: transparent !important;
  border: 1px dashed var(--border-color) !important;
}

:deep(.ant-upload-drag:hover) {
  border-color: var(--primary-color) !important;
}

.margin-top-md {
  margin-top: 20px;
}

.code-textarea {
  font-family: var(--font-mono);
  background: rgba(0, 0, 0, 0.2);
  border-color: var(--border-color);
  color: #f1f1f1;
  border-radius: var(--radius-md);
}

.code-textarea:focus {
  border-color: var(--primary-color);
  box-shadow: 0 0 0 2px var(--primary-glow);
}

.parse-btn {
  margin-top: 14px;
  height: 48px;
  font-weight: 700;
  background: var(--primary-gradient);
  border: none;
  box-shadow: 0 4px 15px var(--primary-glow);
}

/* 右栏：字段微调区域 */
.empty-holder {
  padding: 100px 0;
  display: flex;
  justify-content: center;
  align-items: center;
}

.helper-bar {
  display: flex;
  flex-direction: column;
  gap: 6px;
  margin-bottom: 18px;
}

.badge {
  background: rgba(139, 92, 246, 0.15);
  border: 1px solid rgba(139, 92, 246, 0.3);
  color: var(--primary-light);
  padding: 6px 14px;
  border-radius: 50px;
  font-size: 12px;
  font-weight: 700;
  width: fit-content;
}

.tip {
  font-size: 12px;
  color: var(--text-muted);
}

.api-collapse {
  background: transparent !important;
  border: none !important;
  display: flex;
  flex-direction: column;
  gap: 12px;
}

:deep(.ant-collapse-item) {
  background: rgba(255, 255, 255, 0.02) !important;
  border: 1px solid var(--border-color) !important;
  border-radius: var(--radius-md) !important;
  overflow: hidden;
}

:deep(.ant-collapse-header) {
  background: rgba(0, 0, 0, 0.15) !important;
  color: var(--text-primary) !important;
}

.panel-header-title {
  display: flex;
  align-items: center;
  gap: 12px;
}

.method-tag {
  font-size: 11px;
  font-weight: 900;
  padding: 2px 8px;
  border-radius: 4px;
  text-transform: uppercase;
}

.method-tag.get {
  background: rgba(16, 185, 129, 0.15);
  color: #10b981;
  border: 1px solid rgba(16, 185, 129, 0.3);
}

.method-tag.post {
  background: rgba(245, 158, 11, 0.15);
  color: #f59e0b;
  border: 1px solid rgba(245, 158, 11, 0.3);
}

.api-name-text {
  font-size: 13px;
}

.api-url-text {
  font-family: var(--font-mono);
  font-size: 12px;
  color: var(--text-muted);
}

.inner-form {
  padding: 8px 0;
}

.two-column-row {
  display: flex;
  gap: 16px;
}

.flex-1 {
  flex: 1;
}

.table-section {
  background: rgba(0, 0, 0, 0.12);
  border-radius: var(--radius-md);
  padding: 12px;
  border: 1px solid var(--border-color);
}

.table-title {
  display: flex;
  justify-content: space-between;
  align-items: center;
  font-size: 12px;
  font-weight: 700;
  color: var(--text-secondary);
  margin-bottom: 10px;
  border-left: 2px solid var(--primary-color);
  padding-left: 8px;
}

:deep(.ant-table) {
  background: transparent !important;
  color: var(--text-primary) !important;
}

:deep(.ant-table-thead > tr > th) {
  background: rgba(255, 255, 255, 0.04) !important;
  color: var(--text-secondary) !important;
  border-bottom: 1px solid var(--border-color) !important;
  font-size: 12px;
  font-weight: 600;
}

:deep(.ant-table-tbody > tr > td) {
  border-bottom: 1px solid var(--border-color) !important;
  background: transparent !important;
}

:deep(.ant-table-cell) {
  padding: 6px 12px !important;
}

.select-required {
  width: 100%;
}

.font-mono {
  font-family: var(--font-mono);
  font-size: 12px;
}

.action-bar {
  display: flex;
  justify-content: flex-end;
  margin-top: 14px;
}

.magic-btn {
  background: rgba(139, 92, 246, 0.08) !important;
  border-color: rgba(139, 92, 246, 0.4) !important;
  color: var(--primary-light) !important;
}

.magic-btn:hover {
  border-color: var(--primary-color) !important;
  background: rgba(139, 92, 246, 0.15) !important;
}

.export-section {
  margin-top: 24px;
  border-top: 1px solid var(--border-color);
  padding-top: 20px;
}

.export-btn {
  height: 52px;
  font-weight: 700;
  font-size: 15px;
  background: linear-gradient(135deg, #10b981 0%, #059669 100%);
  border: none;
  box-shadow: 0 4px 15px rgba(16, 185, 129, 0.3);
}

.export-btn:hover {
  box-shadow: 0 6px 20px rgba(16, 185, 129, 0.4);
}
</style>
