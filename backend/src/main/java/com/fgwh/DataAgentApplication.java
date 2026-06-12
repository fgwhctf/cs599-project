package com.fgwh;

import com.fgwh.config.DataAgentProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties(DataAgentProperties.class)
public class DataAgentApplication {
    public static void main(String[] args) {
        SpringApplication.run(DataAgentApplication.class, args);
    }
}
