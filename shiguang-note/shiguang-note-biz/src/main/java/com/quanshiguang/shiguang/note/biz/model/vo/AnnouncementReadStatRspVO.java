package com.quanshiguang.shiguang.note.biz.model.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * @description: 已读率统计响应 VO(已读回执子域)
 * @date: 2026/10/13
 * @version: v1.0.0
 **/
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class AnnouncementReadStatRspVO {

    /**
     * 公告 ID
     */
    private Long announcementId;

    /**
     * 公告标题
     */
    private String title;

    /**
     * 已读人数
     */
    private Long readTotal;

    /**
     * 应读人数(当前企业用户总数,来自用户服务)
     */
    private Long userTotal;

    /**
     * 已读率(0-100,保留一位小数)
     */
    private String readRate;
}
