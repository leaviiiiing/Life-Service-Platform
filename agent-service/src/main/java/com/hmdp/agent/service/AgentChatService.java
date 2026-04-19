package com.hmdp.agent.service;

import lombok.Data;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.List;

/**
 * 对话编排：{@link FaqRuleService} 关键词优先；未命中再尝试 {@link LlmChatService}；最后写入 {@link AgentSessionService}。
 */
@Service
public class AgentChatService {

    @Resource
    private FaqRuleService faqRuleService;

    @Resource
    private AgentSessionService agentSessionService;

    @Resource
    private LlmChatService llmChatService;

    /**
     * 一轮问答：先取历史（不含本轮），再匹配规则或 LLM，最后追加会话。
     */
    public ChatOutcome chat(String sessionId, String text) {
        String sid = agentSessionService.ensureSessionId(sessionId);
        List<AgentSessionService.TurnView> history = agentSessionService.recentTurns(sid);

        FaqRuleService.Match m = faqRuleService.match(text);
        String reply;
        String source;

        if (!"default".equals(m.ruleId)) {
            // 关键词命中 FAQ
            reply = m.reply;
            source = "rule";
        } else if (llmChatService.isAvailable()) {
            String llmReply = llmChatService.chat(history, text);
            if (llmReply != null && !llmReply.isEmpty()) {
                reply = llmReply;
                source = "llm";
            } else {
                // LLM 调用失败或空内容，退回默认提示
                reply = m.reply;
                source = "fallback";
            }
        } else {
            reply = m.reply;
            source = "fallback";
        }

        agentSessionService.append(sid, text, reply);

        ChatOutcome out = new ChatOutcome();
        out.setSessionId(sid);
        out.setRuleId(m.ruleId);
        out.setMatchedKeyword(m.keyword);
        out.setReply(reply);
        out.setSource(source);
        return out;
    }

    @Data
    public static class ChatOutcome {
        private String sessionId;
        /** 规则 id，未命中为 default */
        private String ruleId;
        private String matchedKeyword;
        private String reply;
        /** rule：规则；llm：大模型；fallback：默认文案或 LLM 不可用 */
        private String source;
    }
}
