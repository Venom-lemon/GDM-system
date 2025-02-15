package com.lsw.management.admin.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.lsw.management.admin.mapper.UserAccountMapper;
import com.lsw.management.admin.mapper.UserInfoMapper;
import com.lsw.management.admin.model.dto.UserLoginDto;
import com.lsw.management.admin.model.dto.UserQueryDto;
import com.lsw.management.admin.model.dto.UserRegistryDto;
import com.lsw.management.admin.model.po.UserAccount;
import com.lsw.management.admin.model.po.UserInfo;
import com.lsw.management.admin.model.vo.UserAccountVo;
import com.lsw.management.admin.model.vo.UserVo;
import com.lsw.management.admin.service.UserAccountService;
import com.lsw.management.common.constants.ErrorCode;
import com.lsw.management.common.constants.GlobalConstants;
import com.lsw.management.common.exception.BusinessException;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.util.DigestUtils;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import java.util.Date;
import java.util.List;

/**
 * @author lsw
 * @Date 2023/4/6 16:34
 * @desc
 */
@Service
public class UserAccountServiceImpl extends ServiceImpl<UserAccountMapper, UserAccount> implements UserAccountService {

    private final Logger log = LoggerFactory.getLogger(UserAccountServiceImpl.class);

    /**
     * 盐值，混淆密码
     */
    private static final String SALT = "lsw";

    @Resource
    UserAccountMapper userAccountMapper;

    @Resource
    UserInfoMapper userInfoMapper;

    @Override
    public UserAccount getCurrentUser(HttpServletRequest request) {
        UserAccountVo user = (UserAccountVo) request.getSession().getAttribute(GlobalConstants.SESSION_KEY);
        UserAccount userAccount;
        //兼容匿名操作
        if (user == null || user.getId() == null) {
            userAccount=new UserAccount();
            userAccount.setUsername("anonymous");
        }else{
            QueryWrapper<UserAccount> queryWrapper = new QueryWrapper<>();
            queryWrapper.eq(UserAccount.ID, user.getId());
            userAccount = userAccountMapper.selectOne(queryWrapper);
        }
        return userAccount;
    }

    @Override
    public UserAccountVo baseLogin(UserLoginDto user, HttpServletRequest request) {
        String password = user.getPassword();
        QueryWrapper<UserAccount> userAccountQueryWrapper = new QueryWrapper<>();
        userAccountQueryWrapper.eq(UserAccount.USERNAME, user.getUsername());
        UserAccount userAccount = userAccountMapper.selectOne(userAccountQueryWrapper);
        if (userAccount == null) {
            throw new BusinessException(ErrorCode.NOT_EXIST_USER);
        }
        // 2. 加密
        String encryptPassword = DigestUtils.md5DigestAsHex((SALT + password).getBytes());

        if (!encryptPassword.equals(userAccount.getPassword())) {
            throw new BusinessException(ErrorCode.PASSWORD_ERROR);
        }
        UserAccountVo userAccountVo = new UserAccountVo();
        BeanUtils.copyProperties(userAccount, userAccountVo);
        //用户态保存
        request.getSession().setAttribute(GlobalConstants.SESSION_KEY, userAccountVo);
        return userAccountVo;
    }

    @Override
    public void userRegister(UserRegistryDto registryDto) {
        if (!registryDto.getPassword().equals(registryDto.getCheckPassword())) {
            throw new BusinessException(ErrorCode.INVALID_PARAMS, "两次密码一样");
        }
        String userAccount = registryDto.getUsername();
        QueryWrapper<UserAccount> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq(UserAccount.USERNAME, userAccount);
        if (userAccountMapper.selectCount(queryWrapper) > 0) {
            throw new BusinessException(ErrorCode.ACCOUNT_DUPLICATE);
        }
        //组装账号信息
        UserAccount uAccount = new UserAccount();
        uAccount.setCreateTime(new Date());
        uAccount.setUsername(userAccount);
        // 2. 加密
        String encryptPassword = DigestUtils.md5DigestAsHex((SALT + registryDto.getPassword()).getBytes());
        uAccount.setPassword(encryptPassword);
        userAccountMapper.insert(uAccount);
        //组装用户信息
        UserInfo userInfo = new UserInfo();
        userInfo.setAccountId(uAccount.getId());
        userInfo.setCreateTime(new Date());
        BeanUtils.copyProperties(registryDto,userInfo);
        userInfoMapper.insert(userInfo);
    }

    @Override
    public List<UserVo> selectUserVoPage(Page<UserVo> page, UserQueryDto queryDto) {
        QueryWrapper<UserAccount> queryWrapper = new QueryWrapper<>();
        queryWrapper.select("user_account.id,user_account.username,user_account.permissions,user_account.state",
                        " user_info.name, user_info.gender, user_info.mobile,user_info.email, user_info.birthday" +
                                ",user_info.major,user_info.professional,user_info.student_type")
                .eq("user_account.deleted", 0)
                .eq(StringUtils.isNotBlank(queryDto.getUsername()), "user_account.username", queryDto.getUsername())
                .like(StringUtils.isNotBlank(queryDto.getName()), "user_info.name", queryDto.getName())
                .like(StringUtils.isNotBlank(queryDto.getPermissions()), "user_account.permissions", queryDto.getPermissions())
                .eq(queryDto.getState() != null, "user_account.state", queryDto.getState())
                .eq(queryDto.getGender() != null, "user_info.gender", queryDto.getGender())
                .eq(StringUtils.isNotBlank(queryDto.getMobile()), "user_info.mobile", queryDto.getMobile())
                .eq(StringUtils.isNotBlank(queryDto.getEmail()), "user_info.email", queryDto.getEmail())
                .eq(queryDto.getMajor()!=null, "user_info.major", queryDto.getMajor())
                .eq(queryDto.getProfessional()!=null, "user_info.professional", queryDto.getProfessional())
                .eq(queryDto.getStudentType()!=null, "user_info.student_type", queryDto.getStudentType())
                .orderByDesc("user_account.create_time");
        return userAccountMapper.selectUserVoPage(page, queryWrapper);
    }

    @Override
    public boolean userLogout(HttpServletRequest request) {
        if (request.getSession().getAttribute(GlobalConstants.SESSION_KEY) == null) {
            throw new BusinessException(ErrorCode.UNLOGIN);
        }
        request.removeAttribute(GlobalConstants.SESSION_KEY);
        return false;
    }


}
