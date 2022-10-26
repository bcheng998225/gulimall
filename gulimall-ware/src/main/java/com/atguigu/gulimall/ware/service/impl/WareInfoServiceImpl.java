package com.atguigu.gulimall.ware.service.impl;

import com.alibaba.druid.util.StringUtils;
import com.alibaba.fastjson.TypeReference;
import com.atguigu.common.utils.PageUtils;
import com.atguigu.common.utils.Query;
import com.atguigu.common.utils.R;
import com.atguigu.common.vo.order.MemberAddressVo;
import com.atguigu.common.vo.ware.FareVo;
import com.atguigu.gulimall.ware.dao.WareInfoDao;
import com.atguigu.gulimall.ware.entity.WareInfoEntity;
import com.atguigu.gulimall.ware.feign.MemberFeignService;
import com.atguigu.gulimall.ware.service.WareInfoService;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.Map;


@Service("wmsWareInfoService")
public class WareInfoServiceImpl extends ServiceImpl<WareInfoDao, WareInfoEntity> implements WareInfoService {


    @Autowired
    MemberFeignService memberFeignService;

    @Override
    public PageUtils queryPage(Map<String, Object> params) {

        String key = (String) params.get("key");
        QueryWrapper<WareInfoEntity> queryWrapper = new QueryWrapper<>();
        if (!StringUtils.isEmpty(key)) {
            queryWrapper.eq("id", key)
                    .or().like("name", key)
                    .or().like("address", key)
                    .or().like("areacode", key);
        }

        IPage<WareInfoEntity> page = this.page(
                new Query<WareInfoEntity>().getPage(params),
                queryWrapper
        );

        return new PageUtils(page);
    }

    @Override
    public FareVo getFare(Long addrId) {
        FareVo fareVo = new FareVo();
        R info = memberFeignService.info(addrId);
        MemberAddressVo data = info.getData("memberReceiveAddress",new TypeReference<MemberAddressVo>() {
        });
        if (data != null) {
            String phone = data.getPhone();
            String substring = phone.substring(phone.length() - 1, phone.length());
            BigDecimal bigDecimal = new BigDecimal(substring);
            fareVo.setFare(bigDecimal);
            fareVo.setAddress(data);

            return fareVo;
        }
        return null;
    }

}