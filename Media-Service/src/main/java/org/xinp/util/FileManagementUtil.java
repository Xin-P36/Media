package org.xinp.util;


import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.xinp.constant.FileOperationError;
import org.xinp.exception.FileOperationException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.Comparator;

/**
 * 文件和目录管理工具类 (作为Spring Service)
 * 核心设计：
 * 1. 所有路径操作均基于项目根目录的相对路径，确保安全性。
 * 2. 方法成功时返回有用的结果（如Path对象），失败时抛出 {@link FileOperationException}。
 * 3. 异常中包含明确的错误码 {@link FileOperationError}，便于上层统一处理。
 * 4. 内部实现了路径遍历攻击的安全检查。
 */
@Component
@Slf4j
public class FileManagementUtil {

    private final Path rootPath;
    public FileManagementUtil(@Qualifier("projectPath") Path projectPath) {
        this.rootPath = projectPath;
    }

    // =========================================================================================
    // 文件夹操作 (增 / 删 / 改 / 查 / 移)
    // =========================================================================================

    /**
     * 创建文件夹，包括所有不存在的父目录。
     *
     * @param relativeDirPath 要创建的文件夹的相对路径 (e.g., "data/invoices/2023")
     * @return 创建成功的文件夹的绝对路径 Path 对象。
     * @throws FileOperationException 如果路径已存在但不是目录 (RESOURCE_ALREADY_EXISTS)，或因权限问题无法创建 (IO_EXCEPTION)。
     */
    public Path createDirectory(String relativeDirPath) {
        // 检查路径
        Path targetPath = resolveSafely(relativeDirPath);
        if (Files.exists(targetPath)) {
            if (Files.isDirectory(targetPath)) {
                log.warn("目录已存在，无需创建: {}", targetPath);
                return targetPath; // 幂等性：如果已存在且是目录，直接返回
            } else {
                throw new FileOperationException(FileOperationError.RESOURCE_ALREADY_EXISTS, "无法创建目录，因为同名文件已存在: " + relativeDirPath);
            }
        }
        try {
            log.info("尝试创建目录: {}", targetPath);
            return Files.createDirectories(targetPath);
        } catch (IOException e) {
            throw new FileOperationException(FileOperationError.IO_EXCEPTION, "创建目录失败: " + relativeDirPath, e);
        }
    }

    /**
     * 删除文件夹及其所有内容（递归删除）。
     *
     * @param relativeDirPath 要删除的文件夹的相对路径。
     * @throws FileOperationException 如果路径不存在 (RESOURCE_NOT_FOUND)，路径不是一个目录 (NOT_A_DIRECTORY)，或删除失败 (IO_EXCEPTION)。
     */
    public void deleteDirectory(String relativeDirPath) {
        Path targetPath = resolveSafely(relativeDirPath);
        if (!Files.exists(targetPath)) {
            throw new FileOperationException(FileOperationError.RESOURCE_NOT_FOUND, "要删除的目录不存在: " + relativeDirPath);
        }
        if (!Files.isDirectory(targetPath)) {
            throw new FileOperationException(FileOperationError.NOT_A_DIRECTORY, "无法删除，因为目标不是一个目录: " + relativeDirPath);
        }
        try (Stream<Path> walk = Files.walk(targetPath)) {
            walk.sorted(Comparator.reverseOrder()) // 必须反向排序，先删除内容再删除容器
                    .forEach(path -> {
                        try {
                            Files.delete(path);
                        } catch (IOException e) {
                            // 包装运行时异常以中断 forEach
                            throw new RuntimeException(e);
                        }
                    });
            log.info("成功删除目录及其内容: {}", targetPath);
        } catch (Exception e) {
            // 捕获由 forEach 内部抛出的异常
            throw new FileOperationException(FileOperationError.IO_EXCEPTION, "删除目录期间发生错误: " + relativeDirPath, e);
        }
    }

