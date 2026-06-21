package com.naveenmandal.worker;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.Bean;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.web.client.RestTemplate;

@SpringBootApplication(scanBasePackages = {"com.naveenmandal.worker", "com.naveenmandal.common"})
@EntityScan(basePackages = {"com.naveenmandal.worker", "com.naveenmandal.common"})
@EnableJpaRepositories(basePackages = {"com.naveenmandal.worker", "com.naveenmandal.common"})
public class WorkerServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(WorkerServiceApplication.class, args);
    }

    @Bean
    public RestTemplate restTemplate() {
        return new RestTemplate();
    }
}
