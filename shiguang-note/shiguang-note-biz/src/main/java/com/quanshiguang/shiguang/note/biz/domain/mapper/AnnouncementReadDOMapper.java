package com.quanshiguang.shiguang.note.biz.domain.mapper;

import com.quanshiguang.shiguang.note.biz.domain.dataobject.AnnouncementReadDO;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

/**
 * @description: 公告已读回执 Mapper(已读回执子域)
 * @date: 2026/10/13
 * @version: v1.0.0
 **/
@Mapper
public interface AnnouncementReadDOMapper {

    /**
     * 插入已读回执; 依赖 t_announcement_read(announcement_id, user_id) 唯一索引:
     * 消费端重复消费时插入冲突,由调用方按 DuplicateKeyException 判定幂等
     */
    @Insert("INSERT INTO t_announcement_read (id, announcement_id, user_id, create_time) " +
            "VALUES (#{id}, #{announcementId}, #{userId}, #{createTime})")
    int insert(AnnouncementReadDO announcementReadDO);

    /**
     * 统计公告已读人数
     */
    @Select("SELECT COUNT(*) FROM t_announcement_read WHERE announcement_id = #{announcementId}")
    Long countByAnnouncementId(Long announcementId);

    /**
     * 查询用户是否已确认已读(回执链路兜底校验)
     */
    @Select("SELECT COUNT(*) FROM t_announcement_read WHERE announcement_id = #{announcementId} AND user_id = #{userId}")
    int countByAnnouncementIdAndUserId(Long announcementId, Long userId);

    /**
     * 企业用户总数(已读率统计的应读分母;同库直查,避免跨服务契约变更)
     */
    @Select("SELECT COUNT(*) FROM t_user WHERE is_deleted = 0 AND status = 0")
    Long countAllUsers();
}
