package com.hmdp.agent.web;

import com.hmdp.agent.service.AgentSessionService;
import com.hmdp.agent.service.FaqRuleService;
import lombok.Data;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import java.util.HashMap;
import java.util.Map;

/**
 * 故障规则库对话 + Redis 会话（与计划「RAG/记忆」最小可用版）
 */
@RestController
@RequestMapping("/api/agent")
public class AgentChatController {

    @Resource
    private FaqRuleService faqRuleService;

    @Resource
    private AgentSessionService agentSessionService;

    @PostMapping("/chat")
    public Map<String, Object> chat(@RequestBody ChatRequest req) {
        // 先落会话 id，再规则匹配，最后把本轮问答写入 Redis 便于后续扩展「上下文」
        String sid = agentSessionService.ensureSessionId(req.getSessionId());
        FaqRuleService.Match m = faqRuleService.match(req.getText());
        agentSessionService.append(sid, req.getText(), m.reply);
        Map<String, Object> out = new HashMap<>(8);
        out.put("success", true);
        out.put("sessionId", sid);
        out.put("ruleId", m.ruleId);
        out.put("reply", m.reply);
        out.put("matchedKeyword", m.keyword);
        return out;
    }

    @Data
    public static class ChatRequest {
        /** 可选；不传则服务端生成并在响应里带回 */
        private String sessionId;
        private String text;
    }
}
