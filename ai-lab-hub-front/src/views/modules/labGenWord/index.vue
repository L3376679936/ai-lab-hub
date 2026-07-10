<template>
  <div class="tool-page" @paste="handlePaste">
    <!-- 返回头部 -->
    <div class="page-header">
      <div class="header-top-row">
        <router-link to="/" class="back-link">
          <ArrowLeft class="back-icon" />
          <span>返回工具箱列表</span>
        </router-link>
        
        <!-- 界面方案热切换控制器（保留选型） -->
        <div class="layout-switcher">
          <span class="switcher-label">💡 界面演示切换器：</span>
          <a-radio-group v-model:value="currentLayout" button-style="solid" size="small">
            <a-radio-button value="A">方案 A：左右 Bento 看板 (已选定)</a-radio-button>
          </a-radio-group>
        </div>
      </div>
      
      <div class="page-title">
        <h2>Word 自动生成工作流</h2>
        <span class="subtitle">让 AI 自动读懂任意 Word 模板并提取属性，一键贴码/截图并物理克隆填充写回 Word 报告</span>
      </div>
    </div>

    <!-- ================================================================= -->
    <!-- 渲染 方案 A：双卡片 Bento 分栏看板布局 -->
    <!-- ================================================================= -->
    <div v-if="currentLayout === 'A'" class="bento-layout">
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
                <a-radio-button value="custom">上传自定义模板 (.docx)</a-radio-button>
              </a-radio-group>
            </a-form-item>

            <!-- 自定义模板上传区 -->
            <div v-if="config.templateType === 'custom'" class="upload-container">
              <a-upload-dragger
                name="file"
                :multiple="false"
                action="/ai-lab-hub-api/word/analyze-template"
                :headers="templateUploadHeaders"
                @change="handleTemplateUploadChange"
              >
                <p class="ant-upload-drag-icon">
                  <InboxOutlined />
                </p>
                <p class="ant-upload-text">将您的自定义 .docx 模板拖入此，或点击浏览上传</p>
                <p class="ant-upload-hint">✨ AI 将自动读懂模板大纲并自适应生成右侧修改表单！</p>
              </a-upload-dragger>
            </div>

            <!-- 截图/拓扑图粘贴上传区 -->
            <div class="image-paste-box">
              <div class="paste-zone">
                <div class="zone-label">📷 接口示意图/流程时序图 (支持直接 Ctrl + V 粘贴截图)</div>
                <div v-if="getCurApiImage()" class="preview-container">
                  <img :src="getCurApiImage()" class="paste-preview" alt="paste-preview" />
                  <a-button type="link" danger size="small" @click="removeCurApiImage" class="remove-img-btn">
                    移除图片
                  </a-button>
                </div>
                <div v-else class="paste-placeholder">
                  <span>在屏幕截图后，直接在此处按下 Ctrl + V 粘贴即可智能上传并绑定！</span>
                </div>
              </div>
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
              <span>{{ parsing ? '大模型正在读取 Schema 并对齐提取中...' : 'AI 智能提取并对齐数据' }}</span>
            </a-button>
          </a-form>
        </a-card>
      </div>

      <!-- 右栏：解析字段可视化微调 (完全 Schema 动态渲染) -->
      <div class="right-panel">
        <a-card :bordered="false" class="panel-card result-card">
          <template #title>
            <div class="card-title-box">
              <SlidersOutlined class="title-icon" />
              <span>2. 字段校对与智能补全 (已对齐模板 Schema)</span>
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
              <span class="badge">已成功提取 {{ apiList.length }} 个大纲区块</span>
              <span class="tip">温馨提示：点击标签页可切换接口；支持一键补全各字段。</span>
            </div>

            <a-tabs v-model:activeKey="activeApiIndex" type="card" class="api-tabs">
              <a-tab-pane v-for="(api, idx) in apiList" :key="idx" :tab="api.fields.name || ('区块 ' + (idx + 1))">
                
                <a-form layout="vertical" class="inner-form">
                  <!-- 1. 动态生成 Schema 中规划的 Fields 属性项 -->
                  <div class="dynamic-fields-grid">
                    <div v-for="field in schema.fields" :key="field.key" class="field-item">
                      <a-form-item :label="field.label">
                        <a-textarea v-if="field.type === 'textarea'" v-model:value="api.fields[field.key]" :rows="3" />
                        <a-input v-else v-model:value="api.fields[field.key]" />
                      </a-form-item>
                    </div>
                  </div>

                  <!-- 2. 动态生成 Schema 中规划的 Tables 表格 -->
                  <div v-for="tableSchema in schema.tables" :key="tableSchema.key" class="table-section margin-top-md">
                    <div class="table-title">
                      <span>{{ tableSchema.label }}</span>
                      <a-button type="link" size="small" @click="addRow(api.tables[tableSchema.key], tableSchema.columns)">
                        + 添加数据行
                      </a-button>
                    </div>

                    <a-table 
                      :dataSource="api.tables[tableSchema.key]" 
                      :columns="getAntdColumns(tableSchema.columns)" 
                      size="small" 
                      :pagination="false"
                      bordered
                    >
                      <template #bodyCell="{ column, record, index }">
                        <template v-if="column.key === 'action'">
                          <a-button type="link" danger size="small" @click="deleteRow(api.tables[tableSchema.key], index)">
                            删除
                          </a-button>
                        </template>
                        <template v-else>
                          <a-input v-model:value="record[column.key]" size="small" />
                        </template>
                      </template>
                    </a-table>
                  </div>

                  <!-- 3. 自定义扩展属性项 -->
                  <div class="custom-fields-section margin-top-md">
                    <div class="section-divider">自定义扩展项 (Key-Value)</div>
                    <div v-for="(val, key) in api.extraFields" :key="key" class="extra-field-row">
                      <a-input :value="key" @change="e => renameExtraKey(api, key, e.target.value)" class="extra-key" size="small" />
                      <a-input v-model:value="api.extraFields[key]" class="extra-value" size="small" />
                      <a-button type="link" danger size="small" @click="deleteExtraField(api, key)">删除</a-button>
                    </div>
                    <a-button type="dashed" size="small" block @click="addExtraField(api)" class="margin-top-sm">
                      + 增加自定义说明属性
                    </a-button>
                  </div>

                  <!-- 4. 单项 AI 脑补 -->
                  <div class="action-bar margin-top-md">
                    <a-button 
                      type="dashed" 
                      :loading="api.completing" 
                      @click="autoCompleteApi(api)"
                      class="magic-btn"
                    >
                      <span>🪄 一键 AI 脑补本区块缺失的字段与参数说明</span>
                    </a-button>
                  </div>
                </a-form>
              </a-tab-pane>
            </a-tabs>

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
                <span>一键导出为符合模板规范的 Word 文档 (.docx)</span>
              </a-button>
            </div>
          </div>
        </a-card>
      </div>
    </div>
  </div>
