package com.atguigu.gulimall.product.dao;

import com.atguigu.gulimall.product.entity.ProductAttrValueEntity;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * spu属性值
 * 
 * @author bcheng
 * @email 1255165062@qq.com
 * @date 2022-08-11 21:27:22
 */
@Mapper
public interface ProductAttrValueDao extends BaseMapper<ProductAttrValueEntity> {

    List<String> getSkuAttrValue(@Param("skuId") Long skuId);
}
