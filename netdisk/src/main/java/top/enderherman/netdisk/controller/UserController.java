package top.enderherman.netdisk.controller;

import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import top.enderherman.netdisk.annotation.GlobalInterceptor;
import top.enderherman.netdisk.annotation.VerifyParam;
import top.enderherman.netdisk.common.BaseResponse;
import top.enderherman.netdisk.common.component.RedisComponent;
import top.enderherman.netdisk.common.config.AppConfig;
import top.enderherman.netdisk.common.constants.Constants;
import top.enderherman.netdisk.common.exceptions.BusinessException;
import top.enderherman.netdisk.common.utils.StringUtils;
import top.enderherman.netdisk.entity.dto.SessionWebUserDto;
import top.enderherman.netdisk.entity.dto.UserSpaceDto;
import top.enderherman.netdisk.entity.enums.ResponseCodeEnum;
import top.enderherman.netdisk.entity.enums.VerifyRegexEnum;
import top.enderherman.netdisk.entity.pojo.User;
import top.enderherman.netdisk.service.EmailCodeService;
import top.enderherman.netdisk.common.utils.ImageGenerator;
import top.enderherman.netdisk.service.UserService;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.lang.reflect.Field;

/**
 * 用户信息Controller
 */
@Slf4j
@RestController("accountController")
@RequestMapping()
public class UserController extends BaseController {

    @Resource
    private EmailCodeService emailCodeService;

    @Resource
    private UserService userService;

    @Resource
    private AppConfig appConfig;

    @Resource
    private RedisComponent redisComponent;

    @GetMapping("/checkCode")
    public void checkCode(HttpServletResponse response, HttpSession session, Integer type) {
        try {
            //1.生成图片
            ImageGenerator imageGenerator = new ImageGenerator(130, 38, 5, 10);
            //2.设置HTTP响应头
            response.setHeader("Pragma", "no-cache");
            response.setHeader("Cache-Control", "no-cache");
            response.setDateHeader("Expires", 0);
            response.setContentType("image/jpeg");
            //3.存储验证码到session中
            String code = imageGenerator.getCode();
            //图片验证码是登录用的
            if (type == null || type == 0) {
                session.setAttribute(Constants.CHECK_CODE_KEY, code);
            } else {
                session.setAttribute(Constants.CHECK_CODE_KEY_EMAIL, code);
            }
            //4.将验证码图像写入到响应流中
            imageGenerator.write(response.getOutputStream());
        } catch (IOException e) {
            log.error("error Is:", e);
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
        }
    }

    @PostMapping("/sendEmailCode")
    @GlobalInterceptor(checkParams = true, checkLogin = false)
    public BaseResponse<?> sendEmailCode(HttpSession session,
                                         @VerifyParam(required = true, regex = VerifyRegexEnum.EMAIL, max = 150) String email,
                                         @VerifyParam(required = true) String checkCode,
                                         @VerifyParam(required = true) Integer type) {
        try {
            //1.校验图片验证码
            if (checkCode.equalsIgnoreCase((String) session.getAttribute(Constants.CHECK_CODE_KEY_EMAIL)))
                throw new BusinessException("图片验证码错误");
            emailCodeService.sendEmailCode(email, type);
            return getSuccessResponse(null);
        } finally {
            //设置本次图片验证码失效
            session.removeAttribute(Constants.CHECK_CODE_KEY_EMAIL);
        }
    }

    @PostMapping("/register")
    @GlobalInterceptor(checkParams = true, checkLogin = false)
    public BaseResponse<?> register(HttpSession session,
                                    @VerifyParam(required = true, regex = VerifyRegexEnum.EMAIL, max = 150) String email,
                                    @VerifyParam(required = true) String nickName,
                                    @VerifyParam(required = true, regex = VerifyRegexEnum.PASSWORD, max = 18, min = 8) String password,
                                    @VerifyParam(required = true) String checkCode,
                                    @VerifyParam(required = true) String emailCode) {
        try {
            //1.校验图片验证码
            if (!checkCode.equalsIgnoreCase((String) session.getAttribute(Constants.CHECK_CODE_KEY)))
                throw new BusinessException("图片验证码错误");
            userService.register(email, nickName, password, emailCode);
            return getSuccessResponse(null);
        } finally {
            //设置本次图片验证码失效
            session.removeAttribute(Constants.CHECK_CODE_KEY_EMAIL);
        }
    }

    @PostMapping("/login")
    @GlobalInterceptor(checkParams = true, checkLogin = false)
    public BaseResponse<?> login(HttpSession session,
                                 @VerifyParam(required = true, regex = VerifyRegexEnum.EMAIL, max = 150) String email,
                                 @VerifyParam(required = true) String password,
                                 @VerifyParam(required = true) String checkCode) {
        try {
            //1.校验图片验证码
            if (checkCode.equalsIgnoreCase((String) session.getAttribute(Constants.CHECK_CODE_KEY)))
                throw new BusinessException("图片验证码错误");
            SessionWebUserDto sessionWebUserDto = userService.login(email, password);
            session.setAttribute(Constants.SESSION_KEY, sessionWebUserDto);
            return getSuccessResponse(sessionWebUserDto);
        } finally {
            //设置本次图片验证码失效
            session.removeAttribute(Constants.CHECK_CODE_KEY);
        }
    }

