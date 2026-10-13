package com.quanshiguang.shiguang.count.biz.service;

import com.quanshiguang.shiguang.count.biz.domain.dataobject.MqDedupDO;
import com.quanshiguang.shiguang.count.biz.domain.mapper.MqDedupMapper;
import com.quanshiguang.shiguang.count.biz.service.impl.DedupServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * @description: 双层幂等服务单元测试(L2 验证等级,不依赖中间件)
 * @date: 2026/10/13
 * @version: v1.0.0
 **/
@ExtendWith(MockitoExtension.class)
@DisplayName("消息消费双层幂等测试")
class DedupServiceImplTest {

    @Mock
    private RedisTemplate<String, Object> redisTemplate;

    @Mock
    private ValueOperations<String, Object> valueOperations;

    @Mock
    private MqDedupMapper mqDedupMapper;

    @InjectMocks
    private DedupServiceImpl dedupService;

    private static final String BIZ_KEY = "CountFansTopic:10001:10002:FOLLOW";

    @BeforeEach
    void setUp() {
        // 启用幂等开关
        ReflectionTestUtils.setField(dedupService, "dedupEnabled", true);
    }

    @Test
    @DisplayName("首次消息: Redis SETNX 成功返回 false(放行)")
    void firstMessage_passes() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.setIfAbsent(anyString(), any(), any(Duration.class)))
                .thenReturn(true);

        assertFalse(dedupService.isDuplicate(BIZ_KEY, "msg-1"));
        verify(valueOperations).setIfAbsent(eq("mq:dedup:" + BIZ_KEY), eq("1"), any(Duration.class));
        // Redis 层成功,DB 层不应被触发
        verify(mqDedupMapper, never()).insert(any());
    }

    @Test
    @DisplayName("重复消息: Redis key 已存在返回 true(拦截)")
    void duplicateMessage_blocked() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.setIfAbsent(anyString(), any(), any(Duration.class)))
                .thenReturn(false);

        assertTrue(dedupService.isDuplicate(BIZ_KEY, "msg-1"));
        verify(mqDedupMapper, never()).insert(any());
    }

    @Test
    @DisplayName("Redis 异常时降级: DB 插入成功返回 false(Redis 数据丢失后 DB 接管)")
    void redisFailure_fallsBackToDb_passes() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.setIfAbsent(anyString(), any(), any(Duration.class)))
                .thenThrow(new RuntimeException("redis down"));
        when(mqDedupMapper.insert(any(MqDedupDO.class))).thenReturn(1);

        assertFalse(dedupService.isDuplicate(BIZ_KEY, "msg-1"));
        // 验证 DB 兜底层被触发,且业务键正确
        ArgumentCaptor<MqDedupDO> captor = ArgumentCaptor.forClass(MqDedupDO.class);
        verify(mqDedupMapper).insert(captor.capture());
        assertEquals(BIZ_KEY, captor.getValue().getBizKey());
        assertEquals("msg-1", captor.getValue().getMsgId());
    }

    @Test
    @DisplayName("Redis 异常时降级: DB 唯一索引冲突返回 true(双层兜底拦截)")
    void redisFailure_fallsBackToDb_blocks() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.setIfAbsent(anyString(), any(), any(Duration.class)))
                .thenThrow(new RuntimeException("redis down"));
        when(mqDedupMapper.insert(any(MqDedupDO.class)))
                .thenThrow(new DuplicateKeyException("dup"));

        assertTrue(dedupService.isDuplicate(BIZ_KEY, "msg-1"));
    }

    @Test
    @DisplayName("开关关闭时直接放行(对照实验用)")
    void switchDisabled_passes() {
        ReflectionTestUtils.setField(dedupService, "dedupEnabled", false);

        assertFalse(dedupService.isDuplicate(BIZ_KEY, "msg-1"));
        // 开关关闭时两层都不应被触发
        verifyNoInteractions(redisTemplate, mqDedupMapper);
    }
}
