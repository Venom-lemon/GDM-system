package com.lsw.management.admin.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.plugins.pagination.PageDTO;
import com.lsw.management.admin.annotation.AuthCheck;
import com.lsw.management.admin.annotation.Log;
import com.lsw.management.admin.annotation.PermissionEnum;
import com.lsw.management.admin.model.dto.UserLoginDto;
import com.lsw.management.admin.model.dto.UserQueryDto;
import com.lsw.management.admin.model.dto.UserRegistryDto;
import com.lsw.management.admin.model.dto.UserUpdateDto;
import com.lsw.management.admin.model.po.UserAccount;
import com.lsw.management.admin.model.po.UserInfo;
import com.lsw.management.admin.model.vo.UserAccountVo;
import com.lsw.management.admin.model.vo.UserVo;
import com.lsw.management.admin.model.vo.VerifyImgResult;
import com.lsw.management.admin.service.UserAccountService;
import com.lsw.management.admin.service.UserInfoService;
import com.lsw.management.admin.service.impl.ImgVerifyCodeServiceImpl;
import com.lsw.management.common.constants.ErrorCode;
import com.lsw.management.common.exception.BusinessException;
import com.lsw.management.common.http.response.ApiResponse;
import com.lsw.management.common.http.response.ResponseHelper;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.BeanUtils;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * @author lsw
 * @Date 2023/4/6 15:03
 * @desc 用户信息管理模块
 */
@Api("用户账户模块")
@RestController
@RequestMapping("/user")
public class UserController {

    @Resource
    UserAccountService userService;

    @Resource
    UserInfoService userInfoService;

    @Resource
    ImgVerifyCodeServiceImpl imgVerifyCodeService;

    @Log
    @ApiOperation(value = "用户信息分页查询",httpMethod = "POST")
    @PostMapping("/pageList")
    @AuthCheck(value = PermissionEnum.USER)
    public ApiResponse<PageDTO<UserVo>> pageList(@RequestBody UserQueryDto queryDto){
        long current = 1;
        long size = 10;
        if(queryDto.getCurrent()!=null){
            current=queryDto.getCurrent();
        }
        if(queryDto.getPageSize()!=null){
            size=queryDto.getPageSize();
        }
        Page<UserVo> page = new Page<>(current, size);
        PageDTO<UserVo> pageDTO = new PageDTO<>(current, size);
        List<UserVo> userVoList = userService.selectUserVoPage(page, queryDto);
        pageDTO.setRecords(userVoList);
        pageDTO.setTotal(page.getTotal());
        return ResponseHelper.success(pageDTO);
    }

    @ApiOperation(value = "基础用户登录",httpMethod = "POST")
    @PostMapping(value = "/baseLogin" )
    public ApiResponse<UserAccountVo> baseLogin(@Validated @RequestBody UserLoginDto user, HttpServletRequest request){
        String verifyCode = user.getVerifyCode();
        String randomKey = user.getRandomKey();
//        imgVerifyCodeService.checkCaptcha(randomKey,verifyCode);
        UserAccountVo userAccountVo = userService.baseLogin(user,request);
        return ResponseHelper.success(userAccountVo);
    }

    @ApiOperation(value = "退出登录",httpMethod = "GET")
    @GetMapping("/logout")
    public ApiResponse<Boolean> userLogout(HttpServletRequest request){
        if(request==null){
            throw new BusinessException(ErrorCode.MISSING_PARAMS);
        }
        boolean result = userService.userLogout(request);
        return ResponseHelper.success(result);
    }

    @ApiOperation(value = "获取验证码",httpMethod = "GET")
    @GetMapping("/imgVerifyCode")
    public ApiResponse<VerifyImgResult> getVerifyCode(@RequestParam(value = "length",defaultValue = "4")  Integer length){
        if(length==null||length==0){
           length=4;
        }
      return  ResponseHelper.success(imgVerifyCodeService.generateVerifyCoe(length));
    }

    @ApiOperation(value = "用户注册",httpMethod = "POST")
    @PostMapping("/userRegistry")
    public  ApiResponse<UserAccountVo> userRegistry(@RequestBody @Validated UserRegistryDto registryDto){
        userService.userRegister(registryDto);
        return ResponseHelper.success();
    }

    @Log
    @AuthCheck(value = PermissionEnum.ADMIN)
    @ApiOperation(value = "账号删除",httpMethod = "DELETE")
    @DeleteMapping("/delete")
    public ApiResponse<Boolean> deleteUser(@RequestParam String ids){
        if(StringUtils.isBlank(ids)){
            throw new BusinessException(ErrorCode.MISSING_PARAMS);
        }
        String[] id = ids.split(",");
        boolean userAccountDeleteFlag = userService.removeById(id);
        Arrays.stream(id).forEach(i->{
            QueryWrapper<UserInfo> userInfoQueryWrapper = new QueryWrapper<>();
            userInfoQueryWrapper.eq(UserInfo.ACCOUNT_ID,id);
            userInfoService.remove(userInfoQueryWrapper);
        });
        return ResponseHelper.success(userAccountDeleteFlag);
    }

    @ApiOperation(value = "用户信息更新",httpMethod = "POST")
    @PostMapping("/updateInfo")
    public ApiResponse<Boolean> updateUserInfo(@RequestBody UserUpdateDto updateDto){
        UpdateWrapper<UserAccount> userAccountUpdateWrapper = new UpdateWrapper<>();
        UpdateWrapper<UserInfo> userInfoUpdateWrapper = new UpdateWrapper<>();
        userAccountUpdateWrapper.eq(UserAccount.ID,updateDto.getId());
        UserAccount userAccount = new UserAccount();
        BeanUtils.copyProperties(updateDto,userAccount);
        boolean userAccountUpdateFlag = userService.update(userAccount, userAccountUpdateWrapper);
        userInfoUpdateWrapper.eq(UserInfo.ACCOUNT_ID,updateDto.getId());
        UserInfo userInfo = new UserInfo();
        BeanUtils.copyProperties(updateDto,userInfo);
        userInfo.setId(null);
        boolean userInfoUpdateFlag = userInfoService.update(userInfo, userInfoUpdateWrapper);
        return ResponseHelper.success(userAccountUpdateFlag&&userInfoUpdateFlag);
    }

    @Transactional
    @AuthCheck(value = PermissionEnum.USER)
    @ApiOperation(value = "查询用户账号",httpMethod = "GET")
    @GetMapping("/getUserAccount")
    public ApiResponse<List<UserAccountVo>> getUser(String ids){
        if(StringUtils.isBlank(ids)){
            throw new BusinessException(ErrorCode.MISSING_PARAMS);
        }
        String[] id = ids.split(",");
        List<UserAccount> userAccounts = userService.listByIds(Arrays.asList(id));
        List<UserAccountVo> res = userAccounts.stream().map(account->{
            UserAccountVo userAccountVo = new UserAccountVo();
            BeanUtils.copyProperties(account,userAccountVo);
            return userAccountVo;
        }).collect(Collectors.toList());
        return ResponseHelper.success(res);
    }
}
