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
 * 静态规则库（关键词命中），后续可替换为向量检索/RAG，接口保持不变
 */
@Slf4j
@Service
public class FaqRuleService {

    private final List<Rule> rules = new ArrayList<>();

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

    public Match match(String text) {
        if (text == null || text.trim().isEmpty()) {
            return new Match("default", "请描述现象，例如：死信、PENDING、重放、幂等。", null);
        }
        String lower = text.toLowerCase();
        for (Rule r : rules) {
            for (String k : r.keywords) {
                if (k != null && (text.contains(k) || lower.contains(k.toLowerCase()))) {
                    return new Match(r.id, r.hint, k);
                }
            }
        }
        return new Match("default", "未命中规则：可查看 tb_mq_message_log、Rabbit 管理台 DLQ，或调用 /api/agent/reliability/check。", null);
    }

    private static class Rule {
        String id;
        String hint;
        List<String> keywords = new ArrayList<>();
    }

    public static class Match {
        public final String ruleId;
        public final String reply;
        public final String keyword;

        public Match(String ruleId, String reply, String keyword) {
            this.ruleId = ruleId;
            this.reply = reply;
            this.keyword = keyword;
        }
    }
}
