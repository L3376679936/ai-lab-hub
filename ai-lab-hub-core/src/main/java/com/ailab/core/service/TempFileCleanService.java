package com.ailab.core.service;

import com.ailab.core.entity.CoreTempFile;
import com.ailab.core.repository.CoreTempFileRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Date;
import java.util.List;

@Slf4j
@Service
public class TempFileCleanService {

    @Autowired
    private CoreTempFileRepository coreTempFileRepository;

    @Value("${ai.file.temp-dir:./temp}")
    private String tempDirSetting;

    /**
     * 定时任务：启动后 10 秒执行，之后每小时巡检一次。
     * 为支持高防安全巡检，防目录穿越与软链接劫持。
     */
    @Scheduled(initialDelay = 10000, fixedRate = 3600000)
    @Transactional
    public void cleanExpiredFiles() {
        log.info("开始扫描 24 小时前生成的临时过期物理文件...");

        // 获取 24 小时前的临界时间
        Date expiredBoundary = new Date(System.currentTimeMillis() - 24 * 60 * 60 * 1000L);

        // 查找所有待清理且超时的临时文件记录
        List<CoreTempFile> pendingFiles = coreTempFileRepository.findByStatusAndCreateTimeBefore(0, expiredBoundary);
        if (pendingFiles.isEmpty()) {
            log.info("未发现需要清理的临时物理文件。");
            return;
        }

        log.info("共发现 {} 个待清理文件，开始安全物理删除...", pendingFiles.size());

        try {
            // 获取临时根目录的规范化绝对路径，用于前缀匹配检验，防御相对路径穿越攻击 (../)
            File tempBaseDir = new File(tempDirSetting);
            if (!tempBaseDir.exists()) {
                tempBaseDir.mkdirs();
            }
            String baseCanonicalPath = tempBaseDir.getCanonicalPath();

            for (CoreTempFile record : pendingFiles) {
                try {
                    File targetFile = new File(record.getFilePath());
                    if (!targetFile.exists()) {
                        log.info("临时文件 [{}] 在物理磁盘上已不存在，更新数据库记录", record.getFilePath());
                        record.setStatus(1); // 标记为已清理
                        coreTempFileRepository.save(record);
                        continue;
                    }

                    // 1. 防路径穿越安全校验 (路径前缀安全沙箱比对)
                    String targetCanonicalPath = targetFile.getCanonicalPath();
                    if (!targetCanonicalPath.startsWith(baseCanonicalPath)) {
                        log.error("安全警告：临时文件 [{}] 真实绝对路径 [{}] 试图穿越出临时沙箱根路径 [{}]！已被拒绝物理删除！",
                                record.getFilePath(), targetCanonicalPath, baseCanonicalPath);
                        // 为了系统安全性，将状态标记为 2 (异常拦截) 状态，防止被反复循环扫描
                        record.setStatus(2);
                        coreTempFileRepository.save(record);
                        continue;
                    }

                    // 2. 符号链接安全校验 (防软链接劫持攻击删除系统敏感资源)
                    Path targetPath = targetFile.toPath();
                    if (Files.isSymbolicLink(targetPath)) {
                        log.warn("检测到软链接 [{}]，为了系统安全性，仅删除软链接本身，不追踪其指向的物理资源！", record.getFilePath());
                        Files.delete(targetPath);
                    } else {
                        // 正常物理文件安全删除
                        Files.delete(targetPath);
                        log.info("临时物理文件 [{}] 删除成功", record.getFilePath());
                    }

                    // 3. 更新数据库清理状态
                    record.setStatus(1); // 1-已清理
                    coreTempFileRepository.save(record);

                } catch (Exception e) {
                    log.error("物理清理临时文件 [{}] 失败: {}", record.getFilePath(), e.getMessage());
                }
            }
        } catch (Exception e) {
            log.error("临时文件自动清理器执行异常: {}", e.getMessage());
        }

        log.info("过期临时文件物理清理完成。");
    }
}
