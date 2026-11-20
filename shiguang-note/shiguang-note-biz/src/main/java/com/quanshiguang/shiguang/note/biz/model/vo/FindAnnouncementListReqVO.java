package com.quanshiguang.shiguang.note.biz.model.vo;

import lombok.Data;

/**
 * @description: 公告列表请求 VO(已读回执子域)
 * @date: 2026/11/20
 * @version: v1.0.0
 **/
@Data
public class FindAnnouncementListReqVO {

    /**
     * 页码，从 1 开始；不传默认 1
     */
    private Integer pageNo;

    /**
     * 每页条数，默认 10，最大 50
     */
    private Integer pageSize;
}
