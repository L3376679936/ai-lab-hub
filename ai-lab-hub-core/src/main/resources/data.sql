-- 插入默认系统管理员 (账号/密码明文: admin/admin123)
INSERT IGNORE INTO `sys_user` (`id`, `username`, `password`, `nickname`, `status`) 
VALUES (1, 'admin', 'admin123', '系统管理员', 1);

-- 插入 Word 自动生成工具元数据
INSERT IGNORE INTO `tool_info` (`id`, `tool_code`, `tool_name`, `description`, `status`, `icon`) 
VALUES (1, 'labGenWord', 'Word 自动生成', '通过输入文档大纲或需求，调用大模型生成章节并全自动导出为排版精美的 Word 报告。', 1, 'FileText');

-- 注册默认的 Office MCP 文档处理进程配置
INSERT IGNORE INTO `sys_mcp_server` (`id`, `server_name`, `transport_type`, `command`, `args`, `tool_code`, `status`) 
VALUES (1, 'office-mcp', 'stdio', 'node', '["./mcp/office-mcp.js"]', 'labGenWord', 1);
