package com.atguigu.gulimall.member.service;

import com.atguigu.common.utils.PageUtils;
import com.atguigu.common.vo.auth.SocialUser;
import com.atguigu.gulimall.member.entity.MemberEntity;
import com.atguigu.gulimall.member.exception.PhoneExistException;
import com.atguigu.gulimall.member.exception.UserNameExistException;
import com.atguigu.gulimall.member.vo.MemberLoginVo;
import com.atguigu.gulimall.member.vo.MemberReigistVo;
import com.baomidou.mybatisplus.extension.service.IService;

import java.util.Map;

/**
 * 会员
 *
 * @author bcheng
 * @email 1255165062@qq.com
 * @date 2022-08-11 21:33:45
 */
public interface MemberService extends IService<MemberEntity> {

    PageUtils queryPage(Map<String, Object> params);

    void register(MemberReigistVo vo);

    void checkPhone(String phone) throws PhoneExistException;

    void checkUserName(String userName) throws UserNameExistException;

    MemberEntity login(MemberLoginVo vo);

    MemberEntity oauthLogin(SocialUser socialUser) throws Exception;
}

