package com.ailab.core.manager;

import com.ailab.core.entity.SysMcpServer;
import com.ailab.core.repository.SysMcpServerRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.annotation.PreDestroy;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
public class McpServerManager {

    @Autowired
    private SysMcpServerRepository sysMcpServerRepository;

    private final ObjectMapper objectMapper = new ObjectMapper();

    // 用于管理在运行的 MCP 子进程及其输入输出流
    private final Map<String, Process> processMap = new ConcurrentHashMap<>();
    private final Map<String, BufferedWriter> writerMap = new ConcurrentHashMap<>();
    private final Map<String, BufferedReader> readerMap = new ConcurrentHashMap<>();

    private final Object communicationLock = new Object();

    /**
     * 向指定工具编码对应的 MCP 服务发送 Tool Call 指令
     *
     * @param toolCode     AI 工具编码
     * @param toolName     MCP 工具名
     * @param argumentsMap 工具入参
     * @return MCP 响应的 JSON-RPC 结果字符串
     */
    public String callTool(String toolCode, String toolName, Map<String, Object> argumentsMap) {
        // 1. 确保 MCP 服务已启动
        ensureServerRunning(toolCode);

        BufferedWriter writer = writerMap.get(toolCode);
        BufferedReader reader = readerMap.get(toolCode);

        if (writer == null || reader == null) {
            throw new RuntimeException("MCP 服务连接断开，未找到通信流");
        }

        // 2. 组装符合 JSON-RPC 2.0 规范的 Tools Call 请求
        String requestId = UUID.randomUUID().toString();
        Map<String, Object> request = new HashMap<>();
        request.put("jsonrpc", "2.0");
        request.put("method", "tools/call");
        request.put("id", requestId);

        Map<String, Object> params = new HashMap<>();
        params.put("name", toolName);
        params.put("arguments", argumentsMap);
        request.put("params", params);

        synchronized (communicationLock) {
            try {
                String jsonRequest = objectMapper.writeValueAsString(request);
                log.info("向 MCP 发送 JSON-RPC 请求: {}", jsonRequest);

                // 写入 stdio
                writer.write(jsonRequest);
                writer.newLine();
                writer.flush();

                // 阻塞读取 stdout 响应（一问一答模式）
                String jsonResponse = reader.readLine();
                log.info("从 MCP 接收 JSON-RPC 响应: {}", jsonResponse);

                if (jsonResponse == null) {
                    throw new RuntimeException("MCP 服务无响应或已断开");
                }

                // 校验并解析响应结果
                JsonNode responseNode = objectMapper.readTree(jsonResponse);
                if (responseNode.has("error")) {
                    throw new RuntimeException("MCP 内部报错: " + responseNode.get("error").get("message").asText());
                }

                if (responseNode.has("result")) {
                    return responseNode.get("result").toString();
                }

                throw new RuntimeException("未知的 MCP 响应格式");

            } catch (Exception e) {
                log.error("与 MCP 进程 stdio 通信异常, 正在执行熔断重置: {}", e.getMessage());
                stopServer(toolCode); // 熔断，强制关闭该服务以供下次重试拉起
                throw new RuntimeException("调用 MCP 文档服务失败: " + e.getMessage(), e);
            }
        }
    }

