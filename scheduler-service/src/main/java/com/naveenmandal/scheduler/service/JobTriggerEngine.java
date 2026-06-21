package com.naveenmandal.scheduler.service;

import com.naveenmandal.scheduler.model.Job;
import com.naveenmandal.scheduler.quartz.JobExecutorTask;
import lombok.extern.slf4j.Slf4j;
import org.quartz.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@Slf4j
public class JobTriggerEngine {

    @Autowired
    private Scheduler scheduler;

    /**
     * Schedules or reschedules a job using Quartz CronTrigger.
     */
    public void scheduleJob(Job job) throws SchedulerException {
        JobKey jobKey = JobKey.jobKey(job.getId().toString(), "DEFAULT");
        TriggerKey triggerKey = TriggerKey.triggerKey("trigger:" + job.getId().toString(), "DEFAULT");

        // Delete existing job/trigger before recreating (helps with updates to Cron expressions)
        if (scheduler.checkExists(jobKey)) {
            scheduler.deleteJob(jobKey);
        }

        JobDetail jobDetail = JobBuilder.newJob(JobExecutorTask.class)
                .withIdentity(jobKey)
                .usingJobData("jobId", job.getId().toString())
                .storeDurably()
                .build();

        CronTrigger trigger = TriggerBuilder.newTrigger()
                .withIdentity(triggerKey)
                .withSchedule(CronScheduleBuilder.cronSchedule(job.getCronExpression())
                        .withMisfireHandlingInstructionDoNothing()) // Prevent massive wave of back-fires on startup
                .build();

        scheduler.scheduleJob(jobDetail, trigger);
        log.info("Dynamic job trigger registered in Quartz: [JobId: {}, Cron: {}]", job.getId(), job.getCronExpression());
    }

    /**
     * Pauses the Quartz trigger execution for a job.
     */
    public void pauseJob(UUID jobId) throws SchedulerException {
        JobKey jobKey = JobKey.jobKey(jobId.toString(), "DEFAULT");
        if (scheduler.checkExists(jobKey)) {
            scheduler.pauseJob(jobKey);
            log.info("Quartz trigger paused for JobId: {}", jobId);
        } else {
            log.warn("Cannot pause: JobId {} not found in Quartz", jobId);
        }
    }

    /**
     * Resumes execution of a paused Quartz trigger.
     */
    public void resumeJob(UUID jobId) throws SchedulerException {
        JobKey jobKey = JobKey.jobKey(jobId.toString(), "DEFAULT");
        if (scheduler.checkExists(jobKey)) {
            scheduler.resumeJob(jobKey);
            log.info("Quartz trigger resumed for JobId: {}", jobId);
        } else {
            log.warn("Cannot resume: JobId {} not found in Quartz", jobId);
        }
    }

    /**
     * Removes a job from the Quartz scheduler.
     */
    public void deleteJob(UUID jobId) throws SchedulerException {
        JobKey jobKey = JobKey.jobKey(jobId.toString(), "DEFAULT");
        if (scheduler.checkExists(jobKey)) {
            scheduler.deleteJob(jobKey);
            log.info("Quartz trigger deleted for JobId: {}", jobId);
        } else {
            log.warn("Cannot delete trigger: JobId {} not found in Quartz", jobId);
        }
    }
}
