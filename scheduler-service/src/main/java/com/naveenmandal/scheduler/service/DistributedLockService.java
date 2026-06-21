package com.naveenmandal.scheduler.service;

import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

@Service
@Slf4j
public class DistributedLockService {

    @Autowired
    private RedissonClient redissonClient;

    /**
     * Attempts to acquire a non-blocking lock on a jobId.
     * If acquired, runs the task and releases the lock immediately or allows it to expire.
     */
    public boolean tryLockAndExecute(String jobId, Runnable task) {
        String lockKey = "lock:job:" + jobId;
        RLock lock = redissonClient.getLock(lockKey);

        boolean acquired = false;
        try {
            // 0 wait time: if already held by another cluster node, fail immediately
            // 15 seconds lease: auto-release if scheduler crashes to avoid permanent lockouts
            acquired = lock.tryLock(0, 15, TimeUnit.SECONDS);

            if (acquired) {
                log.info("Lock acquired successfully for job: {}", jobId);
                task.run();
                return true;
            } else {
                log.debug("Lock already held by another node for job: {}. Skipping execution.", jobId);
                return false;
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("Interrupted while trying to acquire lock for job: {}", jobId, e);
            return false;
        } finally {
            if (acquired && lock.isHeldByCurrentThread()) {
                try {
                    lock.unlock();
                } catch (Exception e) {
                    log.error("Failed to release lock for job: {}", jobId, e);
                }
            }
        }
    }
}
