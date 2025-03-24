package top.enderherman.netdisk.controller;


import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import top.enderherman.netdisk.common.BaseResponse;
import top.enderherman.netdisk.common.component.RedisComponent;
import top.enderherman.netdisk.common.config.AppConfig;
import top.enderherman.netdisk.common.constants.Constants;
import top.enderherman.netdisk.common.exceptions.BusinessException;
import top.enderherman.netdisk.common.utils.CopyUtils;
import top.enderherman.netdisk.entity.dto.DownloadFileDto;
import top.enderherman.netdisk.entity.enums.FileCategoryEnum;
import top.enderherman.netdisk.entity.enums.FileFolderTypeEnum;
import top.enderherman.netdisk.entity.enums.ResponseCodeEnum;
import top.enderherman.netdisk.entity.pojo.FileInfo;
import top.enderherman.netdisk.entity.query.FileQuery;
import top.enderherman.netdisk.entity.vo.FileInfoVO;
import top.enderherman.netdisk.service.FileService;
import top.enderherman.netdisk.common.utils.StringUtils;


import java.io.File;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;

public class ACommonFileController extends ABaseController {


    @Resource
    private AppConfig appConfig;

    @Resource
    private FileService fileInfoService;

    @Resource
    private RedisComponent redisComponent;

    /**
     * 获取缩略图
     *
     * @param response    响应流
     * @param imageFolder 缩略图文件夹
     * @param imageName   缩略图名称
     */
    protected void getImage(HttpServletResponse response, String imageFolder, String imageName) {
        if (StringUtils.isEmpty(imageFolder) || StringUtils.isBlank(imageName) || !StringUtils.pathIsOk(imageFolder) || !StringUtils.pathIsOk(imageName)) {
            return;
        }
        String imageSuffix = StringUtils.getFileSuffix(imageName);
        String filePath = appConfig.getProjectFolder() + Constants.FILE_FOLDER_FILE + imageFolder + "/" + imageName;
        imageSuffix = imageSuffix.replace(".", "");
        String contentType = "image/" + imageSuffix;
        response.setContentType(contentType);
        response.setHeader("Cache-Control", "max-age=2592000");
        writeFile(response, filePath);
    }


    /**
     * 获取文件
     *
     * @param response 响应流
     * @param fileId   文件id
     * @param userId   用户id
     */
    protected void getFile(HttpServletResponse response, String fileId, String userId) {
        String filePath = null;
        //视频的第二次读取已经变成ts的请求
        if (fileId.endsWith(".ts")) {
            //获取文件Id
            String[] tsArray = fileId.split("_");
            String realFileId = tsArray[0];
            //根据原文件的id查询出一个文件集合
            FileInfo fileInfo = fileInfoService.getFileInfoByFileIdAndUserId(realFileId, userId);
            if (fileInfo == null) {

                //分享的视频，ts路径记录的是原视频的id,这里通过id直接取出原视频
                FileQuery fileInfoQuery = new FileQuery();
                fileInfoQuery.setFileId(realFileId);
                List<FileInfo> fileInfoList = fileInfoService.findListByParam(fileInfoQuery);
                fileInfo = fileInfoList.get(0);
                if (fileInfo == null) {
                    return;
                }

                //根据当前用户id和路径去查询当前用户是否有该文件，如果没有直接返回
                fileInfoQuery = new FileQuery();
                fileInfoQuery.setFilePath(fileInfo.getFilePath());
                fileInfoQuery.setUserId(userId);
                Integer count = fileInfoService.findCountByParam(fileInfoQuery);
                if (count == 0) {
                    return;
                }
            }

            String fileName = fileInfo.getFilePath();
            fileName = StringUtils.getFileNameWithoutSuffix(fileName) + "/" + fileId;
            filePath = appConfig.getProjectFolder() + Constants.FILE_FOLDER_FILE + fileName;

        } else {
            FileInfo fileInfo = fileInfoService.getFileInfoByFileIdAndUserId(fileId, userId);
            if (fileInfo == null) {
                return;
            }
            //视频文件读取.m3u8文件
            if (FileCategoryEnum.VIDEO.getCategory().equals(fileInfo.getFileCategory())) {
                //重新设置文件路径
                String fileNameNoSuffix = StringUtils.getFileNameWithoutSuffix(fileInfo.getFilePath());
                filePath = appConfig.getProjectFolder() + Constants.FILE_FOLDER_FILE + fileNameNoSuffix + "/" + Constants.M3U8_NAME;
            } else {            //不是视频直接取文件
                filePath = appConfig.getProjectFolder() + Constants.FILE_FOLDER_FILE + fileInfo.getFilePath();
            }
        }
        File file = new File(filePath);
        if (!file.exists()) {
            return;
        }
        writeFile(response, filePath);
    }

    //获取当前目录
    protected BaseResponse<?> getFolderInfo(String path, String userId) {
        String[] pathArray = path.split("/");
        FileQuery fileInfoQuery = new FileQuery();
        fileInfoQuery.setUserId(userId);
        fileInfoQuery.setFolderType(FileFolderTypeEnum.FOLDER.getType());
        fileInfoQuery.setFileIdArray(pathArray);
        //order by ("","") 按split顺序排序
        String orderBy = "field(file_id,\"" + org.apache.commons.lang3.StringUtils.join(pathArray, "\",\"") + "\")";
        fileInfoQuery.setOrderBy(orderBy);
        List<FileInfo> fileInfoList = fileInfoService.findListByParam(fileInfoQuery);
        return getSuccessResponse(CopyUtils.copyList(fileInfoList, FileInfoVO.class));
    }

    /**
     * 有时效性的获取下载链接
     */
    protected BaseResponse<?> createDownloadUrl(String fileId, String userId) {
        FileInfo fileInfo = fileInfoService.getFileInfoByFileIdAndUserId(fileId, userId);
        if (fileInfo == null) {
            throw new BusinessException(ResponseCodeEnum.CODE_600);
        }
        if (FileFolderTypeEnum.FOLDER.getType().equals(fileInfo.getFolderType())) {
            throw new BusinessException(ResponseCodeEnum.CODE_600);
        }

        //token
        String code = StringUtils.getRandomString(Constants.LENGTH_50);
        DownloadFileDto fileDto = new DownloadFileDto();
        fileDto.setDownloadCode(code);
        fileDto.setFileName(fileInfo.getFileName());
        fileDto.setFilePath(fileInfo.getFilePath());
        redisComponent.saveDownloadCode(code, fileDto);
        return getSuccessResponse(code);
    }

    protected void download(HttpServletRequest request, HttpServletResponse response, String code) throws Exception {
        DownloadFileDto downloadFileDto = redisComponent.getDownloadCode(code);
        if (downloadFileDto == null){
            return;
        }
        String filePath = appConfig.getProjectFolder() + Constants.FILE_FOLDER_FILE + downloadFileDto.getFilePath();
        String fileName = downloadFileDto.getFileName();
        response.setContentType("application/x-msdownload; charset=UTF-8");
        if (request.getHeader("User-Agent").toLowerCase().indexOf("msie") > 0){
            //IE浏览器
            fileName = URLEncoder.encode(fileName, StandardCharsets.UTF_8);
        }else {
            fileName = new String(fileName.getBytes(StandardCharsets.UTF_8),"ISO8859-1");
        }
        response.setHeader("Content-Disposition","attachment;filename=\"" + fileName + "\"");
        writeFile(response,filePath);
    }

}
