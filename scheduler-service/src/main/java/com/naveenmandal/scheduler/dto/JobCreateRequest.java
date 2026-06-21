package com.naveenmandal.scheduler.dto;

import com.naveenmandal.scheduler.model.JobType;
import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class JobCreateRequest {
    @NotBlank(message = "Job name cannot be blank")
    @Size(max = 255, message = "Job name must be less than 255 characters")
    private String name;

    @NotBlank(message = "Cron expression cannot be blank")
    private String cronExpression;

    @NotNull(message = "Job type is required")
    private JobType jobType;

    @NotBlank(message = "Payload is required")
    private String payload; // raw JSON string

    @Min(value = 0, message = "Max retries cannot be negative")
    @Max(value = 10, message = "Max retries cannot exceed 10")
    private int maxRetries = 3;
}