    @RequestMapping("/getAvatar/{userId}")
    @GlobalInterceptor(checkParams = true, checkLogin = false)
    public void getAvatar(HttpServletResponse response,
                          @VerifyParam(required = true) @PathVariable("userId") String userId) {
        //头像文件夹路径
        String avatarFolderName = Constants.FILE_FOLDER_FILE + Constants.FILE_FOLDER_AVATAR_NAME;
        File folder = new File(appConfig.getProjectFolder() + avatarFolderName);
        if (!folder.exists()) {
            folder.mkdirs();
        }

        //头像路径
        String avatarPath = appConfig.getProjectFolder() + avatarFolderName + userId + Constants.AVATAR_SUFFIX;
        File avatar = new File(avatarPath);
        if (!avatar.exists()) {

            if (!new File(appConfig.getProjectFolder() + avatarFolderName + Constants.AVATAR_DEFAULT).exists()) {
                //默认头像不存在
                printNoDefaultImage(response);
            }
            //默认头像存在
            avatarPath = appConfig.getProjectFolder() + avatarFolderName + Constants.AVATAR_DEFAULT;
        }
        response.setContentType("image/jpg");
        writeFile(response, avatarPath);
    }

    /**
     * 图像不存在
     */
    private void printNoDefaultImage(HttpServletResponse response) {
        response.setHeader("Content-Type", "application/json;charset=UTF-8");
        response.setStatus(HttpStatus.OK.value());
        try (PrintWriter writer = response.getWriter()) {
            writer.print("请在头像目录添加 默认头像default_avatar.jpg");
        } catch (Exception e) {
            log.error("输出默认图失败", e);
            throw new BusinessException(ResponseCodeEnum.CODE_500);
        }
    }

    @GetMapping("/getUserInfo")
    @GlobalInterceptor(checkParams = true)
    public BaseResponse<?> getUserInfo(HttpSession session) {
        SessionWebUserDto userDto = getUserInfoFromSession(session);
        return getSuccessResponse(userDto);
    }


    @RequestMapping("/getUseSpace")
    @GlobalInterceptor(checkParams = true)
    public BaseResponse<?> getUserSpace(HttpSession session) {
        SessionWebUserDto userDto = getUserInfoFromSession(session);
        UserSpaceDto userSpaceDto = redisComponent.getUserSpace(userDto.getUserId());
        return getSuccessResponse(userSpaceDto);
    }


    @PostMapping("/resetPwd")
    @GlobalInterceptor(checkParams = true, checkLogin = false)
    public BaseResponse<?> resetPwd(HttpSession session,
                                    @VerifyParam(required = true) String email,
                                    @VerifyParam(required = true) String password,
                                    @VerifyParam(required = true) String checkCode,
                                    @VerifyParam(required = true) String emailCode) {
        try {
            if (!checkCode.equalsIgnoreCase((String) session.getAttribute(Constants.CHECK_CODE_KEY))) {
                throw new BusinessException("图片验证码不正确");
            }
            userService.resetPwd(email, password, emailCode);
            return getSuccessResponse(null);
        } finally {
            session.removeAttribute(Constants.CHECK_CODE_KEY_EMAIL);
        }
    }



    @RequestMapping("/updatePassword")
    @GlobalInterceptor(checkParams = true)
    public BaseResponse<?> updatePassword(HttpSession session,
                                          @VerifyParam(required = true, regex = VerifyRegexEnum.PASSWORD, min = 8, max = 18) String password) {
        User user = new User();
        user.setPassword(StringUtils.encodingByMd5(password));
        userService.updateUserByUserId(user, getUserInfoFromSession(session).getUserId());

        return getSuccessResponse(null);
    }

    @RequestMapping("/logout")
    public BaseResponse<?> logout(HttpSession session) {
        session.invalidate();
        return getSuccessResponse(null);
    }


    @RequestMapping("/updateUserAvatar")
    @GlobalInterceptor
    public BaseResponse<?> updateUserAvatar(HttpSession session, MultipartFile avatar) {
        //1.获取目标头像存储位置
        SessionWebUserDto webUserDto = getUserInfoFromSession(session);
        String avatarFolder = appConfig.getProjectFolder() + Constants.FILE_FOLDER_FILE + Constants.FILE_FOLDER_AVATAR_NAME;
        //2.头像文件设置及其上传
        File targetFileFolder = new File(avatarFolder);
        File targetFile = new File(targetFileFolder.getPath() + "/" + webUserDto.getUserId() + Constants.AVATAR_SUFFIX);
        if (!targetFileFolder.exists()) {
            targetFileFolder.mkdirs();
        }
        try {
            avatar.transferTo(targetFile);
        } catch (Exception e) {
            log.error("上传头像失败");
            throw new BusinessException(ResponseCodeEnum.CODE_905);
        }

        User userInfo = new User();
        userInfo.setQqAvatar("");
        userService.updateUserByUserId(userInfo, webUserDto.getUserId());
        webUserDto.setAvatar(null);
        session.setAttribute(Constants.SESSION_KEY, webUserDto);
        return getSuccessResponse(null);
    }
}
