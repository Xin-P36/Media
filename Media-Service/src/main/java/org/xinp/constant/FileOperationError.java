package org.xinp.constant;
/**
 * 文件操作错误码枚举
 * 定义了所有可预见的业务级别文件操作错误，每个错误码对应一个默认的错误消息。
 */
public enum FileOperationError {

    // --- 通用IO错误 ---
    IO_EXCEPTION(1001, "发生未知I/O错误"),

    // --- 路径相关错误 ---
    INVALID_PATH(2001, "路径无效或包含非法字符"),
    ACCESS_DENIED(2002, "权限不足，无法访问路径"),
    PATH_TRAVERSAL_ATTEMPT(2003, "检测到路径遍历攻击，操作被禁止"),

    // --- 资源状态错误 ---
    RESOURCE_NOT_FOUND(3001, "资源未找到"),
    RESOURCE_ALREADY_EXISTS(3002, "资源已存在"),

    // --- 操作特定错误 ---
    NOT_A_DIRECTORY(4001, "目标不是一个目录"),
    NOT_A_FILE(4002, "目标不是一个文件"),
    DIRECTORY_NOT_EMPTY(4003, "目录不为空，无法删除");

    private final int code;
    private final String defaultMessage;

    FileOperationError(int code, String defaultMessage) {
        this.code = code;
        this.defaultMessage = defaultMessage;
    }

    public int getCode() {
        return code;
    }

    public String getDefaultMessage() {
        return defaultMessage;
    }
}