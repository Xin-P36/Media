package org.xinp.exception;

import org.xinp.constant.FileOperationError;

/**
 * 文件操作异常
 */
public class FileOperationException extends RuntimeException {

    private final FileOperationError error;

    /**
     * 构造函数，使用枚举的默认消息
     * @param error 错误码枚举
     */
    public FileOperationException(FileOperationError error) {
        super(error.getDefaultMessage());
        this.error = error;
    }

    /**
     * 构造函数，允许提供更具体的自定义消息
     * @param error 错误码枚举
     * @param message 详细的错误信息
     */
    public FileOperationException(FileOperationError error, String message) {
        super(message);
        this.error = error;
    }

    /**
     * 构造函数，用于包装底层的IOException
     * @param error 错误码枚举
     * @param message 详细的错误信息
     * @param cause 原始的底层异常
     */
    public FileOperationException(FileOperationError error, String message, Throwable cause) {
        super(message, cause);
        this.error = error;
    }

    public FileOperationError getError() {
        return error;
    }

    public int getErrorCode() {
        return error.getCode();
    }
}