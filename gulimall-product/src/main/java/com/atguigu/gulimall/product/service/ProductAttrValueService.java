package com.atguigu.gulimall.product.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.atguigu.common.utils.PageUtils;
import com.atguigu.gulimall.product.entity.ProductAttrValueEntity;

import java.util.List;
import java.util.Map;

/**
 * spu属性值
 *
 * @author bcheng
 * @email 1255165062@qq.com
 * @date 2022-08-11 21:27:22
 */
public interface ProductAttrValueService extends IService<ProductAttrValueEntity> {

    PageUtils queryPage(Map<String, Object> params);


    void saveProductAttr(List<ProductAttrValueEntity> collect);

    List<ProductAttrValueEntity> baseAttrListForspu(Long spuId);

    void updateSpuAttr(Long spuId, List<ProductAttrValueEntity> entities);

    List<String> getSkuAttrValue(Long skuId);
}

