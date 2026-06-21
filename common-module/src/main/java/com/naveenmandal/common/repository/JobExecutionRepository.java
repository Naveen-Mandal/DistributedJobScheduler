package com.naveenmandal.common.repository;

import com.naveenmandal.common.model.JobExecution;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface JobExecutionRepository extends JpaRepository<JobExecution, UUID> {
    List<JobExecution> findByJobIdOrderByStartedAtDesc(UUID jobId);
    
    List<JobExecution> findAllByOrderByStartedAtDesc(Pageable pageable);

    @Query("SELECT COUNT(e) FROM JobExecution e WHERE CAST(e.startedAt as date) = CURRENT_DATE")
    long countTodayExecutions();
}
