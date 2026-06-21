package com.naveenmandal.scheduler.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.KafkaAdmin;

import java.util.HashMap;
import java.util.Map;

@Configuration
public class KafkaConfig {

    @Value("${spring.kafka.bootstrap-servers}")
    private String bootstrapServers;

    @Bean
    public KafkaAdmin kafkaAdmin() {
        Map<String, Object> configs = new HashMap<>();
        configs.put(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        return new KafkaAdmin(configs);
    }

    @Bean
    public NewTopic jobQueueTopic() {
        return TopicBuilder.name("job.queue")
                .partitions(3)
                .replicas(1)
                .build();
    }

    @Bean
    public NewTopic jobRetryTopic() {
        return TopicBuilder.name("job.retry")
                .partitions(3)
                .replicas(1)
                .build();
    }

    @Bean
    public NewTopic jobDlqTopic() {
        return TopicBuilder.name("job.dlq")
                .partitions(1)
                .replicas(1)
                .build();
    }

    @Bean
    public NewTopic jobExecutionUpdatesTopic() {
        return TopicBuilder.name("job.execution.updates")
                .partitions(1)
                .replicas(1)
                .build();
    }
}
