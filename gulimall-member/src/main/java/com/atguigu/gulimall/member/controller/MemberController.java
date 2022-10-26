package com.atguigu.gulimall.member.controller;

import com.atguigu.common.exception.BizCodeEnume;
import com.atguigu.common.utils.PageUtils;
import com.atguigu.common.utils.R;
import com.atguigu.common.vo.auth.SocialUser;
import com.atguigu.gulimall.member.entity.MemberEntity;
import com.atguigu.gulimall.member.exception.PhoneExistException;
import com.atguigu.gulimall.member.exception.UserNameExistException;
import com.atguigu.gulimall.member.feign.CouponFeignService;
import com.atguigu.gulimall.member.service.MemberService;
import com.atguigu.gulimall.member.vo.MemberLoginVo;
import com.atguigu.gulimall.member.vo.MemberReigistVo;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import java.util.Arrays;
import java.util.Map;


/**
 * 会员
 *
 * @author bcheng
 * @email 1255165062@qq.com
 * @date 2022-08-11 21:33:45
 */
@RestController
@RequestMapping("member/member")
public class MemberController {
    @Resource
    private MemberService memberService;

    @Resource
    private CouponFeignService smsCouponService;

    /**
     * 远程调用
     *
     * @return
     */
    @RequestMapping("/coupons")
    public R test() {
        MemberEntity memberEntity = new MemberEntity();
        R memberCoupons = smsCouponService.memberCoupons();
        return R.ok().put("member", memberCoupons).put("coupons", memberCoupons.get("coupons"));
    }

    /**
     * 列表
     */
    @RequestMapping("/list")
    public R list(@RequestParam Map<String, Object> params) {
        PageUtils page = memberService.queryPage(params);

        return R.ok().put("page", page);
    }

    /**
     * 注册
     *
     * @return
     */
    @PostMapping("/register")
    public R register(@RequestBody MemberReigistVo vo) {

        try {
            memberService.register(vo);
        } catch (PhoneExistException e) {
            return R.error(BizCodeEnume.PHONE_EXIST_EXCEPTION.getCode(), BizCodeEnume.PHONE_EXIST_EXCEPTION.getMsg());
        } catch (UserNameExistException e) {
            return R.error(BizCodeEnume.USER_EXIST_EXCEPTION.getCode(), BizCodeEnume.USER_EXIST_EXCEPTION.getMsg());
        }
        return R.ok();
    }

    /**
     * 社交登录
     *
     * @return
     */
    @PostMapping("/oauth2/login")
    public R oauthLogin(@RequestBody SocialUser socialUser) throws Exception {

        MemberEntity entity = memberService.oauthLogin(socialUser);
        if (entity != null) {
            return R.ok().setData(entity);
        } else {
            return R.error(BizCodeEnume.LOGINACCT_PASSWORD_INVAILD_EXCEPTION.getCode()
                    , BizCodeEnume.LOGINACCT_PASSWORD_INVAILD_EXCEPTION.getMsg());
        }
    }

    /**
     * 登录
     *
     * @return
     */
    @PostMapping("/login")
    public R login(@RequestBody MemberLoginVo vo) {

        MemberEntity entity = memberService.login(vo);
        if (entity != null) {
            return R.ok().setData(entity);
        } else {
            return R.error(BizCodeEnume.LOGINACCT_PASSWORD_INVAILD_EXCEPTION.getCode()
                    , BizCodeEnume.LOGINACCT_PASSWORD_INVAILD_EXCEPTION.getMsg());
        }
    }

    /**
     * 信息
     */

    @RequestMapping("/info/{id}")
    public R info(@PathVariable("id") Long id) {
        MemberEntity member = memberService.getById(id);

        return R.ok().put("member", member);
    }

    /**
     * 保存
     */

    @RequestMapping("/save")
    public R save(@RequestBody MemberEntity member) {
        memberService.save(member);

        return R.ok();
    }

    /**
     * 修改
     */
    @RequestMapping("/update")
    public R update(@RequestBody MemberEntity member) {
        memberService.updateById(member);

        return R.ok();
    }

    /**
     * 删除
     */
    @RequestMapping("/delete")
    public R delete(@RequestBody Long[] ids) {
        memberService.removeByIds(Arrays.asList(ids));

        return R.ok();
    }

}
