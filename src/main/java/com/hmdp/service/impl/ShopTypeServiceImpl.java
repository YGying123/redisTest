package com.hmdp.service.impl;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.hmdp.dto.Result;
import com.hmdp.entity.ShopType;
import com.hmdp.mapper.ShopTypeMapper;
import com.hmdp.service.IShopTypeService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.List;
import java.util.stream.Collectors;

import static com.hmdp.utils.RedisConstants.CACHE_SHOP_TYPE_KEY;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */

/**
 *
 * 从redis查询首页面缓存
 *
 * 判断缓存是否命中
 * 命中 返回首页页面集合
 * 为命中
 * 根据
 *
 */


@Service
public class ShopTypeServiceImpl extends ServiceImpl<ShopTypeMapper, ShopType> implements IShopTypeService {
    @Resource
    private StringRedisTemplate stringRedisTemplate;
    @Override
    public Result querList() {

        String key = CACHE_SHOP_TYPE_KEY;

        //从redis中查询首页信息缓存
        List<String> shoptype = stringRedisTemplate.opsForList().range(key,0,-1);
        List<ShopType> res = shoptype.stream().map(s->{
            ShopType shopType = JSONUtil.toBean(s,ShopType.class);
            return shopType;
        }).collect(Collectors.toList());

        if(shoptype !=null &&!shoptype.isEmpty()){
            return Result.ok(res);
        }
        List<ShopType> shopTypess = query().orderByAsc("sort").list();
        if (shopTypess ==null || shopTypess.isEmpty()){
            return Result.fail("首页数据异常");
        }
        stringRedisTemplate.opsForList().rightPushAll(key,shopTypess.stream().map(JSONUtil::toJsonStr).collect(Collectors.toList()));
        return Result.ok(shopTypess);





//        return null;
    }
}
