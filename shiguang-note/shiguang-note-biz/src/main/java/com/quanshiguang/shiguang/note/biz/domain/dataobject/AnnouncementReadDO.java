package com.quanshiguang.shiguang.note.biz.domain.dataobject;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * @description: 公告已读回执(已读确认记录)
 * @date: 2026/10/13
 * @version: v1.0.0
 **/
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class AnnouncementReadDO {

    private Long id;

    /**
     * 公告 ID
     */
    private Long announcementId;

    /**
     * 确认已读的用户 ID
     */
    private Long userId;

    private LocalDateTime createTime;
}
