package com.naveenmandal.scheduler.repository;

import com.naveenmandal.scheduler.model.Job;
import com.naveenmandal.scheduler.model.JobStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface JobRepository extends JpaRepository<Job, UUID> {
    List<Job> findByStatus(JobStatus status);
}
