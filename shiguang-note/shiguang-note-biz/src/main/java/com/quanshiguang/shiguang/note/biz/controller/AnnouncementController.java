package com.quanshiguang.shiguang.note.biz.controller;

import com.quanshiguang.framework.biz.operationlog.aspect.ApiOperationLog;
import com.quanshiguang.framework.common.response.Response;
import com.quanshiguang.shiguang.note.biz.model.vo.AnnouncementReadStatRspVO;
import com.quanshiguang.shiguang.note.biz.model.vo.ConfirmAnnouncementReadReqVO;
import com.quanshiguang.shiguang.note.biz.model.vo.PublishAnnouncementReqVO;
import com.quanshiguang.shiguang.note.biz.service.AnnouncementService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * @description: 公告(已读回执子域)
 *
 * 发布接口由网关按路由绑定 announcement:publish 权限(仅管理员),
 * 确认已读/统计接口登录即可访问。
 *
 * @date: 2026/10/13
 * @version: v1.0.0
 **/
@RestController
@RequestMapping("/announcement")
@Slf4j
public class AnnouncementController {

    @Resource
    private AnnouncementService announcementService;

    @PostMapping(value = "/publish")
    @ApiOperationLog(description = "公告发布(管理员)")
    public Response<?> publishAnnouncement(@Validated @RequestBody PublishAnnouncementReqVO reqVO) {
        return announcementService.publishAnnouncement(reqVO);
    }

    @PostMapping(value = "/read/confirm")
    @ApiOperationLog(description = "确认公告已读")
    public Response<?> confirmRead(@Validated @RequestBody ConfirmAnnouncementReadReqVO reqVO) {
        return announcementService.confirmRead(reqVO);
    }

    @PostMapping(value = "/read/stat")
    @ApiOperationLog(description = "公告已读率统计")
    public Response<AnnouncementReadStatRspVO> readStat(@Validated @RequestBody ConfirmAnnouncementReadReqVO reqVO) {
        return announcementService.readStat(reqVO.getAnnouncementId());
    }
}
