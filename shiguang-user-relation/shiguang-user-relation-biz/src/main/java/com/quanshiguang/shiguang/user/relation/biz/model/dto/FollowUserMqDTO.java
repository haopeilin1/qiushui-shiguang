package com.quanshiguang.shiguang.user.relation.biz.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * @author: 犬小哈
 * @date: 2024/4/7 15:17
 * @version: v1.0.0
 * @description: 关注用户
 **/
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class FollowUserMqDTO {

    private Long userId;

    private Long followUserId;

    private LocalDateTime createTime;

    /**
     * 业务事件 ID: 关注动作发生时生成,随消息体流转,
     * 供下游计数消费者构造幂等键(重投/重放不变,新动作必定不同)
     */
    private String eventId;
}
