package top.enderherman.netdisk.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface FileMapper<T,P> extends BaseMapper<T,P>{

    Long selectUseSpace(@Param("userId") String userId);
}
