package com.quanshiguang.shiguang.note.biz.domain.mapper;

import com.quanshiguang.shiguang.note.biz.domain.dataobject.AnnouncementDO;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

/**
 * @description: 公告 Mapper(已读回执子域)
 * @date: 2026/10/13
 * @version: v1.0.0
 **/
@Mapper
public interface AnnouncementDOMapper {

    /**
     * 插入公告
     */
    @Insert("INSERT INTO t_announcement (id, title, content, creator_id, status, create_time, update_time) " +
            "VALUES (#{id}, #{title}, #{content}, #{creatorId}, #{status}, #{createTime}, #{updateTime})")
    int insert(AnnouncementDO announcementDO);

    /**
     * 按主键查询"生效中"的公告(确认已读/详情用; 撤回后不可确认)
     */
    @Select("SELECT id, title, content, creator_id AS creatorId, status, create_time AS createTime, update_time AS updateTime " +
            "FROM t_announcement WHERE id = #{id} AND status = 0")
    AnnouncementDO selectByPrimaryKey(Long id);

    /**
     * 按主键查询公告, 不校验状态(已读统计用: 撤回后管理员仍可查看历史统计)
     */
    @Select("SELECT id, title, content, creator_id AS creatorId, status, create_time AS createTime, update_time AS updateTime " +
            "FROM t_announcement WHERE id = #{id}")
    AnnouncementDO selectById(Long id);

    /**
     * 分页查询生效中的公告, 按发布时间倒序
     */
    @Select("SELECT id, title, content, creator_id AS creatorId, status, create_time AS createTime, update_time AS updateTime " +
            "FROM t_announcement WHERE status = 0 ORDER BY create_time DESC, id DESC " +
            "LIMIT #{offset}, #{pageSize}")
    List<AnnouncementDO> selectActivePage(@Param("offset") long offset, @Param("pageSize") int pageSize);

    /**
     * 生效中的公告总数
     */
    @Select("SELECT COUNT(*) FROM t_announcement WHERE status = 0")
    long countActive();

    /**
     * 当前用户的未读公告数(红点):
     * 生效公告中, 不存在该用户已读回执的记录数
     */
    @Select("SELECT COUNT(*) FROM t_announcement a WHERE a.status = 0 " +
            "AND NOT EXISTS (SELECT 1 FROM t_announcement_read r " +
            "WHERE r.announcement_id = a.id AND r.user_id = #{userId})")
    long countUnreadByUserId(@Param("userId") Long userId);

    /**
     * 撤回公告: 仅对生效中的公告生效(重复撤回返回 0)
     */
    @Update("UPDATE t_announcement SET status = 1, update_time = NOW() " +
            "WHERE id = #{id} AND status = 0")
    int withdrawById(@Param("id") Long id);
}

