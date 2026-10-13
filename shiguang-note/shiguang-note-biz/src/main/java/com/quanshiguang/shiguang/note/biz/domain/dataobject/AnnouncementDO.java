package com.quanshiguang.shiguang.note.biz.domain.dataobject;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * @description: 公告(已读回执子域)
 * @date: 2026/10/13
 * @version: v1.0.0
 **/
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class AnnouncementDO {

    /**
     * 公告 ID(分布式 ID 服务生成)
     */
    private Long id;

    /**
     * 公告标题
     */
    private String title;

    /**
     * 公告正文(短公告直存)
     */
    private String content;

    /**
     * 发布人用户 ID(需管理员权限)
     */
    private Long creatorId;

    /**
     * 状态: 0-生效 1-撤回
     */
    private Integer status;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
