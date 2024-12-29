package top.enderherman.netdisk.controller;

import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import top.enderherman.netdisk.common.BaseResponse;
import top.enderherman.netdisk.common.constants.Constants;
import top.enderherman.netdisk.common.exceptions.BusinessException;
import top.enderherman.netdisk.service.EmailCodeService;
import top.enderherman.netdisk.common.utils.ImageGenerator;

import java.io.IOException;

/**
 * 用户信息Controller
 */
@Slf4j
@RestController
@RequestMapping("accountController")
public class UserController extends BaseController {

    @Autowired
    private EmailCodeService emailCodeService;

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
    public BaseResponse<String> sendEmailCode(HttpSession session, String email, String checkCode, Integer type) {
        try {
            //1.校验图片验证码
            if (!checkCode.equalsIgnoreCase((String) session.getAttribute(Constants.CHECK_CODE_KEY_EMAIL)))
                throw new BusinessException("图片验证码错误");
            emailCodeService.sendEmailCode(email,type);
            return getSuccessResponse(null);
        } finally {
            //设置本次图片验证码失效
            session.removeAttribute(Constants.CHECK_CODE_KEY_EMAIL);
        }
    }

}
