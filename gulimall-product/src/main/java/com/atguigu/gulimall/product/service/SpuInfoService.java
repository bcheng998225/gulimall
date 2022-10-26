package com.atguigu.gulimall.product.service;

import com.atguigu.common.utils.PageUtils;
import com.atguigu.gulimall.product.entity.SpuInfoEntity;
import com.atguigu.gulimall.product.vo.SpuSaveVo;
import com.baomidou.mybatisplus.extension.service.IService;

import java.util.Map;

/**
 * spu信息
 *
 * @author bcheng
 * @email 1255165062@qq.com
 * @date 2022-08-11 21:27:22
 */
public interface SpuInfoService extends IService<SpuInfoEntity> {

    PageUtils queryPage(Map<String, Object> params);

    PageUtils queryPageByConditin(Map<String, Object> params);

    void saveSpuInfo(SpuSaveVo spuSaveVo);

    void saveBaseSpuInfo(SpuInfoEntity spuInfoEntity);

    void up(Long spuId);

    SpuInfoEntity getSpuInfoBySkuId(Long skuId);

}

