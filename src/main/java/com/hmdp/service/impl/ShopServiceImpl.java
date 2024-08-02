package com.hmdp.service.impl;

import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSON;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import com.hmdp.mapper.ShopMapper;
import com.hmdp.service.IShopService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.CacheClient;
import com.hmdp.utils.RedisData;
import lombok.val;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;

import java.time.LocalDateTime;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstants.*;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 * 业务逻辑
 * 开始
 * 前端提交商铺的id
 * 然后从redis查询商铺缓存
 * 判断缓存是否命中
 * 命中直接redis返回商铺信息
 *
 * 未命中 根据id查询数据库
 * 判断数据库是否存在该id的商铺信息
 * 存在--》将商铺数据写入redis中 并返回
 * 不存在 返回 404
 *
 *
 *
 */
@Service
public class ShopServiceImpl extends ServiceImpl<ShopMapper, Shop> implements IShopService {
    @Resource
    private StringRedisTemplate stringRedisTemplate;
    @Resource
    private CacheClient cacheClient;
    @Override
    public Result queryById(Long id) {
//        String key = CACHE_SHOP_KEY+id;
//        //从redis查询商铺缓存
//        String shopJson = stringRedisTemplate.opsForValue().get(key);
//        //判断是否存在
//        if(StrUtil.isNotBlank(shopJson)){
//            //存在，直接返回
//            Shop shop = JSONUtil.toBean(shopJson,Shop.class);
//            return  Result.ok(shop);
//        }
//        //判断命中是否为空值
//        if (shopJson!=null){
//            //返回一个错误信息
//            return Result.fail("店铺不存在！");
//        }
//
//        //不存在,根据id查询数据库
//        Shop shop = getById(id);
//        //不存在,返回错误
//        if (shop==null){
//            //解决缓存穿透(缓存空对象)
//            stringRedisTemplate.opsForValue().set(key,"",CACHE_NULL_TTL, TimeUnit.MINUTES);
//            return Result.fail("店铺不存在！");
//
//        }
//        //存在 ，写入redis
//        stringRedisTemplate.opsForValue().set(key,JSONUtil.toJsonStr(shop),CACHE_SHOP_TTL, TimeUnit.MINUTES);
//

//        解决缓存穿透
//        queryWithPassThrough(id);

//        互斥锁解决缓存击穿
//        Shop shop = queryWithMute(id);
//        if (shop == null){
//            return Result.fail("店铺不存在!");
////        }
//        使用逻辑过期的方式解决缓存击穿
//        Shop shop = queryWithLogicalExpire(id);
        //如果shop 等于null 表示数据库中对应店铺不存在或者缓存的店铺信息是空字符串
        //解决缓存穿透
//        Shop shop = cacheClient.queryWithPassThrough(CACHE_SHOP_KEY,id, Shop.class,this::getById,CACHE_SHOP_TTL,TimeUnit.MINUTES);
        //逻辑过期解决缓存击穿
        Shop shop = cacheClient.queryWithLogicalExpire(CACHE_SHOP_KEY,id, Shop.class,this::getById,20L,TimeUnit.SECONDS);


        if(shop ==null){
            return Result.fail("店铺不存在！！");
        }


        //返回
        return Result.ok(shop);
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
    public void saveShop2Redis(Long id,Long expireSeconds){
        //1.查询店铺数据
        Shop shop = getById(id);
        //封装逻辑过期时间
        RedisData redisData = new RedisData();
        redisData.setData(shop);
        redisData.setExpireTime(LocalDateTime.now().plusSeconds(expireSeconds));

        //写入Redis
        stringRedisTemplate.opsForValue().set(CACHE_SHOP_KEY+id,JSONUtil.toJsonStr(redisData));
    }
    //逻辑过期解决缓存击穿
//    声明一个线程池,因为使用逻辑过期解决缓存击穿的方式需要新建一个线程池
    private static final ExecutorService CACHE_REBUILD_EXECUTOR = Executors.newFixedThreadPool(10);

    public Shop queryWithLogicalExpire(Long id){
        String key = CACHE_SHOP_KEY+id;
        //从redis查询商铺缓存
        String shopJson = stringRedisTemplate.opsForValue().get(key);
        //缓存未命中
        if(StrUtil.isBlank(shopJson)){
            //直接返回null
//            Shop shop = JSONUtil.toBean(shopJson,Shop.class);
            return null;
        }
        //如果命中，需要将JSON反序列化为RedisData对象
        RedisData redisData =JSONUtil.toBean(shopJson,RedisData.class);
//        redisData.getData()的本质类型是JSONObject(还是JSON字符串)并不是Object类型
        JSONObject data = (JSONObject) redisData.getData();
//        转换为对象
        Shop shop = JSONUtil.toBean(data, Shop.class);
        //判断RedisData对象封装的过期时间,判断是否过期
        LocalDateTime expireTime = redisData.getExpireTime();
        if (expireTime.isAfter(LocalDateTime.now())){
            //未过期,直接返回店铺信息
            return shop;
        }

//        如果过期,需要缓存重建，查询数据库对应的店铺信息然后写入Redis同时设置逻辑过期时间
//        获取互斥锁
        boolean isLock = tryLock(LOCK_SHOP_KEY+id);
//        判断是否获取互斥锁
        if(isLock){
            //如果Redis中缓存的店铺信息还是过期,开启独立线程实现缓存重建
            CACHE_REBUILD_EXECUTOR.submit(()->{
                //查询数据库找那个的店铺信息并设置逻辑过期时间封装为RedisData对象存入Redis
                try {
                    this.saveShop2Redis(id,20L);
                }catch (Exception e){
                    throw new RuntimeException(e);
                }finally {
                    unlock(LOCK_SHOP_KEY+ id);
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
        return shop;
    }



////    基于互斥锁方式解决缓存击穿
//    public Shop queryWithMute(Long id){
//        String key = CACHE_SHOP_KEY+id;
//        //从redis查询商铺缓存
//        String shopJson = stringRedisTemplate.opsForValue().get(key);
//        //判断是否存在
//        if(StrUtil.isNotBlank(shopJson)){
//            //存在，直接返回
////            Shop shop = JSONUtil.toBean(shopJson,Shop.class);
//            return JSONUtil.toBean(shopJson,Shop.class);
//        }
//        //判断命中是否为空值
//        if (shopJson!=null){
//            //返回一个错误信息
//            return null;
//        }
//        //未命中，尝试获取互斥锁
//        String lockkey = "lock:shop:"+id;
//        Shop shop = null;
//        try {
//            boolean islock = tryLock(lockkey);
////        判断是否获取锁
//            if(!islock) {
//    //        没有获取，休眠一段时间，重新从redis查询
//                Thread.sleep(50);
//                queryWithMute(id);
//            }
////        获取到锁 根据id查询数据库
//            shop = getById(id);
//            Thread.sleep(200);
//            //不存在,返回错误
//            if (shop==null){
//                //解决缓存穿透(缓存空对象)
//                stringRedisTemplate.opsForValue().set(key,"",CACHE_NULL_TTL, TimeUnit.MINUTES);
//                return null;
//
//            }
//            //存在 ，写入redis
//            stringRedisTemplate.opsForValue().set(key,JSONUtil.toJsonStr(shop),CACHE_SHOP_TTL, TimeUnit.MINUTES);
//        } catch (InterruptedException e) {
//            throw new RuntimeException(e);
//        }finally {
////            释放锁
//            unlock(lockkey);
//        }
//
//
//
//        //返回
//        return shop;
//    }

//    //缓存穿透
//    public Shop queryWithPassThrough(Long id){
//        String key = CACHE_SHOP_KEY+id;
//        //从redis查询商铺缓存
//        String shopJson = stringRedisTemplate.opsForValue().get(key);
//        //判断是否存在
//        if(StrUtil.isNotBlank(shopJson)){
//            //存在，直接返回
////            Shop shop = JSONUtil.toBean(shopJson,Shop.class);
//            return JSONUtil.toBean(shopJson,Shop.class);
//        }
//        //判断命中是否为空值
//        if (shopJson!=null){
//            //返回一个错误信息
//            return null;
//        }
//
//        //不存在,根据id查询数据库
//        Shop shop = getById(id);
//        //不存在,返回错误
//        if (shop==null){
//            //解决缓存穿透(缓存空对象)
//            stringRedisTemplate.opsForValue().set(key,"",CACHE_NULL_TTL, TimeUnit.MINUTES);
//            return null;
//
//        }
//        //存在 ，写入redis
//        stringRedisTemplate.opsForValue().set(key,JSONUtil.toJsonStr(shop),CACHE_SHOP_TTL, TimeUnit.MINUTES);
//
//
//
//        //返回
//        return shop;
//    }



//    为了保证线程安全性

    /**
     *
     * @param shop
     * @return
     * 一致性：
     * 更新操作
     * 是先更新数据库
     * 再删除缓存
     * 保证原子性可以采取事务控制
     */
    @Override
    @Transactional
    public Result update(Shop shop) {
        Long id = shop.getId();
        if(id ==null){
            return Result.fail("店铺id不能为空!");
        }
        //1.更新数据库
        updateById(shop);
        //2.删除缓存
        stringRedisTemplate.delete(CACHE_SHOP_KEY+id);


        return Result.ok();
    }
}
