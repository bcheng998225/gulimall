package com.atguigu.gulimall.product.service;

import com.atguigu.common.utils.PageUtils;
import com.atguigu.gulimall.product.entity.SkuInfoEntity;
import com.atguigu.gulimall.product.vo.SkuItemVo;
import com.baomidou.mybatisplus.extension.service.IService;

import java.util.List;
import java.util.Map;

/**
 * sku信息
 *
 * @author bcheng
 * @email 1255165062@qq.com
 * @date 2022-08-11 21:27:22
 */
public interface SkuInfoService extends IService<SkuInfoEntity> {

    PageUtils queryPage(Map<String, Object> params);

    PageUtils queryPageByCondition(Map<String, Object> params);

    void saveSkuInfo(SkuInfoEntity skuInfoEntity);

    List<SkuInfoEntity> getSkusBySpuId(Long spuId);

    SkuItemVo item(Long skuId);

}

