package com.hmdp.agent.service;

import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpResponse;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.hmdp.agent.config.AgentProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.List;

/**
 * OpenAI 兼容 Chat Completions（{@code POST .../chat/completions}）。
 * <p>
 * 在 {@link FaqRuleService} 未命中关键词时作为兜底；开关、地址、密钥等见 {@link AgentProperties.Llm}（环境变量 {@code AGENT_LLM_*}）。
 */
@Slf4j
@Service
public class LlmChatService {

    /** 系统提示：角色与运维场景边界，避免危险操作建议 */
    private static final String SYSTEM_PROMPT = ""
            + "你是「消费社交生活服务平台」的运维助手，熟悉 Kafka、Redis 秒杀、MySQL 订单与 MQ 补偿（/mq/compensation/kafka）。"
            + "用简洁中文回答；涉及危险操作（重投、删数据）时提醒用户在内网或经网关鉴权后操作。";

    @Resource
    private AgentProperties agentProperties;

    /**
     * 是否启用 LLM：需开启开关且配置了 apiKey、model。
     */
    public boolean isAvailable() {
        AgentProperties.Llm llm = agentProperties.getLlm();
        String model = llm.getModel();
        return llm.isEnabled()
                && llm.getApiKey() != null && !llm.getApiKey().trim().isEmpty()
                && model != null && !model.trim().isEmpty();
    }

    /**
     * 组装 messages（system + 历史多轮 + 本轮 user），请求远端并解析第一条 choice 的 content。
     *
     * @param history 不含本轮用户输入（由 {@code text} 单独传入）
     * @return 助手正文；HTTP 非 2xx、解析失败或异常时返回 {@code null}，由上层走 fallback
     */
    public String chat(List<AgentSessionService.TurnView> history, String text) {
        AgentProperties.Llm llm = agentProperties.getLlm();
        JSONArray messages = new JSONArray();
        JSONObject sys = new JSONObject();
        sys.set("role", "system");
        sys.set("content", SYSTEM_PROMPT);
        messages.add(sys);
        if (history != null) {
            // 按时间顺序还原 user/assistant 交替
            for (AgentSessionService.TurnView t : history) {
                if (t.getUser() != null) {
                    JSONObject u = new JSONObject();
                    u.set("role", "user");
                    u.set("content", t.getUser());
                    messages.add(u);
                }
                if (t.getAgent() != null) {
                    JSONObject a = new JSONObject();
                    a.set("role", "assistant");
                    a.set("content", t.getAgent());
                    messages.add(a);
                }
            }
        }
        JSONObject userMsg = new JSONObject();
        userMsg.set("role", "user");
        userMsg.set("content", text == null ? "" : text);
        messages.add(userMsg);

        JSONObject body = new JSONObject();
        body.set("model", llm.getModel().trim());
        body.set("messages", messages);
        body.set("temperature", llm.getTemperature());

        // baseUrl 可为网关根路径，此处补全为 /chat/completions
        String url = resolveChatUrl(llm.getBaseUrl());
        try {
            HttpResponse resp = HttpRequest.post(url)
                    .header("Authorization", "Bearer " + llm.getApiKey().trim())
                    .header("Content-Type", "application/json")
                    .body(body.toString())
                    .timeout(llm.getTimeoutMs())
                    .execute();
            if (!resp.isOk()) {
                log.warn("LLM HTTP {} body={}", resp.getStatus(), truncate(resp.body(), 500));
                return null;
            }
            JSONObject json = JSONUtil.parseObj(resp.body());
            JSONArray choices = json.getJSONArray("choices");
            if (choices == null || choices.isEmpty()) {
                log.warn("LLM 响应无 choices: {}", truncate(resp.body(), 300));
                return null;
            }
            JSONObject msg = choices.getJSONObject(0).getJSONObject("message");
            if (msg == null) {
                return null;
            }
            String content = msg.getStr("content");
            return content != null ? content.trim() : null;
        } catch (Exception e) {
            log.error("LLM 调用失败", e);
            return null;
        }
    }

    /**
     * 将配置的 baseUrl 规范为完整 Chat Completions 地址；空则使用内置默认网关根（可按环境覆盖）。
     */
    private static String resolveChatUrl(String baseUrl) {
        String b = baseUrl == null ? "" : baseUrl.trim();
        if (b.isEmpty()) {
            b = "https://ark.cn-beijing.volces.com/api/v3";
        }
        if (b.endsWith("/chat/completions")) {
            return b;
        }
        if (b.endsWith("/")) {
            return b + "chat/completions";
        }
        return b + "/chat/completions";
    }

    private static String truncate(String s, int max) {
        if (s == null) {
            return "";
        }
        return s.length() <= max ? s : s.substring(0, max) + "...";
    }
}
