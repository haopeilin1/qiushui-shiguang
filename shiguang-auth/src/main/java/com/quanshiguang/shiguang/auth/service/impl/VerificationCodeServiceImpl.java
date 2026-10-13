package com.quanshiguang.shiguang.auth.service.impl;

import cn.hutool.core.util.RandomUtil;
import com.quanshiguang.framework.common.exception.BizException;
import com.quanshiguang.framework.common.response.Response;
import com.quanshiguang.shiguang.auth.constant.RedisKeyConstants;
import com.quanshiguang.shiguang.auth.enums.ResponseCodeEnum;
import com.quanshiguang.shiguang.auth.model.vo.verificationcode.SendVerificationCodeReqVO;
import com.quanshiguang.shiguang.auth.service.VerificationCodeService;
import com.quanshiguang.shiguang.auth.sms.AliyunSmsHelper;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

/**
 * @author: 犬小哈
 * @date: 2024/4/7 15:41
 * @version: v1.0.0
 * @description: TODO
 **/
@Service
@Slf4j
public class VerificationCodeServiceImpl implements VerificationCodeService {

    /**
     * 短信 dev 模式开关: true 时不调用阿里云短信,验证码仅日志打印
     * (内网环境无法访问公网短信服务;生产置 false)
     */
    @Value("${sms.dev-mode:true}")
    private boolean smsDevMode;

    @Resource
    private RedisTemplate<String, Object> redisTemplate;
    @Resource(name = "taskExecutor")
    private ThreadPoolTaskExecutor threadPoolTaskExecutor;
    @Resource
    private AliyunSmsHelper aliyunSmsHelper;

    /**
     * 发送短信验证码
     *
     * @param sendVerificationCodeReqVO
     * @return
     */
    @Override
    public Response<?> send(SendVerificationCodeReqVO sendVerificationCodeReqVO) {
        // 手机号
        String phone = sendVerificationCodeReqVO.getPhone();

        // 构建验证码 redis key
        String key = RedisKeyConstants.buildVerificationCodeKey(phone);

        // 判断是否已发送验证码
        boolean isSent = redisTemplate.hasKey(key);
        if (isSent) {
            // 若之前发送的验证码未过期，则提示发送频繁
            throw new BizException(ResponseCodeEnum.VERIFICATION_CODE_SEND_FREQUENTLY);
        }

        // 生成 6 位随机数字验证码
        String verificationCode = RandomUtil.randomNumbers(6);

        log.info("==> 手机号: {}, 已生成验证码：【{}】", phone, verificationCode);

        // 验证码发送: dev 模式仅日志打印(不依赖阿里云短信,内网/本地联调用);
        // 生产环境走短信通道。开关由 sms.dev-mode 配置控制
        if (!smsDevMode) {
            // 调用第三方短信发送服务
            threadPoolTaskExecutor.submit(() -> {
                String signName = "阿里云短信测试";
                String templateCode = "SMS_154950909";
                String templateParam = String.format("{\"code\":\"%s\"}", verificationCode);
                aliyunSmsHelper.sendMessage(signName, templateCode, phone, templateParam);
            });
        } else {
            log.info("==> [dev 模式] 验证码不发送短信,请从日志获取: 手机号 {}, 验证码 【{}】", phone, verificationCode);
        }

        // 存储验证码到 redis, 并设置过期时间为 3 分钟
        redisTemplate.opsForValue().set(key, verificationCode, 3, TimeUnit.MINUTES);

        return Response.success();
    }
}
