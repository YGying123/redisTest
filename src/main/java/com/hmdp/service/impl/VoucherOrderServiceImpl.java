package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.VoucherOrder;
import com.hmdp.mapper.VoucherOrderMapper;
import com.hmdp.service.ISeckillVoucherService;
import com.hmdp.service.IVoucherOrderService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.RedisIdworker;
import com.hmdp.utils.UserHolder;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;

import org.springframework.aop.framework.AopContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.connection.stream.*;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;
import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
@Slf4j
public class VoucherOrderServiceImpl extends ServiceImpl<VoucherOrderMapper, VoucherOrder> implements IVoucherOrderService {
    @Resource
    private ISeckillVoucherService seckillVoucherService;
    @Resource
    private RedisIdworker redisIdworker;
    @Autowired
    private StringRedisTemplate stringRedisTemplate;
    @Resource
    private RedissonClient redissonClient;

    private static final DefaultRedisScript<Long> SECKILL_SCRIPT;
    static {
        SECKILL_SCRIPT = new DefaultRedisScript<>();
        SECKILL_SCRIPT.setLocation(new ClassPathResource("seckill.lua"));
        SECKILL_SCRIPT.setResultType(Long.class);
    }
//    创建阻塞队列
//    private BlockingQueue<VoucherOrder> orderTasks = new ArrayBlockingQueue<>(1024*1024);

    //异步下单开启独立的线程  创建线程池
    private static  final ExecutorService SECKLL_ORDER_EXECUTOR = Executors.newSingleThreadExecutor();
//    private IVoucherOrderService proxy;
    //    控制下单应该在初始化就已经执行
    @PostConstruct
    private void init(){
        SECKLL_ORDER_EXECUTOR.submit(new VocherOrderHandler());
    }

    private class VocherOrderHandler implements Runnable{
        String queueName = "stream.orders";
        @Override
        public void run() {
            while (true){
                try {
                    //1.获取小弟队列中的订单信息 XREADGROUP GROUP g1 c1 COUNT 1 BLOCK 200 STREAMS streams.order>
                    List<MapRecord<String, Object, Object>> list = stringRedisTemplate.opsForStream().read(
                            Consumer.from("g1", "c1"),
                            StreamReadOptions.empty().count(1).block(Duration.ofSeconds(2)),
                            StreamOffset.create(queueName, ReadOffset.lastConsumed())
                    );
                    //判断消息获取是否成功
                    if (list == null || list.isEmpty()) {
                        //如果获取失败，说明没有消息,继续洗下一次循环
                        continue;
                    }
                    //解析消息中的订单信息
                    MapRecord<String, Object, Object> record = list.get(0);
                    Map<Object, Object> values = record.getValue();
                    VoucherOrder voucherOrder = BeanUtil.fillBeanWithMap(values, new VoucherOrder(), true);

//                3.创建订单
                    handleVoucherOrder(voucherOrder);
//                4.ACK确认 SACK stream.orders g1 id
                    stringRedisTemplate.opsForStream().acknowledge(queueName, "g1", record.getId());
                }catch (Exception e){
                    log.error("处理订单异常",e);
                    handlePendingList();
                }
            }
        }

        private void handlePendingList() {
            while (true){
                try {
                    //1.获取pending-list中的订单信息 XREADGROUP GROUP g1 c1 COUNT 1  STREAMS streams.order 0
                    List<MapRecord<String, Object, Object>> list = stringRedisTemplate.opsForStream().read(
                            Consumer.from("g1", "c1"),
                            StreamReadOptions.empty().count(1),
                            StreamOffset.create(queueName, ReadOffset.from("0"))
                    );
                    //判断消息获取是否成功
                    if (list == null || list.isEmpty()) {
                        //如果获取失败，说明没有消息,继续洗下一次循环
                        break;
                    }
                    //解析消息中的订单信息
                    MapRecord<String, Object, Object> record = list.get(0);
                    Map<Object, Object> values = record.getValue();
                    VoucherOrder voucherOrder = BeanUtil.fillBeanWithMap(values, new VoucherOrder(), true);

//                3.如果获取成哥,可以下单
                    handleVoucherOrder(voucherOrder);
//                4.ACK确认
                    stringRedisTemplate.opsForStream().acknowledge(queueName, "g1", record.getId());
                }catch (Exception e){
                    log.error("处理pengding-list订单异常",e);

//                    handlePendingList();
//                    try {
//                        Thread.sleep(20);
//                    }catch (InterruptedException ex){
//                        ex.printStackTrace();
//                    }
                }
            }
        }
    }




