package top.enderherman.netdisk.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import top.enderherman.netdisk.entity.pojo.User;

@Mapper
public interface UserMapper<T, P> extends BaseMapper<T, P> {
    T selectByEmail(@Param("email") String email);

    T selectByNickName(@Param("nickName") String nickName);

    Integer updateByUserId(@Param("bean") T t, @Param("userId") String userId);

    Integer updateByEmail(@Param("bean") T userUpdate,@Param("email") String email);
}
