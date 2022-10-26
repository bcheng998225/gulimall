package com.atguigu.gulimall.order.service.impl;

import com.alibaba.fastjson.TypeReference;
import com.atguigu.common.exception.NoStockException;
import com.atguigu.common.to.seckill.SeckillOrderTo;
import com.atguigu.common.utils.PageUtils;
import com.atguigu.common.utils.Query;
import com.atguigu.common.utils.R;
import com.atguigu.common.vo.auth.MemberRespVo;
import com.atguigu.common.vo.order.*;
import com.atguigu.common.vo.ware.FareVo;
import com.atguigu.common.vo.ware.OrderVo;
import com.atguigu.gulimall.order.dao.OrderDao;
import com.atguigu.gulimall.order.entity.OrderEntity;
import com.atguigu.gulimall.order.entity.OrderItemEntity;
import com.atguigu.gulimall.order.entity.PaymentInfoEntity;
import com.atguigu.gulimall.order.enume.OrderStatusEnum;
import com.atguigu.gulimall.order.feign.CartFeignService;
import com.atguigu.gulimall.order.feign.MemberFeignService;
import com.atguigu.gulimall.order.feign.ProductFeignService;
import com.atguigu.gulimall.order.feign.WareFeignService;
import com.atguigu.gulimall.order.interceptor.LoginUserInterceptor;
import com.atguigu.gulimall.order.service.OrderItemService;
import com.atguigu.gulimall.order.service.OrderService;
import com.atguigu.gulimall.order.service.PaymentInfoService;
import com.atguigu.gulimall.order.to.OrderCreatTo;
import com.atguigu.gulimall.order.vo.OrderSubmitResponseVo;
import com.atguigu.gulimall.order.vo.PayAsyncVo;
import com.atguigu.gulimall.order.vo.PayVo;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static com.atguigu.common.constant.OrderConstant.USER_ORDER_TOKEN_PREFIX;


@Service("omsOrderService")
public class OrderServiceImpl extends ServiceImpl<OrderDao, OrderEntity> implements OrderService {


    @Autowired
    OrderItemService orderItemService;

    @Autowired
    MemberFeignService memberFeignService;

    @Autowired
    CartFeignService cartFeignService;

    @Autowired
    ThreadPoolExecutor executor;

    @Autowired
    WareFeignService wareFeignService;

    @Autowired
    StringRedisTemplate stringRedisTemplate;

    @Autowired
    ProductFeignService productFeignService;

    @Autowired
    RabbitTemplate rackTemplate;

    @Autowired
    PaymentInfoService paymentInfoService;

    private ThreadLocal<OrderSubmitVo> orderSubmitVo = new ThreadLocal<>();

    @Override
    public PageUtils queryPage(Map<String, Object> params) {
        IPage<OrderEntity> page = this.page(new Query<OrderEntity>().getPage(params), new QueryWrapper<OrderEntity>());

        return new PageUtils(page);
    }

