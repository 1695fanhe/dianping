package com.example.dianping.utils;

public interface ILock {
    boolean tryLock(Long timeoutSec);
    void unLock();
}