</template>

<script setup>
import { reactive, ref, watch } from 'vue';
import { ArrowLeft } from 'lucide-vue-next';
import { 
  FileTextOutlined,
  SlidersOutlined,
  ThunderboltOutlined,
  InboxOutlined,
  DownloadOutlined
} from '@ant-design/icons-vue';
import { message } from 'ant-design-vue';

// 视图控制
const currentLayout = ref('A'); 
const parsing = ref(false);
const exporting = ref(false);
const activeApiIndex = ref(0);

// 表单基本配置
const config = reactive({
  templateType: 'nannan',
  material: ''
});

// 图片仅在前端内存中以 Base64 存储，不做实时上传
// 粘贴时存此字段用于预览，导出时随接口数据一同打包发给后端
const globalImageBase64 = ref(null);

// 模板上传拖拽器所需的鉴权请求头
const templateUploadHeaders = {
  Authorization: 'Bearer ' + localStorage.getItem('token')
};

// 默认的河北南网接口文档结构 Schema (AI 分析自定义模板后会覆盖它)
const defaultSchema = {
  fields: [
    { key: 'name', label: '接口中文名称', type: 'input' },
    { key: 'url', label: '请求 URL', type: 'input' },
    { key: 'method', label: '请求方式(GET/POST)', type: 'input' },
    { key: 'description', label: '接口描述说明', type: 'textarea' },
    { key: 'requestExample', label: '请求 JSON 示例', type: 'textarea' },
    { key: 'responseExample', label: '返回 JSON 示例', type: 'textarea' }
  ],
  tables: [
    {
      key: 'requestParams',
      label: '请求参数说明 (Request Params)',
      columns: ['参数名称', '是否必填', '类型', '字段说明']
    },
    {
      key: 'responseParams',
      label: '返回参数说明 (Response Params)',
      columns: ['参数名称', '类型', '字段说明']
    }
  ]
};

