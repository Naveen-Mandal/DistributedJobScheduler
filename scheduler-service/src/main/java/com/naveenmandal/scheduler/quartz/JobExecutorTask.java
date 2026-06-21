package com.naveenmandal.scheduler.quartz;

import com.naveenmandal.common.event.JobEvent;
import com.naveenmandal.scheduler.service.DistributedLockService;
import lombok.extern.slf4j.Slf4j;
import org.quartz.DisallowConcurrentExecution;
import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
@Slf4j
@DisallowConcurrentExecution // Quartz level safety for concurrent fires of the same job definition
public class JobExecutorTask implements Job {

    @Autowired
    private DistributedLockService lockService;

    @Autowired
    private KafkaTemplate<String, JobEvent> kafkaTemplate;

    @Override
    public void execute(JobExecutionContext context) throws JobExecutionException {
        String jobId = context.getMergedJobDataMap().getString("jobId");
        log.info("Quartz trigger fired on this node for job: {}", jobId);

        // Double safety check: Quartz JDBC clustering should select 1 node to trigger.
        // We use DistributedLockService via Redis as a secondary lock to guarantee single fire.
        boolean locked = lockService.tryLockAndExecute(jobId, () -> {
            JobEvent event = JobEvent.builder()
                    .jobId(jobId)
                    .timestamp(System.currentTimeMillis())
                    .retryCount(0)
                    .build();
            
            // Push event to Kafka job.queue topic
            kafkaTemplate.send("job.queue", jobId, event);
            log.info("Job {} successfully pushed to Kafka topic 'job.queue'", jobId);
        });

        if (!locked) {
            log.info("Skipped executing Quartz trigger for job {} as another node acquired the lock.", jobId);
        }
    }
}
