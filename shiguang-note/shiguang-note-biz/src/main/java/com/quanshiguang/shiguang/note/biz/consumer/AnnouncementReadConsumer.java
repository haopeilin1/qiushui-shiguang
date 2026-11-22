package com.quanshiguang.shiguang.note.biz.consumer;

import cn.hutool.core.util.IdUtil;
import com.quanshiguang.framework.common.util.JsonUtils;
import com.quanshiguang.shiguang.note.biz.constant.RedisKeyConstants;
import com.quanshiguang.shiguang.note.biz.domain.dataobject.AnnouncementReadDO;
import com.quanshiguang.shiguang.note.biz.domain.mapper.AnnouncementReadDOMapper;
import com.quanshiguang.shiguang.note.biz.model.dto.AnnouncementReadMqDTO;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Objects;

/**
 * @description: 公告已读确认 MQ 消费者(已读回执子域)
 *
 * 三层幂等设计:
 *   第一层(入口): Service 层 Redis SADD 原子判重,拦截绝大多数重复
 *   第二层(本消费): t_announcement_read(announcement_id, user_id) 唯一索引,
 *                   拦截 Redis 判重失效(Redis 数据丢失后的业务重放)场景
 *   第三层(兜底): readStat 回源 DB count(*),保证统计读数最终正确
 *
 * @date: 2026/10/13
 * @version: v1.0.0
 **/
@Component
@RocketMQMessageListener(consumerGroup = "shiguang_group_" + com.quanshiguang.shiguang.note.biz.constant.MQConstants.TOPIC_ANNOUNCEMENT_READ, // Group 组
        topic = com.quanshiguang.shiguang.note.biz.constant.MQConstants.TOPIC_ANNOUNCEMENT_READ // 消费的主题 Topic
)
@Slf4j
public class AnnouncementReadConsumer implements RocketMQListener<String> {

    @Resource
    private AnnouncementReadDOMapper announcementReadDOMapper;
    @Resource
    private RedisTemplate<String, Object> redisTemplate;

    @Override
    public void onMessage(String body) {
        log.info("==> AnnouncementReadConsumer 消费消息: {}", body);

        AnnouncementReadMqDTO dto = JsonUtils.parseObject(body, AnnouncementReadMqDTO.class);
        if (Objects.isNull(dto) || Objects.isNull(dto.getAnnouncementId())
                || Objects.isNull(dto.getUserId())) {
            log.error("==> 已读确认消息体非法,丢弃: {}", body);
            return;
        }

        AnnouncementReadDO readDO = AnnouncementReadDO.builder()
                // 雪花 ID: 原实现用 System.currentTimeMillis(), 同一毫秒多个用户确认会主键冲突,
                // 冲突被当成"重复消费"吞掉 → 回执丢失、计数不加
                .id(IdUtil.getSnowflakeNextId())
                .announcementId(dto.getAnnouncementId())
                .userId(dto.getUserId())
                .createTime(Objects.nonNull(dto.getCreateTime()) ? dto.getCreateTime() : LocalDateTime.now())
                .build();

        try {
            int count = announcementReadDOMapper.insert(readDO);
            if (count == 0) {
                log.warn("==> 已读回执插入 0 行,跳过计数: {}", body);
                return;
            }
        } catch (DuplicateKeyException e) {
            // 第二层幂等命中: 唯一索引拦截重复消费
            // (正常情况下第一层 Redis SADD 已拦截,此处拦截 Redis 失效后的重放)
            log.info("==> [幂等] 已读回执唯一索引拦截重复消费, announcementId: {}, userId: {}",
                    dto.getAnnouncementId(), dto.getUserId());
            return;
        }

        // 更新 Redis 已读计数 Hash: announcement:read:count:{announcementId} readTotal +1
        // (统计路径直接读 Redis,不回源 DB)
        String countKey = RedisKeyConstants.buildAnnouncementReadCountKey(dto.getAnnouncementId());
        try {
            if (Boolean.TRUE.equals(redisTemplate.hasKey(countKey))) {
                // hash value 走 Jackson 序列化(带引号),HINCRBY 不可用,改为读改写
                Object cur = redisTemplate.opsForHash().get(countKey, "readTotal");
                long next = (Objects.nonNull(cur) ? Long.parseLong(cur.toString()) : 0L) + 1;
                redisTemplate.opsForHash().put(countKey, "readTotal", String.valueOf(next));
            } else {
                // 计数结构不存在时初始化(以 DB 实际值+本次增量为基准,防覆盖)
                Long dbTotal = announcementReadDOMapper.countByAnnouncementId(dto.getAnnouncementId());
                redisTemplate.opsForHash().put(countKey, "readTotal", dbTotal.toString());
            }
            // 短 TTL 防脏计数长期驻留: 过期后 readStat 回源 DB count(*) 重建
            redisTemplate.expire(countKey, Duration.ofMinutes(10));
        } catch (Exception e) {
            // 计数更新失败不影响回执落库;readStat 有 DB 回源兜底
            log.error("==> 已读计数更新失败(统计读数将由 DB 回源兜底): ", e);
        }
    }
}
