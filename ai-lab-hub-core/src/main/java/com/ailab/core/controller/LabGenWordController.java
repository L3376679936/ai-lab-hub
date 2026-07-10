package com.ailab.core.controller;

import com.ailab.core.entity.CoreTempFile;
import com.ailab.core.exception.BusinessException;
import com.ailab.core.common.ResultCode;
import com.ailab.core.repository.CoreTempFileRepository;
import com.ailab.core.service.LabGenWordService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import javax.servlet.http.HttpServletResponse;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URLEncoder;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

@Slf4j
@RestController
@RequestMapping("/modules/labGenWord")
public class LabGenWordController {

    @Autowired
    private LabGenWordService labGenWordService;

    @Autowired
    private CoreTempFileRepository coreTempFileRepository;

    @Value("${ai.file.temp-dir:./temp}")
    private String tempDirSetting;

    /**
     * 发起 Word 文档流式 AI 生成
     *
     * @param topic   报告主题
     * @param outline 报告大纲
     * @return SseEmitter 异步事件流
     */
    @GetMapping("/generate")
    public SseEmitter generateWordReport(@RequestParam String topic, @RequestParam String outline) {
        if (!StringUtils.hasText(topic)) {
            throw new BusinessException(ResultCode.VALIDATE_FAILED, "报告主题不能为空");
        }

        // 创建 SseEmitter 实例，设置超时时间为 5 分钟 (300,000 毫秒)
        SseEmitter emitter = new SseEmitter(300000L);

        // 利用 CompletableFuture 异步拉起大模型生成与 MCP 排版流程，不阻塞 Spring MVC 主通信线程
        CompletableFuture.runAsync(() -> {
            try {
                labGenWordService.generateWordReport(topic, outline, emitter);
            } catch (Exception e) {
                log.error("文档异步流式生成异常: {}", e.getMessage());
                try {
                    emitter.send(SseEmitter.event().name("error").data("生成失败: " + e.getMessage()));
                    emitter.complete();
                } catch (Exception ex) {
                    log.error("SseEmitter 异常关闭推送失败: {}", ex.getMessage());
                }
            }
        });

        return emitter;
    }

    /**
     * 根据文件登记 ID 下载生成的 docx 报告（前缀安全沙箱防目录穿越）
     *
     * @param fileId   临时文件 ID
     * @param response 响应流
     */
    @GetMapping("/download")
    public void downloadReport(@RequestParam Long fileId, HttpServletResponse response) {
        // 1. 查询文件登记信息
        Optional<CoreTempFile> recordOpt = coreTempFileRepository.findById(fileId);
        if (!recordOpt.isPresent()) {
            throw new BusinessException(ResultCode.VALIDATE_FAILED, "下载的文件不存在或已被清理");
        }

        CoreTempFile record = recordOpt.get();
        File file = new File(record.getFilePath());

        // 2. 防路径穿越安全沙箱检验
        try {
            File baseDir = new File(tempDirSetting);
            String baseCanonical = baseDir.getCanonicalPath();
            String targetCanonical = file.getCanonicalPath();

            if (!targetCanonical.startsWith(baseCanonical)) {
                log.error("安全越权警告：下载文件绝对路径 [{}] 不在前缀沙箱 [{}] 中！已被系统拒绝下载！",
                        targetCanonical, baseCanonical);
                throw new BusinessException(ResultCode.FORBIDDEN, "非法越权的文件下载操作被拒绝");
            }

            if (!file.exists() || !file.isFile()) {
                throw new BusinessException(ResultCode.VALIDATE_FAILED, "物理磁盘上的文件不存在或已被自动清理");
            }

            // 3. 设定响应头以导出文件
            response.setContentType("application/octet-stream");
            // 处理中文乱码与字符安全编码
            String safeFilename = URLEncoder.encode(record.getFileName(), "UTF-8");
            response.setHeader("Content-Disposition", "attachment; filename=\"" + safeFilename + "\"; filename*=utf-8''" + safeFilename);
            response.setContentLengthLong(file.length());

            // 4. 写入文件二进制流
            try (InputStream is = new FileInputStream(file);
                 OutputStream os = response.getOutputStream()) {
                byte[] buffer = new byte[4096];
                int bytesRead;
                while ((bytesRead = is.read(buffer)) != -1) {
                    os.write(buffer, 0, bytesRead);
                }
                os.flush();
            }

            log.info("物理文件 [{}] 下载成功交付", record.getFilePath());

        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("导出物理文件失败: {}", e.getMessage());
            throw new BusinessException(ResultCode.ERROR, "导出文件异常: " + e.getMessage());
        }
    }
}
