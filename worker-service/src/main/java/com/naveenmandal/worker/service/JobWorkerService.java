package com.naveenmandal.worker.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.naveenmandal.common.event.JobEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class JobWorkerService {

    private final JobExecutionHandler executionHandler;
    private final ObjectMapper objectMapper;

    /**
     * Consumes jobs from the main execution queue.
     */
    @KafkaListener(topics = "job.queue", groupId = "worker-group")
    public void processMainJob(String message) {
        log.info("Received execution request on 'job.queue': {}", message);
        try {
            JobEvent event = objectMapper.readValue(message, JobEvent.class);
            executionHandler.executeJob(event);
        } catch (Exception e) {
            log.error("Failed to process event: {}", message, e);
        }
    }

    /**
     * Consumes jobs from the retry queue and applies exponential backoff delay.
     */
    @KafkaListener(topics = "job.retry", groupId = "retry-group")
    public void processRetryJob(String message) throws InterruptedException {
        log.info("Received retry request on 'job.retry': {}", message);
        try {
            JobEvent event = objectMapper.readValue(message, JobEvent.class);
            
            // Exponential backoff: 2s, 4s, 8s, 16s...
            long backoffDelay = (long) Math.pow(2, event.getRetryCount()) * 1000L;
            log.info("Applying exponential backoff of {}ms before executing retry {} for job {}", 
                    backoffDelay, event.getRetryCount(), event.getJobId());
            Thread.sleep(backoffDelay); // Sleep outside transaction block

            executionHandler.executeJob(event);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            throw ie;
        } catch (Exception e) {
            log.error("Failed to process retry event: {}", message, e);
        }
    }
}