    /**
     * 确保 MCP 服务在运行（懒加载拉起）
     */
    private void ensureServerRunning(String toolCode) {
        if (processMap.containsKey(toolCode)) {
            Process p = processMap.get(toolCode);
            if (p.isAlive()) {
                return;
            }
            // 进程已死，执行重置
            stopServer(toolCode);
        }

        log.info("准备拉起工具 [{}] 的内置 MCP 独立进程服务...", toolCode);

        // 1. 查询服务配置
        Optional<SysMcpServer> configOpt = sysMcpServerRepository.findByToolCodeAndStatus(toolCode, 1);
        if (!configOpt.isPresent()) {
            throw new RuntimeException("未在数据库中找到可用且启用的 MCP 服务配置，工具编码: " + toolCode);
        }

        SysMcpServer config = configOpt.get();
        if (!"stdio".equalsIgnoreCase(config.getTransportType())) {
            throw new RuntimeException("目前仅支持 stdio 传输类型的 MCP 服务");
        }

        // 2. 针对 E:\其他\liuaobo\AI-LAB\ai-lab-hub\mcp 自动执行 pnpm install 依赖防空防漏
        autoInstallNodeDependencies();

        // 3. 利用 ProcessBuilder 启动独立子进程
        try {
            List<String> commandList = new ArrayList<>();
            // 在 Windows 环境下，直接拉起 node 命令
            commandList.add(config.getCommand()); // node

            // 解析 args 参数 (["./mcp/office-mcp.js"])
            String argsStr = config.getArgs();
            if (argsStr != null && argsStr.trim().startsWith("[")) {
                List<String> argsList = objectMapper.readValue(argsStr, List.class);
                commandList.addAll(argsList);
            }

            log.info("执行启动指令: {}", String.join(" ", commandList));
            ProcessBuilder pb = new ProcessBuilder(commandList);
            // 设定工作目录为项目根目录
            pb.directory(new File("."));

            // 启动子进程
            Process process = pb.start();

            // 绑定通信 IO 流
            BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(process.getOutputStream(), StandardCharsets.UTF_8));
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8));

            // 启动异步线程监听子进程的 Error Stream，防止子进程缓冲区写满导致挂起，并能打印 Node 的 console.error 调试日志
            new Thread(() -> {
                try (BufferedReader errReader = new BufferedReader(new InputStreamReader(process.getErrorStream(), StandardCharsets.UTF_8))) {
                    String errLine;
                    while ((errLine = errReader.readLine()) != null) {
                        log.warn("[MCP Process Error Log]: {}", errLine);
                    }
                } catch (IOException e) {
                    log.debug("监听 MCP ErrorStream 退出");
                }
            }, "mcp-stderr-listener-" + toolCode).start();

            processMap.put(toolCode, process);
            writerMap.put(toolCode, writer);
            readerMap.put(toolCode, reader);

            log.info("工具 [{}] 的 MCP 独立子进程启动成功，进程 ID: {}", toolCode, getPid(process));

        } catch (Exception e) {
            log.error("拉起 MCP 子进程失败: {}", e.getMessage());
            throw new RuntimeException("无法拉起底座内置的 MCP 服务: " + e.getMessage(), e);
        }
    }

    /**
     * 自动化检测并 pnpm install 依赖
     */
    private void autoInstallNodeDependencies() {
        File nodeModules = new File("./mcp/node_modules");
        if (!nodeModules.exists()) {
            log.info("检测到 mcp/node_modules 不存在，正在全自动拉起 pnpm install 安装组件...");
            try {
                // Windows 环境下调用 pnpm cmd 执行
                ProcessBuilder pb = new ProcessBuilder("cmd.exe", "/c", "pnpm install");
                pb.directory(new File("./mcp"));
                pb.redirectErrorStream(true);
                Process p = pb.start();

                // 打印安装日志
                try (BufferedReader br = new BufferedReader(new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = br.readLine()) != null) {
                        log.info("[pnpm install Log]: {}", line);
                    }
                }
                int exitCode = p.waitFor();
                log.info("pnpm install 执行完毕，退出码: {}", exitCode);
            } catch (Exception e) {
                log.error("自动化 pnpm install 遇到异常，请手动检查 Node 环境: {}", e.getMessage());
            }
        }
    }

    /**
     * 强行停止某工具关联的 MCP 进程
     */
    public void stopServer(String toolCode) {
        Process p = processMap.remove(toolCode);
        writerMap.remove(toolCode);
        readerMap.remove(toolCode);

        if (p != null) {
            try {
                p.destroyForcibly();
                log.info("工具 [{}] 关联的 MCP 进程已被强制安全销毁", toolCode);
            } catch (Exception e) {
                log.error("销毁 MCP 进程异常: {}", e.getMessage());
            }
        }
    }

    private long getPid(Process process) {
        // 由于 Java 8 没有公开获取 PID 的 API，这里直接返回 -1。不影响子进程的控制与销毁。
        return -1;
    }

    /**
     * 系统关闭时，安全清理连接池中所有正在运行的 MCP 子进程
     */
    @PreDestroy
    public void destroyAll() {
        log.info("底座服务正在关闭，准备安全清理所有在运行的 MCP 子进程...");
        for (String toolCode : processMap.keySet()) {
            stopServer(toolCode);
        }
    }
}
