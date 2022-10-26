package com.atguigu.gulimall.order.dao;

import com.atguigu.gulimall.order.entity.OrderEntity;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 订单
 * 
 * @author bcheng
 * @email 1255165062@qq.com
 * @date 2022-08-11 21:29:48
 */
@Mapper
public interface OrderDao extends BaseMapper<OrderEntity> {

    void updataOrderStatus(@Param("outTradeNo") String outTradeNo, @Param("code") Integer code);
}
