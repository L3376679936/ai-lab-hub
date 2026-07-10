package com.ailab.core.config;

import com.ailab.core.entity.SysUser;
import com.ailab.core.repository.SysUserRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 系统启动初始化器
 * 职责：检测数据库中的明文密码，自动升级为 BCrypt 哈希存储
 * 幂等设计：如果密码已经是 BCrypt 格式（$2a$ 开头），则跳过不重复处理
 */
@Slf4j
@Component
public class DataInitializer implements ApplicationRunner {

    @Autowired
    private SysUserRepository sysUserRepository;

    @Autowired
    private BCryptPasswordEncoder passwordEncoder;

    @Override
    public void run(ApplicationArguments args) {
        log.info("=== 系统启动检查：扫描明文密码用户，自动执行 BCrypt 哈希迁移 ===");
        try {
            List<SysUser> users = sysUserRepository.findAll();
            int migratedCount = 0;

            for (SysUser user : users) {
                String pwd = user.getPassword();
                // 判断是否已经是 BCrypt 哈希（BCrypt 哈希以 $2a$ 或 $2b$ 开头）
                if (pwd != null && !pwd.startsWith("$2a$") && !pwd.startsWith("$2b$")) {
                    // 明文密码 → BCrypt 哈希并更新
                    String hashed = passwordEncoder.encode(pwd);
                    user.setPassword(hashed);
                    sysUserRepository.save(user);
                    log.info("用户 [{}] 的密码已从明文自动迁移为 BCrypt 哈希存储", user.getUsername());
                    migratedCount++;
                }
            }

            if (migratedCount == 0) {
                log.info("=== 密码安全检查完毕：所有用户密码已是 BCrypt 格式，无需迁移 ===");
            } else {
                log.info("=== 密码安全迁移完成：共迁移 {} 个用户的明文密码 ===", migratedCount);
            }
        } catch (Exception e) {
            log.warn("密码迁移检查时发生异常（不影响正常启动）: {}", e.getMessage());
        }
    }
}
