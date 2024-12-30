package top.enderherman.netdisk.controller;

import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import top.enderherman.netdisk.annotation.GlobalInterceptor;
import top.enderherman.netdisk.annotation.VerifyParam;
import top.enderherman.netdisk.common.BaseResponse;
import top.enderherman.netdisk.common.constants.Constants;
import top.enderherman.netdisk.common.exceptions.BusinessException;
import top.enderherman.netdisk.entity.enums.VerifyRegexEnum;
import top.enderherman.netdisk.service.EmailCodeService;
import top.enderherman.netdisk.common.utils.ImageGenerator;
import top.enderherman.netdisk.service.UserService;

import java.io.IOException;
import java.util.Date;

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
            if (!checkCode.equalsIgnoreCase((String) session.getAttribute(Constants.CHECK_CODE_KEY_EMAIL)))
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
            userService.register(email,nickName,password,emailCode);
            return getSuccessResponse(null);
        } finally {
            //设置本次图片验证码失效
            session.removeAttribute(Constants.CHECK_CODE_KEY_EMAIL);
        }
    }

}
