-- ============================================================
-- 智汇社区（企业内部知识分享平台）数据库初始化脚本
-- 基于各服务 Mapper XML 反推的表结构，单库模式：shiguang
-- 版本：2026-10-13
-- ============================================================
-- 使用方法：
--   mysql -uroot -p < 01_schema.sql   (或 source 执行)
-- 说明：
--   * 所有服务（user/note/count/relation）共用本库
--   * t_announcement / t_announcement_read 为新增的已读回执子域表
--   * t_mq_dedup 为新增的消息幂等去重表

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- ------------------------------------------------------------
-- 用户域
-- ------------------------------------------------------------
DROP TABLE IF EXISTS `t_user`;
CREATE TABLE `t_user` (
  `id`            BIGINT       NOT NULL COMMENT '用户ID',
  `shiguang_id`   VARCHAR(20)  DEFAULT NULL COMMENT '拾光号',
  `password`      VARCHAR(64)  DEFAULT NULL COMMENT '密码(Sa-Token BCrypt)',
  `nickname`      VARCHAR(64)  NOT NULL COMMENT '昵称',
  `avatar`        VARCHAR(255) DEFAULT NULL COMMENT '头像',
  `birthday`      DATE         DEFAULT NULL COMMENT '生日',
  `background_img` VARCHAR(255) DEFAULT NULL COMMENT '主页背景图',
  `phone`         VARCHAR(20)  NOT NULL COMMENT '手机号',
  `sex`           TINYINT      DEFAULT 0 COMMENT '性别 0-未知 1-男 2-女',
  `status`        TINYINT      DEFAULT 0 COMMENT '状态 0-正常 1-禁用',
  `introduction`  VARCHAR(500) DEFAULT NULL COMMENT '简介',
  `create_time`   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time`   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `is_deleted`    TINYINT      DEFAULT 0,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_phone` (`phone`, `is_deleted`),
  KEY `idx_shiguang_id` (`shiguang_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户表';

DROP TABLE IF EXISTS `t_role`;
CREATE TABLE `t_role` (
  `id`          BIGINT      NOT NULL,
  `role_name`   VARCHAR(32) NOT NULL COMMENT '角色名',
  `role_key`    VARCHAR(64) NOT NULL COMMENT '角色标识',
  `status`      TINYINT     DEFAULT 0 COMMENT '0-启用 1-禁用',
  `sort`        INT         DEFAULT 0,
  `remark`      VARCHAR(255) DEFAULT NULL,
  `create_time` DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `is_deleted`  TINYINT     DEFAULT 0,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_role_key` (`role_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='角色表';

DROP TABLE IF EXISTS `t_permission`;
CREATE TABLE `t_permission` (
  `id`             BIGINT       NOT NULL,
  `parent_id`      BIGINT       DEFAULT 0,
  `name`           VARCHAR(64)  NOT NULL COMMENT '权限名',
  `type`           TINYINT      DEFAULT 1 COMMENT '1-菜单 2-按钮 3-接口',
  `menu_url`       VARCHAR(255) DEFAULT NULL,
  `menu_icon`      VARCHAR(64)  DEFAULT NULL,
  `sort`           INT          DEFAULT 0,
  `permission_key` VARCHAR(128) DEFAULT NULL COMMENT '权限标识 如 announcement:publish',
  `status`         TINYINT      DEFAULT 0 COMMENT '0-启用 1-禁用',
  `create_time`    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time`    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `is_deleted`     TINYINT      DEFAULT 0,
  PRIMARY KEY (`id`),
  KEY `idx_parent_id` (`parent_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='权限表';

DROP TABLE IF EXISTS `t_user_role_rel`;
CREATE TABLE `t_user_role_rel` (
  `id`          BIGINT   NOT NULL,
  `user_id`     BIGINT   NOT NULL,
  `role_id`     BIGINT   NOT NULL,
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `is_deleted`  TINYINT  DEFAULT 0,
  PRIMARY KEY (`id`),
  KEY `idx_user_id` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户-角色关系表';

DROP TABLE IF EXISTS `t_role_permission_rel`;
CREATE TABLE `t_role_permission_rel` (
  `id`            BIGINT   NOT NULL,
  `role_id`       BIGINT   NOT NULL,
  `permission_id` BIGINT   NOT NULL,
  `create_time`   DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time`   DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `is_deleted`    TINYINT  DEFAULT 0,
  PRIMARY KEY (`id`),
  KEY `idx_role_id` (`role_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='角色-权限关系表';

-- ------------------------------------------------------------
-- 内容域（笔记/话题）
-- ------------------------------------------------------------
DROP TABLE IF EXISTS `t_note`;
CREATE TABLE `t_note` (
  `id`               BIGINT       NOT NULL COMMENT '笔记ID(雪花ID)',
  `title`            VARCHAR(128) DEFAULT NULL,
  `is_content_empty` TINYINT      DEFAULT 1 COMMENT '正文是否为空 0-否 1-是',
  `creator_id`       BIGINT       NOT NULL,
  `topic_id`         BIGINT       DEFAULT NULL,
  `topic_name`       VARCHAR(64)  DEFAULT NULL COMMENT '冗余话题名',
  `is_top`           TINYINT      DEFAULT 0,
  `type`             TINYINT      NOT NULL COMMENT '0-图文 1-视频',
  `img_uris`         VARCHAR(1024) DEFAULT NULL COMMENT '图片URI列表,逗号分隔',
  `video_uri`        VARCHAR(255) DEFAULT NULL,
  `visible`          TINYINT      DEFAULT 0 COMMENT '0-公开 1-仅自己可见',
  `create_time`      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time`      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `status`           TINYINT      DEFAULT 0 COMMENT '0-正常 1-删除',
  `content_uuid`     VARCHAR(64)  DEFAULT NULL COMMENT '正文UUID(KV服务关联)',
  PRIMARY KEY (`id`),
  KEY `idx_creator_id` (`creator_id`, `status`),
  KEY `idx_topic_id` (`topic_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='笔记表(元数据)';

DROP TABLE IF EXISTS `t_topic`;
CREATE TABLE `t_topic` (
  `id`          BIGINT      NOT NULL,
  `name`        VARCHAR(64) NOT NULL,
  `create_time` DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `is_deleted`  TINYINT     DEFAULT 0,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_name` (`name`, `is_deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='话题表';

-- ------------------------------------------------------------
-- 互动域（点赞/收藏关系表）
-- ------------------------------------------------------------
DROP TABLE IF EXISTS `t_note_like`;
CREATE TABLE `t_note_like` (
  `id`          BIGINT   NOT NULL,
  `user_id`     BIGINT   NOT NULL,
  `note_id`     BIGINT   NOT NULL,
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `status`      TINYINT  DEFAULT 0 COMMENT '0-有效 1-已取消',
  PRIMARY KEY (`id`),
  KEY `idx_user_note` (`user_id`, `note_id`),
  KEY `idx_note_id` (`note_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='笔记点赞关系表';

DROP TABLE IF EXISTS `t_note_collection`;
CREATE TABLE `t_note_collection` (
  `id`          BIGINT   NOT NULL,
  `user_id`     BIGINT   NOT NULL,
  `note_id`     BIGINT   NOT NULL,
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `status`      TINYINT  DEFAULT 0,
  PRIMARY KEY (`id`),
  KEY `idx_user_note` (`user_id`, `note_id`),
  KEY `idx_note_id` (`note_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='笔记收藏关系表';

-- ------------------------------------------------------------
-- 计数域
-- ------------------------------------------------------------
DROP TABLE IF EXISTS `t_note_count`;
CREATE TABLE `t_note_count` (
  `id`            BIGINT  NOT NULL,
  `note_id`       BIGINT  NOT NULL,
  `like_total`    BIGINT  DEFAULT 0,
  `collect_total` BIGINT  DEFAULT 0,
  `comment_total` BIGINT  DEFAULT 0,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_note_id` (`note_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='笔记计数表';

DROP TABLE IF EXISTS `t_user_count`;
CREATE TABLE `t_user_count` (
  `id`              BIGINT NOT NULL,
  `user_id`         BIGINT NOT NULL,
  `fans_total`      BIGINT DEFAULT 0,
  `following_total` BIGINT DEFAULT 0,
  `note_total`      BIGINT DEFAULT 0,
  `like_total`      BIGINT DEFAULT 0,
  `collect_total`   BIGINT DEFAULT 0,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_id` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户计数表';

-- ------------------------------------------------------------
-- 关系域
-- ------------------------------------------------------------
DROP TABLE IF EXISTS `t_following`;
CREATE TABLE `t_following` (
  `id`                BIGINT   NOT NULL,
  `user_id`           BIGINT   NOT NULL COMMENT '关注者',
  `following_user_id` BIGINT   NOT NULL COMMENT '被关注者',
  `create_time`       DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_user_id` (`user_id`),
  KEY `idx_following_user_id` (`following_user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='关注表(正向)';

DROP TABLE IF EXISTS `t_fans`;
CREATE TABLE `t_fans` (
  `id`           BIGINT   NOT NULL,
  `user_id`      BIGINT   NOT NULL COMMENT '被关注者',
  `fans_user_id` BIGINT   NOT NULL COMMENT '关注者',
  `create_time`  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_user_id` (`user_id`),
  KEY `idx_fans_user_id` (`fans_user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='粉丝表(反向)';

-- ------------------------------------------------------------
-- 【新增】已读回执子域
-- ------------------------------------------------------------
DROP TABLE IF EXISTS `t_announcement`;
CREATE TABLE `t_announcement` (
  `id`          BIGINT       NOT NULL COMMENT '公告ID(雪花ID)',
  `title`       VARCHAR(128) NOT NULL,
  `content`     TEXT         COMMENT '公告正文(短公告直存,超长走KV扩展)',
  `creator_id`  BIGINT       NOT NULL COMMENT '发布人(需管理员权限)',
  `status`      TINYINT      DEFAULT 0 COMMENT '0-生效 1-撤回',
  `create_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_create_time` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='公告表';

DROP TABLE IF EXISTS `t_announcement_read`;
CREATE TABLE `t_announcement_read` (
  `id`              BIGINT   NOT NULL,
  `announcement_id` BIGINT   NOT NULL,
  `user_id`         BIGINT   NOT NULL COMMENT '确认已读的员工',
  `create_time`     DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_announcement_user` (`announcement_id`, `user_id`) COMMENT '幂等约束:同一员工同一公告仅一条已读记录',
  KEY `idx_user_id` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='公告已读回执表';

-- ------------------------------------------------------------
-- 【新增】消息消费幂等去重表
-- ------------------------------------------------------------
DROP TABLE IF EXISTS `t_mq_dedup`;
CREATE TABLE `t_mq_dedup` (
  `id`           BIGINT      NOT NULL AUTO_INCREMENT,
  `biz_key`      VARCHAR(128) NOT NULL COMMENT '业务幂等键 topic:tag:userId:targetId:action',
  `msg_id`      VARCHAR(64)  DEFAULT NULL COMMENT 'RocketMQ msgId(辅助排查)',
  `create_time`  DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_biz_key` (`biz_key`) COMMENT '唯一索引兜底:防重复消费的第二层防线',
  KEY `idx_create_time` (`create_time`) COMMENT '按时间清理过期记录'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='MQ消息消费去重表(与Redis SETNX双层幂等)';

SET FOREIGN_KEY_CHECKS = 1;
