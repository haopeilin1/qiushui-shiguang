package com.quanshiguang.shiguang.note.biz.model.vo;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * @description: 公告详情请求 VO(已读回执子域)
 * @date: 2026/11/20
 * @version: v1.0.0
 **/
@Data
public class FindAnnouncementDetailReqVO {

    /**
     * 公告 ID
     */
    @NotNull(message = "公告 ID 不能为空")
    private Long announcementId;
}
