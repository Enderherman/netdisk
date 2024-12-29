package top.enderherman.netdisk.common.component;

import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import top.enderherman.netdisk.common.config.EmailConfig;
import top.enderherman.netdisk.common.constants.Constants;
import top.enderherman.netdisk.common.utils.RedisUtils;

@Slf4j
@Component("redisComponent")
public class RedisComponent {

    @Resource
    private RedisUtils redisUtils;

    public EmailConfig getEmailConfig() {
        EmailConfig emailConfig = (EmailConfig) redisUtils.get(Constants.REDIS_KEY_SYS_SETTING);
        if (emailConfig == null) {
            emailConfig = new EmailConfig();
            redisUtils.set(Constants.REDIS_KEY_SYS_SETTING, emailConfig);
        }

        return emailConfig;
    }

}
