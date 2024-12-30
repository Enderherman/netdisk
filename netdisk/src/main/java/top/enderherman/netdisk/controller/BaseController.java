package top.enderherman.netdisk.controller;

import top.enderherman.netdisk.common.BaseResponse;
import top.enderherman.netdisk.entity.enums.ResponseCodeEnum;
import top.enderherman.netdisk.common.exceptions.BusinessException;

/**
 * @author Enderherman
 * @date 2024/12/24
 * 控制器基类
 */
public class BaseController {

    protected static final String STATUS_SUCCESS = "success";

    protected static final String STATUS_ERROR = "error";

    /**
     * 响应成功
     *
     * @param t 返回数据
     * @return restful返回类型数据
     * @date 2024/12/24
     */
    protected <T> BaseResponse<T> getSuccessResponse(T t) {
        BaseResponse<T> response = new BaseResponse<>();
        response.setStatus(STATUS_SUCCESS);
        response.setCode(ResponseCodeEnum.CODE_200.getCode());
        response.setMessage(ResponseCodeEnum.CODE_200.getMsg());
        response.setData(t);
        return response;
    }

    /**
     * 业务异常响应失败 请求参数错误
     *
     * @param t 返回数据
     * @param e 异常堆栈
     * @return restful格式返回类型数据
     * @date 2024/12/24
     */
    protected <T> BaseResponse<T> getBusinessErrorResponse(BusinessException e, T t) {
        BaseResponse<T> response = new BaseResponse<>();
        response.setStatus(STATUS_ERROR);
        if (e.getCode() == null) {
            response.setCode(ResponseCodeEnum.CODE_600.getCode());
        } else {
            response.setCode(e.getCode());
        }
        response.setMessage(e.getMessage());
        return response;
    }

    protected <T> BaseResponse<T> getServerErrorResponse(T t) {
        BaseResponse<T> response = new BaseResponse<>();
        response.setStatus(STATUS_ERROR);
        response.setCode(ResponseCodeEnum.CODE_500.getCode());
        response.setMessage(ResponseCodeEnum.CODE_500.getMsg());
        return response;
    }
}