    /**
     * 返回订单确认页所需要的数据
     *
     * @return
     */
    @Override
    public OrderConfirmVo confirmOrder() throws ExecutionException, InterruptedException {
        OrderConfirmVo orderConfirmVo = new OrderConfirmVo();
        MemberRespVo memberRespVo = LoginUserInterceptor.loginUser.get();
        //解决异步线程不同步
        RequestAttributes requestAttributes = RequestContextHolder.getRequestAttributes();
        //异步编排
        CompletableFuture<Void> getAddress = CompletableFuture.runAsync(() -> {
            //1. 远程调用收货服务查询收货地址
            RequestContextHolder.setRequestAttributes(requestAttributes);
            List<MemberAddressVo> address = memberFeignService.getAddress(memberRespVo.getId());
            orderConfirmVo.setAddresses(address);
        }, executor);

        CompletableFuture<Void> getCartItems = CompletableFuture.runAsync(() -> {
            //2. 远程调用购物车服务查询已选中的货物
            RequestContextHolder.setRequestAttributes(requestAttributes);
            List<OrderItemVO> items = cartFeignService.currentUserCartItems();
            orderConfirmVo.setItems(items);
        }, executor).thenRunAsync(() -> {
            List<OrderItemVO> items = orderConfirmVo.getItems();
            List<Long> skuIds = items.stream().map(OrderItemVO::getSkuId).collect(Collectors.toList());
            //调用库存系统查库存
            R hasStock = wareFeignService.getSkuHasStock(skuIds);
            List<SkuStockVo> skuStockVos = hasStock.getData("data", new TypeReference<List<SkuStockVo>>() {
            });
            if (skuStockVos != null && skuStockVos.size() > 0) {
                //将skuStockVos集合转换为map
                Map<Long, Boolean> skuHasStockMap = skuStockVos.stream().collect(Collectors.toMap(SkuStockVo::getSkuId, SkuStockVo::getHasStock));
                orderConfirmVo.setStocks(skuHasStockMap);
            }

        }, executor);


        CompletableFuture<Void> getIntegration = CompletableFuture.runAsync(() -> {
            //3. 查询用户积分
            RequestContextHolder.setRequestAttributes(requestAttributes);
            Integer integration = memberRespVo.getIntegration();
            orderConfirmVo.setIntegration(integration);
        }, executor);
        //4.其他数据计算

        //5.防重令牌 lua脚本解决
        String token = UUID.randomUUID().toString().replace("-", "");
        orderConfirmVo.setOrderToken(token);
        stringRedisTemplate.opsForValue().set(USER_ORDER_TOKEN_PREFIX + memberRespVo.getId(), token, 30, TimeUnit.MINUTES);

        CompletableFuture.allOf(getAddress, getCartItems, getIntegration).get();
        return orderConfirmVo;
    }

    /**
     * 下单方法
     *
     * @param vo
     * @return
     */
//    @GlobalTransactional
    @Transactional
    @Override
    public OrderSubmitResponseVo submitOrder(OrderSubmitVo vo) {

        OrderSubmitResponseVo response = new OrderSubmitResponseVo();
        response.setCode(0);
        //拿到当前登录的用户
        MemberRespVo memberRespVo = LoginUserInterceptor.loginUser.get();
        orderSubmitVo.set(vo);
        //1.验证令牌  0失败  1成功
        String script = "if redis.call('get', KEYS[1]) == ARGV[1] then return redis.call('del', KEYS[1]) else return 0 end";
        String orderToken = vo.getOrderToken();
        //lua脚本  原子性验证前后端令牌是否一致
        Long result = stringRedisTemplate.execute(new DefaultRedisScript<Long>(script, Long.class), Arrays.asList(USER_ORDER_TOKEN_PREFIX + memberRespVo.getId()), orderToken);
        if (result == 0L) {
            //验证失败
            response.setCode(1);
            return response;
        } else {
            //验证成功
            //1.创建订单
            OrderCreatTo order = createOrder();
            //2.验价
            BigDecimal payAmount = order.getOrder().getPayAmount();
            BigDecimal payPrice = vo.getPayPrice();
            if (Math.abs(payAmount.subtract(payPrice).doubleValue()) < 0.01) {
                //TODO 3.保存订单
                //3.保存订单
                saveOrder(order);
                //4.锁定库存,有异常，回滚数据
                //订单号 所有订单项信息skuid num  skuName
                WareSkuLockVo lockVo = new WareSkuLockVo();
                lockVo.setOrderSn(order.getOrder().getOrderSn());
                List<OrderItemVO> collect = order.getOrderItems().stream().map(item -> {
                    OrderItemVO itemVO = new OrderItemVO();
                    itemVO.setSkuId(item.getSkuId());
                    itemVO.setCount(item.getSkuQuantity());
                    itemVO.setTitle(item.getSkuName());
                    return itemVO;
                }).collect(Collectors.toList());
                lockVo.setLocks(collect);
                //TODO 4.调用远程锁定库存的方法
                //出现的问题：扣减库存成功了，但是由于网络原因超时，出现异常，导致订单事务回滚，库存事务不回滚(解决方案：seata)
                //为了保证高并发，不推荐使用seata，因为是加锁，并行化，提升不了效率,可以发消息给库存服务
                R r = wareFeignService.orderLockStock(lockVo);
                if (r.getCode() == 0) {
                    //库存锁成功了
                    response.setOrder(order.getOrder());

                    //TODO 5.远程扣减积分

                    //TODO 订单创建成功，给mq发送消息
                    rackTemplate.convertAndSend("order-event-exchange", "order.create.order", order.getOrder());
                    return response;


                } else {
                    //锁失败了
                    String msg = (String) r.get("msg");
                    throw new NoStockException(msg);
                }
            } else {
                response.setCode(2);
                return response;
            }

        }
    }


