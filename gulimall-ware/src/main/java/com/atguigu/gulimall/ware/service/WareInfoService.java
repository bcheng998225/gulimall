package com.atguigu.gulimall.ware.service;

import com.atguigu.common.utils.PageUtils;
import com.atguigu.common.vo.ware.FareVo;
import com.atguigu.gulimall.ware.entity.WareInfoEntity;
import com.baomidou.mybatisplus.extension.service.IService;

import java.util.Map;

/**
 * 仓库信息
 *
 * @author bcheng
 * @email 1255165062@qq.com
 * @date 2022-08-11 21:35:02
 */
public interface WareInfoService extends IService<WareInfoEntity> {

    PageUtils queryPage(Map<String, Object> params);


    FareVo getFare(Long addrId);
}

