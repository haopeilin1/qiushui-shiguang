package com.quanshiguang.shiguang.note.biz.controller;

import com.quanshiguang.framework.biz.operationlog.aspect.ApiOperationLog;
import com.quanshiguang.framework.common.response.Response;
import com.quanshiguang.shiguang.note.biz.model.vo.AnnouncementDetailRspVO;
import com.quanshiguang.shiguang.note.biz.model.vo.AnnouncementReadStatRspVO;
import com.quanshiguang.shiguang.note.biz.model.vo.ConfirmAnnouncementReadReqVO;
import com.quanshiguang.shiguang.note.biz.model.vo.FindAnnouncementDetailReqVO;
import com.quanshiguang.shiguang.note.biz.model.vo.FindAnnouncementListReqVO;
import com.quanshiguang.shiguang.note.biz.model.vo.FindAnnouncementListRspVO;
import com.quanshiguang.shiguang.note.biz.model.vo.PublishAnnouncementReqVO;
import com.quanshiguang.shiguang.note.biz.model.vo.WithdrawAnnouncementReqVO;
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
 * 权限(网关层按路由绑定):
 *   - 发布 /note/announcement/publish  → announcement:publish
 *   - 撤回 /note/announcement/withdraw → announcement:manage
 *   - 统计 /note/announcement/read/stat→ announcement:readstat
 * 列表 / 详情 / 确认已读: 登录即可访问。
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

    @PostMapping(value = "/list")
    @ApiOperationLog(description = "公告列表")
    public Response<FindAnnouncementListRspVO> list(@Validated @RequestBody FindAnnouncementListReqVO reqVO) {
        return announcementService.findAnnouncementList(reqVO);
    }

    @PostMapping(value = "/detail")
    @ApiOperationLog(description = "公告详情")
    public Response<AnnouncementDetailRspVO> detail(@Validated @RequestBody FindAnnouncementDetailReqVO reqVO) {
        return announcementService.findAnnouncementDetail(reqVO);
    }

    @PostMapping(value = "/withdraw")
    @ApiOperationLog(description = "公告撤回(管理员)")
    public Response<?> withdraw(@Validated @RequestBody WithdrawAnnouncementReqVO reqVO) {
        return announcementService.withdrawAnnouncement(reqVO);
    }
}
