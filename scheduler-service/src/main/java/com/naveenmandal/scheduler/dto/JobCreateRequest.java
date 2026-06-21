package com.naveenmandal.scheduler.dto;

import com.naveenmandal.scheduler.model.JobType;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class JobCreateRequest {
    private String name;
    private String cronExpression;
    private JobType jobType;
    private String payload; // raw JSON string
    private int maxRetries = 3;
}
