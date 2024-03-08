package io.camunda.zeebe.spring.client.properties;

import io.camunda.zeebe.spring.client.properties.common.Client;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "tasklist.client")
public class TasklistClientConfigurationProperties extends Client {}
