package top.enderherman.netdisk.service.impl;

import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;
import top.enderherman.netdisk.common.component.RedisComponent;
import top.enderherman.netdisk.common.config.AppConfig;
import top.enderherman.netdisk.common.constants.Constants;
import top.enderherman.netdisk.common.exceptions.BusinessException;
import top.enderherman.netdisk.common.utils.DateUtils;
import top.enderherman.netdisk.common.utils.ProcessUtils;
import top.enderherman.netdisk.common.utils.ScaleFiler;
import top.enderherman.netdisk.common.utils.StringUtils;
import top.enderherman.netdisk.entity.dto.SessionWebUserDto;
import top.enderherman.netdisk.entity.dto.UploadResultDto;
import top.enderherman.netdisk.entity.dto.UserSpaceDto;
import top.enderherman.netdisk.entity.enums.*;
import top.enderherman.netdisk.entity.pojo.FileInfo;
import top.enderherman.netdisk.entity.pojo.User;
import top.enderherman.netdisk.entity.query.FileQuery;
import top.enderherman.netdisk.entity.query.SimplePage;
import top.enderherman.netdisk.entity.query.UserQuery;
import top.enderherman.netdisk.entity.vo.PaginationResultVO;
import top.enderherman.netdisk.mapper.FileMapper;
import top.enderherman.netdisk.mapper.UserMapper;
import top.enderherman.netdisk.service.FileService;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service("fileService")
public class FileServiceImpl implements FileService {

    @Resource
    private FileMapper<FileInfo, FileQuery> fileMapper;

    @Resource
    private UserMapper<User, UserQuery> userMapper;

    @Resource
    private RedisComponent redisComponent;

    @Resource
    private AppConfig appConfig;

    @Resource
    @Lazy
    private FileServiceImpl fileService;

    /**
     * 根据条件查询列表
     */
    @Override
    public List<FileInfo> findListByParam(FileQuery param) {
        return fileMapper.selectList(param);
    }

    /**
     * 根据条件查询数量
     */
    @Override
    public Integer findCountByParam(FileQuery param) {
        return fileMapper.selectCount(param);
    }

    /**
     * 分页查询 可以设置条件查询等参数
     *
     * @param param 条件查询阐述
     * @return 文件List
     */
    @Override
    public PaginationResultVO<FileInfo> findListByPage(FileQuery param) {
        int count = this.findCountByParam(param);
        int pageSize = param.getPageSize() == null ? PageSizeEnum.SIZE15.getSize() : param.getPageSize();

        SimplePage page = new SimplePage(param.getPageNo(), count, pageSize);
        param.setSimplePage(page);
        List<FileInfo> list = this.findListByParam(param);
        PaginationResultVO<FileInfo> resultVO = new PaginationResultVO<>(count,
                page.getPageSize(), page.getPageNo(), page.getPageTotal(), list);
        return resultVO;
    }

    /**
     * 新增
     */
    @Override
    public Integer add(FileInfo bean) {
        return this.fileMapper.insert(bean);
    }

    /**
     * 批量新增
     */
    @Override
    public Integer addBatch(List<FileInfo> listBean) {
        if (listBean == null || listBean.isEmpty()) {
            return 0;
        }
        return this.fileMapper.insertBatch(listBean);
    }

    /**
     * 批量新增或者修改
     */
    @Override
    public Integer addOrUpdateBatch(List<FileInfo> listBean) {
        if (listBean == null || listBean.isEmpty()) {
            return 0;
        }
        return this.fileMapper.insertOrUpdateBatch(listBean);
    }

    /**
     * 多条件更新
     */
    @Override
    public Integer updateByParam(FileInfo bean, FileQuery param) {
        StringUtils.checkParam(param);
        return this.fileMapper.updateByParam(bean, param);
    }

    /**
     * 多条件删除
     */
    @Override
    public Integer deleteByParam(FileQuery param) {
        StringUtils.checkParam(param);
        return this.fileMapper.deleteByParam(param);
    }

    /**
     * 根据FileIdAndUserId获取对象
     */
    @Override
    public FileInfo getFileInfoByFileIdAndUserId(String fileId, String userId) {
        return this.fileMapper.selectByFileIdAndUserId(fileId, userId);
    }

    /**
     * 根据FileIdAndUserId修改
     */
    @Override
    public Integer updateFileInfoByFileIdAndUserId(FileInfo bean, String fileId, String userId) {
        return this.fileMapper.updateByFileIdAndUserId(bean, fileId, userId);
    }

