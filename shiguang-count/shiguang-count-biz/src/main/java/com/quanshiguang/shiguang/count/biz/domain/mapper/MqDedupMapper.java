package com.quanshiguang.shiguang.count.biz.domain.mapper;

import com.quanshiguang.shiguang.count.biz.domain.dataobject.MqDedupDO;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;

/**
 * @description: MQ 消息去重表 Mapper
 * @date: 2026/10/13
 * @version: v1.0.0
 **/
@Mapper
public interface MqDedupMapper {

    /**
     * 插入去重记录; 依赖 t_mq_dedup.biz_key 唯一索引:
     * 插入冲突(重复消费)抛 DuplicateKeyException,由调用方捕获判定
     */
    @Insert("INSERT INTO t_mq_dedup (biz_key, msg_id, create_time) " +
            "VALUES (#{bizKey}, #{msgId}, #{createTime})")
    int insert(MqDedupDO mqDedupDO);
}
