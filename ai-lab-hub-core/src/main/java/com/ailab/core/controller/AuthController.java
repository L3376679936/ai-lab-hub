package com.ailab.core.controller;

import com.ailab.core.common.Result;
import com.ailab.core.entity.SysUser;
import com.ailab.core.exception.BusinessException;
import com.ailab.core.repository.SysUserRepository;
import com.ailab.core.util.JwtUtils;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@Slf4j
@RestController
@RequestMapping("/auth")
public class AuthController {

    @Autowired
    private SysUserRepository sysUserRepository;

    @Autowired
    private BCryptPasswordEncoder passwordEncoder;

    @PostMapping("/login")
    public Result<Map<String, Object>> login(@RequestBody LoginRequest loginRequest) {
        String username = loginRequest.getUsername();
        String password = loginRequest.getPassword();

        if (!StringUtils.hasText(username) || !StringUtils.hasText(password)) {
            throw new BusinessException("用户名和密码不能为空");
        }

        boolean authSuccess = false;
        String nickname = "管理员";

        try {
            Optional<SysUser> userOpt = sysUserRepository.findByUsername(username);
            if (userOpt.isPresent()) {
                SysUser user = userOpt.get();
                if (user.getStatus() != 1) {
                    throw new BusinessException("该账户已被禁用");
                }
                // 使用 BCrypt 安全校验密码（支持哈希存储，不再明文比对）
                if (passwordEncoder.matches(password, user.getPassword())) {
                    authSuccess = true;
                    nickname = user.getNickname();
                }
            }
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("登录查询异常: {}", e.getMessage());
            throw new BusinessException("登录服务暂时不可用，请稍后重试");
        }

        if (!authSuccess) {
            throw new BusinessException("用户名或密码错误");
        }

        // 生成 JWT Token
        String token = JwtUtils.generateToken(username);
        log.info("用户 [{}] 登录成功，Token 已生成", username);

        // 组装返回数据
        Map<String, Object> data = new HashMap<>();
        data.put("token", token);
        data.put("username", username);
        data.put("nickname", nickname);

        return Result.success(data, "登录成功");
    }

    @PostMapping("/change-password")
    public Result<Void> changePassword(@RequestBody ChangePasswordRequest request, javax.servlet.http.HttpServletRequest httpRequest) {
        String username = (String) httpRequest.getAttribute("username");
        if (!StringUtils.hasText(username)) {
            throw new BusinessException("用户未登录或登录凭证已失效");
        }

        String oldPwd = request.getOldPassword();
        String newPwd = request.getNewPassword();
        if (!StringUtils.hasText(oldPwd) || !StringUtils.hasText(newPwd)) {
            throw new BusinessException("旧密码和新密码不能为空");
        }

        Optional<SysUser> userOpt = sysUserRepository.findByUsername(username);
        if (!userOpt.isPresent()) {
            throw new BusinessException("账户不存在");
        }

        SysUser user = userOpt.get();
        // 校验旧密码是否匹配
        if (!passwordEncoder.matches(oldPwd, user.getPassword())) {
            throw new BusinessException("原安全密码校验失败，请重新输入");
        }

        // 加密并更新新密码
        user.setPassword(passwordEncoder.encode(newPwd));
        sysUserRepository.save(user);

        log.info("用户 [{}] 的安全密码修改成功", username);
        return Result.success(null, "安全密码修改成功");
    }

    @Data
    public static class ChangePasswordRequest {
        private String oldPassword;
        private String newPassword;
    }

    @Data
    public static class LoginRequest {
        private String username;
        private String password;
    }
}
