package com.quanshiguang.shiguang.count.biz.domain.dataobject;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * @description: MQ 消息消费去重记录(幂等兜底表)
 * @date: 2026/10/13
 * @version: v1.0.0
 **/
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class MqDedupDO {

    /**
     * 自增主键
     */
    private Long id;

    /**
     * 业务幂等键: topic:tag:{业务主体}:{动作}
     * 例: CountFansTopic:Follow:10001:10002:FOLLOW
     */
    private String bizKey;

    /**
     * RocketMQ msgId(辅助排查,非幂等依据)
     */
    private String msgId;

    /**
     * 记录时间(按时间清理过期记录)
     */
    private LocalDateTime createTime;
}
