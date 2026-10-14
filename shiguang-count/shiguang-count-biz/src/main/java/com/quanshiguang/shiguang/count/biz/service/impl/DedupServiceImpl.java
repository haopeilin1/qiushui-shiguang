package com.quanshiguang.shiguang.count.biz.service.impl;

import com.quanshiguang.shiguang.count.biz.domain.dataobject.MqDedupDO;
import com.quanshiguang.shiguang.count.biz.domain.mapper.MqDedupMapper;
import com.quanshiguang.shiguang.count.biz.service.DedupService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;

/**
 * @description: 消息消费双层幂等服务实现
 *
 * 设计说明:
 * 1. 幂等键使用业务键(userId+targetId+action)而非 msgId:
 *    msgId 在生产端重发时会变化,无法防御"同一业务事件的重放";
 *    业务键对业务重放仍然有效。
 * 2. Redis 层 TTL 7 天: 覆盖 RocketMQ 最大消费重试窗口
 *    (16 次重试最长约 4 天半)与 Rebalance 迟到消息。
 * 3. 双层理由: Redis 快但非持久(AOF everysec 可能丢 1 秒数据,
 *    也可能被运维清空),MySQL 唯一索引是最终防线;
 *    正常流量 99% 被 Redis 挡住,DB 只承接 Redis 故障期。
 *
 * @date: 2026/10/13
 * @version: v1.0.0
 **/
@Service
@Slf4j
public class DedupServiceImpl implements DedupService {

    /**
     * 去重键 Redis 前缀
     */
    private static final String DEDUP_KEY_PREFIX = "mq:dedup:";

    /**
     * Redis 层 TTL: 24 小时。RocketMQ 默认 16 次重试总窗口约 4.6 小时
     * (delayLevel 10s→2h 递增),24h 约 5 倍冗余;键带业务事件 ID,
     * 无需靠超长 TTL 兜底,避免键长期驻留
     */
    private static final Duration DEDUP_TTL = Duration.ofHours(24);

    @Resource
    private RedisTemplate<String, Object> redisTemplate;

    @Resource
    private MqDedupMapper mqDedupMapper;

    /**
     * 幂等开关: 支持灰度启用/快速回滚(对照实验的开关变量)
     */
    @Value("${mq.dedup.enabled:true}")
    private boolean dedupEnabled;

    @Override
    public boolean isDuplicate(String bizKey, String msgId) {
        // 开关关闭时直接放行(用于对照实验复现缺陷)
        if (!dedupEnabled) {
            return false;
        }

        // ---------- 第一层: Redis SETNX 快路径 ----------
        String redisKey = DEDUP_KEY_PREFIX + bizKey;
        try {
            Boolean first = redisTemplate.opsForValue()
                    .setIfAbsent(redisKey, "1", DEDUP_TTL);
            // 写入成功=首次消息,放行
            if (Boolean.TRUE.equals(first)) {
                return false;
            }
            // key 已存在=重复消息,拦截
            log.info("==> [幂等] Redis 层拦截重复消息, bizKey: {}, msgId: {}", bizKey, msgId);
            return true;
        } catch (Exception e) {
            // Redis 异常不阻断业务,降级走 DB 兜底层
            log.error("==> [幂等] Redis 层异常,降级 DB 兜底, bizKey: {}", bizKey, e);
        }

        // ---------- 第二层: MySQL 唯一索引兜底 ----------
        try {
            MqDedupDO mqDedupDO = MqDedupDO.builder()
                    .bizKey(bizKey)
                    .msgId(msgId)
                    .createTime(LocalDateTime.now())
                    .build();
            mqDedupMapper.insert(mqDedupDO);
            // 插入成功=首次消息(Redis 数据丢失后由 DB 层接管),放行
            return false;
        } catch (DuplicateKeyException e) {
            // 唯一索引冲突=重复消息,拦截
            log.info("==> [幂等] DB 层拦截重复消息, bizKey: {}, msgId: {}", bizKey, msgId);
            return true;
        }
    }
}
