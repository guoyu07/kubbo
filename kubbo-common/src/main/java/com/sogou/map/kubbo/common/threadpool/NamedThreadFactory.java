package com.sogou.map.kubbo.common.threadpool;

import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * InternalThreadFactory.
 * 
 * @author liufuliang
 */

public class NamedThreadFactory implements ThreadFactory{
    private static final AtomicInteger POOL_SEQ = new AtomicInteger(1);

    private final AtomicInteger threadNum = new AtomicInteger(1);

    private final String prefix;

    private final boolean daemon;

    private final ThreadGroup group;

    public NamedThreadFactory(){
        this("pool-" + POOL_SEQ.getAndIncrement(),false);
    }

    public NamedThreadFactory(String prefix){
        this(prefix, false);
    }

    // TODO threadPriority
    public NamedThreadFactory(String prefix, boolean daemon){
        this.prefix = prefix + "-thread-";
        this.daemon = daemon;
        SecurityManager s = System.getSecurityManager();
        this.group = ( s == null ) ? Thread.currentThread().getThreadGroup() : s.getThreadGroup();
    }

    @Override
    public Thread newThread(Runnable runnable){
        String name = prefix + threadNum.getAndIncrement();
        Thread ret = new Thread(group, runnable, name, 0);
        //ret.setPriority(Thread.MAX_PRIORITY);
        ret.setDaemon(daemon);
        return ret;
    }

    public ThreadGroup getThreadGroup(){
        return group;
    }
}