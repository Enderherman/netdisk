package top.enderherman.netdisk.service.impl;

import jakarta.annotation.Resource;
import org.apache.commons.lang3.ArrayUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import top.enderherman.netdisk.common.component.RedisComponent;
import top.enderherman.netdisk.common.config.AppConfig;
import top.enderherman.netdisk.common.config.SystemConfig;
import top.enderherman.netdisk.common.constants.Constants;
import top.enderherman.netdisk.common.exceptions.BusinessException;
import top.enderherman.netdisk.common.utils.StringUtils;
import top.enderherman.netdisk.entity.dto.SessionWebUserDto;
import top.enderherman.netdisk.entity.dto.UserSpaceDto;
import top.enderherman.netdisk.entity.enums.PageSize;
import top.enderherman.netdisk.entity.enums.UserStatusEnum;
import top.enderherman.netdisk.entity.pojo.FileInfo;
import top.enderherman.netdisk.entity.pojo.User;
import top.enderherman.netdisk.entity.query.FileQuery;
import top.enderherman.netdisk.entity.query.SimplePage;
import top.enderherman.netdisk.entity.query.UserQuery;
import top.enderherman.netdisk.entity.vo.PaginationResultVO;
import top.enderherman.netdisk.mapper.FileMapper;
import top.enderherman.netdisk.mapper.UserMapper;
import top.enderherman.netdisk.service.EmailCodeService;
import top.enderherman.netdisk.service.UserService;

import java.util.Date;
import java.util.List;

@Service("userService")
public class UserServiceImpl implements UserService {

    @Resource
    private UserMapper<User, UserQuery> userMapper;

    @Resource
    private FileMapper<FileInfo, FileQuery> fileInfoMapper;

    @Resource
    private EmailCodeService emailCodeService;

    @Resource
    private RedisComponent redisComponent;

    @Resource
    private AppConfig appConfig;

    /**
     * 根据条件查询列表
     */
    @Override
    public List<User> findListByParam(UserQuery param) {
        return this.userMapper.selectList(param);
    }

    /**
     * 根据条件查询列表
     */
    @Override
    public Integer findCountByParam(UserQuery param) {
        return this.userMapper.selectCount(param);
    }

    /**
     * 分页查询方法
     */
    @Override
    public PaginationResultVO<User> findListByPage(UserQuery param) {
        int count = this.findCountByParam(param);
        int pageSize = param.getPageSize() == null ? PageSize.SIZE15.getSize() : param.getPageSize();

        SimplePage page = new SimplePage(param.getPageNo(), count, pageSize);
        param.setSimplePage(page);
        List<User> list = this.findListByParam(param);
        return new PaginationResultVO<>(count, page.getPageSize(), page.getPageNo(), page.getPageTotal(), list);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void register(String email, String nickName, String password, String emailCode) {
        User user = userMapper.selectByEmail(email);
        if (user != null) {
            throw new BusinessException("邮箱账号已经存在");
        }
        User nickNameUser = userMapper.selectByNickName(nickName);
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
        userMapper.insert(user);
    }

    @Override
    public SessionWebUserDto login(String email, String password) {
        //1.校验账号密码以及账号状态
        User user = userMapper.selectByEmail(email);
        if (user == null || !user.getPassword().equals(password)) {
            throw new BusinessException("账户或密码错误");
        }
        if (user.getStatus().equals(UserStatusEnum.DISABLE.getStatus())) {
            throw new BusinessException("账户已被禁用");
        }

        //2.更新最近登录时间
        User updateUser = new User();
        updateUser.setLastLoginTime(new Date());
        userMapper.updateByUserId(updateUser, user.getUserId());


        //3.设置用户登录信息
        SessionWebUserDto dto = new SessionWebUserDto();
        dto.setUserId(user.getUserId());
        dto.setNickName(user.getNickName());
        dto.setIsAdmin(ArrayUtils.contains(appConfig.getAdminEmails().split(","), email));

        //4.设置用户空间使用情况
        UserSpaceDto userSpaceDto = new UserSpaceDto();
        Long useSpace = fileInfoMapper.selectUseSpace(user.getUserId());
        userSpaceDto.setUseSpace(useSpace);
        userSpaceDto.setTotalSpace(user.getTotalSpace());
        redisComponent.saveUserSpaceDto(user.getUserId(), userSpaceDto);
        return dto;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void resetPwd(String email, String password, String emailCode) {
        //1.校验账号密码以及账号状态
        User user = userMapper.selectByEmail(email);
        if (user == null) {
            throw new BusinessException("账户不存在");
        }
        //2.校验验证码
        emailCodeService.checkEmailCode(email, emailCode);
        User userUpdate = new User();
        userUpdate.setPassword(StringUtils.encodingByMd5(password));
        userMapper.updateByEmail(userUpdate, email);

    }

    @Override
    public void updateUserByUserId(User bean, String userId) {
        userMapper.updateByUserId(bean, userId);
    }

    @Override
    public SessionWebUserDto qqLogin(String code) {
        //TODO QQLogin
        return null;
    }

    @Override
    public void updateUserStatus(String userId, Integer status) {
        User userInfo = new User();
        userInfo.setStatus(status);
        if(UserStatusEnum.DISABLE.getStatus().equals(status)){
            userInfo.setUseSpace(0L);
        }
        userMapper.updateByUserId(userInfo, userId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void changeUserSpace(String userId, Integer changeSpace) {
        Long space = changeSpace * Constants.MB;
        userMapper.updateUserSpace(userId, null, space);
        redisComponent.resetUserSpaceUse(userId);
    }

    @Override
    public User getUserInfoByUserId(String userId) {
        return this.userMapper.selectByUserId(userId);

    }
}
