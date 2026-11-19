package com.quanshiguang.shiguang.count.biz.service;

import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.rocketmq.common.message.Message;
import org.apache.rocketmq.common.message.MessageConst;
import org.springframework.stereotype.Component;

/**
 * @description: MQ 消费幂等统一入口(13 个计数消费者共用)
 *
 * 两类幂等键:
 * 1. 业务事件级: 优先用上游生成的 eventId, 缺失时退化为业务键(主体+动作)。
 *    - eventId 对"重投 / Rebalance / 业务重放"稳定;
 *    - "取关后重新关注""取消后再点赞"会生成新的 eventId, 不会被误拦。
 * 2. 消息级: RocketMQ 的 UNIQ_KEY(客户端发送时生成, 重投时随消息属性保留)。
 *    用于聚合类消息(CountXxx2DB)和批量评论消息 —— 它们没有单一业务主体 ID,
 *    但同一条消息重投时 UNIQ_KEY 不变, 足以挡住 at-least-once 的重复投递。
 *
 * 注意: 任何一层键为空时直接放行, 宁可重复消费(有对账兜底)也不阻断整条链路。
 *
 * @date: 2026/11/19
 * @version: v1.0.0
 **/
@Component
@Slf4j
public class MqDedupSupport {

    @Resource
    private DedupService dedupService;

    /**
     * 业务事件级判重
     *
     * @param topic          当前消费的 Topic
     * @param eventId        上游业务事件 ID, 可为空
     * @param fallbackBizKey 业务键(如 userId:targetId:ACTION), eventId 为空时使用
     * @return true=重复消息, 应直接 return; false=首次消息, 继续处理
     */
    public boolean isDuplicateByEvent(String topic, String eventId, String fallbackBizKey) {
        String suffix = StringUtils.isNotBlank(eventId) ? eventId : fallbackBizKey;
        if (StringUtils.isBlank(suffix)) {
            return false;
        }
        return dedupService.isDuplicate(topic + ":" + suffix, null);
    }

    /**
     * 消息级判重: 同一条 MQ 消息重投时 UNIQ_KEY 不变
     *
     * 键里带消费者身份(类名): 同一个 Topic 可能被多个消费组订阅(例如
     * CountNoteCommentConsumer 与 CountNoteChildCommentConsumer),
     * 若不区分消费者, 先消费的一方会把另一方误判为重复消息。
     *
     * @param consumer 当前消费者实例(传 this 即可)
     * @param message  RocketMQ 原始消息
     * @return true=重复消息, 应直接 return; false=首次消息, 继续处理
     */
    public boolean isDuplicateByMessage(Object consumer, Message message) {
        String uniqKey = message.getProperty(MessageConst.PROPERTY_UNIQ_CLIENT_MESSAGE_ID_KEYIDX);
        if (StringUtils.isBlank(uniqKey)) {
            // 极少数消息没有 UNIQ_KEY, 不阻断消费
            log.warn("==> [幂等] 消息缺少 UNIQ_KEY, 跳过消息级判重, consumer: {}",
                    consumer.getClass().getSimpleName());
            return false;
        }
        String bizKey = consumer.getClass().getSimpleName() + ":msg:" + uniqKey;
        return dedupService.isDuplicate(bizKey, uniqKey);
    }
}
