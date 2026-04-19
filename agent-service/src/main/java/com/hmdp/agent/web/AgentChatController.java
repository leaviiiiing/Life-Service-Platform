package com.hmdp.agent.web;

import com.hmdp.agent.service.AgentChatService;
import lombok.Data;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import java.util.HashMap;
import java.util.Map;

/**
 * 运维助手对话：关键词规则优先，未命中可走 LLM；会话存 Redis。
 */
@RestController
@RequestMapping("/api/agent")
public class AgentChatController {

    @Resource
    private AgentChatService agentChatService;

    /**
     * 多轮对话；响应含 source：rule（规则命中）| llm | fallback（默认文案或 LLM 失败）
     */
    @PostMapping("/chat")
    public Map<String, Object> chat(@RequestBody ChatRequest req) {
        AgentChatService.ChatOutcome o = agentChatService.chat(req.getSessionId(), req.getText());
        Map<String, Object> out = new HashMap<>(10);
        out.put("success", true);
        out.put("sessionId", o.getSessionId());
        out.put("ruleId", o.getRuleId());
        out.put("reply", o.getReply());
        out.put("matchedKeyword", o.getMatchedKeyword());
        out.put("source", o.getSource());
        return out;
    }

    @Data
    public static class ChatRequest {
        /** 可选；不传则服务端生成 UUID 并在响应里带回 */
        private String sessionId;
        /** 用户输入 */
        private String text;
    }
}
