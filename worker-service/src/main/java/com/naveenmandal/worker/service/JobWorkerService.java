package com.naveenmandal.worker.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.naveenmandal.worker.event.JobEvent;
import com.naveenmandal.worker.model.*;
import com.naveenmandal.worker.repository.JobExecutionRepository;
import com.naveenmandal.worker.repository.JobRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
@Slf4j
public class JobWorkerService {

    private final JobRepository jobRepository;
    private final JobExecutionRepository executionRepository;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final ObjectMapper objectMapper;
    private final RestTemplate restTemplate = new RestTemplate();

    @Autowired
    @Lazy
    private JobWorkerService self;

    // Whitelist pattern to sanitize shell arguments and prevent command injections
    private static final Pattern SAFE_SHELL_PATTERN = Pattern.compile("^[a-zA-Z0-9\\s\\-_\\./:=?&]+$");

    /**
     * Consumes jobs from the main execution queue.
     */
    @KafkaListener(topics = "job.queue", groupId = "worker-group")
    public void processMainJob(String message) {
        log.info("Received execution request on 'job.queue': {}", message);
        try {
            JobEvent event = objectMapper.readValue(message, JobEvent.class);
            self.executeJob(event);
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

            self.executeJob(event); // Invoke via transactional proxy
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            throw ie;
        } catch (Exception e) {
            log.error("Failed to process retry event: {}", message, e);
        }
    }

    /**
     * Coordinates the execution cycle: registers RUNNING, attempts invocation, and routes status to DB/Kafka.
     */
    @Transactional
    public void executeJob(JobEvent event) {
        UUID jobId = UUID.fromString(event.getJobId());
        Job job = jobRepository.findById(jobId)
                .orElseThrow(() -> new RuntimeException("Job definition not found in database: " + jobId));

        String nodeId = getNodeIdentifier();

        // 1. Save RUNNING status in DB
        JobExecution execution = JobExecution.builder()
                .jobId(job.getId())
                .status(ExecutionStatus.RUNNING)
                .startedAt(LocalDateTime.now())
                .nodeId(nodeId)
                .retryCount(event.getRetryCount())
                .build();

        JobExecution savedExecution = executionRepository.save(execution);
        
        // Notify scheduler about the RUNNING state
        publishExecutionUpdate(savedExecution);

        try {
            // 2. Perform the actual work
            runJobPayload(job);

            // 3. Mark success
            savedExecution.setStatus(ExecutionStatus.SUCCESS);
            savedExecution.setFinishedAt(LocalDateTime.now());
            executionRepository.save(savedExecution);
            
            // Notify scheduler of SUCCESS
            publishExecutionUpdate(savedExecution);
            log.info("Successfully executed job: {} on node {}", job.getId(), nodeId);

        } catch (Exception e) {
            log.error("Execution failed for job: {} on node {}", job.getId(), nodeId, e);
            handleFailure(job, event, savedExecution, e);
        }
    }

    /**
     * Handles failure routing: triggers retries or routes to DLQ if max retries are exceeded.
     * Annotated with Transactional to guarantee atomicity of DB state write and Kafka trigger publishing.
     */
    @Transactional
    void handleFailure(Job job, JobEvent event, JobExecution execution, Exception exception) {
        execution.setStatus(ExecutionStatus.FAILED);
        execution.setErrorMessage(exception.getMessage());
        execution.setFinishedAt(LocalDateTime.now());
        JobExecution savedExecution = executionRepository.save(execution);

        // Notify scheduler of FAILURE
        publishExecutionUpdate(savedExecution);

        int currentRetry = event.getRetryCount();
        if (currentRetry < job.getMaxRetries()) {
            // Schedule a retry
            event.setRetryCount(currentRetry + 1);
            event.setTimestamp(System.currentTimeMillis());
            
            try {
                String rawEvent = objectMapper.writeValueAsString(event);
                kafkaTemplate.send("job.retry", event.getJobId(), rawEvent);
                log.warn("Job {} failed. Enqueued retry {}/{} to 'job.retry'", 
                        job.getId(), event.getRetryCount(), job.getMaxRetries());
            } catch (Exception e) {
                log.error("Fatal: failed to serialize retry event for Job {}", job.getId(), e);
            }
        } else {
            // Max retries exhausted: route to Dead Letter Queue
            try {
                String rawEvent = objectMapper.writeValueAsString(event);
                kafkaTemplate.send("job.dlq", event.getJobId(), rawEvent);
                log.error("Job {} exhausted all {} retries. Forwarded to Dead Letter Queue 'job.dlq'", 
                        job.getId(), job.getMaxRetries());
            } catch (Exception e) {
                log.error("Fatal: failed to serialize DLQ event for Job {}", job.getId(), e);
            }
        }
    }

