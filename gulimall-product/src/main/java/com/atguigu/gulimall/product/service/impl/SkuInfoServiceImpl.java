package com.atguigu.gulimall.product.service.impl;

import com.alibaba.fastjson.TypeReference;
import com.atguigu.common.utils.PageUtils;
import com.atguigu.common.utils.Query;
import com.atguigu.common.utils.R;
import com.atguigu.gulimall.product.dao.SkuInfoDao;
import com.atguigu.gulimall.product.entity.SkuImagesEntity;
import com.atguigu.gulimall.product.entity.SkuInfoEntity;
import com.atguigu.gulimall.product.entity.SpuInfoDescEntity;
import com.atguigu.gulimall.product.feign.SeckillFeignService;
import com.atguigu.gulimall.product.service.*;
import com.atguigu.gulimall.product.vo.SeckillInfoVo;
import com.atguigu.gulimall.product.vo.SkuItemSaleAttrVo;
import com.atguigu.gulimall.product.vo.SkuItemVo;
import com.atguigu.gulimall.product.vo.SpuItemAttrGroupVo;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.StringUtils;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ThreadPoolExecutor;


@Service("skuInfoService")
public class SkuInfoServiceImpl extends ServiceImpl<SkuInfoDao, SkuInfoEntity> implements SkuInfoService {

    @Autowired
    SkuImagesService skuImagesService;

    @Autowired
    SpuInfoDescService spuInfoDescService;

    @Autowired
    AttrGroupService attrGroupService;

    @Autowired
    SkuSaleAttrValueService skuSaleAttrValueService;

    @Autowired
    ThreadPoolExecutor executor;

    @Autowired
    SeckillFeignService seckillFeignService;


    @Override
    public PageUtils queryPage(Map<String, Object> params) {
        IPage<SkuInfoEntity> page = this.page(new Query<SkuInfoEntity>().getPage(params), new QueryWrapper<SkuInfoEntity>());

        return new PageUtils(page);
    }

    @Override
    public void saveSkuInfo(SkuInfoEntity skuInfoEntity) {
        this.baseMapper.insert(skuInfoEntity);
    }

    //查询sku的信息
    @Override
    public List<SkuInfoEntity> getSkusBySpuId(Long spuId) {
        List<SkuInfoEntity> list = this.list(new QueryWrapper<SkuInfoEntity>()
                .eq("spu_id", spuId));

        return list;
    }

    /**
     * 商品详情页展示
     *
     * @param skuId
     * @return
     */
    @Override
    public SkuItemVo item(Long skuId) {
        SkuItemVo skuItemVo = new SkuItemVo();
        CompletableFuture<SkuInfoEntity> infoFuture = CompletableFuture.supplyAsync(() -> {
            //1.sku基本信息pms_sku_info表中的sku_title标题  sku_subtitle副标题
            SkuInfoEntity info = getById(skuId);
            skuItemVo.setInfo(info);
            Long catalogId = info.getCatalogId();
            Long spuId = info.getSpuId();
            return info;
        }, executor);
        CompletableFuture<Void> saleAttrFuture = infoFuture.thenAcceptAsync((res) -> {
            //3.获取spu销售属性组合
            List<SkuItemSaleAttrVo> saleAttrVo = skuSaleAttrValueService.getSaleAttrsBySpuId(res.getSpuId());
            skuItemVo.setSaleAttr(saleAttrVo);
        }, executor);
        CompletableFuture<Void> descFuture = infoFuture.thenAcceptAsync((res) -> {
            //4.获取spu的介绍
            SpuInfoDescEntity spuInfoDesc = spuInfoDescService.getById(res.getSpuId());
            skuItemVo.setDesp(spuInfoDesc);
        }, executor);
        CompletableFuture<Void> baseAttrFuture = infoFuture.thenAcceptAsync((res) -> {
            //5.获取spu规格参数信息 pms_product_attr_value        pms_attr_group  pms_attr
            List<SpuItemAttrGroupVo> attrGruoVos = attrGroupService.getAttrGruopWithAttrsBySpuId(res.getSpuId(), res.getCatalogId());
            skuItemVo.setGroupAttrs(attrGruoVos);
        }, executor);
        CompletableFuture<Void> imagesFuture = CompletableFuture.runAsync(() -> {
            //2.sku的图片信息pms_spu_images表中 img_url
            List<SkuImagesEntity> images = skuImagesService.getImagesBySkuId(skuId);
            skuItemVo.setImages(images);
        }, executor);

        //查询当前sku是否参与秒杀优惠
        CompletableFuture<Void> skuSeckillFuture = CompletableFuture.runAsync(() -> {
            R r = seckillFeignService.getSkuSeckillInfo(skuId);
            if (r.getCode() == 0) {
                SeckillInfoVo data = r.getData(new TypeReference<SeckillInfoVo>() {
                });
                skuItemVo.setSeckillInfo(data);
            }
        }, executor);
        //等待所有任务都完成
        try {
            CompletableFuture.allOf(saleAttrFuture, descFuture, baseAttrFuture, imagesFuture,skuSeckillFuture)
                    .get();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        return skuItemVo;
    }

    @Override
    public PageUtils queryPageByCondition(Map<String, Object> params) {

        QueryWrapper<SkuInfoEntity> queryWrapper = new QueryWrapper<>();
        String key = (String) params.get("key");
        if (!StringUtils.isEmpty(key) && !"0".equalsIgnoreCase(key)) {
            queryWrapper.and((wrapper) -> {
                wrapper.eq("sku_id", key).or().like("sku_name", key);
            });
        }

        String catelogId = (String) params.get("catelogId");
        if (!StringUtils.isEmpty(catelogId) && !"0".equalsIgnoreCase(catelogId)) {
            queryWrapper.eq("catalog_id", catelogId);
        }

        String brandId = (String) params.get("brandId");
        if (!StringUtils.isEmpty(brandId) && !"0".equalsIgnoreCase(brandId)) {
            queryWrapper.eq("brand_id", brandId);
        }

        String min = (String) params.get("min");
        if (!StringUtils.isEmpty(min)) {
            queryWrapper.ge("price", min);
        }

        String max = (String) params.get("max");

        if (!StringUtils.isEmpty(max)) {
            try {
                BigDecimal bigDecimal = new BigDecimal(max);
                if (bigDecimal.compareTo(BigDecimal.ZERO) == 1) {
                    queryWrapper.le("price", max);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }


        IPage<SkuInfoEntity> page = this.page(new Query<SkuInfoEntity>().getPage(params),
                queryWrapper);
        return new PageUtils(page);
    }


}