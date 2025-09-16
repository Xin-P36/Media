package org.xinp.constant;

//定义响应码
public enum Code {
    SUCCESS(200, "操作成功"),
    NEED_LOGIN(401, "需要登录后操作"),
    NO_OPERATOR_AUTH(403, "无权限操作"),
    SYSTEM_ERROR(500, "出现错误"),
    TASK_IN_EXECUTION(501, "任务正在执行中");
    int code;
    String msg;

    Code(int code, String errorMessage) {
        this.code = code;
        this.msg = errorMessage;}

    public int getCode() {
        return code;}

    public String getMsg() {
        return msg;}
}
