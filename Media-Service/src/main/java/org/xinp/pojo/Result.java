package org.xinp.pojo;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;
import org.xinp.constant.Code;

/**
 * 响应结果
 */
@Data // getter/setter/toString/equals/hashCode
@JsonInclude(JsonInclude.Include.NON_NULL) //忽略为空的字段
public class Result<T> {
    //响应码
    private int code;
    //响应信息
    private String message;
    //响应数据
    private T data;
    //设置响应码和信息默认值
    public Result() {
        this.code = Code.SUCCESS.getCode();
        this.message = Code.SUCCESS.getMsg();
    }
    public Result(int code, String message) {
        this.code = code;
        this.message = message;
    }
    //成功响应
    public static Result okResult() {
        return new Result();
    }
    //成功响应带数据
    public static Result okResult(Object data) {
        Result result = new Result();
        if (data != null) {
            result.setData(data);
        }
        return result;
    }
    //失败响应
    public static Result errorResult(int code, String message) {
        return new Result(code, message);
    }
}
