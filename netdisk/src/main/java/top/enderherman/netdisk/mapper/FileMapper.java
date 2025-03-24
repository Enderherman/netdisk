package top.enderherman.netdisk.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import top.enderherman.netdisk.entity.pojo.FileInfo;

import java.util.List;

@Mapper
public interface FileMapper<T,P> extends BaseMapper<T,P>{

    /**
     * 根据FileIdAndUserId更新
     */
    Integer updateByFileIdAndUserId(@Param("bean") T t,@Param("fileId") String fileId,@Param("userId") String userId);


    /**
     * 根据FileIdAndUserId删除
     */
    Integer deleteByFileIdAndUserId(@Param("fileId") String fileId,@Param("userId") String userId);


    /**
     * 根据FileIdAndUserId获取对象
     */
    T selectByFileIdAndUserId(@Param("fileId") String fileId,@Param("userId") String userId);

    Long selectUseSpace(@Param("userId") String userId);

    void updateFileStatusWithOldStatus(@Param("fileId") String fileId, @Param("userId") String userId,
                                       @Param("bean") T t, @Param("oldStatus") Integer oldStatus);

    void updateFileDelFlagBatch(@Param("bean") FileInfo fileInfo, @Param("userId") String userId,
                                @Param("filePidList")List<String> filePidList,
                                @Param("fileIdList") List<String> fileIdList,
                                @Param("oldDelFlag") Integer oldDelFlag);


}
