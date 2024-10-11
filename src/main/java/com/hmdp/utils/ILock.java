package com.hmdp.utils;

import java.util.concurrent.TimeUnit;

public interface ILock {

    public boolean trylock(Long expireTime, TimeUnit timeUnit, String keySuffix);

    public void unlock(String keySuffix);
}
