package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hmdp.config.KafkaConfig;
import com.hmdp.dto.BlogFeedMessage;
import com.hmdp.dto.Result;
import com.hmdp.dto.ScrollResult;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.Blog;
import com.hmdp.entity.User;
import com.hmdp.mapper.BlogMapper;
import com.hmdp.service.IBlogService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.service.IUserService;
import com.hmdp.utils.SystemConstants;
import com.hmdp.utils.UserHolder;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.*;
import java.util.stream.Collectors;

import static com.hmdp.utils.RedisConstants.BLOG_LIKED_KEY;
import static com.hmdp.utils.RedisConstants.FEED_KEY;


@Service
public class BlogServiceImpl extends ServiceImpl<BlogMapper, Blog> implements IBlogService {

    @Resource
    private IUserService userService;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private KafkaTemplate<String, BlogFeedMessage> kafkaTemplate;

    @Override
    public Result saveBlog(Blog blog) {
        // 获取登录用户
        UserDTO user = UserHolder.getUser();
        blog.setUserId(user.getId());
        // 保存探店笔记
        boolean isSaved = save(blog);
        if (!isSaved) {
            return Result.fail("新增笔记失败！");
        }
        //查询关注的粉丝
        //推送笔记id给粉丝
        kafkaTemplate.send(
                KafkaConfig.BLOG_FEED_TOPIC,
                blog.getId().toString(),
                new BlogFeedMessage(blog.getId(), user.getId(), System.currentTimeMillis())
        );
        // 返回id
        return Result.ok(blog.getId());
    }

    @Override
    public Result queryBlogById(Long id) {
        //查询blog
        Blog blog = getById(id);
        if(blog==null){
            return Result.fail("Blog不存在！");
        }
        //查询相关用户
        queryBlogUser(blog);
        //查询是否点赞
        isBlogLiked(blog);
        return Result.ok(blog);
    }

    @Override
    public Result queryHotBlog(Integer current) {
        // 根据用户查询
        Page<Blog> page = query()
                .orderByDesc("liked")
                .page(new Page<>(current, SystemConstants.MAX_PAGE_SIZE));
        // 获取当前页数据
        List<Blog> records = page.getRecords();
        if (records == null || records.isEmpty()) {
            return Result.ok(Collections.emptyList());
        }

        Set<Long> userIds = records.stream()
                .map(Blog::getUserId)
                .collect(Collectors.toSet());
        // 查询用户
        List<User> users = userService.listByIds(userIds);

        // 将用户列表转为 Map
        Map<Long, User> userMap = users.stream()
                .collect(Collectors.toMap(User::getId, u -> u));

        records.forEach(blog -> {
            this.isBlogLiked(blog);
            User user = userMap.get(blog.getUserId());
            if (user != null) {
                blog.setName(user.getNickName());
                blog.setIcon(user.getIcon());
            }
        });

        return Result.ok(records);
    }

    @Override
    public Result likeBlog(Long id) {

        //获取当前用户
        Long userId = UserHolder.getUser().getId();
        //判断当前用户是否点赞
        String key = BLOG_LIKED_KEY + id;
        Double score = stringRedisTemplate.opsForZSet().score(key, userId.toString());
        if(score==null){
            //未点赞，数据库点赞+1，保存用户到set集合
            boolean isSuccess = update().setSql("liked = liked + 1").eq("id", id).update();
            if(isSuccess){
                stringRedisTemplate.opsForZSet().add(key, userId.toString(), System.currentTimeMillis());
            }
        }else {
            boolean isSuccess = update().setSql("liked = liked - 1").eq("id", id).update();
            if(isSuccess){
                stringRedisTemplate.opsForZSet().remove(key, userId.toString());
            }
        }
        return Result.ok();
    }

    @Override
    public Result queryBlogLikes(Long id) {
        String key = BLOG_LIKED_KEY + id;
        //分析最近5个点赞用户
        Set<String> top5 = stringRedisTemplate.opsForZSet().range(key, 0, 4);
        //获取用户id
        if(top5==null|| top5.isEmpty()){
            return Result.ok(Collections.emptyList());
        }
        List<Long> userIds = top5.stream().map(Long::valueOf).collect(Collectors.toList());
        String strIds = StrUtil.join(",", userIds);
        //获取用户信息
        List<UserDTO> userDTOs = userService
                .query().in("id",userIds).last("ORDER BY FIELD(id,"+strIds+")").list()
                .stream().map(user -> BeanUtil.copyProperties(user,UserDTO.class)).collect(Collectors.toList());

        return Result.ok(userDTOs);
    }

    @Override
    public Result queryBlogOfFollow(Long max, Integer offset) {

        //查询用户id
        Long userId = UserHolder.getUser().getId();
        String key = FEED_KEY + userId;
        //查询收件箱
        Set<ZSetOperations.TypedTuple<String>> typedTuples = stringRedisTemplate.opsForZSet()
                .reverseRangeByScoreWithScores(key, 0, max, offset, 4);
        //解析数据 blogId 时间戳(miniTime) offset(上次查询末尾相同score数)
        if(typedTuples==null||typedTuples.isEmpty()){
            return Result.ok();
        }
        List<Long> blogIds = new ArrayList<>(typedTuples.size());
        long minTime = 0;
        int os =1;
        //计算os
        for (ZSetOperations.TypedTuple<String> typedTuple : typedTuples) {
            String idStr = typedTuple.getValue();
            if (idStr != null) {
                blogIds.add(Long.valueOf(idStr));
            }
            Double score = typedTuple.getScore();
            long time = (score == null) ? 0L : score.longValue();
            if(time==minTime){
                os++;
            }else {
                minTime = time;
                os=1;
            }

        }
        //查询blog
        String idStr = StrUtil.join(",", blogIds);
        List<Blog> blogs = query()
                .in("id", blogIds).last("ORDER BY FIELD(id," + idStr + ")").list();
        for (Blog blog : blogs) {
            //查询相关用户
            queryBlogUser(blog);
            //查询是否点赞
            isBlogLiked(blog);
        }
        //返回
        ScrollResult r=new ScrollResult();
        r.setList(blogs);
        r.setOffset(os);
        r.setMinTime(minTime);
        return Result.ok(r);
    }

    private void queryBlogUser(Blog blog) {
        Long userId = blog.getUserId();
        User user = userService.getById(userId);
        if (user != null) {
            blog.setName(user.getNickName());
            blog.setIcon(user.getIcon());

        }else {
            log.warn("Blog用户错误！");
        }
    }

    private void isBlogLiked(Blog blog) {
        UserDTO user = UserHolder.getUser();
        if (user == null) {
            //用户未登录
            return;
        }
        //获取当前用户
        Long userId = user.getId();
        //判断当前用户是否点赞
        String key = BLOG_LIKED_KEY + blog.getId();
        Double score = stringRedisTemplate.opsForZSet().score(key, userId.toString());
        blog.setIsLike(score!=null);
    }


}
