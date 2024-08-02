package com.hmdp.utils;

import cn.hutool.core.lang.UUID;
import lombok.AllArgsConstructor;
import lombok.val;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;

//import java.util.UUID;
import java.util.Collections;
import java.util.concurrent.TimeUnit;

@AllArgsConstructor
public class SimpleRedisLock implements ILock{
    private String name;

    private StringRedisTemplate stringRedisTemplate;
    private static  final  String KEY_PREFIX = "lock";
//    创建锁的时候生成一个锁的线程标识
    private static String ID_PREFIX = UUID.randomUUID().toString(true)+"-";

    private static final DefaultRedisScript<Long> UNLOCK_SCRIPT;
    static {
        UNLOCK_SCRIPT = new DefaultRedisScript<>();
        UNLOCK_SCRIPT.setLocation(new ClassPathResource("unlock.lua"));
        UNLOCK_SCRIPT.setResultType(Long.class);
    }


    @Override
    public boolean tryLock(long timeoutSec) {
        long threadId = Thread.currentThread().getId();
        Boolean success = stringRedisTemplate.opsForValue().setIfAbsent(KEY_PREFIX+name,threadId+"",timeoutSec, TimeUnit.SECONDS);
        return Boolean.TRUE.equals(success);
    }

    @Override
    public void unlock() {
//          //调用lua脚本
        stringRedisTemplate.execute(UNLOCK_SCRIPT,
                Collections.singletonList(KEY_PREFIX+name),ID_PREFIX+Thread.currentThread().getId()
        );
    }

//    public void unlock() {
////        获取线程标识
//        String threadId = ID_PREFIX + Thread.currentThread().getId();
//        //获取锁中的标识
//        String id = stringRedisTemplate.opsForValue().get(KEY_PREFIX + name);
//
//        //判断标识是否一致
//        if (threadId.equals(id)) {
//            //释放锁
//            stringRedisTemplate.delete(KEY_PREFIX + name);
//        }
//    }
}
