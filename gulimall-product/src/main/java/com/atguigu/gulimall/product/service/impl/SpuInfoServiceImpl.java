package com.atguigu.gulimall.product.service.impl;

import com.alibaba.druid.util.StringUtils;
import com.alibaba.fastjson.TypeReference;
import com.atguigu.common.constant.ProductConstant;
import com.atguigu.common.to.product.SkuHasStockVo;
import com.atguigu.common.to.product.SkuReductionTo;
import com.atguigu.common.to.product.SpuBoundTo;
import com.atguigu.common.to.es.SkuEsModel;
import com.atguigu.common.utils.PageUtils;
import com.atguigu.common.utils.Query;
import com.atguigu.common.utils.R;
import com.atguigu.gulimall.product.dao.SpuInfoDao;
import com.atguigu.gulimall.product.entity.*;
import com.atguigu.gulimall.product.feign.CouponFeignService;
import com.atguigu.gulimall.product.feign.SearchFeignService;
import com.atguigu.gulimall.product.feign.WareFeignService;
import com.atguigu.gulimall.product.service.*;
import com.atguigu.gulimall.product.vo.*;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;


@Service("spuInfoService")
public class SpuInfoServiceImpl extends ServiceImpl<SpuInfoDao, SpuInfoEntity> implements SpuInfoService {
    @Autowired
    SpuInfoDescService spuDescService;

    @Autowired
    SpuImagesService imagesService;

    @Autowired
    AttrService attrService;

    @Autowired
    ProductAttrValueService productAttrValueService;

    @Autowired
    SkuInfoService skuInfoService;

    @Autowired
    SkuImagesService skuImagesService;

    @Autowired
    SkuSaleAttrValueService skuSaleAttrValueService;

    @Autowired
    CouponFeignService couponFeignService;

    @Autowired
    BrandService brandService;

    @Autowired
    CategoryService categoryService;

    @Autowired
    WareFeignService wareFeignService;

    @Autowired
    SearchFeignService searchFeignService;

    @Override
    public PageUtils queryPage(Map<String, Object> params) {
        IPage<SpuInfoEntity> page = this.page(
                new Query<SpuInfoEntity>().getPage(params),
                new QueryWrapper<SpuInfoEntity>()
        );

        return new PageUtils(page);
    }

