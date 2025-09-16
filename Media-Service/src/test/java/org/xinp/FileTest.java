package org.xinp;


import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

@SpringBootTest
public class FileTest {
    @Test
    public void refile(){
        String property = System.getProperty("user.dir");
        System.out.println(property);
    }

    @Test
    public void processFiles() {
        // 1. 获取当前工作目录 (jar包所在的目录)
        String userDir = System.getProperty("user.dir");
        System.out.println("当前工作目录: " + userDir);

        // 2. 使用 Paths.get() 构造目标文件夹的路径
        // 这是一个跨平台安全的方式，它会自动处理路径分隔符（/ 或 \）
        Path folderPath = Paths.get(userDir, "file");
        System.out.println("目标文件夹的绝对路径: " + folderPath.toAbsolutePath());

        // 3. 检查文件夹是否存在
        if (!folderPath.toFile().exists() || !folderPath.toFile().isDirectory()) {
            System.err.println("错误: 文件夹 " + folderPath + " 不存在或不是一个目录。");
            return;
        }

        // 接下来就可以对 folderPath 进行操作了
        // 例如，列出所有文件
        try {
            Files.list(folderPath).forEach(file -> {
                System.out.println("找到文件: " + file.getFileName());
                // 在这里添加你的文件处理逻辑
            });
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
    @Autowired
    private Path projectPath;
    @Test
    public void processFiles2() {
        System.out.println(projectPath);
    }
}
