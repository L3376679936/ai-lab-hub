package com.ailab.core.exception;

import com.ailab.core.common.Result;
import com.ailab.core.common.ResultCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * 捕获自定义业务异常
     */
    @ExceptionHandler(BusinessException.class)
    public Result<Void> handleBusinessException(BusinessException e) {
        log.error("业务异常 [code={}]: {}", e.getCode(), e.getMessage());
        return Result.failed(e.getCode(), e.getMessage());
    }

    /**
     * 捕获 Spring Validator 参数校验异常
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public Result<Void> handleValidException(MethodArgumentNotValidException e) {
        String msg = "参数校验错误";
        if (e.getBindingResult().getFieldError() != null) {
            msg = e.getBindingResult().getFieldError().getDefaultMessage();
        }
        log.error("参数校验异常: {}", msg);
        return Result.failed(ResultCode.VALIDATE_FAILED.getCode(), msg);
    }

    /**
     * 捕获其他未知运行时异常
     */
    @ExceptionHandler(RuntimeException.class)
    public Result<Void> handleRuntimeException(RuntimeException e) {
        log.error("系统未知运行时异常:", e);
        return Result.failed(ResultCode.ERROR.getCode(), e.getMessage());
    }

    /**
     * 捕获所有其他普通异常
     */
    @ExceptionHandler(Exception.class)
    public Result<Void> handleException(Exception e) {
        log.error("系统未知异常:", e);
        return Result.failed(ResultCode.ERROR.getCode(), "系统内部繁忙，请稍后再试");
    }
}
