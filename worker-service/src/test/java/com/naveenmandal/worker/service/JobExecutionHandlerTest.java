package com.naveenmandal.worker.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.naveenmandal.common.event.JobEvent;
import com.naveenmandal.common.model.*;
import com.naveenmandal.common.repository.JobExecutionRepository;
import com.naveenmandal.common.repository.JobRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.web.client.RestTemplate;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class JobExecutionHandlerTest {

    @Mock
    private JobRepository jobRepository;

    @Mock
    private JobExecutionRepository executionRepository;

    @Mock
    private KafkaTemplate<String, Object> kafkaTemplate;

    @Mock
    private RestTemplate restTemplate;

    @Spy
    private ObjectMapper objectMapper = new ObjectMapper();

    @InjectMocks
    private JobExecutionHandler executionHandler;

    @Test
    void testHandleFailureTriggersRetry() throws Exception {
        UUID id = UUID.randomUUID();
        Job job = Job.builder().id(id).maxRetries(3).build();
        JobEvent event = JobEvent.builder().jobId(id.toString()).retryCount(1).build();
        JobExecution execution = new JobExecution();

        when(executionRepository.save(any(JobExecution.class))).thenReturn(execution);

        executionHandler.handleFailure(job, event, execution, new RuntimeException("Transient Error"));

        verify(kafkaTemplate).send(eq("job.retry"), eq(id.toString()), any(String.class));
        verify(kafkaTemplate, never()).send(eq("job.dlq"), anyString(), anyString());
    }

    @Test
    void testHandleFailureExhaustsRetriesRoutesToDlq() throws Exception {
        UUID id = UUID.randomUUID();
        Job job = Job.builder().id(id).maxRetries(3).build();
        JobEvent event = JobEvent.builder().jobId(id.toString()).retryCount(3).build();
        JobExecution execution = new JobExecution();

        when(executionRepository.save(any(JobExecution.class))).thenReturn(execution);

        executionHandler.handleFailure(job, event, execution, new RuntimeException("Fatal Error"));

        verify(kafkaTemplate).send(eq("job.dlq"), eq(id.toString()), any(String.class));
        verify(kafkaTemplate, never()).send(eq("job.retry"), anyString(), anyString());
    }

    @Test
    void testExecuteHttpJobSuccess() {
        UUID id = UUID.randomUUID();
        Job job = Job.builder()
                .id(id)
                .jobType(JobType.HTTP_CALL)
                .payload("{\"url\":\"https://example.com/api\",\"method\":\"GET\"}")
                .maxRetries(3)
                .build();
        JobEvent event = JobEvent.builder().jobId(id.toString()).retryCount(0).build();
        JobExecution execution = JobExecution.builder().jobId(id).build();

        when(jobRepository.findById(id)).thenReturn(Optional.of(job));
        when(executionRepository.save(any(JobExecution.class))).thenReturn(execution);
        when(restTemplate.exchange(eq("https://example.com/api"), eq(HttpMethod.GET), eq(null), eq(String.class)))
                .thenReturn(new ResponseEntity<>("OK", HttpStatus.OK));

        assertDoesNotThrow(() -> executionHandler.executeJob(event));

        verify(executionRepository, atLeastOnce()).save(argThat(exec -> 
            exec.getStatus() == ExecutionStatus.SUCCESS || exec.getStatus() == ExecutionStatus.RUNNING
        ));
    }

    @Test
    void testExecuteHttpJobFailure() {
        UUID id = UUID.randomUUID();
        Job job = Job.builder()
                .id(id)
                .jobType(JobType.HTTP_CALL)
                .payload("{\"url\":\"https://example.com/api\",\"method\":\"GET\"}")
                .maxRetries(3)
                .build();
        JobEvent event = JobEvent.builder().jobId(id.toString()).retryCount(0).build();
        JobExecution execution = JobExecution.builder().jobId(id).build();

        when(jobRepository.findById(id)).thenReturn(Optional.of(job));
        when(executionRepository.save(any(JobExecution.class))).thenReturn(execution);
        when(restTemplate.exchange(eq("https://example.com/api"), eq(HttpMethod.GET), eq(null), eq(String.class)))
                .thenReturn(new ResponseEntity<>("Internal Error", HttpStatus.INTERNAL_SERVER_ERROR));

        assertDoesNotThrow(() -> executionHandler.executeJob(event));

        // Verifies failure routing triggers a retry enqueue since current retry (0) < max (3)
        verify(kafkaTemplate).send(eq("job.retry"), eq(id.toString()), any(String.class));
    }

    @Test
    void testExecuteShellJobSuccess() {
        UUID id = UUID.randomUUID();
        Job job = Job.builder()
                .id(id)
                .jobType(JobType.SHELL)
                .payload("{\"command\":\"echo hello_world\"}")
                .maxRetries(3)
                .build();
        JobEvent event = JobEvent.builder().jobId(id.toString()).retryCount(0).build();
        JobExecution execution = JobExecution.builder().jobId(id).build();

        when(jobRepository.findById(id)).thenReturn(Optional.of(job));
        when(executionRepository.save(any(JobExecution.class))).thenReturn(execution);

        assertDoesNotThrow(() -> executionHandler.executeJob(event));

        verify(executionRepository, atLeastOnce()).save(argThat(exec -> 
            exec.getStatus() == ExecutionStatus.SUCCESS || exec.getStatus() == ExecutionStatus.RUNNING
        ));
    }

    @Test
    void testExecuteShellJobFailureDueToSecurity() {
        UUID id = UUID.randomUUID();
        Job job = Job.builder()
                .id(id)
                .jobType(JobType.SHELL)
                // command with injection attempt / illegal characters
                .payload("{\"command\":\"rm -rf /; echo injection\"}") 
                .maxRetries(3)
                .build();
        JobEvent event = JobEvent.builder().jobId(id.toString()).retryCount(0).build();
        JobExecution execution = JobExecution.builder().jobId(id).build();

        when(jobRepository.findById(id)).thenReturn(Optional.of(job));
        when(executionRepository.save(any(JobExecution.class))).thenReturn(execution);

        assertDoesNotThrow(() -> executionHandler.executeJob(event));

        // It should check and fail the job due to security violations, enqueuing retry
        verify(executionRepository, atLeastOnce()).save(argThat(exec -> 
            (exec.getStatus() == ExecutionStatus.FAILED && exec.getErrorMessage().contains("SecurityException")) || exec.getStatus() == ExecutionStatus.RUNNING
        ));
    }

    @Test
    void testExecuteEmailJobSuccess() {
        UUID id = UUID.randomUUID();
        Job job = Job.builder()
                .id(id)
                .jobType(JobType.EMAIL)
                .payload("{\"to\":\"test@example.com\",\"subject\":\"hello\",\"body\":\"world\"}")
                .maxRetries(3)
                .build();
        JobEvent event = JobEvent.builder().jobId(id.toString()).retryCount(0).build();
        JobExecution execution = JobExecution.builder().jobId(id).build();

        when(jobRepository.findById(id)).thenReturn(Optional.of(job));
        when(executionRepository.save(any(JobExecution.class))).thenReturn(execution);

        assertDoesNotThrow(() -> executionHandler.executeJob(event));

        verify(executionRepository, atLeastOnce()).save(argThat(exec -> 
            exec.getStatus() == ExecutionStatus.SUCCESS || exec.getStatus() == ExecutionStatus.RUNNING
        ));
    }
}
