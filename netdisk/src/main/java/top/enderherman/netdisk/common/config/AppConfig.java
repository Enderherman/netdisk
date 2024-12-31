package top.enderherman.netdisk.common.config;

import lombok.Data;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Data
@Component("appConfig")
public class AppConfig {

    @Value("${spring.mail.username:}")
    private String sendUserName;

    @Value("${admin.email:}")
    private String adminEmails;

    @Value("${project.folder:}")
    private String projectFolder;
}
