package org.xinp.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.jdbc.datasource.init.DataSourceInitializer;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;

import javax.sql.DataSource;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 数据库初始化
 */
@Configuration
@Slf4j
public class ConditionalDatabaseInitializer {

    // 数据库初始化文件：src/main/resources/schema.sql
    private static final String SCHEMA_SCRIPT_LOCATION = "classpath:schema.sql";

    @Value("${media.initial-account}")
    private String initialAccount;

    @Value("${media.initial-password}")
    private String initialPassword;

    @Value("${media.initial-nickname}")
    private String initialNickname;

    /**
     * 这个 Bean 会在 Spring 初始化数据源后被调用, 并执行数据库初始化逻辑。
     * 数据库存在则跳过 schema.sql，没初始化则执行 schema.sql。
     *
     * @param dataSource  由 Spring 自动注入的数据源
     * @param projectPath 定义的项目根路径 Bean
     * @param jdbcUrl     从 application.yml 中获取的数据库URL
     * @return 配置好的 DataSourceInitializer
     */
    @Bean
    public DataSourceInitializer dataSourceInitializer(
            DataSource dataSource,
            @Qualifier("projectPath") Path projectPath,
            @Value("${spring.datasource.url}") String jdbcUrl
    ) throws IOException {

        // 从JDBC URL中解析出数据库文件名 (例如 "media_manager.db")
        String dbFileName = jdbcUrl.substring(jdbcUrl.lastIndexOf(':') + 1);
        Path dbFilePath = projectPath.resolve(dbFileName);
        log.info("数据库文件路径检测: {}", dbFilePath);

        // 创建一个SQL脚本执行器
        final ResourceDatabasePopulator populator = new ResourceDatabasePopulator();
        populator.setContinueOnError(false); // 如果脚本出错，则停止

        // 创建数据源初始化器-用于初始化数据库
        final DataSourceInitializer initializer = new DataSourceInitializer();
        initializer.setDataSource(dataSource);
        initializer.setDatabasePopulator(populator);

        // 检查数据库文件是否存在。如果存在，则禁用此初始化器。
        if (Files.exists(dbFilePath)) {
            log.info("数据库文件 '{}' 已存在，跳过 schema.sql 初始化。", dbFileName);
            initializer.setEnabled(false);
            return initializer;
        }

        if (initialPassword == null || initialPassword.isBlank()) {
            throw new IllegalStateException("首次初始化数据库需要配置环境变量 MEDIA_INITIAL_PASSWORD");
        }

        Resource[] scripts = new PathMatchingResourcePatternResolver().getResources(SCHEMA_SCRIPT_LOCATION);
        if (scripts.length == 0) {
            log.warn("未找到数据库初始化脚本: {}", SCHEMA_SCRIPT_LOCATION);
        } else {
            populator.addScript(buildInitializedSchema(scripts[0]));
        }

        log.info("数据库文件 '{}' 不存在，将执行 schema.sql进行初始化。", dbFileName);
        initializer.setEnabled(true);
        return initializer;
    }

    private Resource buildInitializedSchema(Resource schemaScript) throws IOException {
        String sql;
        try (InputStream inputStream = schemaScript.getInputStream()) {
            sql = new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
        }
        sql = sql.replace("__INITIAL_ACCOUNT__", escapeSql(initialAccount))
                .replace("__INITIAL_PASSWORD__", escapeSql(initialPassword))
                .replace("__INITIAL_NICKNAME__", escapeSql(initialNickname));
        return new ByteArrayResource(sql.getBytes(StandardCharsets.UTF_8));
    }

    private String escapeSql(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("'", "''");
    }
}
