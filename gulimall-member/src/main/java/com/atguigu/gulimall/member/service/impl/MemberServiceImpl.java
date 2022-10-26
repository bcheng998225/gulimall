package com.atguigu.gulimall.member.service.impl;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.atguigu.common.utils.HttpUtils;
import com.atguigu.common.utils.PageUtils;
import com.atguigu.common.utils.Query;
import com.atguigu.common.vo.auth.SocialUser;
import com.atguigu.gulimall.member.dao.MemberDao;
import com.atguigu.gulimall.member.dao.MemberLevelDao;
import com.atguigu.gulimall.member.entity.MemberEntity;
import com.atguigu.gulimall.member.entity.MemberLevelEntity;
import com.atguigu.gulimall.member.exception.PhoneExistException;
import com.atguigu.gulimall.member.exception.UserNameExistException;
import com.atguigu.gulimall.member.service.MemberService;
import com.atguigu.gulimall.member.vo.MemberLoginVo;
import com.atguigu.gulimall.member.vo.MemberReigistVo;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.apache.http.HttpResponse;
import org.apache.http.util.EntityUtils;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;


@Service("MemberService")
public class MemberServiceImpl extends ServiceImpl<MemberDao, MemberEntity> implements MemberService {

    @Autowired
    MemberLevelDao memberLevelDao;

    @Autowired
    RedissonClient redissonClient;

    @Override
    public PageUtils queryPage(Map<String, Object> params) {
        IPage<MemberEntity> page = this.page(
                new Query<MemberEntity>().getPage(params),
                new QueryWrapper<MemberEntity>()
        );

        return new PageUtils(page);
    }

    /**
     * 注册
     *
     * @param vo
     */
    @Override
    public void register(MemberReigistVo vo) {
        // 1.加锁
//        RLock lock = redissonClient.getLock(MemberConstant.LOCK_KEY_REGIST_PRE + vo.getPhone());
//        try {
        MemberEntity entity = new MemberEntity();
        MemberDao memberDao = this.baseMapper;
        //设置默认等级
        MemberLevelEntity levelEntity = memberLevelDao.getDefaultLevel();
        entity.setLevelId(levelEntity.getId());

        //检查用户名和手机号是否唯一    异常机制
        checkPhone(vo.getPhone());
        checkUserName(vo.getUserName());

        entity.setMobile(vo.getPhone());
        entity.setUserName(vo.getUserName());
        entity.setNickName(vo.getUserName());
        //密码加密处理

        BCryptPasswordEncoder bCryptPasswordEncoder = new BCryptPasswordEncoder();
        String encode = bCryptPasswordEncoder.encode(vo.getPassword());
        entity.setPassWord(encode);

        //其他信息

        //保存
        memberDao.insert(entity);
//        } finally {
//            lock.unlock();
//        }
    }

    @Override
    public void checkPhone(String phone) throws PhoneExistException {
        MemberDao memberDao = this.baseMapper;
        Integer mobile = memberDao.selectCount(new QueryWrapper<MemberEntity>().eq("mobile", phone));
        if (mobile > 0) {
            throw new PhoneExistException();
        }
    }

    @Override
    public void checkUserName(String userName) throws UserNameExistException {
        MemberDao memberDao = this.baseMapper;
        Integer userNames = memberDao.selectCount(new QueryWrapper<MemberEntity>().eq("user_name", userName));
        if (userNames > 0) {
            throw new UserNameExistException();
        }
    }

    @Override
    public MemberEntity login(MemberLoginVo vo) {
        String loginacct = vo.getLoginacct();
        String password = vo.getPassword();// 明文

        // 1.查询MD5密文
        MemberEntity entity = baseMapper.selectOne(new QueryWrapper<MemberEntity>()
                .eq("user_name", loginacct)
                .or()
                .eq("mobile", loginacct));
        if (entity != null) {
            // 2.获取password密文进行校验
            BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();
            if (passwordEncoder.matches(password, entity.getPassWord())) {
                // 登录成功
                return entity;
            }
        }
        // 3.登录失败
        return null;
    }

    /**
     * 社交登录
     *
     * @param socialUser
     * @return
     */
    @Override
    public MemberEntity oauthLogin(SocialUser socialUser) throws Exception {
        String uid = socialUser.getUid();
        //1.判断当前账号是否已经登陆过
        MemberEntity social_uid = baseMapper.selectOne(new QueryWrapper<MemberEntity>().eq("social_uid", uid));
        if (social_uid != null) {
            //这个用户注册过
            MemberEntity entity = new MemberEntity();
            entity.setId(social_uid.getId());
            entity.setAccessToken(socialUser.getAccess_token());
            entity.setExpiresIn(socialUser.getExpires_in());
            baseMapper.updateById(entity);
            social_uid.setAccessToken(social_uid.getAccessToken());
            social_uid.setExpiresIn(social_uid.getExpiresIn());
            return entity;
        } else {
            //没有查到当前用户对应记录
            MemberEntity entity = new MemberEntity();
            //设置默认等级
            MemberLevelEntity levelEntity = memberLevelDao.getDefaultLevel();
            entity.setLevelId(levelEntity.getId());
            try {
                //3.查询当前社交账号的社交信息
                Map<String, String> headers = new HashMap<>();
                Map<String, String> query = new HashMap<>();
                query.put("access_token", socialUser.getAccess_token());
                query.put("social_uid", socialUser.getUid());
                HttpResponse response = HttpUtils.doGet("https://api.weibo.com", "/2/users/show.json", "get", headers, query);
                if (response.getStatusLine().getStatusCode() == 200) {
                    //获取到accessToken
                    String json = EntityUtils.toString(response.getEntity());
                    JSONObject jsonObject = JSON.parseObject(json);
                    //昵称
                    String name = jsonObject.getString("name");
                    //性别
                    String gender = jsonObject.getString("gender");

                    entity.setNickName(name);
                    entity.setGender("m".equals(gender) ? 1 : 0);
                }
            } catch (Exception e) {

            }
            entity.setSocialUid(socialUser.getUid());
            entity.setAccessToken(socialUser.getAccess_token());
            entity.setExpiresIn(socialUser.getExpires_in());
            this.baseMapper.insert(entity);
            return entity;


        }
    }
}