    //    private class VocherOrderHandler implements Runnable{
//
//        @Override
//        public void run() {
//            while (true){
//                try{
//                    //获取队列中的订单信息
//                    VoucherOrder voucherOrder = orderTasks.take();//
//                    //创建订单
//                    handleVoucherOrder(voucherOrder);
//                }catch (InterruptedException e){
//                    log.error("处理订单异常",e);
//                }
//            }
//        }
//    }

    private void handleVoucherOrder(VoucherOrder voucherOrder) {
//        //1.获取用户
//        Long userId=voucherOrder.getUserId();
//        //2.创建锁对象
//        RLock lock=redissonClient.getLock("lock:order:" + userId);
//        //3.获取锁
//        boolean isLock = lock.tryLock();
//        //4.判断是否获取锁成功
//        if(!isLock){
//            log.error("不允许重复下单");
//            return;
//        }
//        try{
//            proxy.createVoucherOrder(voucherOrder);
//        }finally{
//            lock.unlock();
//        }
        Long userId = voucherOrder.getUserId();
        Long voucherId = voucherOrder.getVoucherId();
        // 创建锁对象
        RLock redisLock = redissonClient.getLock("lock:order:" + userId);
        // 尝试获取锁
        boolean isLock = redisLock.tryLock();
        // 判断
        if (!isLock) {
            // 获取锁失败，直接返回失败或者重试
            log.error("不允许重复下单！");
            return;
        }

        try {
            // 5.1.查询订单
            int count = query().eq("user_id", userId).eq("voucher_id", voucherId).count();
            // 5.2.判断是否存在
            if (count > 0) {
                // 用户已经购买过了
                log.error("不允许重复下单！");
                return;
            }

            // 6.扣减库存
            boolean success = seckillVoucherService.update()
                    .setSql("stock = stock - 1") // set stock = stock - 1
                    .eq("voucher_id", voucherId).gt("stock", 0) // where id = ? and stock > 0
                    .update();
            if (!success) {
                // 扣减失败
                log.error("库存不足！");
                return;
            }

            // 7.创建订单
            save(voucherOrder);
        } finally {
            // 释放锁
            redisLock.unlock();
        }
    }

    @Override
    public Result seckillVoucher(Long voucherId) {
        UserDTO user = UserHolder.getUser();
        if (user ==null){
            return Result.ok(Collections.emptyList());
        }
        //取出用户
        Long userId = UserHolder.getUser().getId();
        long orderId = redisIdworker.nextID("order");
        //1.执行lua脚本
        Long result = stringRedisTemplate.execute(
                SECKILL_SCRIPT,
                Collections.emptyList(),
                voucherId.toString(), userId.toString(), String.valueOf(orderId)
        );

        int r = result.intValue();
        //判断结果是否为0
        if (r!=0){
            //2.1不为0，没有购买资格
            return Result.fail(r==1 ? "库存不足":"不能重复下单");
        }
//        获取代理对象
//        proxy = (IVoucherOrderService) AopContext.currentProxy();
//        3.返回订单id
        return Result.ok(orderId);
    }

