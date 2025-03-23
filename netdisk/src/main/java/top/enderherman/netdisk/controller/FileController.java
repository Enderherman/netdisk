package top.enderherman.netdisk.controller;


import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import top.enderherman.netdisk.annotation.GlobalInterceptor;
import top.enderherman.netdisk.annotation.VerifyParam;
import top.enderherman.netdisk.common.BaseResponse;
import top.enderherman.netdisk.common.utils.CopyUtils;
import top.enderherman.netdisk.common.utils.StringUtils;
import top.enderherman.netdisk.entity.dto.SessionWebUserDto;
import top.enderherman.netdisk.entity.dto.UploadResultDto;
import top.enderherman.netdisk.entity.enums.FileCategoryEnum;
import top.enderherman.netdisk.entity.enums.FileDeleteFlagEnum;
import top.enderherman.netdisk.entity.enums.FileFolderTypeEnum;
import top.enderherman.netdisk.entity.pojo.FileInfo;
import top.enderherman.netdisk.entity.query.FileQuery;
import top.enderherman.netdisk.entity.vo.FileInfoVO;
import top.enderherman.netdisk.entity.vo.PaginationResultVO;
import top.enderherman.netdisk.service.FileService;

import java.util.List;


@Slf4j
@RequestMapping("/file")
@RestController("fileInfoController")
public class FileController extends ACommonFileController {

    @Resource
    private FileService fileService;


    /**
     * 查询文件
     *
     * @param query    分类查询
     * @param category 文件状态
     */
    @RequestMapping("loadDataList")
    @GlobalInterceptor
    public BaseResponse<?> loadDataList(HttpSession session, FileQuery query, String category) {
        FileCategoryEnum categoryEnum = FileCategoryEnum.getByCode(category);
        if (categoryEnum != null) {
            //设置查询类型
            query.setFileCategory(categoryEnum.getCategory());
        }
        //设置查询相关信息
        query.setUserId(getUserInfoFromSession(session).getUserId());
        query.setOrderBy("last_update_time desc");
        query.setDelFlag(FileDeleteFlagEnum.USING.getFlag());

        //设置返回结果
        PaginationResultVO<?> resultVO = fileService.findListByPage(query);
        return getSuccessResponse(convert2PaginationVO(resultVO, FileInfoVO.class));
    }


    /**
     * 前端分片上传
     *
     * @param fileId     (非必传) 第一个分片的时 后端会反给前端fileId，下个分片上传时要携带
     * @param file       需要上传的文件
     * @param fileName   文件名
     * @param filePid    父级目录
     * @param fileMd5    切片后的文件名(处理中的) 前端实现分片和获取md5值
     * @param chunkIndex 当前传输的第几个分片
     * @param chunks     分片的总数量
     * @return 文件ID, 状态
     */
    @RequestMapping("/uploadFile")
    @GlobalInterceptor(checkParams = true)
    public BaseResponse<?> uploadFile(HttpSession session,
                                      String fileId,
                                      MultipartFile file,
                                      @VerifyParam(required = true) String fileName,
                                      @VerifyParam(required = true) String filePid,
                                      @VerifyParam(required = true) String fileMd5,
                                      @VerifyParam(required = true) Integer chunkIndex,
                                      @VerifyParam(required = true) Integer chunks) {
        SessionWebUserDto userDto = getUserInfoFromSession(session);

        UploadResultDto resultDto = fileService.uploadFile(userDto, fileId, file, fileName, filePid, fileMd5, chunkIndex, chunks);

        return getSuccessResponse(resultDto);
    }

    /**
     * 获取缩略图
     *
     * @param response    响应流
     * @param imageFolder 缩略图文件夹
     * @param imageName   缩略图名称
     */
    @RequestMapping("/getImage/{imageFolder}/{imageName}")
    @GlobalInterceptor(checkParams = true)
    public void getImage(HttpServletResponse response,
                         @PathVariable("imageFolder") String imageFolder,
                         @PathVariable("imageName") String imageName) {
        super.getImage(response, imageFolder, imageName);
    }

    /**
     * 获取视频
     *
     * @param response 响应流
     * @param fileId   文件id
     */
    @RequestMapping("/ts/getVideoInfo/{fileId}")
    @GlobalInterceptor(checkParams = true)
    public void getVideo(HttpServletResponse response,
                         HttpSession session,
                         @PathVariable("fileId") String fileId) {
        SessionWebUserDto sessionWebUserDto = getUserInfoFromSession(session);
        super.getFile(response, fileId, sessionWebUserDto.getUserId());
    }