    /**
     * 保存订单数据
     *
     * @param order
     */
    private void saveOrder(OrderCreatTo order) {
        OrderEntity orderEntity = order.getOrder();
        orderEntity.setModifyTime(new Date());
        orderEntity.setCreateTime(new Date());
        List<OrderItemEntity> orderItems = order.getOrderItems();
        orderItemService.saveBatch(orderItems);
        this.save(orderEntity);
    }

    //创建订单
    private OrderCreatTo createOrder() {
        OrderCreatTo orderCreatTo = new OrderCreatTo();
        //1.生成一个订单号
        String timeId = IdWorker.getTimeId();
        //创建一个订单
        OrderEntity orderEntity = buildOrder(timeId);
        Long id = orderEntity.getId();
        //2.获取所有订单项信息

        List<OrderItemEntity> orderItemEntities = buildOrderItems(timeId, id);

        //3.计算价格
        computePrice(orderEntity, orderItemEntities);
        //保存数据
        orderCreatTo.setOrder(orderEntity);
        orderCreatTo.setOrderItems(orderItemEntities);
        return orderCreatTo;
    }

    /**
     * 计算价格
     *
     * @param orderEntity
     * @param orderItemEntities
     */
    private void computePrice(OrderEntity orderEntity, List<OrderItemEntity> orderItemEntities) {
        //订单价格计算  //总价
        BigDecimal total = new BigDecimal("0.0");
        //优惠价
        BigDecimal coupon = new BigDecimal("0.0");
        BigDecimal integratio = new BigDecimal("0.0");
        BigDecimal promotion = new BigDecimal("0.0");
        //积分、成长值
        Integer giftIntegration = 0;
        Integer giftGrowth = 0;
        //订单总额，叠加每一个订单项的总额信息
        for (OrderItemEntity entity : orderItemEntities) {
            //总价
            total = total.add(entity.getRealAmount());// 订单总额
            //优惠价格信息
            coupon = coupon.add(entity.getCouponAmount());// 促销总金额
            integratio = integratio.add(entity.getIntegrationAmount());// 优惠券总金额
            promotion = promotion.add(entity.getPromotionAmount());// 积分优惠总金额
            //积分信息和成长值信息
            giftIntegration += giftIntegration + entity.getGiftIntegration();// 积分
            giftGrowth += giftGrowth + entity.getGiftGrowth();// 成长值
        }
        //1、订单价格相关的
        orderEntity.setTotalAmount(total);
        //设置应付总额
        orderEntity.setPayAmount(total.add(orderEntity.getFreightAmount()));
        orderEntity.setCouponAmount(coupon);
        orderEntity.setIntegrationAmount(integratio);
        orderEntity.setPromotionAmount(promotion);
        //设置积分信息
        orderEntity.setIntegration(giftIntegration);
        orderEntity.setGrowth(giftGrowth);
        //订单状态、
        orderEntity.setDeleteStatus(0);//未删除

    }

    //创建一个订单
    private OrderEntity buildOrder(String timeId) {
        OrderEntity orderEntity = new OrderEntity();
        orderEntity.setMemberUsername(LoginUserInterceptor.loginUser.get().getUserName());
        orderEntity.setPayType(1);
        orderEntity.setNote("请尽快发货1");
        //订单创建时间
//        Date currentTime = new Date();
//        SimpleDateFormat simpleDateFormat1 = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
//        orderEntity.setCreateTime(simpleDateFormat1.format(currentTime));
        orderEntity.setOrderSn(timeId);
        orderEntity.setMemberId(LoginUserInterceptor.loginUser.get().getId());
        //获取收货地址信息
        OrderSubmitVo submitVo = orderSubmitVo.get();
        R fare = wareFeignService.getFare(submitVo.getAddrId());
        FareVo fareResp = fare.getData(new TypeReference<FareVo>() {
        });
        BigDecimal fare1 = fareResp.getFare();
        //设置运费信息
        orderEntity.setFreightAmount(fare1);
        //设置收货人信息
        orderEntity.setReceiverCity(fareResp.getAddress().getCity());
        orderEntity.setReceiverName(fareResp.getAddress().getName());
        orderEntity.setReceiverPhone(fareResp.getAddress().getPhone());
        orderEntity.setReceiverPostCode(fareResp.getAddress().getPostCode());
        orderEntity.setReceiverProvince(fareResp.getAddress().getProvince());
        orderEntity.setReceiverRegion(fareResp.getAddress().getRegion());
        //设置订单状态
        orderEntity.setStatus(OrderStatusEnum.CREATE_NEW.getCode());
        orderEntity.setAutoConfirmDay(7);
        orderEntity.setConfirmStatus(0);
        return orderEntity;
    }

