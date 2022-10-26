package com.atguigu.gulimall.ware.service.impl;

import com.alibaba.druid.util.StringUtils;
import com.alibaba.fastjson.TypeReference;
import com.atguigu.common.exception.NoStockException;
import com.atguigu.common.to.mq.ware.StockDetailTo;
import com.atguigu.common.to.mq.ware.StockLocked;
import com.atguigu.common.utils.PageUtils;
import com.atguigu.common.utils.Query;
import com.atguigu.common.utils.R;
import com.atguigu.common.vo.order.OrderItemVO;
import com.atguigu.common.vo.order.WareSkuLockVo;
import com.atguigu.common.vo.ware.OrderVo;
import com.atguigu.gulimall.ware.dao.WareSkuDao;
import com.atguigu.gulimall.ware.dao.vo.SkuHasStockVo;
import com.atguigu.gulimall.ware.entity.WareOrderTaskDetailEntity;
import com.atguigu.gulimall.ware.entity.WareOrderTaskEntity;
import com.atguigu.gulimall.ware.entity.WareSkuEntity;
import com.atguigu.gulimall.ware.feign.OrderFeignService;
import com.atguigu.gulimall.ware.feign.ProductFeignService;
import com.atguigu.gulimall.ware.service.WareOrderTaskDetailService;
import com.atguigu.gulimall.ware.service.WareOrderTaskService;
import com.atguigu.gulimall.ware.service.WareSkuService;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.Data;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;


@Service
public class WareSkuServiceImpl extends ServiceImpl<WareSkuDao, WareSkuEntity> implements WareSkuService {

    @Autowired
    WareSkuDao wareSkuDao;

    @Autowired
    ProductFeignService feignService;

    @Autowired
    RabbitTemplate rabbitTemplate;

    @Autowired
    WareOrderTaskDetailService orderTaskDetailService;

    @Autowired
    WareOrderTaskService orderTaskService;

    @Autowired
    OrderFeignService orderFeignService;

    @Override
    public PageUtils queryPage(Map<String, Object> params) {
        QueryWrapper<WareSkuEntity> queryWrapper = new QueryWrapper<>();
        String skuId = (String) params.get("skuId");
        if (!StringUtils.isEmpty(skuId)) {
            queryWrapper.eq("sku_id", skuId);
        }
        String wareId = (String) params.get("wareId");
        if (!StringUtils.isEmpty(wareId)) {
            queryWrapper.eq("ware_id", wareId);
        }
        IPage<WareSkuEntity> page = this.page(
                new Query<WareSkuEntity>().getPage(params),
                queryWrapper
        );
        return new PageUtils(page);
    }

    /**
     * //3.将成功采购进行入库
     *
     * @param skuId
     * @param wareId
     * @param skuNum
     */
    @Transactional
    @Override
    public void addStock(Long skuId, Long wareId, Integer skuNum) {
        //判断如果还没有这个库存记录那就是新增操作
        List<WareSkuEntity> entities = wareSkuDao.selectList(new QueryWrapper<WareSkuEntity>().eq("sku_id", skuId)
                .eq("ware_id", wareId));
        if (entities == null || entities.size() == 0) {
            WareSkuEntity wareSkuEntity = new WareSkuEntity();
            wareSkuEntity.setSkuId(skuId);
            wareSkuEntity.setWareId(wareId);
            wareSkuEntity.setStock(skuNum);
            wareSkuEntity.setStockLocked(0);
            //TODO  远程查询sku的名字,如果失败，整个事务无需回滚
            //TODO 还有什么办法出现异常，不回滚
            try {
                R info = feignService.info(skuId);
                Map<String, Object> skuInfo = (Map<String, Object>) info.get("skuInfo");
                if (info.getCode() == 0) {
                    wareSkuEntity.setSkuName((String) skuInfo.get("skuName"));
                }
            } catch (Exception e) {

            }

            wareSkuDao.insert(wareSkuEntity);
        } else {
            wareSkuDao.addStock(skuId, wareId, skuNum);
        }

    }

    /**
     * 查询sku是否有库存
     *
     * @param skuIds
     * @return
     */
    @Override
    public List<SkuHasStockVo> getSkuHasStock(List<Long> skuIds) {
        List<SkuHasStockVo> collect = skuIds.stream().map(skuId -> {
            SkuHasStockVo vo = new SkuHasStockVo();
            //查询当前sku总库存量
            /**
             * SELECT SUM(stock-stock_locked) FROM wms_ware_sku WHERE ware_id=2
             */
            Long count = this.baseMapper.getSkuStock(skuId);
            vo.setSkuId(skuId);
            vo.setHasStock(count == null ? false : count > 0);
            return vo;
        }).collect(Collectors.toList());

        return collect;
    }

