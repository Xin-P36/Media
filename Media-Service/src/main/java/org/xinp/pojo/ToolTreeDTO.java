package org.xinp.pojo;

import lombok.Data;
import lombok.EqualsAndHashCode;
import org.xinp.entity.ToolList;

import java.util.List;

@Data
@EqualsAndHashCode(callSuper = true) // 确保hashCode和equals也考虑父类字段
public class ToolTreeDTO extends ToolList { //继承ToolList
    
    private List<ToolTreeDTO> children;
    
}