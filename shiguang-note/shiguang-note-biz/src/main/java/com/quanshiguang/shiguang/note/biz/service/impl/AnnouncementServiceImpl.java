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
import com.quanshiguang.shiguang.note.biz.model.vo.AnnouncementDetailRspVO;
import com.quanshiguang.shiguang.note.biz.model.vo.AnnouncementItemVO;
import com.quanshiguang.shiguang.note.biz.model.vo.AnnouncementReadStatRspVO;
import com.quanshiguang.shiguang.note.biz.model.vo.ConfirmAnnouncementReadReqVO;
import com.quanshiguang.shiguang.note.biz.model.vo.FindAnnouncementDetailReqVO;
import com.quanshiguang.shiguang.note.biz.model.vo.FindAnnouncementListReqVO;
import com.quanshiguang.shiguang.note.biz.model.vo.FindAnnouncementListRspVO;
import com.quanshiguang.shiguang.note.biz.model.vo.PublishAnnouncementReqVO;
import com.quanshiguang.shiguang.note.biz.model.vo.WithdrawAnnouncementReqVO;
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
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

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

    /**
     * 列表默认页码 / 每页条数 / 每页最大条数
     */
    private static final int DEFAULT_PAGE_NO = 1;
    private static final int DEFAULT_PAGE_SIZE = 10;
    private static final int MAX_PAGE_SIZE = 50;

    /**
     * 已读集合缓存 TTL: 到期后由 DB 回执重建, 避免 Set 无限增长
     */
    private static final Duration READ_SET_TTL = Duration.ofDays(7);

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

        // 给判重集合设置 TTL: 到期后由 list/detail 回源 DB 重建, 避免 Set 无限增长
        redisTemplate.expire(RedisKeyConstants.buildAnnouncementReadConfirmKey(userId), READ_SET_TTL);

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
        // 校验公告存在(含已撤回: 撤回后管理员仍可查看历史统计)
        AnnouncementDO announcementDO = announcementDOMapper.selectById(announcementId);
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
            // Redis 无计数(未消费或过期): 回源 DB 并回写(短 TTL,防脏计数长期驻留)
            readTotal = announcementReadDOMapper.countByAnnouncementId(announcementId);
            redisTemplate.opsForHash().put(countKey, "readTotal", readTotal.toString());
            redisTemplate.expire(countKey, Duration.ofMinutes(10));
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

    @Override
    public Response<FindAnnouncementListRspVO> findAnnouncementList(FindAnnouncementListReqVO reqVO) {
        Long userId = LoginUserContextHolder.getUserId();

        // 页码/页大小兜底: 防止前端传 0、负数、超大值
        long pageNo = Objects.isNull(reqVO.getPageNo()) ? DEFAULT_PAGE_NO : Math.max(1, reqVO.getPageNo());
        int pageSize = Objects.isNull(reqVO.getPageSize()) ? DEFAULT_PAGE_SIZE
                : Math.min(MAX_PAGE_SIZE, Math.max(1, reqVO.getPageSize()));

        // 生效公告总数
        long totalCount = announcementDOMapper.countActive();
        List<AnnouncementItemVO> items = new ArrayList<>();

        if (totalCount > 0) {
            long offset = (pageNo - 1) * pageSize;
            List<AnnouncementDO> announcementDOS = announcementDOMapper.selectActivePage(offset, pageSize);

            // 一次性取当前用户的已读集合(Redis Set; 缺失时回源 DB 重建)
            Set<Long> readIds = loadReadAnnouncementIds(userId);

            for (AnnouncementDO announcementDO : announcementDOS) {
                items.add(AnnouncementItemVO.builder()
                        .announcementId(announcementDO.getId())
                        .title(announcementDO.getTitle())
                        .creatorId(announcementDO.getCreatorId())
                        .isRead(readIds.contains(announcementDO.getId()))
                        .createTime(announcementDO.getCreateTime())
                        .build());
            }
        }

        // 未读数(红点): 生效公告数 - 该用户已读回执数(SQL NOT EXISTS 一次算完)
        long unreadTotal = announcementDOMapper.countUnreadByUserId(userId);
        long totalPage = (totalCount + pageSize - 1) / pageSize;

        return Response.success(FindAnnouncementListRspVO.builder()
                .list(items)
                .totalCount(totalCount)
                .unreadTotal(unreadTotal)
                .pageNo(pageNo)
                .pageSize((long) pageSize)
                .totalPage(totalPage)
                .build());
    }

    @Override
    public Response<AnnouncementDetailRspVO> findAnnouncementDetail(FindAnnouncementDetailReqVO reqVO) {
        Long userId = LoginUserContextHolder.getUserId();
        Long announcementId = reqVO.getAnnouncementId();

        // 只允许查看生效中的公告(撤回后不可见)
        AnnouncementDO announcementDO = announcementDOMapper.selectByPrimaryKey(announcementId);
        if (Objects.isNull(announcementDO)) {
            throw new BizException(ResponseCodeEnum.ANNOUNCEMENT_NOT_FOUND);
        }

        return Response.success(AnnouncementDetailRspVO.builder()
                .announcementId(announcementDO.getId())
                .title(announcementDO.getTitle())
                .content(announcementDO.getContent())
                .creatorId(announcementDO.getCreatorId())
                .isRead(isRead(userId, announcementId))
                .createTime(announcementDO.getCreateTime())
                .updateTime(announcementDO.getUpdateTime())
                .build());
    }

    @Override
    public Response<?> withdrawAnnouncement(WithdrawAnnouncementReqVO reqVO) {
        Long announcementId = reqVO.getAnnouncementId();

        AnnouncementDO announcementDO = announcementDOMapper.selectById(announcementId);
        if (Objects.isNull(announcementDO)) {
            throw new BizException(ResponseCodeEnum.ANNOUNCEMENT_NOT_FOUND);
        }

        // 幂等: 已撤回直接返回成功, 避免管理员重复点击报错
        if (Objects.equals(announcementDO.getStatus(), 1)) {
            return Response.success();
        }

        int updated = announcementDOMapper.withdrawById(announcementId);
        if (updated == 0) {
            throw new BizException(ResponseCodeEnum.ANNOUNCEMENT_CANT_WITHDRAW);
        }

        log.info("==> 公告已撤回, announcementId: {}, operatorId: {}",
                announcementId, LoginUserContextHolder.getUserId());
        return Response.success();
    }

    /**
     * 加载当前用户的已读公告 ID 集合
     *
     * 优先读 Redis Set(confirm 时 SADD 写入); 集合不存在(首访/过期)时回源 DB 回执表重建,
     * 并回填 7 天 TTL —— 既避免 Set 无限增长, 又保证列表的 isRead 不会因缓存过期而失真。
     */
    private Set<Long> loadReadAnnouncementIds(Long userId) {
        String key = RedisKeyConstants.buildAnnouncementReadConfirmKey(userId);
        Set<Object> members = redisTemplate.opsForSet().members(key);

        if (members != null && !members.isEmpty()) {
            Set<Long> readIds = new HashSet<>(members.size());
            for (Object member : members) {
                try {
                    readIds.add(Long.valueOf(String.valueOf(member)));
                } catch (NumberFormatException e) {
                    log.warn("==> 公告已读集合存在非法成员, 已忽略: {}", member);
                }
            }
            return readIds;
        }

        List<Long> dbIds = announcementReadDOMapper.selectAnnouncementIdsByUserId(userId);
        Set<Long> readIds = new HashSet<>();
        if (dbIds != null && !dbIds.isEmpty()) {
            readIds.addAll(dbIds);
            redisTemplate.opsForSet().add(key, dbIds.stream().map(String::valueOf).toArray());
            redisTemplate.expire(key, READ_SET_TTL);
        }
        return readIds;
    }

    /**
     * 判断当前用户是否已读某条公告
     *
     * Redis Set 存在时用 SISMEMBER(一次往返); Set 不存在时回源 DB 兜底。
     */
    private boolean isRead(Long userId, Long announcementId) {
        String key = RedisKeyConstants.buildAnnouncementReadConfirmKey(userId);
        if (Boolean.TRUE.equals(redisTemplate.hasKey(key))) {
            return Boolean.TRUE.equals(
                    redisTemplate.opsForSet().isMember(key, String.valueOf(announcementId)));
        }
        return announcementReadDOMapper.countByAnnouncementIdAndUserId(announcementId, userId) > 0;
    }
}
