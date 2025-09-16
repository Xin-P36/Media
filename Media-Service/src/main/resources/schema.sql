-- 删除旧表，防止重复创建出错
DROP TABLE IF EXISTS hide_list;
DROP TABLE IF EXISTS media_files;
DROP TABLE IF EXISTS operation_logs;
DROP TABLE IF EXISTS tool_list;
DROP TABLE IF EXISTS user_settings;


-- 1. 用户隐藏分类列表 (hide_list)
CREATE TABLE hide_list
(
    id      INTEGER PRIMARY KEY AUTOINCREMENT, -- 对应 @TableId(type = IdType.AUTO)
    user_id INTEGER NOT NULL,                  -- 对应 String userId
    hide_id INTEGER NOT NULL                   -- 对应 String hideId
);


-- 2. 文件信息 (media_files)
CREATE TABLE media_files
(
    file_id     INTEGER PRIMARY KEY AUTOINCREMENT, -- 对应 @TableId(type = IdType.AUTO)
    file_name   TEXT NOT NULL,                     -- 对应 String fileName
    mime_type   TEXT,                              -- 对应 String mimeType
    file_size   INTEGER,                           -- 对应 Long fileSize
    file_status TEXT,                              -- 对应 Enum FileStatus (存储枚举名)
    tool_id     INTEGER,                           -- 对应 Integer toolId (外键关联 tool_list)
    file_path   TEXT NOT NULL,                     -- 对应 String filePath
    width       INTEGER,                           -- 对应 Integer width
    height      INTEGER,                           -- 对应 Integer height
    duration    INTEGER,                           -- 对应 Long duration
    thumbnail   TEXT,                              -- 对应 String thumbnail
    file_hash   TEXT UNIQUE,                       -- 对应 String fileHash (建议设为唯一)
    metadata    TEXT,                              -- 对应 String metadata (存储JSON)
    update_time INTEGER                            -- 对应 Long updateTime (存储Unix时间戳)
);


-- 3. 操作记录 (operation_logs)
CREATE TABLE operation_logs
(
    operation_id     INTEGER PRIMARY KEY AUTOINCREMENT, -- 对应 @TableId, operationId -> operation_id
    file_id          INTEGER NOT NULL,                  -- 对应 String fileId
    operation_type   TEXT    NOT NULL,                  -- 对应 String operationType
    operation_detail TEXT,                              -- 对应 String operationDetail (存储JSON)
    status           TEXT,                              -- 对应 Enum OperationLogStatus (存储枚举名)
    error_message    TEXT,                              -- 对应 String errorMessage
    operation_time   INTEGER                            -- 对应 Long operationTime (存储Unix时间戳)
);


-- 4. 分类列表信息 (tool_list)
CREATE TABLE tool_list
(
    tool_id         INTEGER PRIMARY KEY AUTOINCREMENT, -- 对应 @TableId
    tool_name       TEXT NOT NULL,                     -- 对应 String toolName
    path            TEXT,                              -- 对应 String path
    description     TEXT,                              -- 对应 String description
    sort            INTEGER,                           -- 对应 Integer sort
    parent_id       INTEGER DEFAULT 0,                 -- 对应 Integer parentId (0表示根分类)
    cover_image_url TEXT,                              -- 对应 String coverImageUrl
    create_time     INTEGER                            -- 对应 Long createTime (存储Unix时间戳)
);


-- 5. 用户爱好设置 (user_settings)
CREATE TABLE user_settings
(
    user_id             INTEGER PRIMARY KEY AUTOINCREMENT, -- 对应 Long userId (作为主键)
    account             TEXT UNIQUE NOT NULL,              -- 对应 String account (账号通常是唯一的)
    password            TEXT        NOT NULL,              -- 对应 String password
    token               TEXT,                              -- 对应 String token
    nick_name           TEXT,                              -- 对应 String nickName
    avatar              TEXT,                              -- 对应 String avatar
    thumbnail_threshold INTEGER,                           -- 对应 Integer thumbnailThreshold
    width               INTEGER,                           -- 对应 Integer width
    height              INTEGER,                           -- 对应 Integer height
    login_background    TEXT,                              -- 对应 String loginBackground
    home_background     TEXT                               -- 对应 String homeBackground
);
-- 初始化用户
insert into user_settings (user_id, account, password, nick_name,avatar,login_background,home_background)
values (0, 'root', '%9#6ImIpkTot1f*e', 'XinP','/BackgroundImg/avatar.jpg','/BackgroundImg/login.png','/BackgroundImg/home.png');