package com.ailab.word.service;

import com.ailab.core.client.AiClient;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.xwpf.usermodel.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Service
public class AiExtractService {

    @Autowired
    private AiClient aiClient;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private static final String TOOL_CODE = "labGenWord";

    /**
     * 分析 Word 模板结构并提取智能动态 Schema
     *
     * @param templateBytes 模板文件字节数组
     * @return 动态表单及表格 Schema 定义的 JSON 字符串
     */
    public String analyzeTemplateSchema(byte[] templateBytes) {
        log.info("开始提取 Word 模板文件特征...");
        List<String> tableHeaders = new ArrayList<>();
        Set<String> placeholders = new HashSet<>();

        try (ByteArrayInputStream bais = new ByteArrayInputStream(templateBytes);
             XWPFDocument doc = new XWPFDocument(bais)) {

            // 1. 扫描文档中所有的表格，抽取表头特征
            List<XWPFTable> tables = doc.getTables();
            for (XWPFTable table : tables) {
                if (table.getRows().size() > 0) {
                    XWPFTableRow firstRow = table.getRow(0);
                    List<String> headers = new ArrayList<>();
                    for (XWPFTableCell cell : firstRow.getTableCells()) {
                        headers.add(cell.getText().trim());
                    }
                    if (!headers.isEmpty()) {
                        tableHeaders.add(String.join(" | ", headers));
                    }
                }
            }

            // 2. 扫描文档前 10000 字符，正则表达式匹配占位符 ${xxx}
            StringBuilder textBuilder = new StringBuilder();
            for (XWPFParagraph paragraph : doc.getParagraphs()) {
                textBuilder.append(paragraph.getText()).append("\n");
                if (textBuilder.length() > 10000) break;
            }
            Pattern pattern = Pattern.compile("\\$\\{([a-zA-Z0-9_.]+)\\}");
            Matcher matcher = pattern.matcher(textBuilder.toString());
            while (matcher.find()) {
                placeholders.add(matcher.group(1));
            }

        } catch (Exception e) {
            log.warn("Apache POI 静态提取模板特征失败, 将降级交由大模型默认兜底分析. Error: {}", e.getMessage());
        }

        // 3. 构建大模型提示词，分析出最契合该 Word 的自定义渲染 Schema
        String systemPrompt = "你是一个 Word 模板结构分析专家。请根据我提取到的 Word 模板特征（包含表格表头以及占位符定义），" +
                "规划出一套用于填充该模板所需的动态字段表单 Schema。你必须且只能返回合法的 JSON 字符串（不要带有 Markdown 的 ``` 标记或说明）：\n" +
                "{\n" +
                "  \"fields\": [\n" +
                "    { \"key\": \"字段在JSON中的标识\", \"label\": \"中文表单输入标签\", \"type\": \"input或textarea之一\" }\n" +
                "  ],\n" +
                "  \"tables\": [\n" +
                "    {\n" +
                "      \"key\": \"表格标识\",\n" +
                "      \"label\": \"表格中文名称\",\n" +
                "      \"columns\": [\"列名1\", \"列名2\", \"列名3\"]\n" +
                "    }\n" +
                "  ]\n" +
                "}";

        String userPrompt = String.format(
                "检测到的 Word 模板特征如下：\n" +
                "1. 表格第一行结构特征（表头）：%s\n" +
                "2. 提取出的 ${占位符} 标签：%s\n\n" +
                "请为我自动设计一套最适合用于录入、校对这些数据的动态表单 Schema。必须包容接口名称、URL、Method 等标准接口属性，若表头中包含其它说明属性（如安全等级、日志等级等），也请一并规划进去！",
                tableHeaders.isEmpty() ? "未探测到表格" : tableHeaders.toString(),
                placeholders.isEmpty() ? "未探测到占位符" : placeholders.toString()
        );

        try {
            log.info("向 AI 网关发起模板结构 Schema 提取...");
            String result = aiClient.blockingChat(systemPrompt, userPrompt, TOOL_CODE);
            return cleanJsonMarkdown(result);
        } catch (Exception e) {
            log.error("AI 提取 Schema 失败，使用南网接口文档标准 Schema 作为 Fallback 兜底...", e);
            return getFallbackSchema();
        }
    }

