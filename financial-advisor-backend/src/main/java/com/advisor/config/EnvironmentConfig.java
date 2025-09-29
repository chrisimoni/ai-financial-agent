package com.advisor.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.context.annotation.PropertySource;

@Configuration
@PropertySource("classpath:.env")
@Profile("!prod")
public class EnvironmentConfig {
}
