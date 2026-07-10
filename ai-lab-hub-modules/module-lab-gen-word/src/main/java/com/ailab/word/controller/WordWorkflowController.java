package com.ailab.word.controller;

import com.ailab.core.common.Result;
import com.ailab.core.entity.CoreTempFile;
import com.ailab.core.repository.CoreTempFileRepository;
import com.ailab.word.service.AiExtractService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import com.ailab.word.util.WordExportEngine;
import com.fasterxml.jackson.databind.ObjectMapper;

import javax.annotation.PostConstruct;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

@Slf4j
@RestController
@RequestMapping("/word")
public class WordWorkflowController {

    @Autowired
    private AiExtractService aiExtractService;

    @Autowired
    private CoreTempFileRepository coreTempFileRepository;

    @Autowired
    private WordExportEngine wordExportEngine;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${ai.file.temp-dir:./temp}")
    private String tempDir;

    private static final String TOOL_CODE = "labGenWord";

    @PostConstruct
    public void init() {
        try {
            Files.createDirectories(Paths.get(tempDir));
            Files.createDirectories(Paths.get(tempDir, "uploads"));
            log.info("本地 Word 导出与上传缓存物理临时目录初始化成功: {}", tempDir);
        } catch (Exception e) {
            log.error("初始化临时目录失败: {}", e.getMessage());
        }
    }

    /**
     * 上传并智能解析自定义 Word 模板
     */
    @PostMapping("/analyze-template")
    public Result<String> analyzeTemplate(@RequestParam("file") MultipartFile file) {
        if (file.isEmpty()) {
            return Result.failed("上传的文件不能为空！");
        }
        try {
            // 将模板暂存在物理临时目录下以备后续克隆使用
            Path backupPath = Paths.get(tempDir, "uploads", "template_latest.docx");
            Files.write(backupPath, file.getBytes());

            String schemaJson = aiExtractService.analyzeTemplateSchema(file.getBytes());
            return Result.success(schemaJson, "模板智能解析结构提取成功！");
        } catch (Exception e) {
            log.error("解析自定义模板异常: ", e);
            return Result.failed("解析自定义模板失败: " + e.getMessage());
        }
    }

    /**
     * 根据 Schema 智能提取零散资料与代码
     */
    @PostMapping("/parse")
    public Result<String> parseData(@RequestBody Map<String, String> request) {
        String material = request.get("material");
        String schemaJson = request.get("schemaJson");
        if (material == null || material.trim().isEmpty()) {
            return Result.failed("录入的代码或原始资料不能为空！");
        }
        if (schemaJson == null || schemaJson.trim().isEmpty()) {
            return Result.failed("字段 Schema 规则未配置，请先上传模板或使用内置模板！");
        }
        try {
            String extractedJson = aiExtractService.parseDataWithSchema(material, schemaJson);
            return Result.success(extractedJson, "AI 接口字段对齐抽取完成！");
        } catch (Exception e) {
            log.error("AI 提取接口异常: ", e);
            return Result.failed("AI 提取失败: " + e.getMessage());
        }
    }

    /**
     * 单个 API 属性智能补全
     */
    @PostMapping("/complete-api")
    public Result<String> completeApi(@RequestBody Map<String, String> request) {
        String apiJson = request.get("apiJson");
        if (apiJson == null || apiJson.trim().isEmpty()) {
            return Result.failed("接口元数据为空，无法进行脑补！");
        }
        try {
            String completedJson = aiExtractService.completeApiFields(apiJson);
            return Result.success(completedJson, "AI 智能脑补补全字段完成！");
        } catch (Exception e) {
            log.error("AI 字段脑补异常: ", e);
            return Result.failed("脑补失败: " + e.getMessage());
        }
    }

