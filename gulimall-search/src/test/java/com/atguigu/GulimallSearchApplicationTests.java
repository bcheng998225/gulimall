package com.atguigu;

import com.alibaba.fastjson.JSON;
import com.atguigu.gulimall.config.ElasticsearchConfig;
import lombok.Data;
import org.elasticsearch.action.index.IndexRequest;
import org.elasticsearch.action.index.IndexResponse;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.common.xcontent.XContentType;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.SearchHits;
import org.elasticsearch.search.aggregations.AggregationBuilders;
import org.elasticsearch.search.aggregations.Aggregations;
import org.elasticsearch.search.aggregations.bucket.terms.Terms;
import org.elasticsearch.search.aggregations.bucket.terms.TermsAggregationBuilder;
import org.elasticsearch.search.aggregations.metrics.avg.Avg;
import org.elasticsearch.search.aggregations.metrics.avg.AvgAggregationBuilder;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;

import java.io.IOException;

@RunWith(SpringRunner.class)
@SpringBootTest
public class GulimallSearchApplicationTests {

    @Autowired
    private RestHighLevelClient client;

    @Test
    public void contextLoads() {
        System.out.println(client);

    }

    /**
     * 测试存储数据到es
     */
    @Test
    public void lndex() throws IOException {
        IndexRequest indexRequest = new IndexRequest("users");
        indexRequest.id("1");
//        indexRequest.source("username","张三","age","18","sex","男");
        User user = new User();
        String s = JSON.toJSONString(user);
        indexRequest.source(s, XContentType.JSON);
        IndexResponse index = client.index(indexRequest, ElasticsearchConfig.COMMON_OPTIONS);
        System.out.println(index);


    }

    @Test
    public void searchData() throws IOException {
        //1.创建检索请求
        SearchRequest searchRequest = new SearchRequest();
        //指定索引
        searchRequest.indices("bank");
        final SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder();
        //指定检索条件，构造检索条件
        searchSourceBuilder.query(QueryBuilders.matchQuery("address", "171"));
        //按照年龄聚合
        TermsAggregationBuilder ageAgg = AggregationBuilders.terms("aggeAgg").field("age").size(10);
        searchSourceBuilder.aggregation(ageAgg);
        //按照平均薪资聚合
        final AvgAggregationBuilder balance = AggregationBuilders.avg("balanceAvg").field("balance");
        searchSourceBuilder.aggregation(balance);
//        searchSourceBuilder.from();
//        searchSourceBuilder.size();

        searchRequest.source(searchSourceBuilder);
        //2.执行检索
        SearchResponse response = client.search(searchRequest, ElasticsearchConfig.COMMON_OPTIONS);
        //3.分析结果
        SearchHits hits = response.getHits();
        SearchHit[] hits1 = hits.getHits();
        for (SearchHit hit : hits) {
            String asString = hit.getSourceAsString();
//            Object account = JSON.parseObject(asString, Account.class);
//            System.out.println(account);
        }
        Aggregations aggregations = response.getAggregations();
        Terms aggeAgg = aggregations.get("aggeAgg");
        for (Terms.Bucket bucket : aggeAgg.getBuckets()) {
            Number keyAsNumber = bucket.getKeyAsNumber();
        }
         Avg balanceAvg = aggregations.get("balanceAvg");

    }

    @Data
    class User {
        private String name;
        private Integer age;
        private String gender;
    }
}
