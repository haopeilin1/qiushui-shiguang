package com.quanshiguang.shiguang.count.biz.consumer;

import com.github.phantomthief.collection.BufferTrigger;
import com.google.common.collect.Maps;
import com.quanshiguang.framework.common.util.JsonUtils;
import com.quanshiguang.shiguang.count.biz.constant.MQConstants;
import com.quanshiguang.shiguang.count.biz.constant.RedisKeyConstants;
import com.quanshiguang.shiguang.count.biz.enums.FollowUnfollowTypeEnum;
import com.quanshiguang.shiguang.count.biz.model.dto.CountFollowUnfollowMqDTO;
import com.quanshiguang.shiguang.count.biz.service.DedupService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.apache.ibatis.annotations.Case;
import org.apache.rocketmq.client.producer.SendCallback;
import org.apache.rocketmq.client.producer.SendResult;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * @author: 犬小哈
 * @date: 2024/8/9 11:52
 * @version: v1.0.0
 * @description: 计数: 粉丝数
 **/
@Component
@RocketMQMessageListener(consumerGroup = "shiguang_group_" + MQConstants.TOPIC_COUNT_FANS, // Group 组
        topic = MQConstants.TOPIC_COUNT_FANS // 主题 Topic
        )
@Slf4j
public class CountFansConsumer implements RocketMQListener<String> {

    @Resource
    private RedisTemplate<String, Object> redisTemplate;
    @Resource
    private RocketMQTemplate rocketMQTemplate;
    @Resource
    private DedupService dedupService;

    private BufferTrigger<String> bufferTrigger = BufferTrigger.<String>batchBlocking()
            .bufferSize(50000) // 缓存队列的最大容量
            .batchSize(1000)   // 一批次最多聚合 1000 条
            .linger(Duration.ofSeconds(1)) // 多久聚合一次
            .setConsumerEx(this::consumeMessage) // 设置消费者方法
            .build();

    @Override
    public void onMessage(String body) {
        // 幂等拦截: 在消息进入聚合缓冲区前完成判重,
        // 重复消息(消费重试/Rebalance 重投/业务重放)不再进入计数链路
        try {
            CountFollowUnfollowMqDTO dto = JsonUtils.parseObject(body, CountFollowUnfollowMqDTO.class);
            if (Objects.nonNull(dto) && Objects.nonNull(dto.getUserId())
                    && Objects.nonNull(dto.getTargetUserId())) {
                // 业务幂等键: topic + 业务事件 ID
                // eventId 随关注/取关动作生成、随消息体流转: 重投/重放不变(可判重),
                // 而"取关后重新关注"是新动作新 eventId(不误拦合法重复操作)
                // 无 eventId 的历史消息退化为 主体+动作 键
                String action = Integer.valueOf(1).equals(dto.getType()) ? "FOLLOW" : "UNFOLLOW";
                String bizKey = MQConstants.TOPIC_COUNT_FANS + ":"
                        + (Objects.nonNull(dto.getEventId()) ? dto.getEventId()
                                : dto.getUserId() + ":" + dto.getTargetUserId() + ":" + action);
                if (dedupService.isDuplicate(bizKey, null)) {
                    // 重复消息,直接确认消费成功(拦截)
                    return;
                }
            }
        } catch (Exception e) {
            // 幂等检查异常不阻断消费(宁可重复消费交给对账修正,不可漏消费)
            log.error("==> [幂等] 判重异常,消息继续处理: {}", body, e);
        }

        // 往 bufferTrigger 中添加元素
        bufferTrigger.enqueue(body);
    }

    private void consumeMessage(List<String> bodys) {
        log.info("==> 聚合消息, size: {}", bodys.size());
        log.info("==> 聚合消息, {}", JsonUtils.toJsonString(bodys));

        // List<String> 转 List<CountFollowUnfollowMqDTO>
        List<CountFollowUnfollowMqDTO> countFollowUnfollowMqDTOS = bodys.stream()
                .map(body -> JsonUtils.parseObject(body, CountFollowUnfollowMqDTO.class)).toList();

        // 按目标用户进行分组
        Map<Long, List<CountFollowUnfollowMqDTO>> groupMap = countFollowUnfollowMqDTOS.stream()
                .collect(Collectors.groupingBy(CountFollowUnfollowMqDTO::getTargetUserId));

        // 按组汇总数据，统计出最终的计数
        // key 为目标用户ID, value 为最终操作的计数
        Map<Long, Integer> countMap = Maps.newHashMap();

        for (Map.Entry<Long, List<CountFollowUnfollowMqDTO>> entry : groupMap.entrySet()) {
            List<CountFollowUnfollowMqDTO> list = entry.getValue();
            // 最终的计数值，默认为 0
            int finalCount = 0;
            for (CountFollowUnfollowMqDTO countFollowUnfollowMqDTO : list) {
                // 获取操作类型
                Integer type = countFollowUnfollowMqDTO.getType();

                // 根据操作类型，获取对应枚举
                FollowUnfollowTypeEnum followUnfollowTypeEnum = FollowUnfollowTypeEnum.valueOf(type);

                // 若枚举为空，跳到下一次循环
                if (Objects.isNull(followUnfollowTypeEnum)) continue;

                switch (followUnfollowTypeEnum) {
                    case FOLLOW -> finalCount += 1; // 如果为关注操作，粉丝数 +1
                    case UNFOLLOW -> finalCount -= 1; // 如果为取关操作，粉丝数 -1
                }
            }
            // 将分组后统计出的最终计数，存入 countMap 中
            countMap.put(entry.getKey(), finalCount);
        }

        log.info("## 聚合后的计数数据: {}", JsonUtils.toJsonString(countMap));

        // 更新 Redis
        countMap.forEach((k, v) -> {
            // Redis Key
            String redisKey = RedisKeyConstants.buildCountUserKey(k);
            // 判断 Redis 中 Hash 是否存在
            boolean isExisted = redisTemplate.hasKey(redisKey);

            // 若存在才会更新
            // (因为缓存设有过期时间，考虑到过期后，缓存会被删除，这里需要判断一下，存在才会去更新，而初始化工作放在查询计数来做)
            if (isExisted) {
                // 对目标用户 Hash 中的粉丝数字段进行计数操作
                redisTemplate.opsForHash().increment(redisKey, RedisKeyConstants.FIELD_FANS_TOTAL, v);
            }
        });

        // 发送 MQ, 计数数据落库
        // 构建消息体 DTO
        Message<String> message = MessageBuilder.withPayload(JsonUtils.toJsonString(countMap))
                .build();

        // 异步发送 MQ 消息
        rocketMQTemplate.asyncSend(MQConstants.TOPIC_COUNT_FANS_2_DB, message, new SendCallback() {
            @Override
            public void onSuccess(SendResult sendResult) {
                log.info("==> 【计数服务：粉丝数入库】MQ 发送成功，SendResult: {}", sendResult);
            }

            @Override
            public void onException(Throwable throwable) {
                log.error("==> 【计数服务：粉丝数入库】MQ 发送异常: ", throwable);
            }
        });
    }
}
