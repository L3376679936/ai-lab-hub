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

    /**
     * 发起同步 AI 对话请求 (OpenAI 兼容格式)
     *
     * @param systemPrompt 系统提示词 (可为 null)
     * @param userPrompt   用户提示词
     * @param toolCode     工具编码
     * @return 大模型返回的文本内容
     */
    @SuppressWarnings("unchecked")
    public String blockingChat(String systemPrompt, String userPrompt, String toolCode) {
        String apiKey = aiGatewayService.getApiKey(toolCode);
        String endpoint = aiGatewayService.getEndpoint(toolCode);
        String model = aiGatewayService.getModel(toolCode);

        String url = endpoint.endsWith("/") ? endpoint + "v1/chat/completions" : endpoint + "/v1/chat/completions";
        log.info("准备发起同步大模型调用. Endpoint: {}, Model: {}", url, model);

        try {
            Map<String, Object> requestMap = new HashMap<>();
            requestMap.put("model", model);
            requestMap.put("stream", false);

            List<Map<String, String>> messages = new ArrayList<>();
            if (systemPrompt != null && !systemPrompt.trim().isEmpty()) {
                Map<String, String> sysMessage = new HashMap<>();
                sysMessage.put("role", "system");
                sysMessage.put("content", systemPrompt);
                messages.add(sysMessage);
            }
            Map<String, String> userMessage = new HashMap<>();
            userMessage.put("role", "user");
            userMessage.put("content", userPrompt);
            messages.add(userMessage);
            requestMap.put("messages", messages);

            String requestJson = objectMapper.writeValueAsString(requestMap);

            Request request = new Request.Builder()
                    .url(url)
                    .addHeader("Authorization", "Bearer " + apiKey)
                    .post(RequestBody.create(requestJson, JSON_MEDIA_TYPE))
                    .build();

            try (Response response = okHttpClient.newCall(request).execute()) {
                if (!response.isSuccessful()) {
                    throw new RuntimeException("大模型服务响应失败, HTTP code: " + response.code());
                }
                String body = response.body().string();
                Map<String, Object> responseMap = objectMapper.readValue(body, Map.class);
                List<Map<String, Object>> choices = (List<Map<String, Object>>) responseMap.get("choices");
                if (choices != null && !choices.isEmpty()) {
                    Map<String, Object> message = (Map<String, Object>) choices.get(0).get("message");
                    if (message != null) {
                        return (String) message.get("content");
                    }
                }
                throw new RuntimeException("大模型返回体中未包含有效文本内容");
            }
        } catch (Exception e) {
            log.error("大模型同步调用异常: {}", e.getMessage());
            throw new RuntimeException("大模型网关调用失败: " + e.getMessage(), e);
        }
    }
}
