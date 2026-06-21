package com.naveenmandal.scheduler.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class DistributedLockServiceTest {

    @Mock
    private RedissonClient redissonClient;

    @Mock
    private RLock lock;

    @InjectMocks
    private DistributedLockService lockService;

    private final String jobId = "test-job-uuid";
    private final String lockKey = "lock:job:" + jobId;

    @BeforeEach
    void setUp() {
        lenient().when(redissonClient.getLock(lockKey)).thenReturn(lock);
    }

    @Test
    void testLockAcquisitionSuccess() throws InterruptedException {
        // Arrange
        when(lock.tryLock(eq(0L), anyLong(), eq(TimeUnit.SECONDS))).thenReturn(true);
        when(lock.isHeldByCurrentThread()).thenReturn(true);
        AtomicBoolean taskExecuted = new AtomicBoolean(false);

        // Act
        boolean result = lockService.tryLockAndExecute(jobId, () -> taskExecuted.set(true));

        // Assert
        assertTrue(result);
        assertTrue(taskExecuted.get());
        verify(lock).unlock();
    }

    @Test
    void testLockAcquisitionFailure() throws InterruptedException {
        // Arrange
        when(lock.tryLock(eq(0L), anyLong(), eq(TimeUnit.SECONDS))).thenReturn(false);
        AtomicBoolean taskExecuted = new AtomicBoolean(false);

        // Act
        boolean result = lockService.tryLockAndExecute(jobId, () -> taskExecuted.set(true));

        // Assert
        assertFalse(result);
        assertFalse(taskExecuted.get());
        verify(lock, never()).unlock();
    }
}
