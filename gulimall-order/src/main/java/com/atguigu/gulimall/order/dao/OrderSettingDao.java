package com.atguigu.gulimall.order.dao;

import com.atguigu.gulimall.order.entity.OrderSettingEntity;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * 订单配置信息
 * 
 * @author bcheng
 * @email 1255165062@qq.com
 * @date 2022-08-11 21:29:48
 */
@Mapper
public interface OrderSettingDao extends BaseMapper<OrderSettingEntity> {
	
}
