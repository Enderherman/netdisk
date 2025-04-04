package top.enderherman.netdisk.common.utils;

import lombok.extern.slf4j.Slf4j;
import top.enderherman.netdisk.common.exceptions.BusinessException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

/**
 * JAVA执行系统命令
 */
public class ProcessUtils {
    private static final Logger logger = LoggerFactory.getLogger(ProcessUtils.class);

    public static String executeCommand(String cmd, Boolean outprintLog) throws BusinessException {
        if (StringUtils.isEmpty(cmd)){
            logger.error("指令执行失败，要执行的指令为空");
            return null;
        }

        Runtime runtime = Runtime.getRuntime();
        Process process = null;
        try {
            process = Runtime.getRuntime().exec(cmd);
            /**
             * 执行ffmpeg指令
             * 取出输出流和错误流的信息
             * 注意：必须取出ffmpeg在执行命令过程中产生的输出信息，如果不取出的话输出流信息填满JVM存储输出信息的缓冲区时线程就会阻塞
             */
            PrintStream errorStream = new PrintStream(process.getErrorStream());
            PrintStream inputStream = new PrintStream(process.getInputStream());
            errorStream.start();
            inputStream.start();
            //等待ffmpeg命令执行完毕
            process.waitFor();
            //获取执行结果字符串
            String result = errorStream.stringBuffer.append(inputStream.stringBuffer + "\n").toString();
            //输出执行的命令信息

            if (outprintLog){
                logger.info("执行命令:{},已执行完毕,执行结果:{}", cmd, result);
            }else {
                logger.info("执行命令:{},已执行完毕", cmd);
            }
            return result;
        }catch (Exception e){
            e.printStackTrace();
            throw new BusinessException("视频转换失败");
        }finally {
            if (process != null){
                ProcessKiller processKiller = new ProcessKiller(process);
                runtime.addShutdownHook(processKiller);
            }
        }
    }

    /**
     * 在程序退出前结束已有的ffmpeg进程
     */
    public static class ProcessKiller extends Thread{
        private Process process;

        public ProcessKiller(Process process){
            this.process = process;
        }

        @Override
        public void run() {
            this.process.destroy();
        }
    }

    /**
     * 用于取出ffmpeg线程执行过程中产生的各种输出和错误流信息
     */
    static class PrintStream extends Thread{
        InputStream inputStream = null;
        BufferedReader bufferedReader = null;
        StringBuffer stringBuffer = new StringBuffer();

        public PrintStream(InputStream inputStream){
            this.inputStream = inputStream;
        }

        @Override
        public void run() {
            try {
                if (inputStream == null){
                    return;
                }
                bufferedReader = new BufferedReader(new InputStreamReader(inputStream));
                String line = null;
                while ((line = bufferedReader.readLine()) != null){
                    stringBuffer.append(line);
                }
            }catch (Exception e){
                logger.error("读取输入流出错了！错误信息:" + e.getMessage());
            }finally {
                try {
                    if (bufferedReader != null){
                        bufferedReader.close();
                    }
                    if (inputStream != null){
                        inputStream.close();
                    }
                }catch (IOException e){
                    logger.error("调用PrintStream读取输出流后，关闭流时出错!");
                }
            }
        }
    }
}
