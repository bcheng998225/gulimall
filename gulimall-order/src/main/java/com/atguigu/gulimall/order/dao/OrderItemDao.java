package com.atguigu.gulimall.order.dao;

import com.atguigu.gulimall.order.entity.OrderItemEntity;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * 订单项信息
 * 
 * @author bcheng
 * @email 1255165062@qq.com
 * @date 2022-08-11 21:29:48
 */
@Mapper
public interface OrderItemDao extends BaseMapper<OrderItemEntity> {
	
}
