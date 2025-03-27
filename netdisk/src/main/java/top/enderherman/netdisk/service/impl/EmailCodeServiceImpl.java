package top.enderherman.netdisk.service.impl;

import jakarta.annotation.Resource;
import jakarta.mail.internet.MimeMessage;
import lombok.extern.slf4j.Slf4j;

import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import top.enderherman.netdisk.common.component.RedisComponent;
import top.enderherman.netdisk.common.config.AppConfig;
import top.enderherman.netdisk.common.config.SystemConfig;
import top.enderherman.netdisk.common.constants.Constants;
import top.enderherman.netdisk.entity.pojo.EmailCode;
import top.enderherman.netdisk.entity.pojo.User;
import top.enderherman.netdisk.entity.query.EmailCodeQuery;
import top.enderherman.netdisk.entity.query.UserQuery;
import top.enderherman.netdisk.common.exceptions.BusinessException;
import top.enderherman.netdisk.mapper.EmailCodeMapper;
import top.enderherman.netdisk.mapper.UserMapper;
import top.enderherman.netdisk.service.EmailCodeService;
import top.enderherman.netdisk.common.utils.StringUtils;

import java.util.Date;


/**
 * @author Enderherman
 * @date 2024/12/24
 * 邮箱验证码 业务接口
 */
@Slf4j
@Service("emailCodeService")
public class EmailCodeServiceImpl implements EmailCodeService {


    @Resource
    private UserMapper<User, UserQuery> userMapper;

    @Resource
    private EmailCodeMapper<EmailCode, EmailCodeQuery> emailCodeMapper;

    @Resource
    private JavaMailSender javaMailSender;

    @Resource
    private AppConfig appConfig;

    @Resource
    private RedisComponent redisComponent;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void sendEmailCode(String email, Integer type) {
        //0注册 1找回
        if (type.equals(Constants.ZERO)) {
            User user = userMapper.selectByEmail(email);
            if (user != null) {
                throw new BusinessException("邮箱已经存在");
            }
        }

        //1.获取验证码
        String code = StringUtils.getRandomNumber(Constants.LENGTH_5);

        //2.发送验证码给用户
        send(email, code);

        //3.设置之前验证码为过期
        emailCodeMapper.disableEmailCode(email);

        //4.存储验证码
        EmailCode emailCode = new EmailCode(email, code, new Date(), Constants.ZERO);
        emailCodeMapper.insert(emailCode);

    }

    void send(String toEmail, String code) {
        try {
            MimeMessage message = javaMailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true);
            //1.设置发送人
            helper.setFrom(appConfig.getSendUserName());
            helper.setTo(toEmail);
            //2.设置发送主题
            System.out.println("1.开始时间：" + new Date());
            SystemConfig systemConfig = redisComponent.getSystemConfig();
            System.out.println("2.redis操作完成时间：" + new Date());
            helper.setSubject(systemConfig.getRegisterEmailTitle());
            //3.设置发送内容
            helper.setText(String.format(systemConfig.getRegisterEmailContent(), code));
            //4.邮件发送时间
            helper.setSentDate(new Date());
            //5.邮件发送
            System.out.println("3.发送开始时间：" + new Date());
            javaMailSender.send(message);
            System.out.println("4.发送完成时间：" + new Date());
        } catch (Exception e) {
            log.error("邮件发送失败", e);
            throw new BusinessException("邮件发送失败");
        }
    }

    /**
     * 校验验证码
     *
     * @param email 邮箱
     * @param code  验证码
     */
    @Override
    public void checkEmailCode(String email, String code) {
        EmailCode emailCode = emailCodeMapper.selectByEmailAndCode(email, code);
        if (emailCode == null) {
            throw new BusinessException("邮箱验证码错误");
        }
        if (emailCode.getStatus() == 1 ||
                System.currentTimeMillis() - emailCode.getCreateTime().getTime() > Constants.LENGTH_15 * 60 * 1000) {
            throw new BusinessException("邮箱验证码已过期");
        }
        emailCodeMapper.disableEmailCode(email);
    }
}
