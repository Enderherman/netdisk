package top.enderherman.netdisk.mapper;

import org.apache.ibatis.annotations.Param;

public interface BaseMapper<T, P> {

    Integer insert(@Param("bean") T t);
}
