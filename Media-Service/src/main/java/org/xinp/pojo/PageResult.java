package org.xinp.pojo;

import com.baomidou.mybatisplus.core.metadata.IPage;
import lombok.Data;

import java.util.List;

/**
 * 文件获取响应
 * @param <T>
 */
@Data
public class PageResult<T> {
    private long total;   // 总记录数
    private long pages;   // 总页数
    private long current; // 当前页
    private long size;    // 每页数量
    private List<T> records; // 当前页的数据列表

    /**
     * 将MyBatis-Plus的IPage对象转换为我们自定义的PageResult对象
     * @param page mybatis-plus分页结果
     * @param <T>  泛型
     * @return 自定义分页结果
     */
    public static <T> PageResult<T> from(IPage<T> page) {
        PageResult<T> result = new PageResult<>();
        result.setTotal(page.getTotal());
        result.setPages(page.getPages());
        result.setCurrent(page.getCurrent());
        result.setSize(page.getSize());
        result.setRecords(page.getRecords());
        return result;
    }
}