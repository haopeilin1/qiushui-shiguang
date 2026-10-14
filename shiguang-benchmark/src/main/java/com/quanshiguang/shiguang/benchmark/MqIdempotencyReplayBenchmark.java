package com.quanshiguang.shiguang.benchmark;

import org.apache.rocketmq.client.producer.DefaultMQProducer;
import org.apache.rocketmq.common.message.Message;

import java.nio.charset.StandardCharsets;
import java.util.Scanner;

/**
 * @description: 消息消费幂等对照实验(简历数字出处)
 *
 * 实验设计:
 *   直接向 CountFansTopic 重放同一业务消息 N 次(默认 100),
 *   通过 Redis Hash count:user:{targetUserId} 的 fansTotal 字段
 *   观察计数变化,对照两种配置:
 *
 *   - 幂等开关 OFF(mq.dedup.enabled=false):
 *     100 次重复消息 → 粉丝计数被污染 +100 (复现上游缺陷)
 *
 *   - 幂等开关 ON (mq.dedup.enabled=true):
 *     100 次重复消息 → 仅首次生效,计数 +1 (验证修复)
 *
 * 使用方式:
 *   1. 启动中间件 + shiguang-count 服务
 *   2. 先初始化计数 Hash: redis-cli HSET count:user:{targetUserId} fansTotal 0
 *   3. 运行本类 main,按提示输入
 *
 * @date: 2026/10/13
 * @version: v1.0.0
 **/
public class MqIdempotencyReplayBenchmark {

    static final String NAMESRV = "localhost:9876";
    static final String TOPIC = "CountFansTopic";
    static final String PRODUCER_GROUP = "benchmark_replay_producer";

    public static void main(String[] args) throws Exception {
        int replayTimes = 100;
        long userId = 90001L;       // 操作者
        long targetUserId = 90002L; // 被关注者(计数观察对象)

        // 支持命令行参数覆盖: [replayTimes] [userId] [targetUserId]
        if (args.length >= 1) replayTimes = Integer.parseInt(args[0]);
        if (args.length >= 2) userId = Long.parseLong(args[1]);
        if (args.length >= 3) targetUserId = Long.parseLong(args[2]);

        System.out.println("=== 消息消费幂等对照实验 ===");
        System.out.printf("NameServer: %s, Topic: %s%n", NAMESRV, TOPIC);
        System.out.printf("重放同一业务消息 %d 次: userId=%d 关注 targetUserId=%d%n",
                replayTimes, userId, targetUserId);
        System.out.println("预期观察: count:user:" + targetUserId + " 的 fansTotal 字段");
        System.out.println("  幂等 OFF → +" + replayTimes + " (缺陷复现)");
        System.out.println("  幂等 ON  → +1 (修复验证)");
        System.out.println();
        System.out.print("回车开始重放... (Ctrl+C 取消)");
        new Scanner(System.in).nextLine();

        DefaultMQProducer producer = new DefaultMQProducer(PRODUCER_GROUP);
        producer.setNamesrvAddr(NAMESRV);
        producer.setSendMsgTimeout(10000);
        producer.start();
        try {
            // 同一业务事件: userId 关注 targetUserId
            // (与 RelationServiceImpl.follow 发出的消息体结构一致;
            //  eventId 固定 = 模拟同一事件的 100 次重复投递,而非 100 个新事件)
            String body = String.format(
                    "{\"userId\":%d,\"targetUserId\":%d,\"type\":1,\"eventId\":\"benchmark-replay-%d-%d\"}",
                    userId, targetUserId, userId, targetUserId);

            int success = 0;
            long start = System.currentTimeMillis();

            for (int i = 1; i <= replayTimes; i++) {
                Message msg = new Message(TOPIC, body.getBytes(StandardCharsets.UTF_8));
                try {
                    producer.send(msg);
                    success++;
                    if (i % 20 == 0 || i == replayTimes) {
                        System.out.printf("  已发送 %d/%d%n", i, replayTimes);
                    }
                } catch (Exception e) {
                    System.err.printf("  第 %d 条发送失败: %s%n", i, e.getMessage());
                }
            }

            long elapsed = System.currentTimeMillis() - start;
            System.out.println();
            System.out.println("=== 重放完成 ===");
            System.out.printf("发送成功: %d/%d, 耗时 %d ms%n", success, replayTimes, elapsed);
            System.out.println();
            System.out.println("下一步验证(以 DB 为准):");
            System.out.printf("  mysql: SELECT user_id, fans_total FROM t_user_count WHERE user_id=%d;%n", targetUserId);
            System.out.printf("  dedup 键: redis-cli EXISTS mq:dedup:CountFansTopic:benchmark-replay-%d-%d%n",
                    userId, targetUserId);
            System.out.printf("  对照开关: shiguang-count 服务以 -Dmq.dedup.enabled=false 重启后重跑%n");
        } finally {
            producer.shutdown();
        }
    }
}