    /**
     * 锁定库存
     *
     * @param vo
     * @return
     */
    @Override
    @Transactional(rollbackFor = NoStockException.class)
    public Boolean orderLockStock(WareSkuLockVo vo) {
        /**
         * 保存库存工作单详情
         *  wms_ware_order_task
         */
        WareOrderTaskEntity taskEntity = new WareOrderTaskEntity();
        taskEntity.setOrderSn(vo.getOrderSn());
        orderTaskService.save(taskEntity);

        //1.找到每个商品所在的仓库是否有库存
        List<OrderItemVO> locks = vo.getLocks();
        List<skuWareHashStock> collect = locks.stream().map(item -> {
            skuWareHashStock skuWareHashStock = new skuWareHashStock();
            Long skuId = item.getSkuId();
            skuWareHashStock.setSkuId(skuId);
            skuWareHashStock.setNum(item.getCount());
            //查询商品在哪里有库存
            List<Long> wareId = wareSkuDao.listWareIdHashStock(skuId);
            skuWareHashStock.setWareId(wareId);
            return skuWareHashStock;
        }).collect(Collectors.toList());


        //2.锁定库存
        for (skuWareHashStock hashStock : collect) {
            boolean skuStock = false;
            Long skuId = hashStock.getSkuId();
            Integer num = hashStock.getNum();
            List<Long> wareIds = hashStock.getWareId();
            if (wareIds.size() == 0 && wareIds == null) {
                //没有仓库有库存
                throw new NoStockException(skuId.toString());
            }
            //1、如果每一个商品都锁定成功,将当前商品锁定了几件的工作单记录发给MQ
            //2、锁定失败。前面保存的工作单信息都回滚了。发送出去的消息，即使要解锁库存，由于在数据库查不到指定的id，所有就不用解锁
            for (Long wareId : wareIds) {
                Long count = wareSkuDao.lockSkuStock(skuId, wareId, num);
                if (count == 1) {
                    //当前仓库锁成功了
                    skuStock = true;

                    /**
                     * 保存库存工作单详情
                     * wms_ware_order_task_detail
                     */
                    WareOrderTaskDetailEntity orderTaskDetailEntity = new WareOrderTaskDetailEntity(
                            null, skuId, null, num, taskEntity.getId(), wareId, 1);
                    orderTaskDetailService.save(orderTaskDetailEntity);
                    //告诉mq 库存锁定成功
                    StockLocked stockLocked = new StockLocked();
                    stockLocked.setId(taskEntity.getId());
                    StockDetailTo stockDetailTo = new StockDetailTo();
                    BeanUtils.copyProperties(orderTaskDetailEntity, stockDetailTo);
                    //发id不行。防止回滚后找不到数据
                    stockLocked.setDetailTo(stockDetailTo);
                    rabbitTemplate.convertAndSend("stock-event-exchange", "stock.locked", stockLocked);
                    break;
                } else {
                    //当前仓库锁失败了 重试下一个仓库

                }
            }
            if (!skuStock) {
                //所有仓库都没锁住
                throw new NoStockException(skuId.toString());
            }
        }
        //3.锁定成功
        return true;
    }

    @Override
    public void unlockStock(StockLocked to) {
        StockDetailTo detailTo = to.getDetailTo();
        Long id1 = detailTo.getId();
        /**
         * 解锁
         * 1、查询数据库关于这个订单锁定库存信息
         *   有：证明库存锁定成功了
         *      解锁：订单状况
         *          1、没有这个订单，必须解锁库存
         *          2、有这个订单，不一定解锁库存
         *              订单状态：已取消：解锁库存
         *                      已支付：不能解锁库存
         */
        //去数据库查询这个订单锁定库存消息
        WareOrderTaskDetailEntity byId = orderTaskDetailService.getById(id1);
        if (byId != null) {
            //解锁
            Long id = to.getId();//库存工作单id
            String orderSn = orderTaskService.getById(id).getOrderSn();
            //根据订单号查询订单状态
            R r = orderFeignService.getOrderStatus(orderSn);
            if (r.getCode() == 0) {
                //订单数据返回成功
                OrderVo data = r.getData(new TypeReference<OrderVo>() {
                });
                if (data == null || data.getStatus() == 4) {
                    // 订单状态：已取消：解锁库存
                    if (byId.getLockStatus() == 1) {
                        //当前库存工作单详情状态1，已锁定，但是未解锁才可以解锁
                        unlockStock(detailTo.getSkuId(), detailTo.getWareId(), detailTo.getSkuNum(), detailTo.getTaskId());
                    }
                } else {
                    //已支付：不能解锁库存

                }
            } else {
                //远程查询订单数据失败
                //消息拒绝后，重新放回队列
                throw new RuntimeException("远程调用服务失败");
            }
        } else {
            //  无需解锁
        }
    }


    //库存解锁
    private void unlockStock(Long skuId, Long wareId, Integer num, Long taskDetailId) {
        //库存解锁
        wareSkuDao.unlockStock(skuId, wareId, num, taskDetailId);

        //更新库存工作单的状态
        WareOrderTaskDetailEntity entity = new WareOrderTaskDetailEntity();
        entity.setId(taskDetailId);
        entity.setLockStatus(2);//变为已解锁
        orderTaskDetailService.updateById(entity);
    }

    /**
     * 防止订单服务卡顿，导致订单状态消息一直改不了，库存优先到期，查订单状态新建，什么都不处理
     * 导致卡顿的订单，永远都不能解锁库存
     *
     * @param
     */
    @Transactional(rollbackFor = Exception.class)
    @Override
    public void unlockStock(OrderVo to) {
        String orderSn = to.getOrderSn();
        //查一下最新的库存解锁状态，防止重复解锁库存
        WareOrderTaskEntity entity = orderTaskService.getOrderTaskByOrderSn(orderSn);
        Long id = entity.getId();
        if (id != null) {


            //按照库存工作单找到所有没有进行解锁的库存进行解锁
            List<WareOrderTaskDetailEntity> list = orderTaskDetailService.list(new QueryWrapper<WareOrderTaskDetailEntity>().eq("task_id", id)
                    .eq("lock_status", 1));

            //解锁
            for (WareOrderTaskDetailEntity entities : list) {

                unlockStock(entities.getSkuId(), entities.getWareId(), entities.getSkuNum(), entities.getId());
            }
        }
    }

    @Data
    static class skuWareHashStock {
        private Long skuId;
        private Integer num;
        private List<Long> wareId;
    }

}