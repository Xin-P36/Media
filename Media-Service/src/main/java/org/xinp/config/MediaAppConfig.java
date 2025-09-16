package org.xinp.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.nio.file.Path;
import java.nio.file.Paths;

@Configuration
@Slf4j
public class MediaAppConfig {
    /**
     * @return 获取当前项目存储根目录
     */
    @Bean("projectPath")
    public Path projectPath() {
        String userDir = System.getProperty("user.dir");
        //将路径转为Path
        Path path = Paths.get(userDir);
        log.info("应用存储根目录已初始化: {}", path);
        return path;
    }
}