    /**
     * 获取文件
     *
     * @param response 响应流
     * @param fileId   文件id
     */
    @RequestMapping("/getFile/{fileId}")
    @GlobalInterceptor(checkParams = true)
    public void getFile(HttpServletResponse response,
                        HttpSession session,
                        @PathVariable("fileId") String fileId) {
        SessionWebUserDto sessionWebUserDto = getUserInfoFromSession(session);
        super.getFile(response, fileId, sessionWebUserDto.getUserId());
    }

    /**
     * 创建文件夹
     *
     * @param filePid  父级id
     * @param fileName 文件夹名称
     * @return 文件夹信息
     */
    @RequestMapping("/newFoloder")
    @GlobalInterceptor(checkParams = true)
    public BaseResponse<?> newFolder(HttpSession session,
                                     @VerifyParam(required = true) String filePid,
                                     @VerifyParam(required = true) String fileName) {
        SessionWebUserDto userDto = getUserInfoFromSession(session);
        FileInfo fileInfo = fileService.newFolder(filePid, userDto.getUserId(), fileName);
        return getSuccessResponse(CopyUtils.copy(fileInfo, FileInfoVO.class));
    }

    /**
     * 获取当前目录下文件信息
     *
     * @param path 路径
     */
    @RequestMapping("/getFolderInfo")
    @GlobalInterceptor(checkParams = true)
    public BaseResponse<?> getFolderInfo(HttpSession session,
                                         @VerifyParam(required = true) String path) {
        SessionWebUserDto webUserDto = getUserInfoFromSession(session);
        return super.getFolderInfo(path, webUserDto.getUserId());
    }

    /**
     * 文件/文件夹重命名
     */
    @RequestMapping("/rename")
    @GlobalInterceptor(checkParams = true)
    public BaseResponse<?> rename(HttpSession session,
                                  @VerifyParam(required = true) String fileId,
                                  @VerifyParam(required = true) String fileName) {
        SessionWebUserDto webUserDto = getUserInfoFromSession(session);
        FileInfo fileInfo = fileService.rename(fileId, webUserDto.getUserId(), fileName);
        return getSuccessResponse(CopyUtils.copy(fileInfo, FileInfoVO.class));
    }

    /**
     * 获取所有目录
     */
    @RequestMapping("/loadAllFolder")
    @GlobalInterceptor(checkParams = true)
    public BaseResponse<?> loadAllFolder(HttpSession session,
                                         @VerifyParam(required = true) String filePid,
                                         String currentFileIds) {
        SessionWebUserDto userDto = getUserInfoFromSession(session);
        FileQuery fileQuery = new FileQuery();
        fileQuery.setUserId(userDto.getUserId());
        fileQuery.setFilePid(filePid);
        fileQuery.setFolderType(FileFolderTypeEnum.FOLDER.getType());
        //排除当前所选文件 但不排除当前所选文件夹的父级文件夹
        if (!StringUtils.isEmpty(currentFileIds)) {
            String[] split = currentFileIds.split(",");
            if (split.length >= 1) {
                String[] finalStr = new String[split.length - 1];
                System.arraycopy(split,1,finalStr,0,finalStr.length);
                fileQuery.setExcludeFileIdArray(finalStr);
            }

        }
        fileQuery.setDelFlag(FileDeleteFlagEnum.USING.getFlag());
        fileQuery.setOrderBy("create_time desc");
        List<FileInfo> fileInfoList = fileService.findListByParam(fileQuery);

        return getSuccessResponse(CopyUtils.copyList(fileInfoList, FileInfoVO.class));
    }

    /**
     * 移动所选文件到指定目录
     */
    @RequestMapping("/changeFileFolder")
    @GlobalInterceptor(checkParams = true)
    public BaseResponse<?> changFileFolder(HttpSession session,
                                           @VerifyParam(required = true) String fileIds,
                                           @VerifyParam(required = true) String filePid) {
        SessionWebUserDto sessionWebUserDto = getUserInfoFromSession(session);
        fileService.changeFileFolder(fileIds, filePid, sessionWebUserDto.getUserId());
        return getSuccessResponse(null);
    }

}
