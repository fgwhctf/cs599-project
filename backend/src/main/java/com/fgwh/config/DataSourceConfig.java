package com.fgwh.config;

import javax.sql.DataSource;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

@Configuration
public class DataSourceConfig {

    @Bean
    DataSource metaDataSource(DataAgentProperties properties) {
        var meta = properties.getDatasource().getMeta();
        return DataSourceBuilder.create()
                .driverClassName(meta.getDriverClassName())
                .url(meta.getJdbcUrl())
                .username(meta.getUsername())
                .password(meta.getPassword())
                .build();
    }

    @Bean
    DataSource dwDataSource(DataAgentProperties properties) {
        var dw = properties.getDatasource().getDw();
        return DataSourceBuilder.create()
                .driverClassName(dw.getDriverClassName())
                .url(dw.getJdbcUrl())
                .username(dw.getUsername())
                .password(dw.getPassword())
                .build();
    }

    @Bean
    JdbcTemplate metaJdbcTemplate(@Qualifier("metaDataSource") DataSource dataSource) {
        return new JdbcTemplate(dataSource);
    }

    @Bean
    JdbcTemplate dwJdbcTemplate(@Qualifier("dwDataSource") DataSource dataSource) {
        return new JdbcTemplate(dataSource);
    }
}
