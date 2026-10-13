package com.quanshiguang.shiguang.note.biz.model.vo;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * @description: 发布公告请求 VO(已读回执子域,需管理员权限)
 * @date: 2026/10/13
 * @version: v1.0.0
 **/
@Data
public class PublishAnnouncementReqVO {

    /**
     * 公告标题
     */
    @NotBlank(message = "公告标题不能为空")
    @Size(max = 128, message = "标题长度不能超过 128")
    private String title;

    /**
     * 公告正文
     */
    @NotBlank(message = "公告正文不能为空")
    @Size(max = 10000, message = "正文长度不能超过 10000")
    private String content;
}
