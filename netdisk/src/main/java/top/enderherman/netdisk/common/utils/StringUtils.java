package top.enderherman.netdisk.common.utils;

import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.lang3.RandomStringUtils;


public class StringUtils {
    /**
     * 生成随机数
     *
     * @param length 长度
     * @return 随机数
     */
    public static String getRandomNumber(Integer length) {
        return RandomStringUtils.random(length, false, true);
    }

    /**
     * 生成随机码
     *
     * @param length 长度
     * @return 随机码
     */
    public static String getRandomString(Integer length) {
        return RandomStringUtils.random(length, true, true);
    }

    /**
     * 校验空
     *
     * @param str 字符串
     * @return 是否为空
     */
    public static boolean isEmpty(String str) {
        return null == str || "".equals(str) || "null".equals(str) || "\u0000".equals(str) || "".equals(str.trim());
    }

    /**
     * md5编码
     *
     * @param originString 密码
     * @return 编码后的密码
     */
    public static String encodingByMd5(String originString) {
        return isEmpty(originString) ? null : DigestUtils.md5Hex(originString);
    }
}
