package top.enderherman.netdisk.service.impl;

import java.util.Date;
import java.util.List;


import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

import org.springframework.transaction.annotation.Transactional;
import top.enderherman.netdisk.common.constants.Constants;
import top.enderherman.netdisk.common.exceptions.BusinessException;
import top.enderherman.netdisk.common.utils.DateUtils;
import top.enderherman.netdisk.common.utils.StringUtils;
import top.enderherman.netdisk.entity.dto.SessionShareDto;
import top.enderherman.netdisk.entity.enums.PageSizeEnum;
import top.enderherman.netdisk.entity.enums.ResponseCodeEnum;
import top.enderherman.netdisk.entity.enums.ShareValidTypeEnum;
import top.enderherman.netdisk.entity.query.FileShareQuery;
import top.enderherman.netdisk.entity.pojo.FileShare;
import top.enderherman.netdisk.entity.vo.PaginationResultVO;
import top.enderherman.netdisk.entity.query.SimplePage;
import top.enderherman.netdisk.mapper.FileShareMapper;
import top.enderherman.netdisk.service.FileShareService;


/**
 * 分享信息 业务接口实现
 */
@Service("fileShareService")
public class FileShareServiceImpl implements FileShareService {

    @Resource
    private FileShareMapper<FileShare, FileShareQuery> fileShareMapper;

    /**
     * 根据条件查询列表
     */
    @Override
    public List<FileShare> findListByParam(FileShareQuery param) {
        return this.fileShareMapper.selectList(param);
    }

    /**
     * 根据条件查询列表
     */
    @Override
    public Integer findCountByParam(FileShareQuery param) {
        return this.fileShareMapper.selectCount(param);
    }

    /**
     * 分页查询方法
     */
    @Override
    public PaginationResultVO<FileShare> findListByPage(FileShareQuery param) {
        int count = this.findCountByParam(param);
        int pageSize = param.getPageSize() == null ? PageSizeEnum.SIZE15.getSize() : param.getPageSize();

        SimplePage page = new SimplePage(param.getPageNo(), count, pageSize);
        param.setSimplePage(page);
        List<FileShare> list = this.findListByParam(param);
        return new PaginationResultVO<>(count, page.getPageSize(), page.getPageNo(), page.getPageTotal(), list);
    }

    /**
     * 新增
     */
    @Override
    public Integer add(FileShare bean) {
        return this.fileShareMapper.insert(bean);
    }

    /**
     * 批量新增
     */
    @Override
    public Integer addBatch(List<FileShare> listBean) {
        if (listBean == null || listBean.isEmpty()) {
            return 0;
        }
        return this.fileShareMapper.insertBatch(listBean);
    }

    /**
     * 批量新增或者修改
     */
    @Override
    public Integer addOrUpdateBatch(List<FileShare> listBean) {
        if (listBean == null || listBean.isEmpty()) {
            return 0;
        }
        return this.fileShareMapper.insertOrUpdateBatch(listBean);
    }

    /**
     * 多条件更新
     */
    @Override
    public Integer updateByParam(FileShare bean, FileShareQuery param) {
        StringUtils.checkParam(param);
        return this.fileShareMapper.updateByParam(bean, param);
    }

    /**
     * 多条件删除
     */
    @Override
    public Integer deleteByParam(FileShareQuery param) {
        StringUtils.checkParam(param);
        return this.fileShareMapper.deleteByParam(param);
    }

    /**
     * 根据ShareId获取对象
     */
    @Override
    public FileShare getFileShareByShareId(String shareId) {
        return this.fileShareMapper.selectByShareId(shareId);
    }

    /**
     * 根据ShareId修改
     */
    @Override
    public Integer updateFileShareByShareId(FileShare bean, String shareId) {
        return this.fileShareMapper.updateByShareId(bean, shareId);
    }

    /**
     * 根据ShareId删除
     */
    @Override
    public Integer deleteFileShareByShareId(String shareId) {
        return this.fileShareMapper.deleteByShareId(shareId);
    }

    /**
     * 新增分享文件
     */
    @Override
    public void saveShare(FileShare fileShare) {
        ShareValidTypeEnum shareValidTypeEnum = ShareValidTypeEnum.getByType(fileShare.getValidType());
        if (shareValidTypeEnum == null) {
            throw new BusinessException(ResponseCodeEnum.CODE_600);
        }
        //设置过期时间
        if (shareValidTypeEnum != ShareValidTypeEnum.FOREVER) {
            fileShare.setExpireTime(DateUtils.getAfterDate(shareValidTypeEnum.getDays()));
        }
        Date curDate = new Date();
        fileShare.setShareTime(curDate);
        if (StringUtils.isEmpty(fileShare.getCode())) {
            fileShare.setCode(StringUtils.getRandomString(Constants.LENGTH_5));
        }
        fileShare.setShareId(StringUtils.getRandomString(Constants.LENGTH_20));
        fileShare.setShowCount(0);
        fileShareMapper.insert(fileShare);
    }


    /**
     * 取消分享
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void cancelShare(String shareIds, String userId) {
        String[] shareIdArray = shareIds.split(",");
        if (shareIdArray.length == 0) {
            return;
        }

        Integer count = this.fileShareMapper.deleteFileShareBatch(shareIdArray, userId);
        if (count != shareIdArray.length) {
            throw new BusinessException(ResponseCodeEnum.CODE_600);
        }

    }

    @Override
    public SessionShareDto checkShareCode(String shareId, String code) {
        FileShare share = fileShareMapper.selectByShareId(shareId);
        if (null == share || (share.getExpireTime() != null && new Date().after(share.getExpireTime()))) {
            throw new BusinessException(ResponseCodeEnum.CODE_902);
        }
        if (!share.getCode().equals(code)) {
            throw new BusinessException("提取码错误");
        }
        //更新浏览次数
        fileShareMapper.updateShareShowCount(shareId);
        SessionShareDto shareSessionDto = new SessionShareDto();
        shareSessionDto.setShareId(shareId);
        shareSessionDto.setShareUserId(share.getUserId());
        shareSessionDto.setFileId(share.getFileId());
        shareSessionDto.setExpireTime(share.getExpireTime());
        return shareSessionDto;
    }
}