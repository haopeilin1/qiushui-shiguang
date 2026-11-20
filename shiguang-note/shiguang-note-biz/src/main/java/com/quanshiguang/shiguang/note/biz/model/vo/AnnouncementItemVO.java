package com.quanshiguang.shiguang.note.biz.model.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * @description: 公告列表项 VO(已读回执子域)
 * @date: 2026/11/20
 * @version: v1.0.0
 **/
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class AnnouncementItemVO {

    /**
     * 公告 ID
     */
    private Long announcementId;

    /**
     * 公告标题
     */
    private String title;

    /**
     * 发布人用户 ID
     */
    private Long creatorId;

    /**
     * 当前登录用户是否已确认已读
     */
    private Boolean isRead;

    /**
     * 发布时间
     */
    private LocalDateTime createTime;
}
