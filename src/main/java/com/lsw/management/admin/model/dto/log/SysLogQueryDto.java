package com.lsw.management.admin.model.dto.log;

import lombok.Data;

/**
 * @Author: lsw
 * @desc
 * @date: 2023/4/22  11:28
 */
@Data
public class SysLogQueryDto {
    private Integer level;
    private Integer businessType;
    private String requestMethod;
    private String operName;
    private String operUrl;
    private String operIp;
    private Long operTime;
    private String exceptionDetail;
}
