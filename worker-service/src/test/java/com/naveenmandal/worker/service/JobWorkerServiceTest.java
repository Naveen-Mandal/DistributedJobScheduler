package com.naveenmandal.worker.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.naveenmandal.common.event.JobEvent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class JobWorkerServiceTest {

    @Mock
    private JobExecutionHandler executionHandler;

    @Spy
    private ObjectMapper objectMapper = new ObjectMapper();

    @InjectMocks
    private JobWorkerService workerService;

    @Test
    void testProcessMainJobDelegatesToHandler() throws Exception {
        UUID jobId = UUID.randomUUID();
        JobEvent event = JobEvent.builder()
                .jobId(jobId.toString())
                .retryCount(0)
                .build();
        String message = objectMapper.writeValueAsString(event);

        workerService.processMainJob(message);

        verify(executionHandler, times(1)).executeJob(argThat(evt -> 
            evt.getJobId().equals(jobId.toString()) && evt.getRetryCount() == 0
        ));
    }

    @Test
    void testProcessRetryJobDelegatesToHandler() throws Exception {
        UUID jobId = UUID.randomUUID();
        JobEvent event = JobEvent.builder()
                .jobId(jobId.toString())
                .retryCount(1)
                .build();
        String message = objectMapper.writeValueAsString(event);

        // processRetryJob has sleep, but retry count = 1 so it sleeps 2 seconds.
        // We can just verify it calls executionHandler.
        workerService.processRetryJob(message);

        verify(executionHandler, times(1)).executeJob(argThat(evt -> 
            evt.getJobId().equals(jobId.toString()) && evt.getRetryCount() == 1
        ));
    }
}
