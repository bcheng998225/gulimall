package com.atguigu.gulimall.coupon.service.impl;

import com.atguigu.common.utils.PageUtils;
import com.atguigu.common.utils.Query;
import com.atguigu.gulimall.coupon.dao.SeckillSessionDao;
import com.atguigu.gulimall.coupon.entity.SeckillSessionEntity;
import com.atguigu.gulimall.coupon.entity.SeckillSkuRelationEntity;
import com.atguigu.gulimall.coupon.service.SeckillSessionService;
import com.atguigu.gulimall.coupon.service.SeckillSkuRelationService;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;


@Service("seckillSessionService")
public class SeckillSessionServiceImpl extends ServiceImpl<SeckillSessionDao, SeckillSessionEntity> implements SeckillSessionService {

    @Autowired
    SeckillSkuRelationService  relationService;

    @Override
    public PageUtils queryPage(Map<String, Object> params) {
        IPage<SeckillSessionEntity> page = this.page(
                new Query<SeckillSessionEntity>().getPage(params),
                new QueryWrapper<SeckillSessionEntity>()
        );

        return new PageUtils(page);
    }

    //上架最近3天的商品
    @Override
    public List<SeckillSessionEntity> getLates3DaySession() {
        //计算最近3天的日期
         List<SeckillSessionEntity> list = this.list(new QueryWrapper<SeckillSessionEntity>().between("start_time", startTime(), endTime()));
        if (list!=null&&list.size() > 0){
            //查出商品并返回
             List<SeckillSessionEntity> collect = list.stream().map(session -> {
                 Long id = session.getId();//活动id
                List<SeckillSkuRelationEntity> skuProduct = relationService.list(new QueryWrapper<SeckillSkuRelationEntity>().eq("promotion_session_id", id));
                session.setRelationSku(skuProduct);
                return session;
            }).collect(Collectors.toList());
            return collect;
        }
        return null;
    }

    private String startTime(){

         LocalDateTime startTime = LocalDateTime.of(LocalDate.now(), LocalTime.MIN);
         String format = startTime.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));

        return  format;
    }

    private String endTime(){

         LocalDateTime endTime = LocalDateTime.of(LocalDate.now().plusDays(2), LocalTime.MAX);
         String format = endTime.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        return  format;
    }

}