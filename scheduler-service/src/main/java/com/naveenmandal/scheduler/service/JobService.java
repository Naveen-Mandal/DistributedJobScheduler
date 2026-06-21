package com.naveenmandal.scheduler.service;

import com.naveenmandal.scheduler.dto.JobCreateRequest;
import com.naveenmandal.scheduler.dto.JobResponse;
import com.naveenmandal.scheduler.event.JobEvent;
import com.naveenmandal.scheduler.model.*;
import com.naveenmandal.scheduler.repository.JobExecutionRepository;
import com.naveenmandal.scheduler.repository.JobRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.quartz.SchedulerException;
import org.springframework.data.domain.PageRequest;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class JobService {

    private final JobRepository jobRepository;
    private final JobExecutionRepository jobExecutionRepository;
    private final JobTriggerEngine triggerEngine;
    private final KafkaTemplate<String, JobEvent> kafkaTemplate;

    // Registry for connected Server-Sent Events clients
    private final List<SseEmitter> emitters = new CopyOnWriteArrayList<>();

    /**
     * Registers an SSE client subscription.
     */
    public SseEmitter subscribe() {
        SseEmitter emitter = new SseEmitter(24 * 60 * 60 * 1000L); // 24 hours timeout
        emitters.add(emitter);
        
        emitter.onCompletion(() -> emitters.remove(emitter));
        emitter.onTimeout(() -> emitters.remove(emitter));
        emitter.onError((e) -> emitters.remove(emitter));
        
        // Send a test connection event
        try {
            emitter.send(SseEmitter.event().name("ping").data("connected"));
        } catch (IOException e) {
            emitters.remove(emitter);
        }
        
        return emitter;
    }

    /**
     * Broadcasts an execution history update to all connected SSE clients.
     */
    public void publishExecutionUpdate(JobExecution execution) {
        List<SseEmitter> deadEmitters = new ArrayList<>();
        for (SseEmitter emitter : emitters) {
            try {
                emitter.send(SseEmitter.event()
                        .name("execution-update")
                        .data(execution));
            } catch (Exception e) {
                deadEmitters.add(emitter);
            }
        }
        emitters.removeAll(deadEmitters);
    }

    @Transactional
    public JobResponse createJob(JobCreateRequest request) {
        Job job = Job.builder()
                .name(request.getName())
                .cronExpression(request.getCronExpression())
                .jobType(request.getJobType())
                .payload(request.getPayload())
                .status(JobStatus.ACTIVE)
                .maxRetries(request.getMaxRetries())
                .build();

        Job savedJob = jobRepository.save(job);

        try {
            triggerEngine.scheduleJob(savedJob);
        } catch (SchedulerException e) {
            log.error("Failed to register job in Quartz engine: {}", savedJob.getId(), e);
            throw new RuntimeException("Quartz scheduling failed: " + e.getMessage());
        }

        return mapToResponse(savedJob);
    }

    @Transactional
    public JobResponse updateJob(UUID id, JobCreateRequest request) {
        Job job = jobRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Job not found with id: " + id));

        job.setName(request.getName());
        job.setCronExpression(request.getCronExpression());
        job.setJobType(request.getJobType());
        job.setPayload(request.getPayload());
        job.setMaxRetries(request.getMaxRetries());

        Job updatedJob = jobRepository.save(job);

        // Update Quartz if the job is active
        if (updatedJob.getStatus() == JobStatus.ACTIVE) {
            try {
                triggerEngine.scheduleJob(updatedJob);
            } catch (SchedulerException e) {
                log.error("Failed to reschedule job in Quartz: {}", updatedJob.getId(), e);
                throw new RuntimeException("Quartz rescheduling failed: " + e.getMessage());
            }
        }

        return mapToResponse(updatedJob);
    }

    @Transactional
    public void deleteJob(UUID id) {
        Job job = jobRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Job not found with id: " + id));

        job.setStatus(JobStatus.DELETED);
        jobRepository.save(job);

        try {
            triggerEngine.deleteJob(id);
        } catch (SchedulerException e) {
            log.error("Failed to delete job from Quartz scheduler: {}", id, e);
        }
    }

    @Transactional
    public void pauseJob(UUID id) {
        Job job = jobRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Job not found with id: " + id));

        if (job.getStatus() == JobStatus.ACTIVE) {
            job.setStatus(JobStatus.PAUSED);
            jobRepository.save(job);
            try {
                triggerEngine.pauseJob(id);
            } catch (SchedulerException e) {
                log.error("Failed to pause Quartz trigger for job: {}", id, e);
                throw new RuntimeException("Quartz pause failed: " + e.getMessage());
            }
        }
    }

    @Transactional
    public void resumeJob(UUID id) {
        Job job = jobRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Job not found with id: " + id));

        if (job.getStatus() == JobStatus.PAUSED) {
            job.setStatus(JobStatus.ACTIVE);
            jobRepository.save(job);
            try {
                triggerEngine.scheduleJob(job); // re-schedule registers it again
            } catch (SchedulerException e) {
                log.error("Failed to resume Quartz trigger for job: {}", id, e);
                throw new RuntimeException("Quartz resume failed: " + e.getMessage());
            }
        }
    }

    /**
     * Bypasses the schedule to trigger a job execution immediately.
     */
    public void triggerNow(UUID id) {
        Job job = jobRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Job not found with id: " + id));

        JobEvent event = JobEvent.builder()
                .jobId(job.getId().toString())
                .timestamp(System.currentTimeMillis())
                .retryCount(0)
                .build();

        kafkaTemplate.send("job.queue", job.getId().toString(), event);
        log.info("Job {} triggered manually. Dispatched directly to 'job.queue'.", id);
    }

    /**
     * Requeues a job that has landed in the Dead Letter Queue.
     */
    public void requeueDlq(UUID id) {
        Job job = jobRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Job not found with id: " + id));

        JobEvent event = JobEvent.builder()
                .jobId(job.getId().toString())
                .timestamp(System.currentTimeMillis())
                .retryCount(0) // Reset retry count
                .build();

        kafkaTemplate.send("job.queue", job.getId().toString(), event);
        log.info("Requeued job {} from DLQ back into main pipeline.", id);
    }

    public List<JobResponse> getAllJobs() {
        return jobRepository.findAll().stream()
                .filter(job -> job.getStatus() != JobStatus.DELETED)
                .map(this::mapToResponse)
                .toList();
    }

    public JobResponse getJobDetails(UUID id) {
        Job job = jobRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Job not found"));
        return mapToResponse(job);
    }

    public List<JobExecution> getJobExecutions(UUID jobId) {
        return jobExecutionRepository.findByJobIdOrderByStartedAtDesc(jobId);
    }

    public List<JobExecution> getRecentExecutions() {
        // Return latest 50 executions
        return jobExecutionRepository.findAllByOrderByStartedAtDesc(PageRequest.of(0, 50));
    }

    public Map<String, Object> getDashboardStats() {
        List<Job> allJobs = jobRepository.findAll().stream()
                .filter(job -> job.getStatus() != JobStatus.DELETED)
                .toList();

        long activeCount = allJobs.stream().filter(j -> j.getStatus() == JobStatus.ACTIVE).count();
        long pausedCount = allJobs.stream().filter(j -> j.getStatus() == JobStatus.PAUSED).count();

        long todayExecutions = jobExecutionRepository.countTodayExecutions();

        // Calculate success rate over all executions
        List<JobExecution> allExecutions = jobExecutionRepository.findAll();
        long totalExecutions = allExecutions.size();
        long successCount = allExecutions.stream().filter(e -> e.getStatus() == ExecutionStatus.SUCCESS).count();
        double successRate = totalExecutions == 0 ? 100.0 : ((double) successCount / totalExecutions) * 100.0;

        // Calculate DLQ count (Jobs whose last execution failed and reached max retries)
        // We can scan the recent executions or group by jobId
        Map<UUID, JobExecution> latestExecutionPerJob = new HashMap<>();
        for (JobExecution exec : allExecutions) {
            latestExecutionPerJob.compute(exec.getJobId(), (k, v) -> {
                if (v == null || exec.getStartedAt().isAfter(v.getStartedAt())) {
                    return exec;
                }
                return v;
            });
        }
        
        Map<UUID, Job> jobMap = jobRepository.findAll().stream()
                .collect(Collectors.toMap(Job::getId, j -> j));

        long dlqCount = latestExecutionPerJob.values().stream()
                .filter(e -> e.getStatus() == ExecutionStatus.FAILED)
                .filter(e -> {
                    Job j = jobMap.get(e.getJobId());
                    return j != null && e.getRetryCount() >= j.getMaxRetries();
                })
                .count();

        Map<String, Object> stats = new HashMap<>();
        stats.put("totalJobs", allJobs.size());
        stats.put("activeJobs", activeCount);
        stats.put("pausedJobs", pausedCount);
        stats.put("todayExecutions", todayExecutions);
        stats.put("successRate", Math.round(successRate * 10.0) / 10.0); // round to 1 decimal place
        stats.put("dlqJobs", dlqCount);

        return stats;
    }

    private JobResponse mapToResponse(Job job) {
        return JobResponse.builder()
                .id(job.getId())
                .name(job.getName())
                .cronExpression(job.getCronExpression())
                .jobType(job.getJobType())
                .payload(job.getPayload())
                .status(job.getStatus())
                .maxRetries(job.getMaxRetries())
                .createdAt(job.getCreatedAt())
                .updatedAt(job.getUpdatedAt())
                .build();
    }
}
