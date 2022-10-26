package com.atguigu;

import com.atguigu.gulimall.product.service.BrandService;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.test.context.junit4.SpringRunner;

import javax.annotation.Resource;
import java.util.UUID;

@RunWith(SpringRunner.class)
@SpringBootTest
public class GulimallProductApplicationTests {

    @Resource
    StringRedisTemplate stringRedisTemplate;

    @Resource
    BrandService brandService;



    @Test
    public void test(){
       ValueOperations<String, String> ops = stringRedisTemplate.opsForValue();
     ops.set("hello","word"+ UUID.randomUUID().toString());
        String hello = ops.get("hello");
        System.out.println("之前保存的数据是:"+hello);
    }


}
