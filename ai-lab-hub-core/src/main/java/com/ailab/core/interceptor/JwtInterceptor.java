package com.ailab.core.interceptor;

import com.ailab.core.common.ResultCode;
import com.ailab.core.exception.BusinessException;
import com.ailab.core.util.JwtUtils;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
public class JwtInterceptor implements HandlerInterceptor {

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        // 支持 Options 预检请求直接通过
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }

        // 获取 Header 里的 Authorization 字段
        String authHeader = request.getHeader("Authorization");
        if (!StringUtils.hasText(authHeader) || !authHeader.startsWith("Bearer ")) {
            throw new BusinessException(ResultCode.UNAUTHORIZED, "用户未登录，请先登录");
        }

        // 截取真正的 Token 字符串
        String token = authHeader.substring(7);
        if (JwtUtils.isTokenExpired(token)) {
            throw new BusinessException(ResultCode.UNAUTHORIZED, "登录已过期，请重新登录");
        }

        // 解析用户名
        String username = JwtUtils.getUsernameFromToken(token);
        if (!StringUtils.hasText(username)) {
            throw new BusinessException(ResultCode.UNAUTHORIZED, "登录凭证校验失败");
        }

        // 将用户名存入 request attribute
        request.setAttribute("username", username);
        return true;
    }
}
