package top.enderherman.netdisk.utils;

import org.apache.commons.lang3.RandomStringUtils;


public class StringUtils {
    /**
     * 生成随机数
     * @param count 位数
     * @return 随机数
     */
    public static String getRandomNumber(Integer count) {
        return RandomStringUtils.random(count, false, true);
    }
    /**
     * 生成随机码
     * @param count
     * @return
     */
    public static String getRandomString(Integer count){
        return RandomStringUtils.random(count,true,true);
    }

}
