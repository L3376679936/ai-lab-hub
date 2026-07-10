package com.ailab.bootstrap;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@SpringBootApplication(scanBasePackages = "com.ailab")
@EntityScan(basePackages = "com.ailab")
@EnableJpaRepositories(basePackages = "com.ailab")
public class AiLabHubApplication {
    public static void main(String[] args) {
        SpringApplication.run(AiLabHubApplication.class, args);
    }
}
