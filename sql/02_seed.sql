-- ============================================================
-- 智汇社区 种子数据
-- 角色/权限对齐 RoleConstants.COMMON_USER_ROLE_ID = 1L
-- 版本：2026-10-13
-- ============================================================
-- 注意：
--   * t_user 密码为 BCrypt(Sa-Token SecureUtil), 先插入测试账号后可用
--     验证码登录注册合一流程会自动建号, 种子账号仅用于快速验证
--   * 权限点需与网关 SaTokenConfigure 路由绑定对齐(见 Step 2)

SET NAMES utf8mb4;

-- ------------------------------------------------------------
-- 角色与权限
-- ------------------------------------------------------------
INSERT INTO `t_role` (`id`, `role_name`, `role_key`, `status`, `sort`, `remark`, `is_deleted`) VALUES
(1, '普通用户', 'COMMON_USER', 0, 1, '默认角色,注册自动分配', 0),
(2, '管理员',   'ADMIN',       0, 0, '公告发布/内容管理权限', 0);

-- 权限点: 编码规范 {模块}:{资源}:{动作}
INSERT INTO `t_permission` (`id`, `parent_id`, `name`, `type`, `permission_key`, `status`, `sort`, `is_deleted`) VALUES
(101, 0, '笔记发布',   3, 'note:publish',        0, 1, 0),
(102, 0, '笔记删除',   3, 'note:delete',         0, 2, 0),
(103, 0, '公告发布',   3, 'announcement:publish', 0, 3, 0),
(104, 0, '公告管理',   3, 'announcement:manage',  0, 4, 0),
(105, 0, '已读统计查询', 3, 'announcement:readstat', 0, 5, 0);

-- 普通用户角色: 笔记发布/删除
INSERT INTO `t_role_permission_rel` (`id`, `role_id`, `permission_id`, `is_deleted`) VALUES
(11, 1, 101, 0),
(12, 1, 102, 0);

-- 管理员角色: 全部权限(含公告)
INSERT INTO `t_role_permission_rel` (`id`, `role_id`, `permission_id`, `is_deleted`) VALUES
(21, 2, 101, 0),
(22, 2, 102, 0),
(23, 2, 103, 0),
(24, 2, 104, 0),
(25, 2, 105, 0);

-- ------------------------------------------------------------
-- 话题
-- ------------------------------------------------------------
INSERT INTO `t_topic` (`id`, `name`, `is_deleted`) VALUES
(1, '技术分享', 0),
(2, '项目复盘', 0),
(3, '安全生产', 0),
(4, '制度流程', 0);

-- ------------------------------------------------------------
-- 测试账号(两个:普通用户与管理员,用于权限矩阵验证)
-- 密码哈希为 "123456" 的 BCrypt; 登录走验证码流程无需密码,
-- 此密码仅备用。手机号用于验证码 dev 模式(日志打印)登录。
-- ------------------------------------------------------------
INSERT INTO `t_user` (`id`, `shiguang_id`, `password`, `nickname`, `phone`, `sex`, `status`, `introduction`, `is_deleted`) VALUES
(10001, 'u10001', '$2a$10$Y3JFdGVzdFBhc3N3b3JkSGFzaA==', '测试用户-普通', '13800000001', 0, 0, '普通用户账号', 0),
(10002, 'u10002', '$2a$10$Y3JFdGVzdFBhc3N3b3JkSGFzaA==', '测试用户-管理员', '13800000002', 0, 0, '管理员账号', 0);

INSERT INTO `t_user_role_rel` (`id`, `user_id`, `role_id`, `is_deleted`) VALUES
(1, 10001, 1, 0),
(2, 10002, 2, 0);

-- 用户角色 Redis 缓存将由 user 服务注册流程/启动 Runner 写入
-- 此处同步初始化计数行,避免首次查询报错
INSERT INTO `t_user_count` (`id`, `user_id`, `fans_total`, `following_total`, `note_total`, `like_total`, `collect_total`) VALUES
(1, 10001, 0, 0, 0, 0, 0),
(2, 10002, 0, 0, 0, 0, 0);