    /**
     * Parses the payload according to type and invokes the corresponding executor.
     */
    private void runJobPayload(Job job) throws Exception {
        Map<String, String> payloadMap = objectMapper.readValue(job.getPayload(), new TypeReference<Map<String, String>>() {});
        
        switch (job.getJobType()) {
            case HTTP_CALL -> executeHttpJob(payloadMap);
            case SHELL -> executeShellJob(payloadMap);
            case EMAIL -> executeEmailJob(payloadMap);
        }
    }

    /**
     * Executes an HTTP Call job using RestTemplate.
     */
    private void executeHttpJob(Map<String, String> payload) {
        String url = payload.get("url");
        String methodStr = payload.getOrDefault("method", "GET");
        
        if (url == null || url.trim().isEmpty()) {
            throw new IllegalArgumentException("HTTP_CALL payload missing 'url'");
        }

        log.info("Executing HTTP call: [{} {}]", methodStr, url);
        HttpMethod method = HttpMethod.valueOf(methodStr.toUpperCase());
        
        ResponseEntity<String> response = restTemplate.exchange(url, method, null, String.class);
        
        if (!response.getStatusCode().is2xxSuccessful()) {
            throw new RuntimeException("HTTP service returned non-2xx status code: " + response.getStatusCode());
        }
    }

    /**
     * Executes a whitelisted shell script command safely.
     */
    private void executeShellJob(Map<String, String> payload) throws Exception {
        String command = payload.get("command");
        if (command == null || command.trim().isEmpty()) {
            throw new IllegalArgumentException("SHELL payload missing 'command'");
        }

        // Security check: validate arguments to block command injections
        if (!SAFE_SHELL_PATTERN.matcher(command).matches()) {
            throw new SecurityException("Command execution blocked. Contains illegal characters or potential injection vectors: " + command);
        }

        log.info("Executing whitelisted shell command: {}", command);
        
        // Execute under sh safely
        ProcessBuilder processBuilder = new ProcessBuilder("sh", "-c", command);
        processBuilder.redirectErrorStream(true);
        Process process = processBuilder.start();

        int exitCode = process.waitFor();
        if (exitCode != 0) {
            throw new RuntimeException("Shell command exited with code: " + exitCode);
        }
    }

    /**
     * Mock Email dispatcher.
     */
    private void executeEmailJob(Map<String, String> payload) {
        String to = payload.get("to");
        String subject = payload.get("subject");
        String body = payload.get("body");

        if (to == null || to.trim().isEmpty()) {
            throw new IllegalArgumentException("EMAIL payload missing 'to' address");
        }

        log.info("Sending mock email to: {}, Subject: '{}', Body snippet: '{}'", to, subject, 
                body != null && body.length() > 30 ? body.substring(0, 30) + "..." : body);
    }

    /**
     * Publishes execution reports back to the scheduler service's updates topic for SSE broadcasting.
     */
    private void publishExecutionUpdate(JobExecution execution) {
        try {
            String json = objectMapper.writeValueAsString(execution);
            kafkaTemplate.send("job.execution.updates", execution.getJobId().toString(), json);
        } catch (Exception e) {
            log.error("Failed to publish execution status update to Kafka", e);
        }
    }

    /**
     * Retrieves a unique identifier of the node (Docker Container ID or local hostname).
     */
    private String getNodeIdentifier() {
        String host = System.getenv("HOSTNAME"); // Hostname matches container ID in Docker Compose
        if (host == null || host.isEmpty()) {
            try {
                host = java.net.InetAddress.getLocalHost().getHostName();
            } catch (Exception e) {
                host = "worker-node-" + UUID.randomUUID().toString().substring(0, 8);
            }
        }
        return host;
    }
}