    /**
     * 保存数据
     * //TODO 保存数据 分布式事务问题
     *
     * @param vo
     */
    @Transactional
    @Override
    public void saveSpuInfo(SpuSaveVo vo) {
        //1.保存spu基本信息 pms_spu_info
        SpuInfoEntity spuInfoEntity = new SpuInfoEntity();
        BeanUtils.copyProperties(vo, spuInfoEntity);
        spuInfoEntity.setCreateTime(new Date());
        spuInfoEntity.setUpdateTime(new Date());
        this.saveBaseSpuInfo(spuInfoEntity);


        //2.保存spu的描述图片 pms_spu_info_desc
        final List<String> decript = vo.getDecript();
        SpuInfoDescEntity descEntity = new SpuInfoDescEntity();
        descEntity.setSpuId(spuInfoEntity.getId());
        descEntity.setDecript(String.join(",", decript));
        spuDescService.saveSpuInfoDescript(descEntity);

        //3.保存spu的图片集 pms_spu_images
        List<String> images = vo.getImages();
        imagesService.saveImages(spuInfoEntity.getId(), images);

        //4.保存spu的规格参数 pms_product_attr_value
        List<BaseAttrs> baseAttrs = vo.getBaseAttrs();
        List<ProductAttrValueEntity> collect = baseAttrs.stream().map(attr -> {
            ProductAttrValueEntity attrValue = new ProductAttrValueEntity();
            attrValue.setAttrId(attr.getAttrId());
            AttrEntity byId = attrService.getById(attr.getAttrId());
            String attrName = byId.getAttrName();
            attrValue.setSpuId(spuInfoEntity.getId());
            attrValue.setAttrName(attrName);
            attrValue.setAttrValue(attr.getAttrValues());
            attrValue.setQuickShow(attr.getShowDesc());
            return attrValue;
        }).collect(Collectors.toList());
        productAttrValueService.saveProductAttr(collect);

        //保存积分信息 sms_spu_bounds
        Bounds bounds = vo.getBounds();
        SpuBoundTo spuBoundTo = new SpuBoundTo();
        BeanUtils.copyProperties(bounds, spuBoundTo);
        spuBoundTo.setSpuId(spuInfoEntity.getId());
        R r = couponFeignService.saveSuoBounds(spuBoundTo);
        if (r.getCode() != 0) {
            log.error("远程保存spu积分信息失败！");
        }

        //5.保存当前spu对应的sku信息pms_spu_info
        //5.1保存spu基本信息 pms_sku_info
        List<Skus> skus = vo.getSkus();
        if (skus != null && skus.size() > 0) {
            skus.forEach(item -> {
                String defaultImg = "";
                for (Images image : item.getImages()) {
                    if (image.getDefaultImg() == 1) {
                        defaultImg = image.getImgUrl();
                    }
                }
                ;
                SkuInfoEntity skuInfoEntity = new SkuInfoEntity();
                BeanUtils.copyProperties(item, skuInfoEntity);
//                private String skuName;
//                private BigDecimal price;
//                private String skuTitle;
//                private String skuSubtitle;
                skuInfoEntity.setBrandId(spuInfoEntity.getBrandId());
                skuInfoEntity.setCatalogId(spuInfoEntity.getCatalogId());
                skuInfoEntity.setSaleCount(0L);
                skuInfoEntity.setSpuId(spuInfoEntity.getId());
                skuInfoEntity.setSkuDefaultImg(defaultImg);
                skuInfoService.saveSkuInfo(skuInfoEntity);
                Long skuId = spuInfoEntity.getId();

                //5.2保存spu的图片  pms_sku_images
                List<SkuImagesEntity> collect1 = item.getImages().stream().map(img -> {
                    SkuImagesEntity skuImagesEntity = new SkuImagesEntity();
                    skuImagesEntity.setSkuId(skuId);
                    skuImagesEntity.setImgUrl(img.getImgUrl());
                    skuImagesEntity.setDefaultImg(img.getDefaultImg());
                    return skuImagesEntity;
                }).filter(entity -> {
                    //返回true就需要，返回false就被过滤掉
                    return !com.baomidou.mybatisplus.core.toolkit.StringUtils.isEmpty(entity.getImgUrl());
                }).collect(Collectors.toList());
                skuImagesService.saveBatch(collect1);

                //5.3保存sku的销售属性信息 pms_sku_sale_attr_value
                List<Attr> attr = item.getAttr();
                final List<SkuSaleAttrValueEntity> skuSaleAttrValueEntities = attr.stream().map(a -> {
                    final SkuSaleAttrValueEntity valueEntity = new SkuSaleAttrValueEntity();
                    BeanUtils.copyProperties(a, valueEntity);
                    valueEntity.setSkuId(skuId);
                    return valueEntity;
                }).collect(Collectors.toList());
                skuSaleAttrValueService.saveBatch(skuSaleAttrValueEntities);

                //5.4保存sku的优惠、满减等信息  sms_sku_ladder  sms_spu_bounds  sms_member_price
                SkuReductionTo skuReductionTo = new SkuReductionTo();
                BeanUtils.copyProperties(item, skuReductionTo);
                skuReductionTo.setSkuId(spuInfoEntity.getId());
                if (skuReductionTo.getFullCount() > 0 || skuReductionTo.getFullPrice().compareTo(new BigDecimal("0")) == 1) {
                    R r1 = couponFeignService.saveSkuReduction(skuReductionTo);
                    if (r1.getCode() != 0) {
                        log.error("远程保存优惠信息失败！");
                    }
                }
            });
        }
    }

    @Override
    public void saveBaseSpuInfo(SpuInfoEntity spuInfoEntity) {
        this.baseMapper.insert(spuInfoEntity);
    }