    /**
     * 根据 Schema 动态对齐提取代码中的结构化字段
     *
     * @param material   原始接口代码 / 报文 / 零散大纲
     * @param schemaJson 模板提取出的 Schema 属性规范
     * @return 接口/区块对齐后的结构化 JSON 数组
     */
    public String parseDataWithSchema(String material, String schemaJson) {
        log.info("开始根据指定 Schema 解析并抽取原始代码资料...");

        String systemPrompt = "你是一个专业的高并发系统大纲与代码结构化对齐抽取专家。" +
                "我会提供给你一份字段对齐规范 Schema JSON，以及一份用户的原始代码或零散资料。\n" +
                "请你从资料中抽取对应的实体大纲（如果是接口代码，请抽取接口，允许抽取多个），并将字段精准对齐填充进 Schema 里的 fields 和 tables 中。\n" +
                "你必须且只能返回符合格式的 JSON 数组（不要包含任何 Markdown 格式包裹，不要带 ```json）：\n" +
                "[\n" +
                "  {\n" +
                "    \"fields\": {\n" +
                "      \"Schema定义的key\": \"抽取并智能填充的内容\"\n" +
                "    },\n" +
                "    \"tables\": {\n" +
                "      \"Schema定义的tableKey\": [\n" +
                "        { \"Schema定义的列名1\": \"数据\", \"Schema定义的列名2\": \"数据\" }\n" +
                "      ]\n" +
                "    }\n" +
                "  \n" +
                "  }\n" +
                "]";

        String userPrompt = String.format(
                "【对齐 Schema 规范】：\n%s\n\n" +
                "【原始代码/零散资料】：\n%s\n\n" +
                "请执行对齐抽取，智能补齐缺失字段的合理默认值（如字段说明、类型等）。",
                schemaJson,
                material
        );

        try {
            String result = aiClient.blockingChat(systemPrompt, userPrompt, TOOL_CODE);
            return cleanJsonMarkdown(result);
        } catch (Exception e) {
            log.error("AI 智能提取对齐失败!", e);
            throw new RuntimeException("AI 对齐提取失败: " + e.getMessage(), e);
        }
    }

    /**
     * 一键 AI 脑补单个 API 的缺失字段和参数说明
     *
     * @param apiJson 单个 API 的当前可编辑 JSON 字符串
     * @return 脑补补全后的单个 API JSON 字符串
     */
    public String completeApiFields(String apiJson) {
        log.info("向大模型发起单个 API 字段脑补请求...");
        String systemPrompt = "你是一个专业的后端接口设计师。我会提供给你一个包含了接口基本信息（可能缺少参数说明、示例等）的 JSON 字符串。\n" +
                "请你根据接口的 URL、Method 和中文名称，智能推测并补全其缺失的参数说明、类型、必填性、以及入参/出参 JSON 示例。\n" +
                "你必须且只能返回补全后的合法 JSON 格式（不要包含任何 Markdown 格式包裹，不要带 ```json）：";

        try {
            String result = aiClient.blockingChat(systemPrompt, apiJson, TOOL_CODE);
            return cleanJsonMarkdown(result);
        } catch (Exception e) {
            log.error("AI 脑补 API 字段失败!", e);
            throw new RuntimeException("AI 脑补失败: " + e.getMessage(), e);
        }
    }

    /**
     * 清理 AI 返回中可能夹带的 Markdown 字符 ```json ... ```
     */
    private String cleanJsonMarkdown(String content) {
        if (content == null) return "";
        String cleaned = content.trim();
        if (cleaned.startsWith("```json")) {
            cleaned = cleaned.substring(7);
        } else if (cleaned.startsWith("```")) {
            cleaned = cleaned.substring(3);
        }
        if (cleaned.endsWith("```")) {
            cleaned = cleaned.substring(0, cleaned.length() - 3);
        }
        return cleaned.trim();
    }

    /**
     * 降级兜底的 Schema 定义 (适配河北南网标准格式)
     */
    private String getFallbackSchema() {
        return "{\n" +
                "  \"fields\": [\n" +
                "    { \"key\": \"name\", \"label\": \"接口中文名称\", \"type\": \"input\" },\n" +
                "    { \"key\": \"url\", \"label\": \"请求 URL\", \"type\": \"input\" },\n" +
                "    { \"key\": \"method\", \"label\": \"请求方式(GET/POST)\", \"type\": \"input\" },\n" +
                "    { \"key\": \"description\", \"label\": \"接口描述说明\", \"type\": \"textarea\" },\n" +
                "    { \"key\": \"requestExample\", \"label\": \"请求 JSON 示例\", \"type\": \"textarea\" },\n" +
                "    { \"key\": \"responseExample\", \"label\": \"返回 JSON 示例\", \"type\": \"textarea\" }\n" +
                "  ],\n" +
                "  \"tables\": [\n" +
                "    {\n" +
                "      \"key\": \"requestParams\",\n" +
                "      \"label\": \"请求参数说明 (Request Params)\",\n" +
                "      \"columns\": [\"参数名称\", \"是否必填\", \"类型\", \"字段说明\"]\n" +
                "    },\n" +
                "    {\n" +
                "      \"key\": \"responseParams\",\n" +
                "      \"label\": \"返回参数说明 (Response Params)\",\n" +
                "      \"columns\": [\"参数名称\", \"类型\", \"字段说明\"]\n" +
                "    }\n" +
                "  ]\n" +
                "}";
    }
}
