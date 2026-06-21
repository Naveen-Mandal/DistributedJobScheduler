package com.naveenmandal.scheduler.dto;

import com.naveenmandal.common.model.JobStatus;
import com.naveenmandal.common.model.JobType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class JobResponse {
    private UUID id;
    private String name;
    private String cronExpression;
    private JobType jobType;
    private String payload;
    private JobStatus status;
    private int maxRetries;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
