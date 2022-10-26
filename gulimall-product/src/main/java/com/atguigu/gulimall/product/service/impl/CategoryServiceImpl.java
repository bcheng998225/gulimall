package com.atguigu.gulimall.product.service.impl;

import com.atguigu.common.utils.PageUtils;
import com.atguigu.common.utils.Query;
import com.atguigu.gulimall.product.dao.CategoryDao;
import com.atguigu.gulimall.product.entity.CategoryEntity;
import com.atguigu.gulimall.product.service.CategoryBrandRelationService;
import com.atguigu.gulimall.product.service.CategoryService;
import com.atguigu.gulimall.product.vo.Catelog2Vo;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service("categoryService")
public class CategoryServiceImpl extends ServiceImpl<CategoryDao, CategoryEntity> implements CategoryService {


    @Autowired
    CategoryBrandRelationService categoryBrandRelationService;

    @Autowired
    StringRedisTemplate stringRedisTemplate;

    @Autowired
    RedissonClient redissonClient;

    @Override
    public PageUtils queryPage(Map<String, Object> params) {
        IPage<CategoryEntity> page = this.page(
                new Query<CategoryEntity>().getPage(params),
                new QueryWrapper<CategoryEntity>()
        );

        return new PageUtils(page);
    }

    @Override
    public List<CategoryEntity> listWithTree() {
        //1、查出所有分类
        List<CategoryEntity> entities = super.baseMapper.selectList(null);

        //2、组装成父子的树形结构

        //2.1）、找到所有的一级分类
        List<CategoryEntity> level1Menus = entities.stream().filter(categoryEntity ->
                categoryEntity.getParentCid() == 0
        ).map((menu) -> {
            menu.setChildren(getChildrens(menu, entities));
            return menu;
        }).sorted((menu1, menu2) -> {
            return (menu1.getSort() == null ? 0 : menu1.getSort()) - (menu2.getSort() == null ? 0 : menu2.getSort());
        }).collect(Collectors.toList());


        return level1Menus;
    }

    @Override
    public void removeMenuByIds(List<Long> asList) {
        //TODO  1、检查当前删除的菜单，是否被别的地方引用

        //逻辑删除
        baseMapper.deleteBatchIds(asList);
    }

    //[2,25,225]
    @Override
    public Long[] findCatelogPath(Long catelogId) {
        List<Long> paths = new ArrayList<>();
        List<Long> parentPath = findParentPath(catelogId, paths);

        Collections.reverse(parentPath);


        return parentPath.toArray(new Long[parentPath.size()]);
    }

    /**
     * 级联更新所有关联的数据
     *
     * @param category
     */

//    @Caching(evict = {
//            @CacheEvict(value = {"category"},key = "'getLevel1Category'"),
//            @CacheEvict(value = {"category"},key = "'getCatalogJson'")
//    })
    @CacheEvict(value = {"category"},allEntries = true)
   // @CachePut //双写
    @Transactional
    @Override
    public void updateCascade(CategoryEntity category) {
        this.updateById(category);
        categoryBrandRelationService.updateCategory(category.getCatId(), category.getName());
    }

    //查出所有一级分类展示在页面
    @Cacheable(value = {"category"},key = "'getLevel1Category'",sync = true)     //当前方法结果可以缓存，如果缓存中有，方法不用调用，如果缓存中没有，调用方法，最够将方法结果返回缓存
    @Override
    public List<CategoryEntity> getLevel1Category() {
        System.out.println("getLevel1Category。。。。");
        List<CategoryEntity> categoryEntities = this.baseMapper.selectList(
                new QueryWrapper<CategoryEntity>().eq("parent_cid", 0));
        return categoryEntities;
    }
    //TODO 产生堆外内存溢出OutOfDirectMemoryError:
    //1)、springboot2.0以后默认使用lettuce操作redis的客户端，它使用通信
    //2)、lettuce的bug导致netty堆外内存溢出   可设置：-Dio.netty.maxDirectMemory
    //解决方案：不能直接使用-Dio.netty.maxDirectMemory去调大堆外内存
    //1)、升级lettuce客户端。      2）、切换使用jedis

