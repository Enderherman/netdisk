package top.enderherman.netdisk.service.impl;

import org.springframework.stereotype.Service;
import top.enderherman.netdisk.common.Constants;
import top.enderherman.netdisk.entity.pojo.User;
import top.enderherman.netdisk.service.EmailCodeService;

/**
 * @author Enderherman
 * @date 2024/12/24
 * 邮箱验证码 业务接口
 */
@Service("emailCodeService")
public class EmailCodeServiceImpl implements EmailCodeService {

    //TODO 邮件验证码基础实现
    @Override
    public void sendEmailCode(String email, Integer type) {
        //0注册 1找回
        if (type == Constants.ZERO) {
            User user;
        }
    }
}
