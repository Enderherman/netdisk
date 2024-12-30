package top.enderherman.netdisk.service.impl;

import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import top.enderherman.netdisk.common.component.RedisComponent;
import top.enderherman.netdisk.common.config.SystemConfig;
import top.enderherman.netdisk.common.constants.Constants;
import top.enderherman.netdisk.common.exceptions.BusinessException;
import top.enderherman.netdisk.common.utils.StringUtils;
import top.enderherman.netdisk.entity.enums.UserStatusEnum;
import top.enderherman.netdisk.entity.pojo.File;
import top.enderherman.netdisk.entity.pojo.User;
import top.enderherman.netdisk.entity.query.FileQuery;
import top.enderherman.netdisk.entity.query.UserQuery;
import top.enderherman.netdisk.mapper.FileMapper;
import top.enderherman.netdisk.mapper.UserMapper;
import top.enderherman.netdisk.service.EmailCodeService;
import top.enderherman.netdisk.service.UserService;

import java.util.Date;

@Service("userService")
public class UserServiceImpl implements UserService {

    @Resource
    private UserMapper<User, UserQuery> userInfoMapper;

    @Resource
    private FileMapper<File, FileQuery> fileInfoMapper;

    @Resource
    private EmailCodeService emailCodeService;

    @Resource
    private RedisComponent redisComponent;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void register(String email, String nickName, String password, String emailCode) {
        User user = userInfoMapper.selectByEmail(email);
        if (user != null) {
            throw new BusinessException("邮箱账号已经存在");
        }
        User nickNameUser = userInfoMapper.selectByNickName(nickName);
        if (nickNameUser != null) {
            throw new BusinessException("昵称已经存在");
        }
        //校验邮箱验证码
        emailCodeService.checkEmailCode(email, emailCode);
        //用户个人信息初始化
        String userId = StringUtils.getRandomNumber(Constants.LENGTH_10);
        user = new User();
        user.setUserId(userId);
        user.setNickName(nickName);
        user.setEmail(email);
        user.setPassword(StringUtils.encodingByMd5(password));
        user.setCreateTime(new Date());
        user.setStatus(UserStatusEnum.ENABLE.getStatus());
        user.setUseSpace(0L);
        //容量初始化
        SystemConfig systemConfig = redisComponent.getSystemConfig();
        user.setTotalSpace(systemConfig.getUserInitUseSpace() * Constants.MB);
        //插入用户数据
        userInfoMapper.insert(user);
    }
}
