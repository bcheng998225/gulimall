package com.atguigu.gulimall.ware.service;

import com.atguigu.common.to.mq.ware.StockLocked;
import com.atguigu.common.utils.PageUtils;
import com.atguigu.common.vo.order.WareSkuLockVo;
import com.atguigu.common.vo.ware.OrderVo;
import com.atguigu.gulimall.ware.dao.vo.SkuHasStockVo;
import com.atguigu.gulimall.ware.entity.WareSkuEntity;
import com.baomidou.mybatisplus.extension.service.IService;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

/**
 * 商品库存
 *
 * @author bcheng
 * @email 1255165062@qq.com
 * @date 2022-08-11 21:35:02
 */
public interface WareSkuService extends IService<WareSkuEntity> {

    PageUtils queryPage(Map<String, Object> params);

    void addStock(@Param("skuId") Long skuId, @Param("wareId") Long wareId, @Param("skuNum") Integer skuNum);

    List<SkuHasStockVo> getSkuHasStock(List<Long> skuIds);

    Boolean orderLockStock(WareSkuLockVo vo);

    void unlockStock(StockLocked to);

    void unlockStock(OrderVo to);
}

