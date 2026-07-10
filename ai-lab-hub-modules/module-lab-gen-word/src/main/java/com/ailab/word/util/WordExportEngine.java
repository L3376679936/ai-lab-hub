package com.ailab.word.util;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.util.Units;
import org.apache.poi.xwpf.usermodel.*;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTShd;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTTcPr;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.STShd;
import org.springframework.stereotype.Component;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;

@Slf4j
@Component
public class WordExportEngine {

    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 物理克隆并导出 Word 报文
     *
     * @param schemaJson    字段定义 Schema
     * @param apiListJson   前端传递的对齐接口数据 JSON (包含 fields, tables 等)
     * @param templatePath  主模板文件路径
     * @param tempDir       临时文件存储目录
     * @param outputStream  物理文件下载输出流
     */
    @SuppressWarnings("unchecked")
    public void exportPhysicalWord(String schemaJson, String apiListJson, String templatePath, String tempDir, OutputStream outputStream) {
        log.info("POI 开始执行通用区块物理渲染. 模板路径: {}", templatePath);

        try (InputStream is = new FileInputStream(templatePath);
             XWPFDocument doc = new XWPFDocument(is)) {

            // 1. 解析前端传来的接口大纲 Block 数据
            List<Map<String, Object>> apiList = objectMapper.readValue(apiListJson, List.class);
            if (apiList == null || apiList.isEmpty()) {
                doc.write(outputStream);
                return;
            }

            // 2. 获取文档里最后一个段落，作为后续追加 Block 的锚点
            XWPFParagraph anchorParagraph = doc.getParagraphs().get(doc.getParagraphs().size() - 1);

            // 3. 遍历每一个接口区块进行动态克隆追加
            int blockIndex = 1;
            for (Map<String, Object> apiBlock : apiList) {
                Map<String, Object> fields = (Map<String, Object>) apiBlock.get("fields");
                Map<String, Object> tables = (Map<String, Object>) apiBlock.get("tables");

                // --- 3.1 物理渲染接口 H2 标题小节 ---
                String apiName = fields != null ? (String) fields.getOrDefault("name", "未命名接口") : "未命名区块";
                String apiUrl = fields != null ? (String) fields.getOrDefault("url", "") : "";
                String apiMethod = fields != null ? (String) fields.getOrDefault("method", "") : "";

                XWPFParagraph titlePara = doc.createParagraph();
                titlePara.setStyle("Heading2"); // 使用模板中自带的 H2 段落样式
                XWPFRun titleRun = titlePara.createRun();
                titleRun.setBold(true);
                titleRun.setFontSize(14);
                titleRun.setText(String.format("1.%d %s", blockIndex++, apiName));

                // --- 3.2 物理渲染基本说明段落 ---
                if (fields != null) {
                    XWPFParagraph descPara = doc.createParagraph();
                    XWPFRun descRun = descPara.createRun();
                    descRun.setFontSize(10.5); // 宋体五号
                    descRun.setText(String.format("【接口路径】: %s  [%s]\n【业务描述】: %s", 
                            apiUrl, apiMethod, fields.getOrDefault("description", "暂无说明")));
                }

                // --- 3.3 物理追加表格区块 ---
                if (tables != null && !tables.isEmpty()) {
                    for (Map.Entry<String, Object> tableEntry : tables.entrySet()) {
                        String tableKey = tableEntry.getKey();
                        List<Map<String, String>> rows = (List<Map<String, String>>) tableEntry.getValue();
                        if (rows == null || rows.isEmpty()) continue;

                        // 动态获取每一行数据的列集合
                        Set<String> columns = rows.get(0).keySet();

                        // 在 Word 中创建表格
                        XWPFTable table = doc.createTable(rows.size() + 1, columns.size());
                        // 设置表格宽度自适应，且应用细线边框 (默认为 POI 标准网格)
                        table.setWidth("100%");

                        // 填充表头并涂上“南网青”背景色 (#EBF2F0)
                        XWPFTableRow headerRow = table.getRow(0);
                        int colIdx = 0;
                        for (String colName : columns) {
                            XWPFTableCell cell = headerRow.getCell(colIdx++);
                            cell.setText(colName);
                            setCellBackgroundColor(cell, "EBF2F0"); // 南网标准的淡青色表头底色
                            XWPFParagraph para = cell.getParagraphs().get(0);
                            para.createRun().setBold(true);
                        }

                        // 填充数据行
                        int rowIdx = 1;
                        for (Map<String, String> rowData : rows) {
                            XWPFTableRow dataRow = table.getRow(rowIdx++);
                            int cIdx = 0;
                            for (String colName : columns) {
                                XWPFTableCell cell = dataRow.getCell(cIdx++);
                                cell.setText(rowData.getOrDefault(colName, ""));
                            }
                        }

                        // 追加一个空段落做行距隔离
                        doc.createParagraph();
                    }
                }

                // --- 3.4 物理插入可能关联的拓扑图图片 (如果前端关联了本地临时 imageId) ---
                if (fields != null && fields.containsKey("imageId")) {
                    String imageId = (String) fields.get("imageId");
                    if (imageId != null && !imageId.trim().isEmpty()) {
                        String imagePath = tempDir + "/uploads/" + imageId;
                        if (Files.exists(Paths.get(imagePath))) {
                            log.info("POI 检测到接口关联图片，执行物理插入. Path: {}", imagePath);
                            XWPFParagraph imgPara = doc.createParagraph();
                            imgPara.setAlignment(ParagraphAlignment.CENTER);
                            XWPFRun imgRun = imgPara.createRun();
                            imgRun.setText("图 1. 接口拓扑/业务时序示意图：");
                            imgRun.addBreak();

                            try (InputStream imgStream = new FileInputStream(imagePath)) {
                                // 限制宽度为 450 像素，高度按比例适配
                                imgRun.addPicture(imgStream, XWPFDocument.PICTURE_TYPE_PNG, imageId, 
                                        Units.toEMU(420), Units.toEMU(260));
                            } catch (Exception imgEx) {
                                log.error("POI 插入图片失败: {}", imgEx.getMessage());
                            }
                            doc.createParagraph(); // 间距
                        }
                    }
                }

                // --- 3.5 渲染 JSON 入参出参代码框 ---
                if (fields != null) {
                    if (fields.containsKey("requestExample") || fields.containsKey("responseExample")) {
                        XWPFParagraph codePara = doc.createParagraph();
                        XWPFRun codeRun = codePara.createRun();
                        codeRun.setFontFamily("Consolas");
                        codeRun.setFontSize(9);
                        if (fields.containsKey("requestExample")) {
                            codeRun.setText("【请求 JSON 示例】:\n" + fields.get("requestExample") + "\n");
                        }
                        if (fields.containsKey("responseExample")) {
                            codeRun.setText("【返回 JSON 示例】:\n" + fields.get("responseExample") + "\n");
                        }
                    }
                }

                // 每个接口小节后，增加分隔线或空段落
                doc.createParagraph();
            }

            // 4. 将克隆组装完毕的物理 Word 输出到 response
            doc.write(outputStream);
            log.info("POI 物理 Word 文档填充克隆全部成功！");

        } catch (Exception e) {
            log.error("物理 Word 导出失败: ", e);
            throw new RuntimeException("导出 Word 文档失败: " + e.getMessage(), e);
        }
    }

    /**
     * 设置单元格底色 XML 辅助函数 (完美适配 Word底色 规范)
     */
    private void setCellBackgroundColor(XWPFTableCell cell, String hexColor) {
        org.openxmlformats.schemas.wordprocessingml.x2006.main.CTTcPr tcPr = 
                cell.getCTTc().getTcPr() != null ? cell.getCTTc().getTcPr() : cell.getCTTc().addNewTcPr();
        org.openxmlformats.schemas.wordprocessingml.x2006.main.CTShd shd = 
                tcPr.isSetShd() ? tcPr.getShd() : tcPr.addNewShd();
        shd.setVal(org.openxmlformats.schemas.wordprocessingml.x2006.main.STShd.CLEAR);
        shd.setColor("auto");
        shd.setFill(hexColor);
    }
}
