package com.hmdp.agent.service;

import cn.hutool.core.io.IoUtil;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * 静态 FAQ 规则库：从 classpath {@code agent/faq-rules.json} 加载，按关键词子串匹配（先注册先命中）。
 * <p>
 * 后续可替换为向量检索 / RAG，对外仍通过 {@link #match(String)} 返回 {@link Match}，上层无感。
 */
@Slf4j
@Service
public class FaqRuleService {

    private final List<Rule> rules = new ArrayList<>();

    /**
     * 启动时加载 JSON；失败则日志报错，运行时仅走 {@code default} 提示。
     */
    @PostConstruct
    public void load() {
        try {
            ClassPathResource res = new ClassPathResource("agent/faq-rules.json");
            String json = IoUtil.read(res.getInputStream(), StandardCharsets.UTF_8);
            JSONObject root = JSONUtil.parseObj(json);
            JSONArray arr = root.getJSONArray("rules");
            if (arr == null) {
                return;
            }
            for (int i = 0; i < arr.size(); i++) {
                JSONObject o = arr.getJSONObject(i);
                Rule r = new Rule();
                r.id = o.getStr("id");
                r.hint = o.getStr("hint");
                JSONArray kw = o.getJSONArray("keywords");
                if (kw != null) {
                    for (int j = 0; j < kw.size(); j++) {
                        r.keywords.add(kw.getStr(j));
                    }
                }
                rules.add(r);
            }
        } catch (Exception e) {
            log.error("加载 agent/faq-rules.json 失败", e);
        }
    }

    /**
     * 对用户输入做关键词匹配；空输入与未命中均返回 {@code ruleId = default}，由 {@link AgentChatService} 决定是否再走 LLM。
     */
    public Match match(String text) {
        if (text == null || text.trim().isEmpty()) {
            return new Match("default", "请描述现象，例如：死信、PENDING、重放、幂等。", null);
        }
        String lower = text.toLowerCase();
        // 关键词包含即命中，先注册先匹配
        for (Rule r : rules) {
            for (String k : r.keywords) {
                if (k != null && (text.contains(k) || lower.contains(k.toLowerCase()))) {
                    return new Match(r.id, r.hint, k);
                }
            }
        }
        return new Match("default", "未命中规则：可查看 tb_mq_kafka_log、Kafka Topic voucher.order.dlt，或经网关调用 GET /api/agent/reliability/failed-logs。", null);
    }

    private static class Rule {
        String id;
        /** 命中后返回给前端的说明文案 */
        String hint;
        List<String> keywords = new ArrayList<>();
    }

    /** 单次匹配结果：{@code ruleId} 为 default 表示未命中自定义规则 */
    public static class Match {
        public final String ruleId;
        public final String reply;
        /** 命中的关键词，未命中为 null */
        public final String keyword;

        public Match(String ruleId, String reply, String keyword) {
            this.ruleId = ruleId;
            this.reply = reply;
            this.keyword = keyword;
        }
    }
}
