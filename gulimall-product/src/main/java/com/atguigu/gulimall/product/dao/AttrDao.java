package com.atguigu.gulimall.product.dao;

import com.atguigu.gulimall.product.entity.AttrEntity;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 商品属性
 * 
 * @author bcheng
 * @email 1255165062@qq.com
 * @date 2022-08-11 21:27:22
 */
@Mapper
public interface AttrDao extends BaseMapper<AttrEntity> {

    List<Long> seleteSearchAttrs(@Param("attrIds") List<Long> attrIds);
}
