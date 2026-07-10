package com.ailab.core.common;

import lombok.Getter;

@Getter
public enum ResultCode {
    SUCCESS(200, "操作成功"),
    ERROR(500, "系统未知异常，请稍后再试"),
    VALIDATE_FAILED(404, "参数检验失败"),
    UNAUTHORIZED(401, "暂未登录或Token已过期"),
    FORBIDDEN(403, "没有相关权限"),
    BUSINESS_ERROR(1001, "业务处理异常"),
    AI_CONFIG_ERROR(2001, "AI 服务未配置或 API Key 缺失");

    private final int code;
    private final String message;

    ResultCode(int code, String message) {
        this.code = code;
        this.message = message;
    }
}
