package com.quanshiguang.shiguang.note.biz.model.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * @description: 公告详情响应 VO(已读回执子域)
 * @date: 2026/11/20
 * @version: v1.0.0
 **/
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class AnnouncementDetailRspVO {

    private Long announcementId;

    private String title;

    /**
     * 公告正文(公告短文本直接存 MySQL,不走 KV)
     */
    private String content;

    private Long creatorId;

    /**
     * 当前登录用户是否已确认已读
     */
    private Boolean isRead;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
