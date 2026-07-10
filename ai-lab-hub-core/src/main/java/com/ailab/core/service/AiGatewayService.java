package com.ailab.core.service;

import com.ailab.core.common.ResultCode;
import com.ailab.core.entity.SysConfig;
import com.ailab.core.entity.ToolConfig;
import com.ailab.core.exception.BusinessException;
import com.ailab.core.repository.SysConfigRepository;
import com.ailab.core.repository.ToolConfigRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.Optional;

@Slf4j
@Service
public class AiGatewayService {

    @Autowired
    private ToolConfigRepository toolConfigRepository;

    @Autowired
    private SysConfigRepository sysConfigRepository;

    @Value("${ai.global.api-key}")
    private String defaultApiKey;

    @Value("${ai.global.endpoint}")
    private String defaultEndpoint;

    @Value("${ai.global.model}")
    private String defaultModel;

    /**
     * 三级覆盖获取 API Key
     */
    public String getApiKey(String toolCode) {
        // Level 1: 局部优先 (Tool Config)
        if (StringUtils.hasText(toolCode)) {
            Optional<ToolConfig> toolConfig = toolConfigRepository.findByToolCodeAndConfigKey(toolCode, "api_key");
            if (toolConfig.isPresent() && StringUtils.hasText(toolConfig.get().getConfigValue())) {
                return toolConfig.get().getConfigValue();
            }
        }

        // Level 2: 全局优先 (Sys Config)
        Optional<SysConfig> sysConfig = sysConfigRepository.findByConfigKey("global_api_key");
        if (sysConfig.isPresent() && StringUtils.hasText(sysConfig.get().getConfigValue())) {
            return sysConfig.get().getConfigValue();
        }

        // Level 3: 配置文件兜底 (application.yml)
        if (!StringUtils.hasText(defaultApiKey) || defaultApiKey.contains("default-key")) {
            throw new BusinessException(ResultCode.AI_CONFIG_ERROR, "大模型 API Key 尚未配置，请在右上角系统设置中完成配置。");
        }

        return defaultApiKey;
    }

    /**
     * 三级覆盖获取 Endpoint
     */
    public String getEndpoint(String toolCode) {
        // Level 1: 局部优先 (Tool Config)
        if (StringUtils.hasText(toolCode)) {
            Optional<ToolConfig> toolConfig = toolConfigRepository.findByToolCodeAndConfigKey(toolCode, "endpoint");
            if (toolConfig.isPresent() && StringUtils.hasText(toolConfig.get().getConfigValue())) {
                return toolConfig.get().getConfigValue();
            }
        }

        // Level 2: 全局优先 (Sys Config)
        Optional<SysConfig> sysConfig = sysConfigRepository.findByConfigKey("global_endpoint");
        if (sysConfig.isPresent() && StringUtils.hasText(sysConfig.get().getConfigValue())) {
            return sysConfig.get().getConfigValue();
        }

        // Level 3: 配置文件兜底 (application.yml)
        return defaultEndpoint;
    }

    /**
     * 三级覆盖获取 Model
     */
    public String getModel(String toolCode) {
        // Level 1: 局部优先 (Tool Config)
        if (StringUtils.hasText(toolCode)) {
            Optional<ToolConfig> toolConfig = toolConfigRepository.findByToolCodeAndConfigKey(toolCode, "model");
            if (toolConfig.isPresent() && StringUtils.hasText(toolConfig.get().getConfigValue())) {
                return toolConfig.get().getConfigValue();
            }
        }

        // Level 2: 全局优先 (Sys Config)
        Optional<SysConfig> sysConfig = sysConfigRepository.findByConfigKey("global_model");
        if (sysConfig.isPresent() && StringUtils.hasText(sysConfig.get().getConfigValue())) {
            return sysConfig.get().getConfigValue();
        }

        // Level 3: 配置文件兜底 (application.yml)
        return defaultModel;
    }
}
