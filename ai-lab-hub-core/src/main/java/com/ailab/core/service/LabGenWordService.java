package com.ailab.core.service;

import com.ailab.core.client.AiClient;
import com.ailab.core.entity.CoreTempFile;
import com.ailab.core.manager.McpServerManager;
import com.ailab.core.repository.CoreTempFileRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import okhttp3.Response;
import okhttp3.sse.EventSource;
import okhttp3.sse.EventSourceListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.File;
import java.io.IOException;
import java.util.*;

@Slf4j
@Service
public class LabGenWordService {

    @Autowired
    private AiClient aiClient;

    @Autowired
    private McpServerManager mcpServerManager;

    @Autowired
    private CoreTempFileRepository coreTempFileRepository;

    @Value("${ai.file.temp-dir:./temp}")
    private String tempDirSetting;

    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 流式大模型生成章节并调用 MCP 物理生成 Word 报告的主流程
     */
    public void generateWordReport(String topic, String outline, SseEmitter emitter) {
        // 1. 组装让大模型按标准格式返回文档结构的 Prompt
        String prompt = "你是一个专业的技术文档与商业报告撰写专家。请针对主题：[" + topic + "]和大纲：[" + outline + "]生成一份排版精美、结构严谨的 Word 文档内容。\n" +
                "请必须严格使用以下标准的 JSON 数组格式返回数据，不要包含任何额外的解释性说明文字，也不要用 ```json 等 Markdown 标记包裹代码：\n" +
                "[\n" +
                "  {\"type\": \"title\", \"text\": \"（此处填报告主标题）\"},\n" +
                "  {\"type\": \"subtitle\", \"text\": \"（此处填报告副标题或作者信息）\"},\n" +
                "  {\"type\": \"h1\", \"text\": \"第一章 （此处填章节名称）\"},\n" +
                "  {\"type\": \"paragraph\", \"text\": \"（此处填章节的详细正文，正文应详实、专业、严谨且字数饱满）\"},\n" +
                "  {\"type\": \"h2\", \"text\": \"1.1 （此处填小节名称）\"},\n" +
                "  {\"type\": \"paragraph\", \"text\": \"（此处填小节的详细正文，段落应逻辑连贯，字数充足）\"}\n" +
                "]\n" +
                "请现在开始生成：";

        // 2. 使用 StringBuilder 缓存大模型吐出的所有原始流式字符
        StringBuilder rawResponseBuilder = new StringBuilder();

        // 3. 发起 OkHttp-SSE 异步流式聊天
        aiClient.streamChat(prompt, "labGenWord", new EventSourceListener() {

            @Override
            public void onOpen(EventSource eventSource, Response response) {
                log.info("与大模型的流式 SSE 通信信道已成功建立");
                sendSseMessage(emitter, "system", "与大模型网关建立连接成功，开始撰写文档...");
            }

            @Override
            public void onEvent(EventSource eventSource, String id, String type, String data) {
                if ("[DONE]".equals(data)) {
                    log.info("大模型生成事件流全部接收完毕 [DONE]");
                    return;
                }

                try {
                    // 解析 OpenAI 格式的流式 delta 数据
                    JsonNode node = objectMapper.readTree(data);
                    JsonNode contentNode = node.path("choices").path(0).path("delta").path("content");
                    if (!contentNode.isMissingNode() && contentNode.textValue() != null) {
                        String deltaText = contentNode.textValue();
                        rawResponseBuilder.append(deltaText);

                        // 实时推送给前端控制台展现打字机效果 (URLEncoder 防止换行和空格在 SSE 中解析断流)
                        sendSseMessage(emitter, "text", deltaText);
                    }
                } catch (Exception e) {
                    log.error("流式读取大模型数据解析异常: {}", e.getMessage());
                }
            }

            @Override
            public void onClosed(EventSource eventSource) {
                log.info("大模型连接已关闭，开始物理组装 Word 文档...");
                sendSseMessage(emitter, "system", "大模型内容生成完毕，正在排版并生成物理 Word 报告...");

                try {
                    String rawText = rawResponseBuilder.toString().trim();
                    log.info("大模型完整原始响应为:\n{}", rawText);

                    // 1. 数据清洗（过滤掉大模型自带的 ```json / ``` 标记）
                    String cleanJson = cleanJsonMarkdown(rawText);

                    // 2. 反序列化为段落 List
                    List<Map<String, String>> contentItems = objectMapper.readValue(cleanJson, List.class);

                    // 3. 准备物理生成路径
                    File tempDir = new File(tempDirSetting);
                    if (!tempDir.exists()) {
                        tempDir.mkdirs();
                    }
                    String filename = "report_" + UUID.randomUUID().toString().replace("-", "") + ".docx";
                    File docxFile = new File(tempDir, filename);
                    String fileAbsolutePath = docxFile.getAbsolutePath();

                    // 4. 组装 MCP 调用参数
                    Map<String, Object> arguments = new HashMap<>();
                    arguments.put("outputPath", fileAbsolutePath);
                    arguments.put("contentItems", contentItems);

                    // 5. 调用 MCP Stdio 服务生成物理 Docx 文件
                    mcpServerManager.callTool("labGenWord", "write_docx", arguments);
                    log.info("Word 报告已通过 MCP 服务生成，物理路径: {}", fileAbsolutePath);

                    // 6. 登记到临时文件表（用于 24 小时后自动物理删除）
                    CoreTempFile fileRecord = new CoreTempFile();
                    fileRecord.setFileName(filename);
                    fileRecord.setFilePath(fileAbsolutePath);
                    fileRecord.setToolCode("labGenWord");
                    fileRecord.setStatus(0); // 0-待清理
                    coreTempFileRepository.save(fileRecord);

                    // 7. 推送 success 事件告知前端已生成成功，并带上 fileId 供前端触发物理下载
                    sendSseMessage(emitter, "success", fileRecord.getId().toString());
                    emitter.complete();

                } catch (Exception e) {
                    log.error("生成物理 Word 报告失败: {}", e.getMessage());
                    sendSseMessage(emitter, "error", "物理排版生成 Word 失败: " + e.getMessage());
                    emitter.completeWithError(e);
                }
            }

            @Override
            public void onFailure(EventSource eventSource, Throwable t, Response response) {
                log.error("流式 AI 网关调用失败: {}", t != null ? t.getMessage() : "未知网络异常");
                sendSseMessage(emitter, "error", "AI 服务响应异常，请重试");
                emitter.completeWithError(t != null ? t : new RuntimeException("AI Gateway response failure"));
            }
        });
    }

    /**
     * 数据清洗，剔除 AI 可能返回的 Markdown 标记
     */
    private String cleanJsonMarkdown(String raw) {
        String clean = raw.trim();
        if (clean.startsWith("```json")) {
            clean = clean.substring(7);
        } else if (clean.startsWith("```")) {
            clean = clean.substring(3);
        }

        if (clean.endsWith("```")) {
            clean = clean.substring(0, clean.length() - 3);
        }

        clean = clean.trim();
        // 容错：定位到第一个 '[' 和最后一个 ']'
        int start = clean.indexOf("[");
        int end = clean.lastIndexOf("]");
        if (start != -1 && end != -1 && end > start) {
            clean = clean.substring(start, end + 1);
        }
        return clean;
    }

    private void sendSseMessage(SseEmitter emitter, String name, String data) {
        try {
            emitter.send(SseEmitter.event().name(name).data(data));
        } catch (IOException e) {
            log.warn("向 SseEmitter 发送数据失败: {}", e.getMessage());
        }
    }
}
