package com.naveenmandal.scheduler;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication(scanBasePackages = {"com.naveenmandal.scheduler", "com.naveenmandal.common"})
@EntityScan(basePackages = {"com.naveenmandal.scheduler", "com.naveenmandal.common"})
@EnableJpaRepositories(basePackages = {"com.naveenmandal.scheduler", "com.naveenmandal.common"})
@EnableScheduling
public class SchedulerServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(SchedulerServiceApplication.class, args);
    }
}
