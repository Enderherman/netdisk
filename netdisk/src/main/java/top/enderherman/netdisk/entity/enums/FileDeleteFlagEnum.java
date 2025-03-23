package top.enderherman.netdisk.entity.enums;

public enum FileDeleteFlagEnum {
    DELETE(0,"删除"),
    RECYCLE(1,"回收站"),
    USING(2,"正常使用中");

    private Integer flag;
    private String desc;

    FileDeleteFlagEnum(Integer flag, String desc) {
        this.flag = flag;
        this.desc = desc;
    }

    public Integer getFlag() {
        return flag;
    }

    public String getDesc() {
        return desc;
    }
}
