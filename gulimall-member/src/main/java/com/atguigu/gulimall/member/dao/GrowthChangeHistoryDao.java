package com.atguigu.gulimall.member.dao;

import com.atguigu.gulimall.member.entity.GrowthChangeHistoryEntity;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * 成长值变化历史记录
 * 
 * @author bcheng
 * @email 1255165062@qq.com
 * @date 2022-08-11 21:33:45
 */
@Mapper
public interface GrowthChangeHistoryDao extends BaseMapper<GrowthChangeHistoryEntity> {
	
}
