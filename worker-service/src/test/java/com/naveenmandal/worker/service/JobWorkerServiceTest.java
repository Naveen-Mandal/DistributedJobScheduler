package com.naveenmandal.worker.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.naveenmandal.worker.event.JobEvent;
import com.naveenmandal.worker.model.Job;
import com.naveenmandal.worker.model.JobExecution;
import com.naveenmandal.worker.repository.JobExecutionRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class JobWorkerServiceTest {

    @Mock
    private JobExecutionRepository executionRepository;

    @Mock
    private KafkaTemplate<String, Object> kafkaTemplate;

    @Spy
    private ObjectMapper objectMapper = new ObjectMapper();

    @InjectMocks
    private JobWorkerService workerService;

    @Test
    void testHandleFailureTriggersRetry() throws Exception {
        // Arrange
        UUID id = UUID.randomUUID();
        Job job = Job.builder()
                .id(id)
                .maxRetries(3)
                .build();
        
        JobEvent event = JobEvent.builder()
                .jobId(id.toString())
                .retryCount(1) // Under max retries
                .build();
        
        JobExecution execution = new JobExecution();
        when(executionRepository.save(any(JobExecution.class))).thenReturn(execution);

        // Act
        workerService.handleFailure(job, event, execution, new RuntimeException("Transient Connection Error"));

        // Assert
        verify(kafkaTemplate).send(eq("job.retry"), eq(id.toString()), any(String.class));
        verify(kafkaTemplate, never()).send(eq("job.dlq"), anyString(), anyString());
    }

    @Test
    void testHandleFailureExhaustsRetriesRoutesToDlq() throws Exception {
        // Arrange
        UUID id = UUID.randomUUID();
        Job job = Job.builder()
                .id(id)
                .maxRetries(3)
                .build();
        
        JobEvent event = JobEvent.builder()
                .jobId(id.toString())
                .retryCount(3) // Reached max retries
                .build();
        
        JobExecution execution = new JobExecution();
        when(executionRepository.save(any(JobExecution.class))).thenReturn(execution);

        // Act
        workerService.handleFailure(job, event, execution, new RuntimeException("Fatal Auth Error"));

        // Assert
        verify(kafkaTemplate).send(eq("job.dlq"), eq(id.toString()), any(String.class));
        verify(kafkaTemplate, never()).send(eq("job.retry"), anyString(), anyString());
    }
}