    /**
     * 1.空结果缓存  解决缓存穿透
     * 2.设置缓存过期时间加随机值  解决缓存雪崩
     * 3.加锁 解决缓存击穿问题
     *
     * @return
     */
    @Cacheable(value = {"category"},key = "'getCatalogJson'" ,sync = true)
    @Override
    public Map<String, List<Catelog2Vo>> getCatalogJson() {
        System.out.println("查询了数据库");
        /**
         * 将数据库多次查询变为一次查询
         */
        List<CategoryEntity> selectList = baseMapper.selectList(null);

        // 查询所有一级分类
        List<CategoryEntity> level1 = getParent_cid(selectList, 0L);

        Map<String, List<Catelog2Vo>>  parentCid = level1.stream().collect(Collectors.toMap(k -> k.getCatId().toString(), v -> {
            // 拿到每一个一级分类 然后查询他们的二级分类
            List<CategoryEntity> level2 = getParent_cid(selectList, v.getCatId());
            List<Catelog2Vo> catelog2Vos = null;
            if (level2 != null) {
                catelog2Vos = level2.stream().map(l2 -> {
                    Catelog2Vo catelog2Vo = new Catelog2Vo(v.getCatId().toString(), null, l2.getCatId().toString(), l2.getName());
                    // 找当前二级分类的三级分类
                    List<CategoryEntity> level3 = getParent_cid(selectList, l2.getCatId());
                    // 三级分类有数据的情况下
                    if (level3 != null) {
                        List<Catelog2Vo.Catelog3Vo> catelog3Vos = level3.stream().map((l3) -> {
                            Catelog2Vo.Catelog3Vo catelog3Vo = new Catelog2Vo.Catelog3Vo(l2.getCatId().toString(), l3.getCatId().toString(), l3.getName());
                            return catelog3Vo;
                        }).collect(Collectors.toList());
                        catelog2Vo.setCatalog3List(catelog3Vos);
                    }
                    return catelog2Vo;
                }).collect(Collectors.toList());
            }
            return catelog2Vos;

        }));
        return  parentCid;
    }

    /**
     * 缓存数据一致性问题 解决
     * @return
     */
    //查出所有分类展示在页面
//    public Map<String, List<Catelog2Vo>> getCatalogJsonFromRedissonLock() {
//        //1.获取分布式锁
//        RLock lock = redissonClient.getLock("catalogJson-lock");
//        lock.lock();
//        Map<String, List<Catelog2Vo>> getCatalogJson;
//        try {
//            getCatalogJson = getCatalogJson();
//        } finally {
//            lock.unlock();
//
//        }
//
//        return getCatalogJson;
//    }

    private List<CategoryEntity> getParent_cid(List<CategoryEntity> selectList, long parent_cid) {
        final List<CategoryEntity> collect = selectList.stream().filter(item -> Objects.equals(item.getParentCid(), parent_cid)).collect(Collectors.toList());
        return collect;
    }

    //225,25,2
    private List<Long> findParentPath(Long catelogId, List<Long> paths) {
        //1、收集当前节点id
        paths.add(catelogId);
        CategoryEntity byId = this.getById(catelogId);
        if (byId.getParentCid() != 0) {
            findParentPath(byId.getParentCid(), paths);
        }
        return paths;

    }


    //递归查找所有菜单的子菜单
    private List<CategoryEntity> getChildrens(CategoryEntity root, List<CategoryEntity> all) {

        List<CategoryEntity> children = all.stream().filter(categoryEntity -> {
            return categoryEntity.getParentCid() == root.getCatId();
        }).map(categoryEntity -> {
            //1、找到子菜单
            categoryEntity.setChildren(getChildrens(categoryEntity, all));
            return categoryEntity;
        }).sorted((menu1, menu2) -> {
            //2、菜单的排序
            return (menu1.getSort() == null ? 0 : menu1.getSort()) - (menu2.getSort() == null ? 0 : menu2.getSort());
        }).collect(Collectors.toList());

        return children;
    }


}