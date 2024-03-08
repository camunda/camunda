package io.camunda.zeebe.spring.client.properties;

import io.camunda.zeebe.spring.client.properties.common.Client;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "optimize.client")
public class OptimizeClientConfigurationProperties extends Client {}
