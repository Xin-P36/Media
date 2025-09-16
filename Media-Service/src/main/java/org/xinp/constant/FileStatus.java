package org.xinp.constant;

/**
 * 文件状态枚举类
 */
public enum FileStatus {
    /**
     * 待处理 - 文件刚被扫描入库，位于临时目录，等待用户进行初次分类。
     * (在你的设计中是 TemporaryMedia 文件夹)
     */
    PENDING_CLASSIFICATION("待分类"),

    /**
     * 正常 - 文件已分类，功能齐全，可供正常访问和使用。
     * 这是文件的“稳定”状态。
     */
    AVAILABLE("正常"),

    /**
     * 处理中 - 文件正在被后台任务操作，例如生成缩略图、压缩视频等。
     * 在此状态下，应限制对文件的某些操作（如移动、删除），以避免冲突。
     */
    PROCESSING("处理中"),

    /**
     * 已锁定 - 文件被用户手动锁定，或因其他业务逻辑需要，暂时禁止任何修改或删除操作。
     * 这是一个可选但很有用的状态，可以防止误操作。
     */
    LOCKED("已锁定"),

    /**
     * 待删除 - 文件已被移入回收站，等待定时任务最终清理。
     * 用户可以从此状态恢复文件。
     */
    MARKED_FOR_DELETION("待删除"),

    /**
     * 已归档 - 文件可能被移动到了冷存储或被深度压缩，访问可能较慢。
     * (这是一个高级功能，可以为未来扩展预留)
     */
    ARCHIVED("已归档"),

    /**
     * 错误/损坏 - 文件信息在数据库中，但物理文件丢失、损坏或无法访问。
     * 在扫描或访问时检测到问题后，可将文件置于此状态，方便管理员排查。
     */
    ERROR("错误/损坏");

    private final String description;

    FileStatus(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }

}