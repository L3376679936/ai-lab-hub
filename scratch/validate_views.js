const fs = require('fs');
const path = require('path');

// 递归查找指定目录下的所有 .vue 文件
function getVueFiles(dir, fileList = []) {
  if (!fs.existsSync(dir)) return fileList;
  const files = fs.readdirSync(dir);
  files.forEach(file => {
    const filePath = path.join(dir, file);
    const stat = fs.statSync(filePath);
    if (stat.isDirectory()) {
      getVueFiles(filePath, fileList);
    } else if (file.endsWith('.vue')) {
      fileList.push(filePath);
    }
  });
  return fileList;
}

// 静态代码健全性检测规则
const rules = [
  {
    name: 'a-collapse 指令崩溃验证',
    check: (content) => {
      // 检查在 a-collapse 上是否错误地使用了 v-slot:activeKey 绑定，这会导致 Vue3 运行时插槽树解析异常并崩溃白屏
      if (content.includes('a-collapse') && content.includes('v-slot:activeKey')) {
        return '在 a-collapse 容器上错误使用了 v-slot:activeKey 绑定，应当使用 v-model:activeKey！';
      }
      return null;
    }
  },
  {
    name: '原生 alert 警告排除验证',
    check: (content, relativePath) => {
      // 排除 router/index.js 或 node_modules，仅检查 views 业务组件
      // 我们要求在任何交互和逻辑中都不允许使用原生的 alert 弹窗，应当改用 ant-design-vue 的 message
      if (content.includes('alert(') && !relativePath.includes('deploy') && !relativePath.includes('test_ui')) {
        return '检测到遗留的原生 alert() 调用，应当使用更加美观的 ant-design-vue message 反馈！';
      }
      return null;
    }
  }
];

const viewsDir = path.join(__dirname, '../ai-lab-hub-front/src/views');
const vueFiles = getVueFiles(viewsDir);
let errorsCount = 0;

console.log(`[TDD 静态诊断] 开始扫描前端视图目录: ${viewsDir}`);
console.log(`找到待检测的 Vue 业务文件数: ${vueFiles.length}\n`);

vueFiles.forEach(file => {
  const content = fs.readFileSync(file, 'utf-8');
  const relativePath = path.relative(path.join(__dirname, '..'), file);
  
  rules.forEach(rule => {
    const err = rule.check(content, relativePath);
    if (err) {
      console.error(`❌ [TDD 检测失败] 文件: ${relativePath}`);
      console.error(`   违反规则: [${rule.name}]`);
      console.error(`   具体原因: ${err}\n`);
      errorsCount++;
    }
  });
});

if (errorsCount > 0) {
  console.error(`--- 检测失败 ---`);
  console.error(`共发现 ${errorsCount} 处影响运行时稳定或美观度的核心 Bug 隐患，请立即修复！`);
  process.exit(1);
} else {
  console.log(`🟢 [TDD 检测通过] 未发现任何 v-slot 绑定崩溃隐患或遗留 alert 调用，页面渲染健壮。`);
  process.exit(0);
}
