package com.hmdp.agent.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import javax.annotation.Resource;
import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.concurrent.TimeUnit;

/**
 * 简单网关限流：按 IP 滑动窗口计数（与计划「告警/兜底」前置保护一致）
 */
@Slf4j
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 1)
public class AgentRateLimitFilter extends OncePerRequestFilter {

    private static final String PREFIX = "agent:rl:";

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private AgentProperties agentProperties;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        int limit = agentProperties.getRateLimitPerMinute();
        if (limit <= 0 || !request.getRequestURI().startsWith("/api/agent/")) {
            filterChain.doFilter(request, response);
            return;
        }
        String ip = clientIp(request);
        String key = PREFIX + ip;
        Long n = stringRedisTemplate.opsForValue().increment(key);
        if (n != null && n == 1L) {
            stringRedisTemplate.expire(key, 1, TimeUnit.MINUTES);
        }
        if (n != null && n > limit) {
            log.warn("agent 限流触发 ip={} uri={}", ip, request.getRequestURI());
            response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write("{\"success\":false,\"errorMsg\":\"rate limit\"}");
            return;
        }
        filterChain.doFilter(request, response);
    }

    private static String clientIp(HttpServletRequest request) {
        String xff = request.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isEmpty()) {
            int comma = xff.indexOf(',');
            return comma > 0 ? xff.substring(0, comma).trim() : xff.trim();
        }
        return request.getRemoteAddr() == null ? "unknown" : request.getRemoteAddr();
    }
}
