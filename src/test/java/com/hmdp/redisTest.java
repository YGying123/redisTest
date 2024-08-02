package com.hmdp;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.bean.copier.CopyOptions;
import cn.hutool.core.lang.UUID;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.User;
import com.hmdp.service.IUserService;
import com.hmdp.utils.RedisIdworker;
import com.hmdp.utils.UserHolder;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;

import javax.annotation.Resource;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.HashMap;
import java.util.List;
//import java.util.UUID;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstants.LOGIN_USER_KEY;
import static com.hmdp.utils.RedisConstants.LOGIN_USER_TTL;

@SpringBootTest
public class redisTest {
    @Resource
    private StringRedisTemplate stringRedisTemplate;
    @Resource
    private RedisIdworker redisIdworker;
    @Resource
    private IUserService userService;
    private ExecutorService es = Executors.newFixedThreadPool(500);
    @Test
    void testIdWOrker() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(300);
        Runnable task = ()->{
            for (int i = 0; i < 100; i++) {
                long id = redisIdworker.nextID("order");
                System.out.println("id="+id);
            }
            latch.countDown();
        };
        latch.countDown();
        long begin = System.currentTimeMillis();
        for (int i = 0; i < 300; i++) {
            es.submit(task);
        }
        latch.await();
        long end = System.currentTimeMillis();
        System.out.println("time = "+(end-begin));
    }

@Test
//    根据用户信息生成token.txt
    public void createToken() throws IOException {
        List<User> list = userService.list();
        PrintWriter printWriter = new PrintWriter(new FileWriter("D:\\ying\\apache-maven-3.6.1\\mavenlaji\\Redis\\token.txt"));
        for(User user:list){
            String token = UUID.randomUUID().toString(true);
            UserDTO userDTO = BeanUtil.copyProperties(user, UserDTO.class);
            Map<String, Object> userMap = BeanUtil.beanToMap(userDTO, new HashMap<>(),
                    CopyOptions.create().setIgnoreNullValue(true).setFieldValueEditor((fieldName, fieldValue) -> fieldValue.toString()));
            String tokenKey = LOGIN_USER_KEY+ token;
            stringRedisTemplate.opsForHash().putAll(tokenKey,userMap);
            stringRedisTemplate.expire(tokenKey,LOGIN_USER_TTL, TimeUnit.MINUTES);
            printWriter.print(token+"\n");
            printWriter.flush();

        }
    }
//    @Test
//    public void createToken() throws IOException {
//        List<User> list = userService.list();
//        PrintWriter printWriter = new PrintWriter(new FileWriter("E:\\token.txt"));
//        for(User user: list){
//            String token = UUID.randomUUID().toString(true);
//            UserDTO userDTO = BeanUtil.copyProperties(user, UserDTO.class);
//            Map<String, Object> userMap = BeanUtil.beanToMap(userDTO, new HashMap<>(),
//                    CopyOptions.create()
//                            .setIgnoreNullValue(true)
//                            .setFieldValueEditor((fieldName, fieldValue)->fieldValue.toString()));
//            String tokenKey = LOGIN_USER_KEY + token;
//            stringRedisTemplate.opsForHash().putAll(tokenKey, userMap);
//            stringRedisTemplate.expire(tokenKey, LOGIN_USER_TTL, TimeUnit.MINUTES);
//            printWriter.print(token + "\n");
//            printWriter.flush();
//        }
//    }


}