    /**
     * 根据FileIdAndUserId删除
     */
    @Override
    public Integer deleteFileInfoByFileIdAndUserId(String fileId, String userId) {
        return this.fileMapper.deleteByFileIdAndUserId(fileId, userId);
    }


    /**
     * 文件上传
     *
     * @param userDto    用户信息
     * @param fileId     (非必传) 第一个分片的时候后端会反给前端fileId，下个分片上传时要携带
     * @param file       需要上传的文件
     * @param fileName   文件名
     * @param filePid    父级目录
     * @param fileMd5    切片后的文件
     * @param chunkIndex 当前传输的第几个分片
     * @param chunks     分片的总数量
     * @return 上传结果
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public UploadResultDto uploadFile(SessionWebUserDto userDto, String fileId,
                                      MultipartFile file, String fileName, String filePid,
                                      String fileMd5, Integer chunkIndex, Integer chunks) {
        UploadResultDto resultDto = new UploadResultDto();
        boolean uploadStatus = true;
        File tempFolder = null;
        try {
            //1.首次上传设置文件id
            if (StringUtils.isEmpty(fileId)) {
                fileId = StringUtils.getRandomString(Constants.LENGTH_10);
            }
            resultDto.setFileId(fileId);

            Date curDate = new Date();
            UserSpaceDto spaceDto = redisComponent.getUserSpace(userDto.getUserId());

            //2.首次上传
            if (chunkIndex == 0) {
                FileQuery fileQuery = new FileQuery();
                fileQuery.setFileMd5(fileMd5);
                fileQuery.setSimplePage(new SimplePage(0, 1));
                fileQuery.setStatus(FileStatusEnum.USING.getStatus());
                List<FileInfo> dbFileList = fileMapper.selectList(fileQuery);
                //2.1秒传
                if (!dbFileList.isEmpty()) {
                    FileInfo dbFile = dbFileList.get(0);
                    //2.1.1判断文件大小
                    if (dbFile.getFileSize() + spaceDto.getUseSpace() > spaceDto.getTotalSpace()) {
                        throw new BusinessException(ResponseCodeEnum.CODE_904);
                    }
                    //2.1.2copy一份
                    dbFile.setFileId(fileId);
                    dbFile.setFilePid(filePid);
                    dbFile.setUserId(userDto.getUserId());
                    dbFile.setCreateTime(curDate);
                    dbFile.setStatus(FileStatusEnum.USING.getStatus());
                    dbFile.setDelFlag(FileDeleteFlagEnum.USING.getFlag());
                    dbFile.setFileMd5(fileMd5);
                    //文件重名
                    fileName = autoRename(filePid, userDto.getUserId(),
                            fileName);
                    dbFile.setFileName(fileName);
                    this.fileMapper.insert(dbFile);
                    //更新用户使用空间
                    updateUSeSpace(userDto, dbFile.getFileSize());

                    resultDto.setStatus(UploadStatusEnum.UPLOAD_SECONDS.getCode());
                    return resultDto;
                }
            }
            //3.1判断磁盘空间
            Long currentTempSize = redisComponent.getFileTemplateSize(userDto.getUserId(), fileId);
            if (file.getSize() + currentTempSize + spaceDto.getUseSpace() > spaceDto.getTotalSpace()) {
                throw new BusinessException(ResponseCodeEnum.CODE_904);
            }
            //3.2暂存临时目录
            String tempFolderName = appConfig.getProjectFolder() + Constants.FILE_FOLDER_FILE + Constants.FILE_FOLDER_TEMP;
            String currentUserFolderName = userDto.getUserId() + fileId;
            //3.3创建临时目录
            tempFolder = new File(tempFolderName + currentUserFolderName);
            if (!tempFolder.exists()) {
                tempFolder.mkdirs();
            }


            File newFile = new File(tempFolder.getPath() + "/" + chunkIndex);
            file.transferTo(newFile);

            //保存临时大小
            redisComponent.saveFileTempSize(userDto.getUserId(), fileId, file.getSize());
            //不是最后一个分片，直接返回
            if (chunkIndex < chunks - 1) {
                resultDto.setStatus(UploadStatusEnum.UPLOADING.getCode());
                return resultDto;
            }
            //4.最后一个分片上传文件 异步合并并记录到数据库
            String curMonth = DateUtils.format(curDate, DateTimePatternEnum.YYYY_MM.getPattern());
            String fileSuffix = StringUtils.getFileSuffix(fileName);

            //真实文件名
            String realFileName = currentUserFolderName + fileSuffix;
            FileTypeEnum fileTypeEnum = FileTypeEnum.getFileTypeBySuffix(fileSuffix);
            //文件重名
            fileName = autoRename(filePid, userDto.getUserId(), fileName);

            //更新数据库
            FileInfo fileInfo = new FileInfo();
            fileInfo.setFileId(fileId);
            fileInfo.setUserId(userDto.getUserId());
            fileInfo.setFileMd5(fileMd5);
            fileInfo.setFilePid(filePid);
            fileInfo.setFileName(fileName);
            fileInfo.setFilePath(curMonth + "/" + realFileName);
            fileInfo.setCreateTime(curDate);
            fileInfo.setLastUpdateTime(curDate);
            fileInfo.setFolderType(FileFolderTypeEnum.FILE.getType());
            fileInfo.setFileCategory(fileTypeEnum.getCategory().getCategory());
            fileInfo.setFileType(fileTypeEnum.getType());
            fileInfo.setStatus(FileStatusEnum.TRANSFER.getStatus());
            fileInfo.setDelFlag(FileDeleteFlagEnum.USING.getFlag());
            fileMapper.insert(fileInfo);

            //更新用户空间
            Long totalSize = redisComponent.getFileTemplateSize(userDto.getUserId(), fileId);
            updateUSeSpace(userDto, totalSize);
            resultDto.setStatus(UploadStatusEnum.UPLOAD_FINISH.getCode());

            //异步转码
            TransactionSynchronizationManager.registerSynchronization(
                    new TransactionSynchronization() {
                        @Override
                        public void afterCommit() {
                            fileService.transferFile(fileInfo.getFileId(), userDto);
                        }
                    });
            return resultDto;
        } catch (BusinessException e) {
            log.error("文件上传失败", e);
            uploadStatus = false;
            throw e;
        } catch (Exception e) {
            log.error("文件上传失败", e);
            uploadStatus = false;
        } finally {
            if (!uploadStatus && tempFolder != null) {
                try {
                    FileUtils.deleteDirectory(tempFolder);
                } catch (IOException e) {
                    log.error("删除临时文件目录失败", e);
                }
            }
        }
        return null;
    }

    /**
     * 创建目录
     *
     * @param filePid    父级id
     * @param userId     用户id
     * @param folderName 文件夹名
     * @return 文件信息
     */
    @Override
    public FileInfo newFolder(String filePid, String userId, String folderName) {
        //校验是否存在重名文件夹
        checkFileName(filePid, userId, folderName, FileFolderTypeEnum.FOLDER.getType());
        Date curDate = new Date();
        FileInfo fileInfo = new FileInfo();
        fileInfo.setFileId(StringUtils.getRandomString(Constants.LENGTH_10));
        fileInfo.setUserId(userId);
        fileInfo.setFilePid(filePid);
        fileInfo.setFileName(folderName);
        fileInfo.setFolderType(FileFolderTypeEnum.FOLDER.getType());
        fileInfo.setCreateTime(curDate);
        fileInfo.setLastUpdateTime(curDate);
        fileInfo.setStatus(FileStatusEnum.USING.getStatus());
        fileInfo.setDelFlag(FileDeleteFlagEnum.USING.getFlag());
        this.fileMapper.insert(fileInfo);
        return fileInfo;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public FileInfo rename(String fileId, String userId, String fileName) {
        FileInfo fileInfo = this.fileMapper.selectByFileIdAndUserId(fileId, userId);
        if (fileInfo == null) {
            throw new BusinessException("文件不存在");
        }
        //校验当前文件夹下是否有重名文件
        String filePid = fileInfo.getFilePid();
        checkFileName(filePid, userId, fileName, fileInfo.getFolderType());
        if (FileFolderTypeEnum.FILE.getType().equals(fileInfo.getFileType())) {
            fileName = fileName + StringUtils.getFileSuffix(fileInfo.getFileName());
        }

        Date curDate = new Date();
        FileInfo dbInfo = new FileInfo();
        dbInfo.setFileName(fileName);
        dbInfo.setLastUpdateTime(curDate);
        fileMapper.updateByFileIdAndUserId(dbInfo, fileId, userId);

        //面向并发型事务
        FileQuery fileQuery = new FileQuery();
        fileQuery.setFilePid(filePid);
        fileQuery.setUserId(userId);
        fileQuery.setFileName(fileName);
        fileQuery.setDelFlag(FileDeleteFlagEnum.USING.getFlag());
        Integer count = fileMapper.selectCount(fileQuery);
        if (count > 1) {
            throw new BusinessException("文件名" + fileName + "已经存在");
        }
        fileInfo.setFileName(fileName);
        fileInfo.setLastUpdateTime(curDate);

        return fileInfo;
    }

    /**
     * 移动所选文件到指定文件夹
     *
     * @param fileIds 所选文件id
     * @param filePid 目标文件夹id
     * @param userId  用户id
     */
    @Override
    public void changeFileFolder(String fileIds, String filePid, String userId) {
        //1.原地tp 干啥呢
        if (fileIds.equals(filePid)) {
            throw new BusinessException(ResponseCodeEnum.CODE_600);
        }
        //2.不在根目录下 看看有没问题
        if (!Constants.ZERO_STR.equals(filePid)) {
            FileInfo fileInfo = fileService.getFileInfoByFileIdAndUserId(filePid, userId);
            if (fileInfo == null || !FileDeleteFlagEnum.USING.getFlag().equals(fileInfo.getDelFlag())) {
                throw new BusinessException(ResponseCodeEnum.CODE_600);
            }
        }
        //3.查询移动的目标文件夹里所包含的文件
        String[] fileIdArray = fileIds.split(",");
        FileQuery query = new FileQuery();
        query.setFilePid(filePid);
        query.setUserId(userId);
        List<FileInfo> dbFileList = fileService.findListByParam(query);
        //4.查询到的文件转为map
        Map<String, FileInfo> dbFileMap = dbFileList.stream().collect(
                Collectors.toMap(FileInfo::getFileName, Function.identity(), (file1, file2) -> file2));
        //5.查询选中的文件
        query = new FileQuery();
        query.setUserId(userId);
        query.setFileIdArray(fileIdArray);
        List<FileInfo> selectFileList = this.findListByParam(query);

        //6.选中文件夹更新父级id 重名的进行重命名
        for (FileInfo item : selectFileList) {
            FileInfo rootFileInfo = dbFileMap.get(item.getFileName());
            //文件名已存在，重命名要移动的文件加密
            FileInfo updateInfo = new FileInfo();
            if (rootFileInfo != null) {
                String fileName = StringUtils.rename(item.getFileName());
                updateInfo.setFileName(fileName);
            }
            updateInfo.setFilePid(filePid);
            fileMapper.updateByFileIdAndUserId(updateInfo, item.getFileId(), userId);
        }

    }

    /**
     * 文件删除
     */
    @Override
    public void removeFile2RecycleBatch(String userId, String fileIds) {
        String[] fileIdArray = fileIds.split(",");
        FileQuery query = new FileQuery();
        query.setUserId(userId);
        query.setDelFlag(FileDeleteFlagEnum.USING.getFlag());
        query.setFileIdArray(fileIdArray);
        List<FileInfo> list = fileMapper.selectList(query);
        if (list.isEmpty()) {
            return;
        }

        //查找所有子目录标记为删除
        List<String> delFilePidList = new ArrayList<>();
        for (FileInfo fileInfo : list) {
            findAllSubFolderFileList(delFilePidList, userId, fileInfo.getFileId(), FileDeleteFlagEnum.USING.getFlag());
        }

        //将子目录下的所有文件更新为已删除
        if (!delFilePidList.isEmpty()) {
            FileInfo fileInfo = new FileInfo();
            fileInfo.setRecoveryTime(new Date());
            fileInfo.setDelFlag(FileDeleteFlagEnum.DELETE.getFlag());
            fileMapper.updateFileDelFlagBatch(fileInfo, userId, delFilePidList, null, FileDeleteFlagEnum.USING.getFlag());
        }

        //将选中的文件更新为回收站
        List<String> delFileIsList = Arrays.asList(fileIdArray);
        FileInfo fileInfo = new FileInfo();
        fileInfo.setRecoveryTime(new Date());
        fileInfo.setDelFlag(FileDeleteFlagEnum.RECYCLE.getFlag());
        fileMapper.updateFileDelFlagBatch(fileInfo, userId, null, delFileIsList, FileDeleteFlagEnum.USING.getFlag());
    }

    /**
     * 从回收站恢复文件
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void recoverFile(String userId, String fileIds) {
        String[] fileIdArray = fileIds.split(",");
        if (fileIdArray.length == 0) {
            return;
        }
        FileQuery query = new FileQuery();
        query.setUserId(userId);
        query.setFileIdArray(fileIdArray);
        query.setDelFlag(FileDeleteFlagEnum.RECYCLE.getFlag());
        //1.回收站里所有父级文件
        List<FileInfo> fileInfoList = fileMapper.selectList(query);

        //2.查询子集文件
        List<String> dbFileInfoList = new ArrayList<>();
        for (FileInfo fileInfo : fileInfoList) {
            if (FileFolderTypeEnum.FOLDER.getType().equals(fileInfo.getFolderType())) {
                findAllSubFolderFileList(dbFileInfoList, userId, fileInfo.getFileId(),
                        FileDeleteFlagEnum.DELETE.getFlag());
            }
        }

        //2.2子集文件更新为正常使用 我才是真高手
        if (!dbFileInfoList.isEmpty()) {
            FileInfo fileInfo = new FileInfo();
            fileInfo.setLastUpdateTime(new Date());
            fileInfo.setDelFlag(FileDeleteFlagEnum.USING.getFlag());
            fileMapper.updateFileDelFlagBatch(fileInfo, userId, dbFileInfoList, null,
                    FileDeleteFlagEnum.DELETE.getFlag());
        }
        //3.查询根目录正常使用文件 以文件名作为key
        query = new FileQuery();
        query.setUserId(userId);
        query.setDelFlag(FileDeleteFlagEnum.USING.getFlag());
        query.setFilePid(Constants.ZERO_STR);
        List<FileInfo> allRootFileList = fileMapper.selectList(query);
        Map<String, FileInfo> rootFileMap = allRootFileList.stream().collect(
                Collectors.toMap(FileInfo::getFileName, Function.identity(), (file1, file2) -> file2));
        //4.查询所有所选文件 将目录下的所有删除文件更新为使用中 且父级目录到根目录
        List<String> delFileIdList = Arrays.asList(fileIdArray);
        FileInfo fileInfo = new FileInfo();
        fileInfo.setDelFlag(FileDeleteFlagEnum.USING.getFlag());
        fileInfo.setFilePid(Constants.ZERO_STR);
        fileInfo.setLastUpdateTime(new Date());
        fileMapper.updateFileDelFlagBatch(fileInfo, userId, null, delFileIdList,
                FileDeleteFlagEnum.RECYCLE.getFlag());
        //5.重命名重名父级文件名
        for (FileInfo item : fileInfoList) {
            FileInfo rootFileInfo = rootFileMap.get(item.getFileName());
            //文件名已经存在，重命名被还原的文件名
            if (rootFileInfo != null) {
                String fileName = StringUtils.rename(item.getFileName());
                FileInfo updateInfo = new FileInfo();
                updateInfo.setFileName(fileName);
                this.fileMapper.updateByFileIdAndUserId(updateInfo, item.getFileId(), userId);
            }
        }
    }

    /**
     * 彻底删除回收站内文件`
     *
     * @param adminOp 是否为管理员
     */
    @Override
    public void deleteFile(String userId, String fileIds, boolean adminOp) {
        String[] fileIdArray = fileIds.split(",");
        FileQuery query = new FileQuery();
        query.setUserId(userId);
        query.setFileIdArray(fileIdArray);
        query.setDelFlag(FileDeleteFlagEnum.RECYCLE.getFlag());
        //1.回收站里所有父级文件
        List<FileInfo> fileInfoList = fileMapper.selectList(query);

        //2.查询子集文件
        List<String> dbFileInfoList = new ArrayList<>();
        for (FileInfo fileInfo : fileInfoList) {
            if (FileFolderTypeEnum.FOLDER.getType().equals(fileInfo.getFolderType())) {
                findAllSubFolderFileList(dbFileInfoList, userId, fileInfo.getFileId(),
                        FileDeleteFlagEnum.DELETE.getFlag());
            }
        }
        //目前已增加彻底删除标记 FINAL_DELETE(delFlag:3), 服务器每三月五号定时删除文件
        FileInfo updateFileInfo = new FileInfo();
        updateFileInfo.setDelFlag(FileDeleteFlagEnum.FINAL_DELETE.getFlag());

        //3.先删除子集文件
        if (!dbFileInfoList.isEmpty()) {
            fileMapper.updateFileDelFlagBatch(updateFileInfo, userId, dbFileInfoList, null,
                    FileDeleteFlagEnum.DELETE.getFlag());
        }

        //4.删除父级文件
        if (!fileInfoList.isEmpty()) {
            fileMapper.updateFileDelFlagBatch(updateFileInfo, userId, null, Arrays.asList(fileIdArray),
                    FileDeleteFlagEnum.RECYCLE.getFlag());
        }

        //final 更新使用空间到数据库以及redis缓存
        Long useSpace = fileMapper.selectUseSpace(userId);
        User user = new User();
        user.setUseSpace(useSpace);
        userMapper.updateByUserId(user, userId);

        //更新缓存
        UserSpaceDto userSpaceDto = redisComponent.getUserSpace(userId);
        userSpaceDto.setUseSpace(useSpace);
        redisComponent.saveUserSpaceDto(userId, userSpaceDto);
    }

    /**
     * 只给看已经分享的 根目录其它的不给看
     */
    @Override
    public void checkRootFilePid(String rootFilePid, String userId, String fileId) {
        if (StringUtils.isEmpty(fileId)) {
            throw new BusinessException(ResponseCodeEnum.CODE_600);
        }
        if (rootFilePid.equals(fileId)) {
            return;
        }
        checkFilePid(rootFilePid, fileId, userId);
    }

    /**
     * 保存分享的文件
     */
    @Override
    public void saveShare(String shareRootFilePid, String shareFileIds, String myFolderId, String shareUserId, String currentUserId) {
        String[] shareFileIdArray = shareFileIds.split(",");
        //1.目标目录文件列表
        FileQuery query = new FileQuery();
        query.setUserId(currentUserId);
        query.setFilePid(myFolderId);
        List<FileInfo> currentFileList = fileMapper.selectList(query);
        Map<String, FileInfo> currentFileMap = currentFileList.stream().collect(Collectors.toMap(FileInfo::getFileName, Function.identity(), (file1, file2) -> file2));
        //2.要保存的文件
        query = new FileQuery();
        query.setUserId(shareUserId);
        query.setFileIdArray(shareFileIdArray);
        List<FileInfo> shareFileList = fileMapper.selectList(query);
        //3.重命名选择的文件
        List<FileInfo> copyFileList = new ArrayList<>();
        Date curDate = new Date();
        for (FileInfo item : shareFileList) {
            FileInfo haveFile = currentFileMap.get(item.getFileName());
            if (haveFile != null) {
                item.setFileName(StringUtils.rename(item.getFileName()));
            }
            findAllSubFile(copyFileList, item, shareUserId, currentUserId, curDate, myFolderId);
        }
        fileMapper.insertBatch(copyFileList);

        //4.更新空间
        Long useSpace = fileMapper.selectUseSpace(currentUserId);
        User dbUserInfo = userMapper.selectByUserId(currentUserId);
        if (useSpace > dbUserInfo.getTotalSpace()) {
            throw new BusinessException(ResponseCodeEnum.CODE_904);
        }
        User userInfo = new User();
        userInfo.setUseSpace(useSpace);
        userMapper.updateByUserId(userInfo, currentUserId);
        //5.设置缓存
        UserSpaceDto userSpaceDto = redisComponent.getUserSpace(currentUserId);
        userSpaceDto.setUseSpace(useSpace);
        redisComponent.saveUserSpaceDto(currentUserId, userSpaceDto);
    }

    /**
     * 自动重命名文件
     *
     * @param filePid  文件id
     * @param userId   用户id
     * @param fileName 文件名
     * @return 重命名后的文件名
     */
    private String autoRename(String filePid, String userId, String fileName) {
        FileQuery fileInfoQuery = new FileQuery();
        fileInfoQuery.setUserId(userId);
        fileInfoQuery.setFileId(filePid);
        fileInfoQuery.setDelFlag(FileDeleteFlagEnum.USING.getFlag());
        fileInfoQuery.setFileName(fileName);
        Integer count = fileMapper.selectCount(fileInfoQuery);
        if (count > 0) {
            fileName = StringUtils.rename(fileName);
        }
        return fileName;
    }

    /**
     * 更新用户空间 先更新数据库校验容量是否正确再更新redis
     *
     * @param sessionWebUserDto 用户信息
     * @param useSpace          新增使用空间
     */
    private void updateUSeSpace(SessionWebUserDto sessionWebUserDto, Long useSpace) {
        Integer count = userMapper.updateUserSpace(sessionWebUserDto.getUserId(), useSpace, null);
        if (count == 0) {
            throw new BusinessException(ResponseCodeEnum.CODE_904);
        }

        UserSpaceDto spaceDto = redisComponent.getUserSpace(sessionWebUserDto.getUserId());
        spaceDto.setUseSpace(spaceDto.getUseSpace() + useSpace);
        redisComponent.saveUserSpaceDto(sessionWebUserDto.getUserId(), spaceDto);
    }

    /**
     * 转码
     */
    @Async
    public void transferFile(String fileId, SessionWebUserDto userDto) {
        Boolean transferSuccess = true;
        String targetFilePath = null;
        String cover = null;
        FileTypeEnum fileTypeEnum = null;
        FileInfo fileInfo = fileMapper.selectByFileIdAndUserId(fileId, userDto.getUserId());
        try {
            if (fileInfo == null || !FileStatusEnum.TRANSFER.getStatus().equals(fileInfo.getStatus())) {
                return;
            }

            //临时目录
            String tempFolderName = appConfig.getProjectFolder() + Constants.FILE_FOLDER_FILE + Constants.FILE_FOLDER_TEMP;
            String currentUserFolderName = userDto.getUserId() + fileId;
            File fileFolder = new File(tempFolderName + currentUserFolderName);
            String fileSuffix = StringUtils.getFileSuffix(fileInfo.getFileName());
            String curMonth = DateUtils.format(fileInfo.getCreateTime(), DateTimePatternEnum.YYYY_MM.getPattern());

            //目标目录
            String targetFolderName = appConfig.getProjectFolder() + Constants.FILE_FOLDER_FILE;
            File targetFolder = new File(targetFolderName + curMonth);
            if (!targetFolder.exists()) {
                targetFolder.mkdirs();
            }
            //真实文件名
            String realFileName = currentUserFolderName + fileSuffix;
            targetFilePath = targetFolder.getPath() + "/" + realFileName;

            //合并文件
            mergeFile(fileFolder.getPath(), targetFilePath, fileInfo.getFileName(), true);

            //视频文件切割
            fileTypeEnum = FileTypeEnum.getFileTypeBySuffix(StringUtils.getFileSuffix(fileInfo.getFileName()));
            if (FileTypeEnum.VIDEO.equals(fileTypeEnum)) {
                cutFile4Video(fileId, targetFilePath);
                //视频生成缩略图
                cover = curMonth + "/" + currentUserFolderName + Constants.IMAGE_PNG_SUFFIX;
                String coverPath = targetFolderName + "/" + cover;
                ScaleFiler.createCover4Video(new File(targetFilePath), Constants.LENGTH_150, new File(coverPath));
            } else if (FileTypeEnum.IMAGE.equals(fileTypeEnum)) {
                //生成缩略图
                cover = curMonth + "/" + realFileName.replace(".", "_.");
                String coverPath = targetFolderName + "/" + cover;
                Boolean created = ScaleFiler.createThumbnailWidthFFmpeg(new File(targetFilePath),
                        Constants.LENGTH_150, new File(coverPath), false);
                //没生成了copy一份当缩略图 有点抽象
                if (!created) {
                    FileUtils.copyFile(new File(targetFilePath), new File(coverPath));
                }
            }

        } catch (Exception e) {
            log.error("文件转码失败,文件ID:{},userId:{}", fileId, userDto.getUserId(), e);
            transferSuccess = false;
        } finally {
            FileInfo updateInfo = new FileInfo();
            updateInfo.setFileSize(new File(targetFilePath).length());
            updateInfo.setFileCover(cover);
            updateInfo.setStatus(transferSuccess ? FileStatusEnum.USING.getStatus() : FileStatusEnum.TRANSFER_FAIL.getStatus());
            //乐观锁 一个状态到另一个状态
            fileMapper.updateFileStatusWithOldStatus(fileId, userDto.getUserId(), updateInfo, FileStatusEnum.TRANSFER.getStatus());
        }

    }


    private void mergeFile(String srcPath, String destPath, String fileName, Boolean deleteSource) {
        File src = new File(srcPath);
        if (!src.exists()) {
            throw new BusinessException("目录不存在");
        }

        File[] fileList = src.listFiles();

        File targetFile = new File(destPath);
        RandomAccessFile writeFile = null;
        try {
            writeFile = new RandomAccessFile(targetFile, "rw");
            byte[] bytes = new byte[1024 * 10];
            for (int i = 0; i < fileList.length; i++) {
                int len = -1;
                File chunkFile = new File(srcPath + "/" + i);
                RandomAccessFile readFile = null;
                try {
                    readFile = new RandomAccessFile(chunkFile, "r");
                    while ((len = readFile.read(bytes)) != -1) {
                        writeFile.write(bytes, 0, len);
                    }
                } catch (Exception e) {
                    log.error("合并分片失败", e);
                    throw new BusinessException("合并分片失败");
                } finally {
                    if (readFile != null) {
                        readFile.close();
                    }
                }
            }
        } catch (Exception e) {
            log.error("合并文件: {}失败", fileName, e);
            throw new BusinessException("合并文件失败:" + fileName);
        } finally {
            if (writeFile != null) {
                try {
                    writeFile.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }

            if (deleteSource && src.exists()) {
                try {
                    FileUtils.deleteDirectory(src);
                } catch (IOException e) {
                    log.error("原目录删除失败", e);
                }
            }
        }
    }

    private void cutFile4Video(String fileId, String videoFilePath) {
        //创建同名切片目录
        File tsFolder = new File(videoFilePath.substring(0, videoFilePath.lastIndexOf(".")));
        if (!tsFolder.exists()) {
            tsFolder.mkdirs();
        }
        //ffmpeg命令
        final String CMD_TRANSFER_2TS = "ffmpeg -y -i %s  -vcodec copy -acodec copy -vbsf h264_mp4toannexb %s";
        final String CMD_CUT_TS = "ffmpeg -i %s -c copy -map 0 -f segment -segment_list %s -segment_time 30 %s/%s_%%4d.ts";
        String tsPath = tsFolder + "/" + Constants.TS_NAME;

        //生成.ts
        String cmd = String.format(CMD_TRANSFER_2TS, videoFilePath, tsPath);
        ProcessUtils.executeCommand(cmd, false);
        //生成索引文件.m3u8 和切片.ts
        cmd = String.format(CMD_CUT_TS, tsPath, tsFolder.getPath() + "/" + Constants.M3U8_NAME, tsFolder.getPath(), fileId);
        ProcessUtils.executeCommand(cmd, false);
        //删除index.ts
        new File(tsPath).delete();
    }

    /**
     * 检查重名文件
     */
    private void checkFileName(String filePid, String userId, String fileName, Integer folderType) {
        FileQuery fileInfoQuery = new FileQuery();
        fileInfoQuery.setFolderType(folderType);
        fileInfoQuery.setFileName(fileName);
        fileInfoQuery.setFilePid(filePid);
        fileInfoQuery.setUserId(userId);
        fileInfoQuery.setDelFlag(FileDeleteFlagEnum.USING.getFlag());
        Integer count = this.fileMapper.selectCount(fileInfoQuery);
        if (count > 0) {
            throw new BusinessException("此目录下已经存在同名文件，请修改名称");
        }
    }


    /**
     * 找到所有子集所包含的文件
     *
     * @param fileList 已经找到的文件list(父级)
     */
    private void findAllSubFolderFileList(List<String> fileList, String userId, String fileId, Integer delFlag) {
        fileList.add(fileId);
        FileQuery query = new FileQuery();
        query.setUserId(userId);
        query.setFilePid(fileId);
        query.setDelFlag(delFlag);
        query.setFolderType(FileFolderTypeEnum.FOLDER.getType());
        List<FileInfo> fileInfoList = fileMapper.selectList(query);
        for (FileInfo fileInfo : fileInfoList) {
            findAllSubFolderFileList(fileList, userId, fileInfo.getUserId(), delFlag);
        }
    }

    /**
     * 找到所有的子文件
     */
    private void findAllSubFile(List<FileInfo> copyFileList,
                                FileInfo fileInfo,
                                String sourceUserId,
                                String currentUserId,
                                Date curDate,
                                String newFilePid) {
        String sourceFileId = fileInfo.getFileId();
        fileInfo.setCreateTime(curDate);
        fileInfo.setLastUpdateTime(curDate);
        fileInfo.setFilePid(newFilePid);
        fileInfo.setUserId(currentUserId);
        String newFileId = StringUtils.getRandomString(Constants.LENGTH_10);
        fileInfo.setFileId(newFileId);
        copyFileList.add(fileInfo);
        //目录的话继续递归
        if (FileFolderTypeEnum.FOLDER.getType().equals(fileInfo.getFolderType())) {
            FileQuery query = new FileQuery();
            query.setFilePid(sourceFileId);
            query.setUserId(sourceUserId);
            List<FileInfo> sourceFileList = fileMapper.selectList(query);
            for (FileInfo item : sourceFileList) {
                findAllSubFile(copyFileList, item, sourceUserId, currentUserId, curDate, newFileId);
            }
        }
    }

    /**
     * 校验父级id
     */
    private void checkFilePid(String rootFilePid, String fileId, String userId) {
        FileInfo fileInfo = this.fileMapper.selectByFileIdAndUserId(fileId, userId);
        if (fileInfo == null) {
            throw new BusinessException(ResponseCodeEnum.CODE_600);
        }
        //不可能分享根目录吧都
        if (Constants.ZERO_STR.equals(fileInfo.getFilePid())) {
            throw new BusinessException(ResponseCodeEnum.CODE_600);
        }
        //recursion找到分享的父级id
        if (fileInfo.getFilePid().equals(rootFilePid)) {
            return;
        }
        checkFilePid(rootFilePid, fileInfo.getFilePid(), userId);
    }
}