    //    @Override

//    public Result seckillVoucher(Long voucherId) {
//        //取出用户 Long userId = UserHolder.getUser().getId();
//        ////
//        ////        //1.执行luo脚本
//        ////        Long result = stringRedisTemplate.execute(
//        ////                SECKILL_SCRIPT,
//        ////                Collections.emptyList(),
//        ////                voucherId.toString(), userId.toString()
//        ////        );
//        ////        int r = result.intValue();
//        ////        //2.判断是否为0
//        ////        if(r!=0) {
//        ////            //2.1 不为0 代表没有购买资格
//        ////            return Result.fail(r==1? "库存不足":"不能重复下单");
//        ////        }
//        ////
//        ////        //2.1为0 有购买资格,把下单信息保存到阻塞队列
//        ////        long orderId = redisIdworker.nextID("order");
//        ////
//        ////        //TODD 保存阻塞队列
//        ////
//        ////        //3.返回订单id
//        ////        return Result.ok(orderId);
//        // 取出用户
//        Long userId = UserHolder.getUser().getId();
//        //1. 执行lua脚本
//        Long result = stringRedisTemplate.execute(
//                SECKILL_SCRIPT,
//                Collections.emptyList(),
//                voucherId.toString(),userId.toString()
//        );
//        int r=result.intValue();
//        //2. 判断结果是否为0
//        if(r != 0) {
//            //2.1 不为0，没有购买资格
//            return Result.fail(r==1 ? "库存不足" : "不能重复下单");
//        }
//        //2.1 为0，有购买资格，把下单信息保存到阻塞队列
//        long orderId = redisIdworker.nextID("order");
//        //TODD保存阻塞队列
//        VoucherOrder voucherOrder = new VoucherOrder();
//        voucherOrder.setId(orderId);
//        voucherOrder.setUserId(userId);
//        voucherOrder.setVoucherId(voucherId);
//        orderTasks.add(voucherOrder);
//        //获取代理对象
//        proxy = (IVoucherOrderService) AopContext.currentProxy();
//         //3.返回订单id
//        return Result.ok(orderId);
////
//    }
////    @Override
////
////    public Result seckillVoucher(Long voucherId) {
////        //查询优惠劵信息
////        SeckillVoucher voucher = seckillVoucherService.getById(voucherId);
////        //判断秒杀是否开始
////        if(voucher.getBeginTime().isAfter(LocalDateTime.now())){
////            return Result.fail("秒杀尚未开始！");
////        }
//////        判断秒杀是否结束
////        if(voucher.getEndTime().isBefore(LocalDateTime.now())){
////            return Result.fail("秒杀已经结束！");
////        }
//////        判断库存书否充足
////        if(voucher.getStock()<1){
////            return Result.fail("库存不足");
////        }
////        //使用悲观锁来解决用户并发问题
////
////        Long userId = UserHolder.getUser().getId();
////        //创建锁对象
//////        SimpleRedisLock lock = new SimpleRedisLock("order:"+userId,stringRedisTemplate);
////
////        RLock lock =  redissonClient.getLock("lock:order:"+userId);
////
////        boolean isLock = lock.tryLock();
//////        synchronized (userId.toString().intern()) {
////        if(!isLock){
////            //获取锁失败,返回错误或重试
////            return Result.fail("一个人只能下一单");
////        }
////        try {
////            //获取代理对象（事务）
////            IVoucherOrderService proxy = (IVoucherOrderService) AopContext.currentProxy();
////            return proxy.createVoucherOrder(voucherId);
//////        }
////        }finally {
////            lock.unlock();
////        }
////
////    }
    @Transactional
    public void createVoucherOrder(VoucherOrder voucherOrder) {
        //实现一人一单
//        Long userId = UserHolder.getUser().getId();
        Long userId = voucherOrder.getUserId();

        //查询订单
        int count = query().eq("user_id",userId).eq("voucher_id", voucherOrder.getVoucherId()).count();
        //判断是否存在
        if (count>0){
            //用户已经购买过了
            log.error("用户已经购买过一次！");
            return;
        }


//        扣减库存
        boolean success = seckillVoucherService
                .update()
                .setSql("stock = stock-1")
                .eq("voucher_id", voucherOrder.getVoucherId()).gt("stock",0)
                .update();
        if(!success){
            //扣减失败
            log.error("库存不足");
        }
//        创建订单
//        VoucherOrder voucherOrder = new VoucherOrder();
////        返回订单id
//        long orderId = redisIdworker.nextID("order");
//        voucherOrder.setId(orderId);
//        //用户id
////        Long userId = UserHolder.getUser().getId();
//        voucherOrder.setUserId(userId);
////        代金券id
//        voucherOrder.setVoucherId(voucherOrder);
        save(voucherOrder);
        //返回订单id
//        return Result.ok(orderId);
    }
}
