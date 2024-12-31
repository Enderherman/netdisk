package top.enderherman.netdisk.service;

import top.enderherman.netdisk.entity.dto.SessionWebUserDto;
import top.enderherman.netdisk.entity.pojo.User;

public interface UserService {
    /**
     * 注册
     */
    void register(String email, String nickName, String password, String emailCode);

    SessionWebUserDto login(String email, String password);

    void resetPwd(String email, String password, String emailCode);

    void updateUserByUserId(User user, String userId);
}
