package com.quanshiguang.shiguang.note.biz.model.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * @description: 公告列表响应 VO(已读回执子域)
 *
 * 额外带 unreadTotal: 前端红点直接用，不用再发一次请求。
 *
 * @date: 2026/11/20
 * @version: v1.0.0
 **/
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class FindAnnouncementListRspVO {

    /**
     * 当前页公告
     */
    private List<AnnouncementItemVO> list;

    /**
     * 生效公告总数
     */
    private Long totalCount;

    /**
     * 当前登录用户未读公告数(红点)
     */
    private Long unreadTotal;

    private Long pageNo;

    private Long pageSize;

    /**
     * 总页数
     */
    private Long totalPage;
}