    //获取所有订单项信息
    private List<OrderItemEntity> buildOrderItems(String timeId, Long id) {
        List<OrderItemEntity> orderItemEntityList = new ArrayList<>();
        //最后确定每个购物项的价格
        List<OrderItemVO> currentUserCartItems = cartFeignService.currentUserCartItems();
        if (currentUserCartItems.size() > 0 && currentUserCartItems != null) {
            orderItemEntityList = currentUserCartItems.stream().map((items) -> {
                OrderItemEntity orderItemEntity = buildOrderItem(items);
                orderItemEntity.setOrderSn(timeId);
                orderItemEntity.setOrderId(id);


                return orderItemEntity;
            }).collect(Collectors.toList());

        }
        return orderItemEntityList;
    }

    //获取某一个订单项信息
    private OrderItemEntity buildOrderItem(OrderItemVO items) {
        //订单信息
        OrderItemEntity orderItemEntity = new OrderItemEntity();
        //商品spu信息
        Long skuId = items.getSkuId();
        R spuInfo = productFeignService.getSpuInfoBySkuId(skuId);
        SpuInfoEntity data = spuInfo.getData("data", new TypeReference<SpuInfoEntity>() {
        });
        orderItemEntity.setSpuId(data.getId());
        orderItemEntity.setSpuName(data.getSpuName());
        orderItemEntity.setSpuBrand(data.getBrandId().toString());
        orderItemEntity.setCategoryId(data.getCatalogId());
        //商品sku信息

        orderItemEntity.setSkuId(items.getSkuId());
        orderItemEntity.setSkuName(items.getTitle());
        orderItemEntity.setSkuPic(items.getImage());
        orderItemEntity.setSkuPrice(items.getPrice());
        orderItemEntity.setSkuQuantity(items.getCount());
        String skuAttr = StringUtils.collectionToDelimitedString(items.getSkuAttr(), ";");
        orderItemEntity.setSkuAttrsVals(skuAttr);
        //优惠信息

        //积分信息
        int num = items.getPrice().multiply(new BigDecimal(items.getCount())).intValue();// 分值=单价*数量
        orderItemEntity.setGiftGrowth(num);// 成长值
        orderItemEntity.setGiftIntegration(num);// 积分

//        orderItemEntity.setGiftGrowth(items.getPrice().multiply(new BigDecimal(items.getCount().toString())).intValue());
//        orderItemEntity.setIntegrationAmount(items.getPrice().multiply(new BigDecimal(items.getCount().toString())));
        //设置订单项价格信息
        orderItemEntity.setPromotionAmount(new BigDecimal("0.0"));
        orderItemEntity.setCouponAmount(new BigDecimal("0.0"));
        orderItemEntity.setIntegrationAmount(new BigDecimal("0.0"));
        //当前订单项的实际金额
        BigDecimal orign = orderItemEntity.getSkuPrice().multiply(new BigDecimal(orderItemEntity.getSkuQuantity().toString()));
        BigDecimal subtract = orign.subtract(orderItemEntity.getPromotionAmount()).subtract(orderItemEntity.getCouponAmount()).subtract(orderItemEntity.getIntegrationAmount());
        orderItemEntity.setRealAmount(subtract);
        return orderItemEntity;
    }

    //查询订单状态
    @Override
    public OrderEntity getOrderByOrderSn(String orderSn) {
        OrderEntity orderEntity = this.getOne(new QueryWrapper<OrderEntity>().eq("order_sn", orderSn));
        return orderEntity;
    }

