package com.quanshiguang.shiguang.count.biz.service;

/**
 * @description: 消息消费双层幂等服务
 * @date: 2026/10/13
 * @version: v1.0.0
 **/
public interface DedupService {

    /**
     * 判断消息是否为重复消息(双层幂等)
     *
     * 第一层 Redis SETNX(快路径,非持久):
     *   - SET bizKey 1 NX EX 24h,首次消息写入成功返回 false(非重复)
     *   - key 已存在返回 true(重复,拦截)
     *   - 覆盖消费重试/Rebalance 重投/业务重放场景
     *
     * 第二层 MySQL 唯一索引(慢路径,兜底):
     *   - 仅在 Redis 异常(宕机/清空/AOF丢数据)时触发
     *   - INSERT t_mq_dedup,bizKey 唯一索引冲突即重复
     *   - 持久保证,与 Redis 层互补
     *
     * @param bizKey 业务幂等键(topic:tag:主体:动作)
     * @param msgId  RocketMQ msgId(仅记录辅助排查)
     * @return true=重复消息,应拦截; false=首次消息,放行
     */
    boolean isDuplicate(String bizKey, String msgId);
}
