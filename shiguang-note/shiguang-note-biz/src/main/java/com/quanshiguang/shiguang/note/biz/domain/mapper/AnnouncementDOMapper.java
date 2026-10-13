package com.quanshiguang.shiguang.note.biz.domain.mapper;

import com.quanshiguang.shiguang.note.biz.domain.dataobject.AnnouncementDO;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Select;

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
     * 按主键查询公告
     */
    @Select("SELECT id, title, content, creator_id AS creatorId, status, create_time AS createTime, update_time AS updateTime " +
            "FROM t_announcement WHERE id = #{id} AND status = 0")
    AnnouncementDO selectByPrimaryKey(Long id);
}
