package com.hmdp.utils;

import lombok.val;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

/**
 * 全局唯一ID生成器工具类
 */
@Component
public class RedisIdworker {
    //开始时间戳
    private  static final long BEGIN_TIMESTAMP = 1721865600L;
    private static final int COUNT_BITS=32;


    private   StringRedisTemplate stringRedisTemplate;

    public RedisIdworker(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    public long nextID(String keyPrefix){
        //生成时间戳
        LocalDateTime now = LocalDateTime.now();
        long nowSecond = now.toEpochSecond(ZoneOffset.UTC);
        long timestamp = nowSecond-BEGIN_TIMESTAMP;

        //生成序列号
        //获取当前日期,精确到天
        String date = now.format(DateTimeFormatter.ofPattern("yyyy:MM:dd"));
        //自增长
        long count =stringRedisTemplate.opsForValue().increment("icr:"+keyPrefix+":"+date);
        return timestamp<<COUNT_BITS | count;
    }



//    public static void main(String[] args) {
//        LocalDateTime time = LocalDateTime.of(2024, 7, 25, 0, 0, 0);
//        Long second = time.toEpochSecond(ZoneOffset.UTC);
//        System.out.println("second ="+second);
//    }

}
