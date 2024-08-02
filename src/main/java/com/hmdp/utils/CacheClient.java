package com.hmdp.utils;

import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.hmdp.entity.Shop;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

import static com.hmdp.utils.RedisConstants.*;
@Component
public class CacheClient {
    /**
     * 方法1：任意java对象序列化为json并存储在String类型的key中，并且可以设置TTL过期时间
     * 方法2：将任意Java对象序列化为json并存储在string类型的key中,并且可以设置逻辑过期时间,用与处理缓存击穿问题
     *
     * 方法3根据指定的key查询缓存,并反序列换指定类型，利用缓存控制的方式解决缓存床头问题
     * 方法4：根据指定的key查询缓存,并反序列化为指定类型,需要利用逻辑过期解决缓存击穿问题
     */
    private final StringRedisTemplate stringRedisTemplate;


    public CacheClient(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }
    public void set(String key , Object value, Long time, TimeUnit unit){
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(value),time,unit);
    }
    public void setWithLogicalExpire(String key,Object value,Long time,TimeUnit unit){
        //设置逻辑过期时间
        RedisData redisData = new RedisData();
        redisData.setData(value);
        redisData.setExpireTime(LocalDateTime.now().plusSeconds(unit.toSeconds(time)));
        stringRedisTemplate.opsForValue().set(key,JSONUtil.toJsonStr(redisData));

    }

//    空对象解决缓存穿透
//缓存穿透
public <R, ID> R queryWithPassThrough(String keyPrefix, ID id, Class<R> type, Function<ID,R>dbFallback,Long time ,TimeUnit unit){
    String key = keyPrefix+id;

    String json = stringRedisTemplate.opsForValue().get(key);
    //判断是否存在
    if(StrUtil.isNotBlank(json)){
        //存在，直接返回
//            Shop shop = JSONUtil.toBean(shopJson,Shop.class);
        return JSONUtil.toBean(json,type);
    }
    //判断命中是否为空值
    if (json!=null){
        //返回一个错误信息
        return null;
    }

    //不存在,根据id查询数据库
    R r = dbFallback.apply(id);
    //不存在,返回错误
    if (r==null){
        //解决缓存穿透(缓存空对象)
        stringRedisTemplate.opsForValue().set(key,"",time, unit);
        return null;

    }
    //存在 ，写入redis
//    stringRedisTemplate.opsForValue().set(key,JSONUtil.toJsonStr(shop),CACHE_SHOP_TTL, TimeUnit.MINUTES);
    this.set(key,r,time,unit);


    //返回
    return r;
}


//    使用逻辑过期方式来解决缓存击穿问题
private static final ExecutorService CACHE_REBUILD_EXECUTOR = Executors.newFixedThreadPool(10);

    public <R ,ID> R queryWithLogicalExpire(String keyPrefix,ID id,Class<R> type,Function<ID ,R>dbFallback,Long time,TimeUnit unit){
        String key = keyPrefix+id;
        //从redis查询商铺缓存
        String json = stringRedisTemplate.opsForValue().get(key);
        //缓存未命中
        if(StrUtil.isBlank(json)){
            //直接返回null
//            Shop shop = JSONUtil.toBean(shopJson,Shop.class);
            return null;
        }
        //如果命中，需要将JSON反序列化为RedisData对象
        RedisData redisData =JSONUtil.toBean(json,RedisData.class);
//        redisData.getData()的本质类型是JSONObject(还是JSON字符串)并不是Object类型
//        JSONObject data = (JSONObject) redisData.getData();
//        转换为对象
        R r = JSONUtil.toBean((JSONObject) redisData.getData(), type);
        //判断RedisData对象封装的过期时间,判断是否过期
        LocalDateTime expireTime = redisData.getExpireTime();
        if (expireTime.isAfter(LocalDateTime.now())){
            //未过期,直接返回店铺信息
            return r;
        }

//        如果过期,需要缓存重建，查询数据库对应的店铺信息然后写入Redis同时设置逻辑过期时间
//        获取互斥锁
//        boolean isLock = tryLock(LOCK_SHOP_KEY+id);
        String lockKey = LOCK_SHOP_KEY+id;
        boolean isLock = tryLock(lockKey);
//        判断是否获取互斥锁
        if(isLock){
            //如果Redis中缓存的店铺信息还是过期,开启独立线程实现缓存重建
            CACHE_REBUILD_EXECUTOR.submit(()->{
                //查询数据库找那个的店铺信息并设置逻辑过期时间封装为RedisData对象存入Redis
                try {
                     //重建缓存
                    //1.查询数据库
                    R newR = dbFallback.apply(id);
                    this.setWithLogicalExpire(key,newR ,time,unit);
                }catch (Exception e){
                    throw new RuntimeException(e);
                }finally {
                    unlock(lockKey);
                }
            });
        }


//        如果没获取互斥锁,返回旧店铺信息
//        如果获取互斥锁 重新建立缓存


        //不存在,根据id查询数据库
//        Shop shop = getById(id);


        //存在 ，写入redis
//        stringRedisTemplate.opsForValue().set(key,JSONUtil.toJsonStr(shop),CACHE_SHOP_TTL, TimeUnit.MINUTES);



        //返回
        return r;
    }

    //用互斥锁来解决缓存击穿问题
    //获取锁
    public boolean tryLock(String key){
        stringRedisTemplate.opsForValue().setIfAbsent(key,"1",LOCK_SHOP_TTL,TimeUnit.MINUTES);
        return BooleanUtil.isTrue(false);//防止程序在拆箱的时候出现空指针，手动拆箱

    }
    //释放锁
    public void unlock(String key){
        stringRedisTemplate.delete(key);
    }







}