    //关闭订单
    @Override
    public void closeOrder(OrderEntity entity) {
        //查询当前订单的最新状态
        OrderEntity orderEntity = this.getById(entity.getId());
        if (orderEntity.getStatus().equals(OrderStatusEnum.CREATE_NEW.getCode())) {
            //关单
            OrderEntity entity1 = new OrderEntity();
            entity1.setId(entity.getId());
            entity1.setStatus(OrderStatusEnum.PAYED.getCode());
            this.updateById(entity1);

            //发给mq
            OrderVo orderVo = new OrderVo();
            BeanUtils.copyProperties(orderEntity, orderVo);
            try {
                //TODO 确保每个消息发送成功，给每个消息做好日志记录，(给数据库保存每一个详细信息)保存每个消息的详细信息
                rackTemplate.convertAndSend("order-event-exchange", "order.release.other.#", orderVo);
            } catch (Exception e) {
                //TODO 定期扫描数据库，重新发送失败的消息
            }
        }
    }

    //获取订单详细信息
    @Override
    public PayVo getOrderPay(String orderSn) {

        PayVo payVo = new PayVo();
        OrderEntity order = this.getOrderByOrderSn(orderSn);
        payVo.setOut_trade_no(order.getOrderSn());// 商户订单号 必填

        List<OrderItemEntity> order_sn = orderItemService.list(new QueryWrapper<OrderItemEntity>().eq("order_sn", orderSn));
        OrderItemEntity orderItemEntity = order_sn.get(0);
        BigDecimal bigDecimal = order.getPayAmount().setScale(2, RoundingMode.UP);
        payVo.setTotal_amount(bigDecimal.toString());// 付款金额 必填
        payVo.setBody(order.getNote());// 商品描述 可空
        payVo.setSubject(orderItemEntity.getSkuName());// 订单名称 必填

        return payVo;
    }

    @Override
    public PageUtils queryPageWithItem(Map<String, Object> params) {

        MemberRespVo memberRespVo = LoginUserInterceptor.loginUser.get();

        IPage<OrderEntity> page = this.page(
                new Query<OrderEntity>().getPage(params),
                new QueryWrapper<OrderEntity>().eq("member_id", memberRespVo.getId())
                        .orderByDesc("create_time"));

        List<OrderEntity> collect = page.getRecords().stream().map(order -> {
            List<OrderItemEntity> itemEntities = orderItemService.list(new QueryWrapper<OrderItemEntity>().eq("order_sn", order.getOrderSn()));
            order.setItemEntities(itemEntities);
            return order;
        }).collect(Collectors.toList());

        page.setRecords(collect);

        return new PageUtils(page);
    }

    //处理支付宝支付结果
    @Override
    public String handPayResult(PayAsyncVo vo) {
        //保存交易流水
        PaymentInfoEntity info = new PaymentInfoEntity();
        info.setAlipayTradeNo(vo.getTrade_no());
        info.setOrderSn(vo.getOut_trade_no());
        info.setPaymentStatus(vo.getTrade_status());
        info.setTotalAmount(new BigDecimal(vo.getTotal_amount()));
        info.setCallbackTime(vo.getNotify_time());
        paymentInfoService.save(info);
        //修改订单状态信息
        if (vo.getTrade_status().equals("TRADE_SUCCESS") || vo.getTrade_status().equals("TRADE_FINISHED")) {
            //支付成功
            String outTradeNo = vo.getOut_trade_no();
            Integer code = OrderStatusEnum.PAYED.getCode();
            this.baseMapper.updataOrderStatus(outTradeNo, code);
        }
        return "success";
    }

    //创建秒杀单详细信息
    @Override
    public void createSeckillOrder(SeckillOrderTo seckillOrderTo) {
        //保存订单信息
        OrderEntity orderEntity = new OrderEntity();
        orderEntity.setOrderSn(seckillOrderTo.getOrderSn());
        orderEntity.setMemberId(seckillOrderTo.getMemberId());
        orderEntity.setStatus(OrderStatusEnum.CREATE_NEW.getCode());
        BigDecimal multiply = seckillOrderTo.getSeckillPrice().multiply(new BigDecimal("" + seckillOrderTo.getNum()));
        orderEntity.setPayAmount(multiply);
       this.save(orderEntity);
       //保存订单项信息
        OrderItemEntity orderItemEntity=new OrderItemEntity();
        orderItemEntity.setOrderSn(seckillOrderTo.getOrderSn());
        orderItemEntity.setRealAmount(multiply);
        orderItemEntity.setSkuQuantity(seckillOrderTo.getNum());
        orderItemService.save(orderItemEntity);

    }
}