package com.hmdp.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hmdp.entity.MqKafkaLog;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface MqKafkaLogMapper extends BaseMapper<MqKafkaLog> {
}
