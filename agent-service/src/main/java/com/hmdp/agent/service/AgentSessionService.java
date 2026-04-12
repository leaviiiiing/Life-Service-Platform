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

    /** Redis 键前缀，与 sessionId 拼接 */
    private static final String PREFIX = "agent:session:";
    /** 单会话最多保留轮数，超出则丢弃最早一轮 */
    private static final int MAX_TURNS = 20;
    /** 无续期时会话数据过期时间 */
    private static final int TTL_HOURS = 24;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    public String ensureSessionId(String sessionId) {
        // 前端带上的 sessionId 可延续多轮；否则新建
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
        // 控制单 key 体积，避免无限增长
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
        // 无历史则空对象，由上层补 turns
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
