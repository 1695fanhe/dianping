package com.example.dianping.utils;

import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;

import java.util.Collection;
import java.util.Collections;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

public class SimpleRedisLock implements ILock{
    private StringRedisTemplate stringRedisTemplate;
    private String name;
    public SimpleRedisLock(String name,StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
        this.name = name;
    }
    private static final String KEY_PREFIX = "lock:";
    private static final String ID_PREFIX = UUID.randomUUID().toString()+"-";
    private static final DefaultRedisScript<Long> unlock_script;
    static{
        unlock_script = new DefaultRedisScript<>();
        unlock_script.setLocation(new ClassPathResource("unlock.lua"));
        unlock_script.setResultType(Long.class);
    }

    @Override
    public boolean tryLock(Long timeoutSec) {
        String threadId = ID_PREFIX+Thread.currentThread().getId();
        Boolean success = stringRedisTemplate.opsForValue().setIfAbsent
                (KEY_PREFIX+name, threadId,timeoutSec, TimeUnit.SECONDS);
        return Boolean.TRUE.equals( success);
    }
    @Override
    public void unLock() {
        stringRedisTemplate.execute
                (unlock_script, Collections.singletonList(KEY_PREFIX +name),
                        ID_PREFIX+Thread.currentThread().getId());


    }

    /*@Override
    public void unLock() {
        String threadId=ID_PREFIX+Thread.currentThread().getId();
        String id = stringRedisTemplate.opsForValue().get(KEY_PREFIX+name);
        if (threadId.equals(id)){
            stringRedisTemplate.delete(KEY_PREFIX+name);
        }

    }*/
}
