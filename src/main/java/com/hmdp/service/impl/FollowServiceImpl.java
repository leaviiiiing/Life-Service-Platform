package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.conditions.query.QueryChainWrapper;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.Follow;
import com.hmdp.mapper.FollowMapper;
import com.hmdp.service.IFollowService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.service.IUserService;
import com.hmdp.utils.UserHolder;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;


@Service
public class FollowServiceImpl extends ServiceImpl<FollowMapper, Follow> implements IFollowService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;
    
    @Resource
    private IUserService userService;

    @Transactional
    @Override
    public Result follow(Long followUserId, Boolean isFollow) {
        Long userId = UserHolder.getUser().getId();
        String key = "follows:" + userId;
        //判断关注还是取关
        if(isFollow){
            //关注，新增数据
            boolean isFollowd = isFollow(userId, followUserId);
            if(isFollowd){
                return Result.fail("已关注！");
            }
            Follow follow = new Follow();
            follow.setUserId(userId);
            follow.setFollowUserId(followUserId);
            try {
                boolean isSaved = save(follow);
                if(isSaved){
                    stringRedisTemplate.opsForSet().add(key,followUserId.toString());
                }else {
                    return Result.fail("关注失败！");
                }
            } catch (Exception e) {
                return Result.fail("您已经关注过该用户了");
            }
        }else {
            //取关，删除数据
            boolean isFollowd = isFollow(userId, followUserId);
            if(!isFollowd){
                return Result.fail("未关注！");
            }
            boolean isRemoved = lambdaUpdate()
                    .eq(Follow::getUserId, userId)
                    .eq(Follow::getFollowUserId, followUserId)
                    .remove();
            if(isRemoved){
                stringRedisTemplate.opsForSet().remove(key,followUserId.toString());
            }else {
                return Result.fail("取关失败！");
            }
        }

        return Result.ok();
    }


    @Override
    //public Result isFollow
    public Result followOrNot(Long followUserId) {
        Long userId = UserHolder.getUser().getId();
        boolean isFollowed = isFollow(userId,followUserId);
        if(isFollowed){
            return Result.ok(true);
        }else {
       return Result.ok(false);
        }
    }

    @Override
    public Result followCommons(Long id) {
        Long userId = UserHolder.getUser().getId();
        String key1 = "follows:" + userId;
        String key2 = "follows:" + id;
        //求交集
        Set<String> intersect = stringRedisTemplate.opsForSet().intersect(key1, key2);
        if(intersect == null || intersect.isEmpty()){
            return Result.ok(Collections.emptyList());
        }
        //解析id
        List<Long> ids = intersect.stream().map(Long::valueOf).collect(Collectors.toList());
        //查询用户
        List<UserDTO> userDTOs = userService.listByIds(ids)
                .stream()
                .map(user -> BeanUtil.copyProperties(user, UserDTO.class)).collect(Collectors.toList());

        return Result.ok(userDTOs);
    }

    private boolean isFollow(Long userId,Long followUserId) {
        String key = "follows:" + userId;
        Boolean isFollowed = stringRedisTemplate.opsForSet().isMember(key, followUserId.toString());
        return Boolean.TRUE.equals(isFollowed);
    }
}
