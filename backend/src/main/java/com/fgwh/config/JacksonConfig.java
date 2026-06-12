package com.fgwh.config;

import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class JacksonConfig {

    @Bean
    YAMLMapper yamlMapper() {
        return YAMLMapper.builder().build();
    }
}
