package com.naveenmandal.scheduler.event;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.naveenmandal.scheduler.model.JobExecution;
import com.naveenmandal.scheduler.service.JobService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class JobExecutionUpdateListener {

    @Autowired
    private JobService jobService;

    @Autowired
    private ObjectMapper objectMapper;

    /**
     * Listens for execution status updates pushed by the workers and broadcasts them to SSE dashboard clients.
     */
    @KafkaListener(topics = "job.execution.updates", groupId = "scheduler-group")
    public void listenExecutionUpdate(String message) {
        log.debug("Received execution update from Kafka: {}", message);
        try {
            JobExecution execution = objectMapper.readValue(message, JobExecution.class);
            jobService.publishExecutionUpdate(execution);
        } catch (Exception e) {
            log.error("Failed to parse execution update event: {}", message, e);
        }
    }
}
