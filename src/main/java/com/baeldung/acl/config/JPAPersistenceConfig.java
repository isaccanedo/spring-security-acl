package com.isaccanedo.acl.config;

import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.transaction.annotation.EnableTransactionManagement;

@Configuration
@EnableTransactionManagement
@EnableJpaRepositories(basePackages = "com.isaccanedo.acl.persistence.dao")
@PropertySource("classpath:com.isaccanedo.acl.datasource.properties")
@EntityScan(basePackages={ "com.isaccanedo.acl.persistence.entity" })
public class JPAPersistenceConfig {
    
}
