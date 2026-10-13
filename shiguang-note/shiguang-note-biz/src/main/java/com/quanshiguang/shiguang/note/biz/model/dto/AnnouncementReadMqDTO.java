package com.quanshiguang.shiguang.note.biz.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * @description: 公告已读确认 MQ 消息体(已读回执子域)
 * @date: 2026/10/13
 * @version: v1.0.0
 **/
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class AnnouncementReadMqDTO {

    /**
     * 公告 ID
     */
    private Long announcementId;

    /**
     * 确认已读的用户 ID
     */
    private Long userId;

    /**
     * 确认时间
     */
    private LocalDateTime createTime;
}
