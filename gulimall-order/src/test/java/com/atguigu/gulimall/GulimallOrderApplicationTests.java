package com.atguigu.gulimall;

import lombok.extern.slf4j.Slf4j;
import org.junit.runner.RunWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;

@RunWith(SpringRunner.class)
@Slf4j
@SpringBootTest
 public class GulimallOrderApplicationTests {

//    @Autowired
//    AmqpAdmin amqpAdmin;
//
//    @Autowired
//    RabbitTemplate rabbitTemplate;
//
//    @Test
//    public  void creatExchange() {
//
//         DirectExchange directExchange = new DirectExchange("hello-java-exchange",true,false);
//        amqpAdmin.declareExchange(directExchange);
//        log.info("交换机创建完成");
//
//    }
//
//    @Test
//    public  void creatQueue() {
//
//       Queue queue = new Queue("hello-java-queue",true,false,false);
//        amqpAdmin.declareQueue(queue);
//        log.info("队列创建完成");
//
//
//    }
//
//    @Test
//    public  void creatBinding() {
//        Binding binding = new Binding("hello-java-queue",
//                 Binding.DestinationType.QUEUE,
//                 "hello-java-exchange",
//                 "hello.java",
//                 null);
//        amqpAdmin.declareBinding(binding);
//        log.info("绑定关系创建完成");
//
//
//    }
//
//    @Test
//    public  void sendMessage() {
//
//
//        String msg="哈哈";
//        rabbitTemplate.convertAndSend("hello-java-exchange","hello.java",msg,new CorrelationData(UUID.randomUUID().toString()));
//        log.info("消息发送完成：{}",msg);
//
//    }
//    @Test
//    public  void SAS() {
//        Date currentTime = new Date();
//
//        SimpleDateFormat simpleDateFormat1 = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
//
//        System.out.println(currentTime);  // 输出2019-02-18 13:53:50.6
//    }
}
