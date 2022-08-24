package com.atguigu.gulimall.service.impl;

import com.alibaba.fastjson.JSON;
import com.atguigu.common.to.es.SkuEsModel;
import com.atguigu.gulimall.config.ElasticsearchConfig;
import com.atguigu.gulimall.constanct.EsConstant;
import com.atguigu.gulimall.service.ProductSaveService;
import lombok.extern.slf4j.Slf4j;
import org.elasticsearch.action.bulk.BulkItemResponse;
import org.elasticsearch.action.bulk.BulkRequest;
import org.elasticsearch.action.bulk.BulkResponse;
import org.elasticsearch.action.index.IndexRequest;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.common.xcontent.XContentType;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
public class ProductSaveServiceImpl implements ProductSaveService {

    @Autowired
    RestHighLevelClient client;


    /**
     * 上架商品
     * @param esModels
     * @return
     */
    @Override
    public boolean productStatusUp(List<SkuEsModel> esModels) throws IOException {
        //1.给Es中建立索引 product，建立好映射关系

        //2.给es保存数据
         BulkRequest bulkRequest = new BulkRequest();
        for (SkuEsModel esModel : esModels) {
            //构造保存请求
          IndexRequest indexRequest = new IndexRequest(EsConstant.PRODUCT_INDEX);
          indexRequest.id(esModel.getSkuId().toString());
           String s = JSON.toJSONString(esModel);
            indexRequest.source(s, XContentType.JSON);
            bulkRequest.add(indexRequest);
        }
        BulkResponse bulk = client.bulk(bulkRequest, ElasticsearchConfig.COMMON_OPTIONS);
        //TODO 如果批量错误 处理错误
        boolean b = bulk.hasFailures();
        List<Object> collect = Arrays.stream(bulk.getItems()).map(BulkItemResponse::getId).collect(Collectors.toList());
        log.error("商品上架失败:{}",collect);

        return b;
    }
}
