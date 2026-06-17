package com.example.dianping.config;

import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RedisonConfig {
    @Bean
    public RedissonClient redissonClient(){
        Config config = new Config();
        // 修改为本地Redis，并移除密码（本地Redis默认无密码）
        config.useSingleServer()
                .setAddress("redis://127.0.0.1:6379");
        // 如果本地Redis设置了密码，取消下面的注释并填写密码
        // .setPassword("your_password");
        return Redisson.create(config);
    }
}