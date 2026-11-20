package com.quanshiguang.shiguang.note.biz.service;

import com.quanshiguang.framework.common.response.Response;
import com.quanshiguang.shiguang.note.biz.model.vo.AnnouncementDetailRspVO;
import com.quanshiguang.shiguang.note.biz.model.vo.AnnouncementReadStatRspVO;
import com.quanshiguang.shiguang.note.biz.model.vo.ConfirmAnnouncementReadReqVO;
import com.quanshiguang.shiguang.note.biz.model.vo.FindAnnouncementDetailReqVO;
import com.quanshiguang.shiguang.note.biz.model.vo.FindAnnouncementListReqVO;
import com.quanshiguang.shiguang.note.biz.model.vo.FindAnnouncementListRspVO;
import com.quanshiguang.shiguang.note.biz.model.vo.PublishAnnouncementReqVO;
import com.quanshiguang.shiguang.note.biz.model.vo.WithdrawAnnouncementReqVO;

/**
 * @description: 公告已读回执服务(已读回执子域)
 * @date: 2026/10/13
 * @version: v1.0.0
 **/
public interface AnnouncementService {

    /**
     * 发布公告(需管理员权限,权限校验在网关层完成)
     */
    Response<?> publishAnnouncement(PublishAnnouncementReqVO reqVO);

    /**
     * 确认已读(幂等: 同一用户对同一公告仅一次有效确认;
     * Redis SADD 原子判重 → MQ 异步落库 → 计数)
     */
    Response<?> confirmRead(ConfirmAnnouncementReadReqVO reqVO);

    /**
     * 查询已读率统计(已读数来自 Redis 计数,应读数为用户总数)
     */
    Response<AnnouncementReadStatRspVO> readStat(Long announcementId);

    /**
     * 公告列表(登录用户): 生效公告分页 + 当前用户已读状态 + 未读总数
     */
    Response<FindAnnouncementListRspVO> findAnnouncementList(FindAnnouncementListReqVO reqVO);

    /**
     * 公告详情(登录用户): 正文 + 当前用户已读状态
     */
    Response<AnnouncementDetailRspVO> findAnnouncementDetail(FindAnnouncementDetailReqVO reqVO);

    /**
     * 撤回公告(需 announcement:manage 权限,权限校验在网关层完成)
     */
    Response<?> withdrawAnnouncement(WithdrawAnnouncementReqVO reqVO);
}