const schema = ref(JSON.parse(JSON.stringify(defaultSchema)));
const apiList = ref([]);

// 监听模板类型变化，若选回默认，重置 schema
watch(() => config.templateType, (val) => {
  if (val === 'nannan') {
    schema.value = JSON.parse(JSON.stringify(defaultSchema));
  }
});

// 1. 处理自定义模板上传并解析 Schema
const handleTemplateUploadChange = (info) => {
  const status = info.file.status;
  if (status === 'uploading') {
    return;
  }
  if (status === 'done') {
    const res = info.file.response;
    if (res && res.code === 200) {
      try {
        schema.value = JSON.parse(res.data);
        message.success(`自定义模板 "${info.file.name}" 解析成功！已动态对齐表单字段。`);
      } catch (e) {
        message.error('模板解析的数据格式非法，已降级回默认 Schema');
        schema.value = JSON.parse(JSON.stringify(defaultSchema));
      }
    } else {
      message.error(res ? res.message : '模板解析失败');
    }
  } else if (status === 'error') {
    message.error(`${info.file.name} 上传失败.`);
  }
};

// 2. 粘贴事件监听：只在前端内存中以 Base64 存图并回显，不做任何实时上传
const handlePaste = (event) => {
  const items = (event.clipboardData || window.clipboardData).items;
  for (let i = 0; i < items.length; i++) {
    if (items[i].type.indexOf('image') !== -1) {
      const blob = items[i].getAsFile();
      event.preventDefault();

      const reader = new FileReader();
      reader.onload = (e) => {
        const base64 = e.target.result; // data:image/png;base64,xxx...
        // 如果右侧已有解析出的接口，绑在当前激活项上；否则存全局
        if (apiList.value.length > 0) {
          apiList.value[activeApiIndex.value].fields.imageUrl = base64;
        } else {
          globalImageBase64.value = base64;
        }
        message.success('截图已就绪，点击「导出」时将随数据一同提交！');
      };
      reader.readAsDataURL(blob);
    }
  }
};

// 获取当前接口所绑定的图片（Base64 格式，仅前端内存）
const getCurApiImage = () => {
  if (apiList.value.length > 0) {
    return apiList.value[activeApiIndex.value]?.fields?.imageUrl || null;
  }
  return globalImageBase64.value;
};

// 移除当前接口绑定的图片
const removeCurApiImage = () => {
  if (apiList.value.length > 0) {
    apiList.value[activeApiIndex.value].fields.imageUrl = null;
  } else {
    globalImageBase64.value = null;
  }
  message.info('图片已清除');
};

// 3. 开始智能解析大纲
const startParsing = async () => {
  if (!config.material.trim()) {
    message.warning('请先在左侧输入框贴入原始接口代码或资料！');
    return;
  }

  parsing.value = true;
  apiList.value = [];

  try {
    const response = await fetch('/ai-lab-hub-api/word/parse', {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        'Authorization': 'Bearer ' + localStorage.getItem('token')
      },
      body: JSON.stringify({
        material: config.material,
        schemaJson: JSON.stringify(schema.value)
      })
    });

    const res = await response.json();
    if (res.code === 200) {
      const parsedData = JSON.parse(res.data);
      
      // 遍历接口补全可能缺失的 data-structure
      apiList.value = parsedData.map((item, idx) => {
        if (!item.fields) item.fields = {};

        // 将解析前粘贴的全局内存图片绑给第一个接口
        if (idx === 0 && globalImageBase64.value && !item.fields.imageUrl) {
          item.fields.imageUrl = globalImageBase64.value;
        }

        // 确保 tables 字典里的表格完全被声明
        if (!item.tables) item.tables = {};
        schema.value.tables.forEach(t => {
          if (!item.tables[t.key]) {
            item.tables[t.key] = [];
          }
        });

        // 初始化 extraFields
        if (!item.extraFields) {
          item.extraFields = {};
        }

        item.completing = false;
        return item;
      });

      // 消费掉全局暂存图片
      globalImageBase64.value = null;

      activeApiIndex.value = 0;
      message.success('AI 智能分析大纲抽取完成！已为您自动生成对齐数据。');
    } else {
      message.error('AI 提取失败: ' + res.message);
    }
  } catch (e) {
    message.error('调用大语言模型发生网络异常，请检查网关。');
  } finally {
    parsing.value = false;
  }
};

