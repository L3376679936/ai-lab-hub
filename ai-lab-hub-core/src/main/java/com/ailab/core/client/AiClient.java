package com.ailab.core.client;

import com.ailab.core.service.AiGatewayService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;
import okhttp3.sse.EventSource;
import okhttp3.sse.EventSourceListener;
import okhttp3.sse.EventSources;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
public class AiClient {

    @Autowired
    private AiGatewayService aiGatewayService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    // 默认超时时间 60s
    private final OkHttpClient okHttpClient = new OkHttpClient.Builder()
            .connectTimeout(60, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .build();

    private static final MediaType JSON_MEDIA_TYPE = MediaType.parse("application/json; charset=utf-8");

    /**
     * 发起流式 AI 对话请求 (OpenAI 兼容格式)
     *
     * @param prompt   用户提示词
     * @param toolCode 工具编码 (用于匹配三级覆盖 API Key)
     * @param listener SSE 事件监听器
     * @return EventSource 流对象
     */
    public EventSource streamChat(String prompt, String toolCode, EventSourceListener listener) {
        // 1. 获取三级覆盖的大模型参数
        String apiKey = aiGatewayService.getApiKey(toolCode);
        String endpoint = aiGatewayService.getEndpoint(toolCode);
        String model = aiGatewayService.getModel(toolCode);

        // 2. 拼接请求 URL
        String url = endpoint.endsWith("/") ? endpoint + "v1/chat/completions" : endpoint + "/v1/chat/completions";
        log.info("准备发起流式大模型调用. Endpoint: {}, Model: {}", url, model);

        try {
            // 3. 组装标准 OpenAI 兼容的 JSON 请求体
            Map<String, Object> requestMap = new HashMap<>();
            requestMap.put("model", model);
            requestMap.put("stream", true);

            List<Map<String, String>> messages = new ArrayList<>();
            Map<String, String> userMessage = new HashMap<>();
            userMessage.put("role", "user");
            userMessage.put("content", prompt);
            messages.add(userMessage);
            requestMap.put("messages", messages);

            String requestJson = objectMapper.writeValueAsString(requestMap);

            // 4. 构建 OkHttp 请求
            Request request = new Request.Builder()
                    .url(url)
                    .addHeader("Authorization", "Bearer " + apiKey)
                    .addHeader("Accept", "text/event-stream")
                    .post(RequestBody.create(requestJson, JSON_MEDIA_TYPE))
                    .build();

            // 5. 启动 OkHttp-SSE 监听流式字符
            EventSource.Factory factory = EventSources.createFactory(okHttpClient);
            return factory.newEventSource(request, listener);

        } catch (Exception e) {
            log.error("大模型流式调用组装请求异常: {}", e.getMessage());
            throw new RuntimeException("大模型网关调用失败: " + e.getMessage(), e);
        }
    }
}
