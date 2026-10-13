package com.quanshiguang.shiguang.note.biz.service.impl;

import com.quanshiguang.framework.biz.context.holder.LoginUserContextHolder;
import com.quanshiguang.framework.common.exception.BizException;
import com.quanshiguang.framework.common.response.Response;
import com.quanshiguang.framework.common.util.JsonUtils;
import com.quanshiguang.shiguang.note.biz.constant.MQConstants;
import com.quanshiguang.shiguang.note.biz.constant.RedisKeyConstants;
import com.quanshiguang.shiguang.note.biz.domain.dataobject.AnnouncementDO;
import com.quanshiguang.shiguang.note.biz.domain.dataobject.AnnouncementReadDO;
import com.quanshiguang.shiguang.note.biz.domain.mapper.AnnouncementDOMapper;
import com.quanshiguang.shiguang.note.biz.domain.mapper.AnnouncementReadDOMapper;
import com.quanshiguang.shiguang.note.biz.enums.ResponseCodeEnum;
import com.quanshiguang.shiguang.note.biz.model.dto.AnnouncementReadMqDTO;
import com.quanshiguang.shiguang.note.biz.model.vo.AnnouncementReadStatRspVO;
import com.quanshiguang.shiguang.note.biz.model.vo.ConfirmAnnouncementReadReqVO;
import com.quanshiguang.shiguang.note.biz.model.vo.PublishAnnouncementReqVO;
import com.quanshiguang.shiguang.note.biz.rpc.DistributedIdGeneratorRpcService;
import com.quanshiguang.shiguang.note.biz.service.AnnouncementService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.client.producer.SendCallback;
import org.apache.rocketmq.client.producer.SendResult;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.Objects;

/**
 * @description: 公告已读回执服务实现(已读回执子域)
 *
 * 链路设计(复用平台"点赞"核心模式):
 *   发布: 管理员发布(网关 RBAC 校验) → 分布式 ID → 直接入库
 *   已读: Redis SISMEMBER/SADD 原子判重(幂等) → MQ 异步落 t_announcement_read
 *         → 消费端唯一索引兜底 → Redis 计数 Hash 维护已读数
 *   统计: 读 Redis 计数,不回源 DB(高频查询路径)
 *
 * @date: 2026/10/13
 * @version: v1.0.0
 **/
@Service
@Slf4j
public class AnnouncementServiceImpl implements AnnouncementService {

    @Resource
    private AnnouncementDOMapper announcementDOMapper;
    @Resource
    private AnnouncementReadDOMapper announcementReadDOMapper;
    @Resource
    private DistributedIdGeneratorRpcService distributedIdGeneratorRpcService;
    @Resource
    private RocketMQTemplate rocketMQTemplate;
    @Resource
    private RedisTemplate<String, Object> redisTemplate;

    /**
     * 公告 ID 的 Leaf 号段 biz tag
     */
    private static final String BIZ_TAG_ANNOUNCEMENT_ID = "leaf-segment-announcement-id";

    @Override
    public Response<?> publishAnnouncement(PublishAnnouncementReqVO reqVO) {
        // 当前登录用户(发布人,网关已校验其具备 announcement:publish 权限)
        Long creatorId = LoginUserContextHolder.getUserId();

        // 分布式 ID 生成公告 ID
        String announcementIdStr = distributedIdGeneratorRpcService.getAnnouncementId();

        AnnouncementDO announcementDO = AnnouncementDO.builder()
                .id(Long.valueOf(announcementIdStr))
                .title(reqVO.getTitle())
                .content(reqVO.getContent())
                .creatorId(creatorId)
                .status(0)
                .createTime(LocalDateTime.now())
                .updateTime(LocalDateTime.now())
                .build();

        try {
            announcementDOMapper.insert(announcementDO);
        } catch (Exception e) {
            log.error("==> 公告发布失败: ", e);
            throw new BizException(ResponseCodeEnum.ANNOUNCEMENT_PUBLISH_FAIL);
        }

        log.info("==> 公告发布成功, announcementId: {}, creatorId: {}", announcementDO.getId(), creatorId);
        return Response.success();
    }

