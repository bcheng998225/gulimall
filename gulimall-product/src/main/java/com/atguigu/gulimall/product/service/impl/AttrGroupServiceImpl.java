package com.atguigu.gulimall.product.service.impl;

import com.atguigu.common.utils.PageUtils;
import com.atguigu.common.utils.Query;
import com.atguigu.gulimall.product.dao.AttrGroupDao;
import com.atguigu.gulimall.product.entity.AttrEntity;
import com.atguigu.gulimall.product.entity.AttrGroupEntity;
import com.atguigu.gulimall.product.service.AttrGroupService;
import com.atguigu.gulimall.product.service.AttrService;
import com.atguigu.gulimall.product.vo.AttrGroupWithAttrsVo;
import com.atguigu.gulimall.product.vo.SpuItemAttrGroupVo;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.StringUtils;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;


@Service("attrGroupService")
public class AttrGroupServiceImpl extends ServiceImpl<AttrGroupDao, AttrGroupEntity> implements AttrGroupService {


    @Autowired
    AttrService attrService;
    @Override
    public PageUtils queryPage(Map<String, Object> params) {
        IPage<AttrGroupEntity> page = this.page(
                new Query<AttrGroupEntity>().getPage(params),
                new QueryWrapper<AttrGroupEntity>()
        );

        return new PageUtils(page);
    }

    @Override
    public PageUtils queryPage(Map<String, Object> params, Long catelogId) {

        String key = (String) (params.get("key"));
        QueryWrapper<AttrGroupEntity> wrapper = new QueryWrapper<AttrGroupEntity>();
        if (!StringUtils.isEmpty(key)) {
            wrapper.and((obj) -> {
                obj.eq("attr_group_id", key).or().like("attr_group_name", key);
            });
        } if (catelogId == 0){
            IPage<AttrGroupEntity> page = this.page(new Query<AttrGroupEntity>().getPage(params),
                    wrapper);
            return new PageUtils(page);
        } else {
            wrapper.eq("catelog_id", catelogId);
            IPage<AttrGroupEntity> page = this.page(new Query<AttrGroupEntity>().getPage(params),
                    wrapper);
            return new PageUtils(page);
        }

    }

    /**
     * 根据分类查出所有分组以及这些组别的属性
     *
     * @param catelogId
     * @return
     */
    @Override
    public List<AttrGroupWithAttrsVo> getAttrGroupWithAttrsBycatelogId(Long catelogId) {
        //1.查出所有分组信息
        List<AttrGroupEntity> catelogId1 = this.list(new QueryWrapper<AttrGroupEntity>()
                .eq("catelog_id", catelogId));
        //2.查询所有属性
        List<AttrGroupWithAttrsVo> collect = catelogId1.stream().map(group -> {
            AttrGroupWithAttrsVo withAttrsVo = new AttrGroupWithAttrsVo();
            BeanUtils.copyProperties(group,withAttrsVo);
            List<AttrEntity> attrs = attrService.getRelationAttr(withAttrsVo.getAttrGroupId());
            withAttrsVo.setAttrs(attrs);
            return withAttrsVo;
        }).collect(Collectors.toList());
        return collect;
    }
    //5.获取spu规格参数信息
    @Override
    public List<SpuItemAttrGroupVo> getAttrGruopWithAttrsBySpuId(Long spuId, Long catalogId) {
        //查出当前spu对应的分组信息，以及当前分组对应的所有属性的值pms_attr_group
        //1、查出当前spu对应的所有属性的分组信息以及当前分组下的所有属性对应的值
        AttrGroupDao baseMapper = this.getBaseMapper();
        List<SpuItemAttrGroupVo> vos = baseMapper.getAttrGruopWithAttrsBySpuId(spuId,catalogId);
        return vos;
    }

}