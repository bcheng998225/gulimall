package com.atguigu.gulimall.service;

import com.atguigu.common.to.es.SkuEsModel;

import java.io.IOException;
import java.util.List;


public interface ProductSaveService {
    boolean productStatusUp(List<SkuEsModel> esModels) throws IOException;
}