// 4. 一键单个接口 AI 脑补
const autoCompleteApi = async (api) => {
  api.completing = true;
  try {
    const res = await fetch('/ai-lab-hub-api/word/complete-api', {
      method: 'POST',
      headers: { 
        'Content-Type': 'application/json',
        'Authorization': 'Bearer ' + localStorage.getItem('token')
      },
      body: JSON.stringify({ apiJson: JSON.stringify(api) })
    });
    const result = await res.json();
    if (result.code === 200) {
      const completed = JSON.parse(result.data);
      api.fields = completed.fields;
      api.tables = completed.tables;
      api.extraFields = completed.extraFields || {};
      message.success(`[${api.fields.name || '接口'}] 字段说明已通过 AI 自动补齐脑补！`);
    } else {
      message.error('脑补失败: ' + result.message);
    }
  } catch (err) {
    message.error('调用 AI 补全网络错误');
  } finally {
    api.completing = false;
  }
};

// 5. 导出 Word 文档 (Apache POI 流式拉取)
const exportToWord = async () => {
  exporting.value = true;
  message.loading({ content: '正在打包字段数据与图片，并物理插值克隆 Word...', key: 'export', duration: 0 });

  try {
    const res = await fetch('/ai-lab-hub-api/word/export', {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        'Authorization': 'Bearer ' + localStorage.getItem('token')
      },
      body: JSON.stringify({
        schemaJson: JSON.stringify(schema.value),
        apiList: apiList.value,
        isCustom: config.templateType === 'custom'
      })
    });

    if (res.ok) {
      const blob = await res.blob();
      const url = window.URL.createObjectURL(blob);
      const a = document.createElement('a');
      a.href = url;
      a.download = config.templateType === 'custom' ? '自定义模板文档_Generated.docx' : '河北南网火电说明文档_Generated.docx';
      document.body.appendChild(a);
      a.click();
      document.body.removeChild(a);
      message.success({ content: '万能 Word 报文填充导出成功！请在浏览器下载栏查看文件。', key: 'export', duration: 3 });
    } else {
      message.error({ content: '物理导出失败，请检查后端 POI 克隆模块状态。', key: 'export' });
    }
  } catch (e) {
    message.error({ content: '网络错误，物理导出失败。', key: 'export' });
  } finally {
    exporting.value = false;
  }
};

// 6. 动态表格增加/删除行
const addRow = (tableList, columns) => {
  const newRow = {};
  columns.forEach(col => {
    newRow[col] = '';
  });
  tableList.push(newRow);
  message.info('表格槽位已新增，请完善定义');
};

const deleteRow = (tableList, idx) => {
  tableList.splice(idx, 1);
  message.info('数据行已移除');
};

// 动态将 Schema 的列名数组转换为 Antd 的 Table 字段对象
const getAntdColumns = (columns) => {
  const antdCols = columns.map(col => {
    return {
      title: col,
      dataIndex: col,
      key: col
    };
  });
  // 追加一个操作列
  antdCols.push({
    title: '操作',
    key: 'action',
    width: '80px',
    align: 'center'
  });
  return antdCols;
};

// 7. 自定义扩展字段 Key-Value 动态管理
const addExtraField = (api) => {
  const uniqueKey = '自定义字段_' + (Object.keys(api.extraFields).length + 1);
  api.extraFields[uniqueKey] = '';
  message.info('扩展说明属性已追加');
};

const deleteExtraField = (api, key) => {
  delete api.extraFields[key];
  message.info('扩展属性已删除');
};

