package com.naveenmandal.scheduler.controller;

import com.naveenmandal.scheduler.dto.JobCreateRequest;
import com.naveenmandal.scheduler.dto.JobResponse;
import com.naveenmandal.scheduler.model.JobExecution;
import com.naveenmandal.scheduler.service.JobService;
import lombok.RequiredArgsConstructor;
import org.quartz.CronExpression;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class JobController {

    private final JobService jobService;

    // --- Job CRUD ---

    @PostMapping("/jobs")
    public ResponseEntity<JobResponse> createJob(@RequestBody JobCreateRequest request) {
        return ResponseEntity.ok(jobService.createJob(request));
    }

    @GetMapping("/jobs")
    public ResponseEntity<List<JobResponse>> getAllJobs() {
        return ResponseEntity.ok(jobService.getAllJobs());
    }

    @GetMapping("/jobs/{id}")
    public ResponseEntity<JobResponse> getJobDetails(@PathVariable UUID id) {
        return ResponseEntity.ok(jobService.getJobDetails(id));
    }

    @PutMapping("/jobs/{id}")
    public ResponseEntity<JobResponse> updateJob(@PathVariable UUID id, @RequestBody JobCreateRequest request) {
        return ResponseEntity.ok(jobService.updateJob(id, request));
    }

    @DeleteMapping("/jobs/{id}")
    public ResponseEntity<Void> deleteJob(@PathVariable UUID id) {
        jobService.deleteJob(id);
        return ResponseEntity.noContent().build();
    }

    // --- Job Actions ---

    @PostMapping("/jobs/{id}/pause")
    public ResponseEntity<Void> pauseJob(@PathVariable UUID id) {
        jobService.pauseJob(id);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/jobs/{id}/resume")
    public ResponseEntity<Void> resumeJob(@PathVariable UUID id) {
        jobService.resumeJob(id);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/jobs/{id}/run")
    public ResponseEntity<Void> runNow(@PathVariable UUID id) {
        jobService.triggerNow(id);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/jobs/{id}/requeue")
    public ResponseEntity<Void> requeueDlq(@PathVariable UUID id) {
        jobService.requeueDlq(id);
        return ResponseEntity.ok().build();
    }

    // --- History & Monitoring ---

    @GetMapping("/jobs/{id}/executions")
    public ResponseEntity<List<JobExecution>> getJobExecutions(@PathVariable UUID id) {
        return ResponseEntity.ok(jobService.getJobExecutions(id));
    }

    @GetMapping("/executions")
    public ResponseEntity<List<JobExecution>> getRecentExecutions() {
        return ResponseEntity.ok(jobService.getRecentExecutions());
    }

    @GetMapping("/dashboard/stats")
    public ResponseEntity<Map<String, Object>> getDashboardStats() {
        return ResponseEntity.ok(jobService.getDashboardStats());
    }

    /**
     * Cron expression validator endpoint.
     */
    @PostMapping("/jobs/validate-cron")
    public ResponseEntity<Map<String, Object>> validateCron(@RequestBody Map<String, String> payload) {
        String cron = payload.get("cronExpression");
        Map<String, Object> response = new HashMap<>();
        
        boolean isValid = CronExpression.isValidExpression(cron);
        response.put("valid", isValid);
        
        if (isValid) {
            try {
                CronExpression exp = new CronExpression(cron);
                response.put("message", "Valid cron expression. Next fire time: " + exp.getNextValidTimeAfter(new java.util.Date()));
            } catch (Exception e) {
                response.put("message", "Valid structure, but cannot calculate next fire time.");
            }
        } else {
            response.put("message", "Invalid cron structure. Use format: 'Seconds Minutes Hours Day-of-month Month Day-of-week [Year]'");
        }
        
        return ResponseEntity.ok(response);
    }

    /**
     * Server-Sent Events (SSE) stream for live executions log.
     */
    @GetMapping(value = "/executions/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamExecutions() {
        return jobService.subscribe();
    }
}