    /**
     * 临时图片源上传 (如粘贴接口图、时序图等)
     */
    @PostMapping("/upload-image")
    public Result<Map<String, String>> uploadImage(@RequestParam("file") MultipartFile file, HttpServletRequest request) {
        if (file.isEmpty()) {
            return Result.failed("上传图片文件不能为空！");
        }
        try {
            String originalFilename = file.getOriginalFilename();
            String extension = originalFilename.contains(".") ? originalFilename.substring(originalFilename.lastIndexOf(".")) : ".png";
            String uuidFilename = UUID.randomUUID().toString() + extension;
            Path targetPath = Paths.get(tempDir, "uploads", uuidFilename);

            Files.write(targetPath, file.getBytes());
            log.info("用户上传接口图片暂存至本地: {}", targetPath);

            // 写入本地清理数据底盘
            CoreTempFile tempFile = new CoreTempFile();
            tempFile.setFileName(uuidFilename);
            tempFile.setFilePath(targetPath.toAbsolutePath().toString());
            tempFile.setToolCode(TOOL_CODE);
            tempFile.setStatus(0);
            coreTempFileRepository.save(tempFile);

            String contextPath = request.getContextPath(); // 动态获取运行时的 "/ai-lab-hub-api"
            Map<String, String> data = new HashMap<>();
            data.put("imageId", uuidFilename);
            data.put("url", contextPath + "/word/view-image/" + uuidFilename);
            return Result.success(data, "接口辅助图片暂存成功！");
        } catch (Exception e) {
            log.error("上传图片异常: ", e);
            return Result.failed("图片上传暂存失败: " + e.getMessage());
        }
    }

    /**
     * 物理图片预览展示接口
     */
    @GetMapping("/view-image/{imageId}")
    public void viewImage(@PathVariable("imageId") String imageId, HttpServletResponse response) {
        try {
            File file = new File(tempDir + "/uploads/" + imageId);
            if (!file.exists()) {
                response.sendError(HttpServletResponse.SC_NOT_FOUND);
                return;
            }
            response.setContentType("image/png");
            try (FileInputStream fis = new FileInputStream(file);
                 OutputStream os = response.getOutputStream()) {
                byte[] buffer = new byte[1024];
                int len;
                while ((len = fis.read(buffer)) != -1) {
                    os.write(buffer, 0, len);
                }
                os.flush();
            }
        } catch (Exception e) {
            log.error("预览图片输出异常: ", e);
        }
    }

    /**
     * 物理 Word 导出 (Apache POI 对齐物理克隆写入)
     */
    @PostMapping("/export")
    public void exportWord(@RequestBody Map<String, Object> requestData, HttpServletResponse response) {
        log.info("触发 POI 动态物理克隆与 Word 物理写盘导出...");
        try {
            String schemaJson = (String) requestData.get("schemaJson");
            // 将 apiList 以 JSON 字符串格式传递给引擎进行安全反序列化
            String apiListJson = objectMapper.writeValueAsString(requestData.get("apiList"));
            Boolean isCustom = (Boolean) requestData.get("isCustom");
            if (isCustom == null) isCustom = false;

            // 确定模板路径：如果使用自定义模板，则读取上传的备份，否则读取内置的模板
            String templatePath = tempDir + "/uploads/template_latest.docx";
            if (!isCustom || !new File(templatePath).exists()) {
                // 读取底座 classpath 资源模板
                Path resourcePath = Paths.get(tempDir, "templateApiDoc.docx");
                if (!Files.exists(resourcePath)) {
                    // 从 Classpath 复制内置模板到临时目录中以便读取
                    try (InputStream is = getClass().getResourceAsStream("/templates/templateApiDoc.docx")) {
                        if (is != null) {
                            Files.copy(is, resourcePath);
                        } else {
                            throw new RuntimeException("底座中缺失默认内置的河北南网Word模板文件 templateApiDoc.docx");
                        }
                    }
                }
                templatePath = resourcePath.toAbsolutePath().toString();
            }

            response.setContentType("application/vnd.openxmlformats-officedocument.wordprocessingml.document");
            response.setHeader("Content-Disposition", "attachment; filename=Generated.docx");

            try (OutputStream os = response.getOutputStream()) {
                wordExportEngine.exportPhysicalWord(schemaJson, apiListJson, templatePath, tempDir, os);
                os.flush();
            }
        } catch (Exception e) {
            log.error("导出物理 Word 文档异常: ", e);
        }
    }
}
