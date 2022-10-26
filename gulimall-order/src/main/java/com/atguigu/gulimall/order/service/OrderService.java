package com.atguigu.gulimall.order.service;

import com.atguigu.common.to.seckill.SeckillOrderTo;
import com.atguigu.common.utils.PageUtils;
import com.atguigu.common.vo.order.OrderConfirmVo;
import com.atguigu.common.vo.order.OrderSubmitVo;
import com.atguigu.gulimall.order.entity.OrderEntity;
import com.atguigu.gulimall.order.vo.OrderSubmitResponseVo;
import com.atguigu.gulimall.order.vo.PayAsyncVo;
import com.atguigu.gulimall.order.vo.PayVo;
import com.baomidou.mybatisplus.extension.service.IService;

import java.util.Map;
import java.util.concurrent.ExecutionException;

/**
 * 订单
 *
 * @author bcheng
 * @email 1255165062@qq.com
 * @date 2022-08-11 21:29:48
 */
public interface OrderService extends IService<OrderEntity> {

    PageUtils queryPage(Map<String, Object> params);

    OrderConfirmVo confirmOrder() throws ExecutionException, InterruptedException;


    OrderSubmitResponseVo submitOrder(OrderSubmitVo vo);

    OrderEntity getOrderByOrderSn(String orderSn);

    void closeOrder(OrderEntity entity);

    PayVo getOrderPay(String orderSn);

    PageUtils queryPageWithItem(Map<String, Object> params);

    String handPayResult(PayAsyncVo vo);

    void createSeckillOrder(SeckillOrderTo seckillOrderTo);

}

