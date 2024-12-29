package top.enderherman.netdisk.entity.query;

import lombok.Data;

@Data
public class BaseParam {
    private SimplePage simplePage;
    private Integer pageNo;
    private Integer pageSize;
    private String orderBy;


}