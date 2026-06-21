package com.naveenmandal.worker.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class JobEvent implements Serializable {
    private String jobId;
    private long timestamp;
    private int retryCount;
}
