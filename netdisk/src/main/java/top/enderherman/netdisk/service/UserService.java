package top.enderherman.netdisk.service;

public interface UserService {
    /**
     * 注册
     */
    void register(String email, String nickName, String password, String emailCode);
}
