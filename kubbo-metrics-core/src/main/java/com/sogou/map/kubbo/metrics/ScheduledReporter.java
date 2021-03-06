package com.sogou.map.kubbo.metrics;


import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import com.sogou.map.kubbo.common.logger.Logger;
import com.sogou.map.kubbo.common.logger.LoggerFactory;
import com.sogou.map.kubbo.common.threadpool.NamedThreadFactory;
import com.sogou.map.kubbo.common.util.ExecutorUtils;

/**
 * abstract scheduled reporters 
 * @author liufuliang
 */
public abstract class ScheduledReporter implements Reporter {

    private static final Logger LOG = LoggerFactory.getLogger(ScheduledReporter.class);
    
    private final MetricRegistry registry;
    
    private final ScheduledExecutorService executor;
    
    private ScheduledFuture<?> scheduledFuture;

    protected ScheduledReporter(MetricRegistry registry,
                                String name,
                                TimeUnit rateUnit,
                                TimeUnit durationUnit) {
        this.registry = registry;
        this.executor = new ScheduledThreadPoolExecutor(1, new NamedThreadFactory(name, true));
    }

    @Override
    public void start(long period, TimeUnit unit) {
       start(period, period, unit);
    }

    @Override
    public synchronized void start(long initialDelay, long period, TimeUnit unit) {
      if (this.scheduledFuture != null) {
          throw new IllegalArgumentException("Reporter already started");
      }

      this.scheduledFuture = executor.scheduleAtFixedRate(new Runnable() {
         @Override
         public void run() {
             try {
                 report();
             } catch (Exception ex) {
                 LOG.error(ScheduledReporter.this.getClass().getSimpleName(), ex);
             }
         }
      }, initialDelay, period, unit);
    }

    @Override
    public void stop() {
        ExecutorUtils.shutdownGracefully(executor, 1000);
    }

    /**
     * Stops the reporter and shuts down its thread of execution.
     */
    @Override
    public void close() {
        stop();
    }

    /**
     * Report the current values of all metrics in the registry.
     */
    @Override
    public void report() {
        synchronized (this) {
            report(registry);
        }
    }
    
    protected abstract void report(MetricRegistry registry);

}
