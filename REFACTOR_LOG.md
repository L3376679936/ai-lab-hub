# REFACTOR_LOG.md - AI-Lab-Hub 迭代重构日志

## 2026-07-10 首次迭代：架构确立与前端高保真交互 Demo 交付

### 1. 业务与架构设计 (SDD)
* **需求规划**：确定项目定位为个人 AI 工具箱（Toolbox 模式），旨在将日常开发的 AI 小工具（如 Word 生成）集成到统一平台上，采用 Vue 3 + Spring Boot 3.x + MySQL 的架构。
* **规格书确立**：编写并落盘了系统设计规格说明书 [2026-07-10-ai-lab-hub-design.md](file:///e:/其他/liuaobo/AI-LAB/新建文件夹/docs/superpowers/specs/2026-07-09-ai-lab-hub-design.md)。
* **安全与性能优化 (Spec v2.1)**：
  * **后端多模块**：规避包扫描与打包冲突，增设独立的 `ai-lab-hub-bootstrap` 启动模块。
  * **AI网关加密**：对数据库中的大模型 API Key 规划 AES-256 对称加密。
  * **文件清理防穿越**：引入 `getCanonicalPath()` 边界检查和 `isSymbolicLink()` 软链接防删除攻击防护。
  * **Nginx与SSE传输**：针对大模型流式响应设定长超时 (300s)，并在 Nginx 中显式关闭 Gzip 缓存。
  * **生态集成 (MCP & Skills)**：底座设计了对 Stdio/SSE 通信模式的 `McpClientManager` 客户端连接池，以及本地业务方法 `@AiSkill` 声明式注解反射执行闭环。
* **隔离策略**：将 `docs/` 等 AI 内部协作目录写入 [.gitignore](file:///e:/其他/liuaobo/AI-LAB/新建文件夹/.gitignore)，防止污染代码库。

### 2. 前端高保真交互 Demo 实现
在 `ai-lab-hub-front` 目录下基于 Vue 3 + Vite + pnpm 搭建了完整的前端交互 Demo，并已在后台运行开发服务器。具体交付组件及路由如下：
* **首屏与登录页** [Login.vue](file:///e:/其他/liuaobo/AI-LAB/新建文件夹/ai-lab-hub-front/src/views/login/index.vue)：
  * 引入了炫丽的渐变发光球背景动画和磨砂玻璃卡片。
  * 实现了输入框获取焦点的发光过渡和点击登录时的安全验签 Loading 动效。
* **主仪表盘** [Dashboard.vue](file:///e:/其他/liuaobo/AI-LAB/新建文件夹/ai-lab-hub-front/src/views/dashboard/index.vue)：
  * 实现了基于工具名和描述的模糊搜索栏与分类 Tag 筛选。
  * 规划了工具状态（已上线/建设中）徽章和卡片悬浮浮动阴影（`hover-card`）效果。
* **主题与框架** [Layout.vue](file:///e:/其他/liuaobo/AI-LAB/新建文件夹/ai-lab-hub-front/src/core/components/Layout.vue)：
  * 采用后台管理侧边栏布局，集成了 Pinia 主题切换与行内防闪屏（FOUC）拦截。
  * 主题切换配备了太阳/月亮图标旋转与缩放淡入淡出动效。
* **自动 Word 生成工具 Mock** [LabGenWord.vue](file:///e:/其他/liuaobo/AI-LAB/新建文件夹/ai-lab-hub-front/src/views/modules/labGenWord/index.vue)：
  * 提供了大纲配置表单、独立 AI 大模型配置抽屉（体现局部配置高于全局底座的覆盖逻辑）。
  * **流式命令行终端**：模拟 SSE 流式打字机效果。点击“一键生成”，可实时观测大模型流式吐出文章、POI 模板排版、文件在临时文件夹物理落盘的全部执行日志与状态过渡。
  * **物理下载**：生成完毕亮起下载按钮，支持基于 Blob 动态组装数据物理下载生成文件。

### 3. 后续工作规划
* **第一步**：前端 Demo 通过用户视觉与交互确认后，开始初始化后端 Maven 父子多模块骨架；（已完成文件写入）
* **第二步**：开发数据库与底座 `core` 的通用能力（JWT 鉴权拦截、AES 加密解密、文件自动清理、统一 Result 封装）；
* **第三步**：开发统一 AI 大模型网关及 MCP/Skill 连接池引擎；
* **第四步**：将前端 Mock 接口替换为真实 Axios 联调，接入物理小工具。

## 2026-07-10 第二次迭代：后端 Maven 父子多模块骨架初始化

### 1. 后端工程多模块搭建
* **父工程 POM** [pom.xml](file:///e:/其他/liuaobo/AI-LAB/新建文件夹/pom.xml)：声明 `ai-lab-hub` 主包，配置 Spring Boot 3.3.4 作为依赖管理，以及 Lombok、MySQL 驱动版本，聚合核心子模块。
* **底座核心模块** [ai-lab-hub-core/pom.xml](file:///e:/其他/liuaobo/AI-LAB/新建文件夹/ai-lab-hub-core/pom.xml)：集成 Spring Boot Web、Data JPA、MySQL 驱动和 Lombok 依赖。
* **工具模块聚合层** [ai-lab-hub-modules/pom.xml](file:///e:/其他/liuaobo/AI-LAB/新建文件夹/ai-lab-hub-modules/pom.xml)：承载所有 AI 工具模块。
* **具体小工具模块** [ai-lab-hub-modules/module-lab-gen-word/pom.xml](file:///e:/其他/liuaobo/AI-LAB/新建文件夹/ai-lab-hub-modules/module-lab-gen-word/pom.xml)：Word 生成工具模块，声明依赖底座核心包。
* **打包引导模块** [ai-lab-hub-bootstrap/pom.xml](file:///e:/其他/liuaobo/AI-LAB/新建文件夹/ai-lab-hub-bootstrap/pom.xml)：依赖 core 与 module-lab-gen-word，集成 `spring-boot-maven-plugin` 用于生成独立运行的 Fat Jar。

### 2. 执行状态与疑虑 (Concerns)
* 所有 Maven 多模块 pom.xml 骨架文件均已创建并按规范写入。
* 由于本地命令行 `run_command` 请求在当前执行环境中遇到用户授权超时问题，未能通过 `mvn clean compile` 进行实际编译验证，亦未能执行 `git add`，请主代理或用户手动执行后续编译验证和 git add 命令。

## 2026-07-10 第三次迭代：自动建表与初始化数据 SQL 脚本编写

### 1. 数据库初始化 SQL 写入
* **自动建表脚本** [schema.sql](file:///e:/其他/liuaobo/AI-LAB/新建文件夹/ai-lab-hub-core/src/main/resources/schema.sql)：配置了 6 张系统核心表的 DDL，包括管理员、AI工具信息、工具独立大模型配置、全局配置、MCP独立服务进程、临时物理文件清理表。
* **初始化数据脚本** [data.sql](file:///e:/其他/liuaobo/AI-LAB/新建文件夹/ai-lab-hub-core/src/main/resources/data.sql)：配置了系统默认管理员初始账号 (`admin`/`admin123`)，插入了自动 Word 生成工具的主元数据记录，以及注册了默认的 `office-mcp` 服务进程配置。

### 2. 执行状态与疑虑 (Concerns)
* **SQL脚本与计划文档落盘**：`schema.sql` 和 `data.sql` 已成功生成且完整写入。相关的 `docs/superpowers/status/` 任务清单、实现计划以及验收报告也已全部更新并落盘。
* **Git 与命令行操作限制**：因当前环境 `run_command` 执行超时限制，无法自动执行 `git add` 和编译测试，需由主代理或用户在命令行手动对 `ai-lab-hub-core/src/main/resources/schema.sql` 和 `ai-lab-hub-core/src/main/resources/data.sql` 进行 `git add` 提交以及后续的编译部署测试。