    /**
     * 商品上架
     */
    @Override
    public void up(Long spuId) {

        //1.查出当前spuId对应的所有sku信息，品牌的名字
        List<SkuInfoEntity> skus = skuInfoService.getSkusBySpuId(spuId);
        List<Long> skuIdList = skus.stream().map(SkuInfoEntity::getSkuId).collect(Collectors.toList());

        //TODO 4.查出sku当前规格的所有可以被检索的属性
        List<ProductAttrValueEntity> baseAttrs = productAttrValueService.baseAttrListForspu(spuId);
        List<Long> attrIds = baseAttrs.stream().map(ProductAttrValueEntity::getAttrId).collect(Collectors.toList());
        //在指定所有集合里面查取可以被检索的属性
        List<Long> searchAttrIds = attrService.selectSearchAttrs(attrIds);
        Set<Long> idSet = new HashSet<>(searchAttrIds);
        List<SkuEsModel.Attrs> collect = baseAttrs.stream().filter((item) -> {
            return idSet.contains(item.getAttrId());
        }).map((item) -> {
            SkuEsModel.Attrs attrs1 = new SkuEsModel.Attrs();
            BeanUtils.copyProperties(item, attrs1);
            return attrs1;
        }).collect(Collectors.toList());

        //TODO 1.发送远程调用，库存查询是否有库存 hasStock
        Map<Long, Boolean> stockMap = null;
        try {
            R r = wareFeignService.getSkuHasStock(skuIdList);
            TypeReference<List<SkuHasStockVo>> typeReference = new TypeReference<List<SkuHasStockVo>>() {
            };
            stockMap = r.getData(typeReference)
                    .stream().collect(Collectors.toMap(SkuHasStockVo::getSkuId, SkuHasStockVo::getHasStock));
        } catch (Exception e) {
            log.error("库存服务查询异常:原因{}", e);
        }

        //2.封装信息
        Map<Long, Boolean> finalStockMap = stockMap;
        List<SkuEsModel> upProducts = skus.stream().map(sku -> {
            //组装信息
            SkuEsModel esModel = new SkuEsModel();
            BeanUtils.copyProperties(sku, esModel);
            //sku价格 skuPrice
            esModel.setSkuPrice(sku.getPrice());
            //sku图片 skuImg
            esModel.setSkuImg(sku.getSkuDefaultImg());

            //1.发送远程调用，库存查询是否有库存 hasStock
            if (finalStockMap == null) {
                esModel.setHasStock(true);
            } else {
                esModel.setHasStock(finalStockMap.get(sku.getSkuId()));
            }
            //TODO 2. 热度评分 0，hasScore
            esModel.setHotScore(0L);
            //TODO 3.查出品牌名字和分类名字设置出去去
            BrandEntity brand = brandService.getById(esModel.getBrandId());
            esModel.setBrandName(brand.getName());
            esModel.setBrandImg(brand.getLogo());
            //查出分类
            CategoryEntity category = categoryService.getById(esModel.getCatalogId());
            esModel.setCatalogName(category.getName());
            //查出sku当前规格的所有可以被检索的属性
            esModel.setAttrs(collect);
            return esModel;

        }).collect(Collectors.toList());

        //TODO 5.将数据发给es进行保存，gulimall-search
        R r = searchFeignService.productStatusUp(upProducts);
        if (r.getCode() == 0) {
            //远程调用成功
            //TODO 6.修改当前商品已上架状态
            this.baseMapper.updataSpuStatus(spuId, ProductConstant.StatusEnum.SPU_UP.getCode());
        } else {
            //远程调用失败
            //TODO 7.接口幂等性，重试机制
        }
    }

    //查询spu信息
    @Override
    public SpuInfoEntity getSpuInfoBySkuId(Long skuId) {
        //先查询sku表里的数据
        SkuInfoEntity byId = skuInfoService.getById(skuId);
        //获得spuId
        Long spuId = byId.getSpuId();
        //再通过spuId查询spuInfo信息表里的数据
        SpuInfoEntity spuInfoEntity = this.baseMapper.selectById(spuId);
        //查询品牌表的数据获取品牌名
//        BrandEntity brandEntity = brandService.getById(spuInfoEntity.getBrandId());
//        spuInfoEntity.setBrandName(brandEntity.getName());
        spuInfoEntity.setBrandId(spuInfoEntity.getBrandId());

        return spuInfoEntity;
    }

    /**
     * spu检索
     *
     * @param params
     * @return
     */
    @Override
    public PageUtils queryPageByConditin(Map<String, Object> params) {


        QueryWrapper<SpuInfoEntity> queryWrapper = new QueryWrapper<>();

        String key = (String) params.get("key");
        if (!StringUtils.isEmpty(key)) {
            queryWrapper.and((wrapper) -> {
                wrapper.eq("id", key).or().like("spu_name", key);
            });
        }

        String status = (String) params.get("status");
        if (!StringUtils.isEmpty(status)) {
            queryWrapper.eq("publish_status", status);
        }

        String brandId = (String) params.get("brandId");
        if (!StringUtils.isEmpty(brandId) && !"0".equalsIgnoreCase(brandId)) {
            queryWrapper.eq("brand_id", brandId);
        }

        String catelogId = (String) params.get("catelogId");
        if (!StringUtils.isEmpty(catelogId) && !"0".equalsIgnoreCase(catelogId)) {
            queryWrapper.eq("catalog_id", catelogId);
        }

        IPage<SpuInfoEntity> page = this.page(new Query<SpuInfoEntity>().getPage(params), queryWrapper);

        return new PageUtils(page);
    }

}