const renameExtraKey = (api, oldKey, newKey) => {
  if (oldKey === newKey) return;
  const val = api.extraFields[oldKey];
  delete api.extraFields[oldKey];
  api.extraFields[newKey] = val;
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

.header-top-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
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

.layout-switcher {
  display: flex;
  align-items: center;
  gap: 8px;
  background: rgba(255, 255, 255, 0.05);
  padding: 6px 14px;
  border-radius: 6px;
  border: 1px solid var(--border-color);
}

.switcher-label {
  font-size: 12px;
  font-weight: 700;
  color: var(--primary-light);
}

.page-title h2 {
  font-size: 22px;
  font-weight: 700;
  color: var(--text-primary);
  margin: 8px 0 0 0;
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
  transition: background 0.3s, border 0.3s;
}

/* 暗黑明亮主题变量适配 */
.light .panel-card {
  background: #ffffff;
  border-color: #e5e7eb;
  box-shadow: 0 4px 20px rgba(0, 0, 0, 0.05);
}

.card-title-box {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 15px;
  font-weight: 700;
  color: var(--text-primary);
}

.title-icon {
  color: var(--primary-color);
}

.upload-container {
  margin-bottom: 16px;
}

/* 图片粘贴录入区样式 */
.image-paste-box {
  margin-bottom: 16px;
}

.paste-zone {
  border: 1.5px dashed var(--border-color);
  background: rgba(255, 255, 255, 0.01);
  padding: 12px;
  border-radius: var(--radius-md);
  transition: all 0.3s;
}

.light .paste-zone {
  background: #f9fafb;
  border-color: #d1d5db;
}

.paste-zone:hover {
  border-color: var(--primary-color);
}

.zone-label {
  font-size: 12px;
  font-weight: 700;
  color: var(--text-secondary);
  margin-bottom: 8px;
}

.paste-placeholder {
  height: 64px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 11px;
  color: var(--text-muted);
  text-align: center;
}

.preview-container {
  display: flex;
  align-items: center;
  gap: 16px;
  background: rgba(255, 255, 255, 0.02);
  padding: 8px;
  border-radius: var(--radius-sm);
  border: 1px solid var(--border-color);
}

.paste-preview {
  max-width: 140px;
  max-height: 80px;
  border-radius: var(--radius-xs);
  object-fit: cover;
  border: 1px solid var(--border-color);
  box-shadow: 0 4px 10px rgba(0, 0, 0, 0.2);
}

.code-textarea {
  font-family: 'Consolas', monospace;
  font-size: 13px;
  background: rgba(0, 0, 0, 0.15) !important;
  border-color: var(--border-color);
  color: var(--text-primary);
}

.light .code-textarea {
  background: #ffffff !important;
  color: #1f2937;
}

.parse-btn {
  background: linear-gradient(135deg, var(--primary-color), var(--primary-light));
  border: none;
  font-weight: 700;
  letter-spacing: 0.5px;
}

.parse-btn:hover {
  opacity: 0.9;
}

.empty-holder {
  padding: 80px 0;
  display: flex;
  justify-content: center;
}

.api-editor-area {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.helper-bar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  font-size: 12px;
}

.badge {
  background: rgba(16, 184, 129, 0.15);
  color: #10b881;
  padding: 2px 8px;
  border-radius: 4px;
  font-weight: 700;
}

.tip {
  color: var(--text-muted);
}

/* 动态表单栅格 */
.dynamic-fields-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 16px;
}

.dynamic-fields-grid .field-item:nth-child(4),
.dynamic-fields-grid .field-item:nth-child(5),
.dynamic-fields-grid .field-item:nth-child(6) {
  grid-column: span 2;
}

.table-section {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.table-title {
  display: flex;
  justify-content: space-between;
  align-items: center;
  font-size: 12.5px;
  font-weight: 700;
  color: var(--primary-light);
  border-left: 3px solid var(--primary-color);
  padding-left: 8px;
}

.magic-btn {
  background: linear-gradient(to right, rgba(139, 92, 246, 0.1), rgba(59, 130, 246, 0.1));
  border: 1px dashed var(--primary-color);
  color: var(--primary-light);
  width: 100%;
  font-weight: 700;
  border-radius: 6px;
}

.magic-btn:hover {
  background: rgba(139, 92, 246, 0.2);
  color: #ffffff;
}

.export-section {
  border-top: 1px solid var(--border-color);
  padding-top: 16px;
  margin-top: 8px;
}

.export-btn {
  background: linear-gradient(135deg, #10b881, #059669);
  border: none;
  font-weight: 700;
}

/* 自定义字段排版 */
.section-divider {
  font-size: 12px;
  font-weight: 700;
  color: var(--text-secondary);
  border-bottom: 1px solid var(--border-color);
  padding-bottom: 4px;
  margin-bottom: 8px;
}

.extra-field-row {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 8px;
}

.extra-key {
  font-weight: 700;
  width: 120px;
}

.extra-value {
  flex: 1;
}

.margin-top-md {
  margin-top: 16px;
}
.margin-top-sm {
  margin-top: 8px;
}
</style>
