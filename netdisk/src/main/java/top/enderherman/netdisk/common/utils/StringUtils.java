package top.enderherman.netdisk.common.utils;

import org.apache.commons.lang3.RandomStringUtils;


public class StringUtils {
    /**
     * 生成随机数
     * @param length 长度
     * @return 随机数
     */
    public static String getRandomNumber(Integer length) {
        return RandomStringUtils.random(length, false, true);
    }
    /**
     * 生成随机码
     * @param length 长度
     * @return 随机码
     */
    public static String getRandomString(Integer length){
        return RandomStringUtils.random(length,true,true);
    }

}
