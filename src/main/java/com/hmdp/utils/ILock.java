package com.hmdp.utils;

import java.util.concurrent.TimeUnit;

public interface ILock {
    boolean tryLock(Long timeout, TimeUnit unit);

    void unLock();
}
