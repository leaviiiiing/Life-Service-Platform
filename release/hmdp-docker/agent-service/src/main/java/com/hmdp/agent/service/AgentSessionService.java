package com.hmdp.agent.service;

import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import lombok.Data;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * 会话：最近若干轮问答落在 Redis，供排障上下文（轻量「记忆」）
 */
@Service
public class AgentSessionService {

    private static final String PREFIX = "agent:session:";
    private static final int MAX_TURNS = 20;
    private static final int TTL_HOURS = 24;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    public String ensureSessionId(String sessionId) {
        if (sessionId != null && !sessionId.trim().isEmpty()) {
            return sessionId.trim();
        }
        return UUID.randomUUID().toString().replace("-", "");
    }

    public void append(String sessionId, String userText, String agentReply) {
        String key = PREFIX + sessionId;
        JSONObject root = readRoot(key);
        JSONArray turns = root.getJSONArray("turns");
        if (turns == null) {
            turns = new JSONArray();
            root.set("turns", turns);
        }
        JSONObject t = new JSONObject();
        t.set("user", userText);
        t.set("agent", agentReply);
        turns.add(t);
        while (turns.size() > MAX_TURNS) {
            turns.remove(0);
        }
        stringRedisTemplate.opsForValue().set(key, root.toString(), TTL_HOURS, TimeUnit.HOURS);
    }

    public List<TurnView> recentTurns(String sessionId) {
        String key = PREFIX + sessionId;
        JSONObject root = readRoot(key);
        JSONArray turns = root.getJSONArray("turns");
        List<TurnView> list = new ArrayList<>();
        if (turns == null) {
            return list;
        }
        for (int i = 0; i < turns.size(); i++) {
            JSONObject o = turns.getJSONObject(i);
            TurnView v = new TurnView();
            v.setUser(o.getStr("user"));
            v.setAgent(o.getStr("agent"));
            list.add(v);
        }
        return list;
    }

    private JSONObject readRoot(String key) {
        String s = stringRedisTemplate.opsForValue().get(key);
        if (s == null || s.isEmpty()) {
            return new JSONObject();
        }
        return JSONUtil.parseObj(s);
    }

    @Data
    public static class TurnView {
        private String user;
        private String agent;
    }
}