    /**
     * 移动或重命名文件夹。
     *
     * @param oldRelativePath 原始文件夹的相对路径。
     * @param newRelativePath 新的文件夹相对路径。
     * @return 移动/重命名后的文件夹的绝对路径。
     * @throws FileOperationException 如果源不存在 (RESOURCE_NOT_FOUND)，源不是目录 (NOT_A_DIRECTORY)，或目标已存在 (RESOURCE_ALREADY_EXISTS)。
     */
    public Path moveDirectory(String oldRelativePath, String newRelativePath) {
        Path sourcePath = resolveSafely(oldRelativePath);
        Path destinationPath = resolveSafely(newRelativePath);

        if (!Files.isDirectory(sourcePath)) {
            throw new FileOperationException(FileOperationError.RESOURCE_NOT_FOUND, "源不是一个有效的目录: " + oldRelativePath);
        }
        if (Files.exists(destinationPath)) {
            throw new FileOperationException(FileOperationError.RESOURCE_ALREADY_EXISTS, "目标路径已存在: " + newRelativePath);
        }
        try {
            // 确保父目录存在
            Files.createDirectories(destinationPath.getParent());
            log.info("移动目录从 {} 到 {}", sourcePath, destinationPath);
            return Files.move(sourcePath, destinationPath, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            throw new FileOperationException(FileOperationError.IO_EXCEPTION, "移动目录失败", e);
        }
    }

    /**
     * 根据名称模糊查找文件夹（深度遍历）。
     *
     * @param fuzzyName 文件夹名称的片段，不区分大小写。
     * @return 匹配的文件夹相对路径列表 (List<String>)。
     * @throws FileOperationException 如果遍历时发生IO错误。
     */
    public List<String> findDirectories(String fuzzyName) {
        if (!StringUtils.hasText(fuzzyName)) {
            return List.of();
        }
        try (Stream<Path> walk = Files.walk(rootPath)) {
            return walk
                    .filter(Files::isDirectory)
                    .filter(path -> path.getFileName().toString().toLowerCase().contains(fuzzyName.toLowerCase()))
                    .map(rootPath::relativize) // 转换为相对路径
                    .map(Path::toString)
                    .collect(Collectors.toList());
        } catch (IOException e) {
            throw new FileOperationException(FileOperationError.IO_EXCEPTION, "查找目录时发生错误", e);
        }
    }

    // =========================================================================================
    // 文件操作 (增 / 删 / 改 / 查 / 移)
    // =========================================================================================

    /**
     * 创建新文件并写入数据。
     *
     * @param relativeDirPath 文件要存放的目录的相对路径。
     * @param filename        完整的文件名，包含扩展名 (e.g., "report.pdf")。
     * @param data            文件的字节数据。
     * @return 创建的文件的绝对路径。
     * @throws FileOperationException 如果文件已存在 (RESOURCE_ALREADY_EXISTS) 或创建失败 (IO_EXCEPTION)。
     */
    public Path createFile(String relativeDirPath, String filename, byte[] data) {
        createDirectory(relativeDirPath); // 确保目标目录存在
        Path filePath = resolveSafely(relativeDirPath).resolve(filename);

        if (Files.exists(filePath)) {
            throw new FileOperationException(FileOperationError.RESOURCE_ALREADY_EXISTS, "文件已存在: " + rootPath.relativize(filePath));
        }
        try {
            log.info("创建文件: {}", filePath);
            return Files.write(filePath, data, StandardOpenOption.CREATE_NEW);
        } catch (IOException e) {
            throw new FileOperationException(FileOperationError.IO_EXCEPTION, "创建文件失败: " + filename, e);
        }
    }

    /**
     * 读取文件数据。
     *
     * @param relativeFilePath 文件的相对路径。
     * @return 文件的字节数组 (byte[])。
     * @throws FileOperationException 如果文件不存在 (RESOURCE_NOT_FOUND) 或不是一个普通文件 (NOT_A_FILE)。
     */
    public byte[] readFile(String relativeFilePath) {
        Path filePath = resolveSafely(relativeFilePath);
        if (!Files.isRegularFile(filePath)) {
            throw new FileOperationException(FileOperationError.RESOURCE_NOT_FOUND, "文件未找到或目标不是一个文件: " + relativeFilePath);
        }
        try {
            return Files.readAllBytes(filePath);
        } catch (IOException e) {
            throw new FileOperationException(FileOperationError.IO_EXCEPTION, "读取文件失败: " + relativeFilePath, e);
        }
    }

    /**
     * 删除一个文件。
     *
     * @param relativeFilePath 要删除的文件的相对路径。
     * @throws FileOperationException 如果文件不存在 (RESOURCE_NOT_FOUND) 或删除失败 (IO_EXCEPTION)。
     */
    public void deleteFile(String relativeFilePath) {
        Path filePath = resolveSafely(relativeFilePath);
        try {
            boolean deleted = Files.deleteIfExists(filePath);
            if (!deleted) {
                // 如果 deleteIfExists 返回 false，意味着文件在检查时就不存在
                throw new FileOperationException(FileOperationError.RESOURCE_NOT_FOUND, "要删除的文件不存在: " + relativeFilePath);
            }
            log.info("成功删除文件: {}", filePath);
        } catch (IOException e) {
            // 这通常发生在没有删除权限的情况下
            throw new FileOperationException(FileOperationError.IO_EXCEPTION, "删除文件失败: " + relativeFilePath, e);
        }
    }

    /**
     * 移动或重命名文件。
     *
     * @param oldRelativePath 文件的原始相对路径。
     * @param newRelativePath 文件的新相对路径。
     * @return 移动/重命名后的文件的绝对路径。
     * @throws FileOperationException 如果源文件不存在 (RESOURCE_NOT_FOUND)，或目标文件已存在 (RESOURCE_ALREADY_EXISTS)。
     */
    public Path moveFile(String oldRelativePath, String newRelativePath) {
        Path sourcePath = resolveSafely(oldRelativePath);
        Path destinationPath = resolveSafely(newRelativePath);

        if (!Files.isRegularFile(sourcePath)) {
            throw new FileOperationException(FileOperationError.RESOURCE_NOT_FOUND, "源文件不存在或不是一个文件: " + oldRelativePath);
        }
        if (Files.exists(destinationPath)) {
            throw new FileOperationException(FileOperationError.RESOURCE_ALREADY_EXISTS, "目标文件已存在: " + newRelativePath);
        }
        try {
            // 确保目标目录存在
            Files.createDirectories(destinationPath.getParent());
            log.info("移动文件从 {} 到 {}", sourcePath, destinationPath);
            return Files.move(sourcePath, destinationPath, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            throw new FileOperationException(FileOperationError.IO_EXCEPTION, "移动文件失败", e);
        }
    }

    // =========================================================================================
    // 辅助方法
    // =========================================================================================

    /**
     * 将相对路径安全地解析为基于根目录的绝对路径。
     * 此方法是本工具类的安全核心，可防止路径遍历攻击。
     *
     * @param relativePath 用户提供的相对路径。
     * @return 一个规范化的、在根目录之内的绝对路径 Path 对象。
     * @throws FileOperationException 如果路径为空或包含非法字符 (INVALID_PATH)，或试图访问根目录之外 (PATH_TRAVERSAL_ATTEMPT)。
     */
    public Path resolveSafely(String relativePath) {
        if (!StringUtils.hasText(relativePath)) {
            throw new FileOperationException(FileOperationError.INVALID_PATH, "相对路径不能为空。");
        }
        // normalize() 会解析 ".." 和 "."
        Path resolvedPath = this.rootPath.resolve(relativePath).normalize();

        // 安全检查：确保解析后的路径仍然在我们的根目录之内
        if (!resolvedPath.startsWith(this.rootPath)) {
            log.warn("检测到非法的路径访问尝试: {} -> {}", relativePath, resolvedPath);
            throw new FileOperationException(FileOperationError.PATH_TRAVERSAL_ATTEMPT, "非法路径，试图访问根目录之外的位置: " + relativePath);
        }
        return resolvedPath;
    }
}