    @Override
    public Response<?> confirmRead(ConfirmAnnouncementReadReqVO reqVO) {
        Long announcementId = reqVO.getAnnouncementId();
        Long userId = LoginUserContextHolder.getUserId();

        // 校验公告存在且生效
        AnnouncementDO announcementDO = announcementDOMapper.selectByPrimaryKey(announcementId);
        if (Objects.isNull(announcementDO)) {
            throw new BizException(ResponseCodeEnum.ANNOUNCEMENT_NOT_FOUND);
        }

        // ---------- 幂等判重: Redis Set 原子操作 ----------
        // key: announcement:read:confirm:{userId}, member: announcementId
        // SADD 原子性保证同一用户对同一公告的重复确认只成功一次
        Long added = redisTemplate.opsForSet()
                .add(RedisKeyConstants.buildAnnouncementReadConfirmKey(userId), announcementId.toString());

        if (added == null || added == 0) {
            // 已确认过,幂等返回(不重复发 MQ,不重复计数)
            throw new BizException(ResponseCodeEnum.ANNOUNCEMENT_ALREADY_READ);
        }

        // ---------- 异步落库: MQ 解耦削峰 ----------
        AnnouncementReadMqDTO mqDTO = AnnouncementReadMqDTO.builder()
                .announcementId(announcementId)
                .userId(userId)
                .createTime(LocalDateTime.now())
                .build();

        Message<String> message = MessageBuilder.withPayload(JsonUtils.toJsonString(mqDTO)).build();
        rocketMQTemplate.asyncSend(MQConstants.TOPIC_ANNOUNCEMENT_READ, message, new SendCallback() {
            @Override
            public void onSuccess(SendResult sendResult) {
                log.info("==> 【已读确认】MQ 发送成功: {}", sendResult);
            }

            @Override
            public void onException(Throwable throwable) {
                log.error("==> 【已读确认】MQ 发送异常: ", throwable);
                // 发送失败回滚 Redis 判重标记,允许用户重新确认(避免消息丢失导致漏记)
                redisTemplate.opsForSet().remove(
                        RedisKeyConstants.buildAnnouncementReadConfirmKey(userId), announcementId.toString());
            }
        });

        return Response.success();
    }

    @Override
    public Response<AnnouncementReadStatRspVO> readStat(Long announcementId) {
        // 校验公告存在
        AnnouncementDO announcementDO = announcementDOMapper.selectByPrimaryKey(announcementId);
        if (Objects.isNull(announcementDO)) {
            throw new BizException(ResponseCodeEnum.ANNOUNCEMENT_NOT_FOUND);
        }

        // 已读数: 优先读 Redis 计数 Hash(count:note: 域外独立 key)
        String countKey = RedisKeyConstants.buildAnnouncementReadCountKey(announcementId);
        Object readTotalObj = redisTemplate.opsForHash().get(countKey, "readTotal");
        Long readTotal;
        if (Objects.nonNull(readTotalObj)) {
            readTotal = Long.parseLong(readTotalObj.toString());
        } else {
            // Redis 无计数(未消费或过期): 回源 DB 并回写
            readTotal = announcementReadDOMapper.countByAnnouncementId(announcementId);
            redisTemplate.opsForHash().put(countKey, "readTotal", readTotal.toString());
        }

        // 应读数: 企业用户总数(同库直查,避免跨服务契约变更)
        Long userTotal = announcementReadDOMapper.countAllUsers();

        // 已读率(百分比,一位小数)
        String readRate = BigDecimal.valueOf(readTotal)
                .multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(Math.max(userTotal, 1)), 1, RoundingMode.HALF_UP)
                .stripTrailingZeros().toPlainString();

        AnnouncementReadStatRspVO rspVO = AnnouncementReadStatRspVO.builder()
                .announcementId(announcementId)
                .title(announcementDO.getTitle())
                .readTotal(readTotal)
                .userTotal(userTotal)
                .readRate(readRate)
                .build();

        return Response.success(rspVO);
    }
}
