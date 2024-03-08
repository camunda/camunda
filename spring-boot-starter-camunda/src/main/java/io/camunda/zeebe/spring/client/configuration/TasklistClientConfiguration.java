package io.camunda.zeebe.spring.client.configuration;

import io.camunda.common.auth.Authentication;
import io.camunda.zeebe.spring.client.properties.TasklistClientConfigurationProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@ConditionalOnProperty(
    prefix = "tasklist.client",
    name = "enabled",
    havingValue = "true",
    matchIfMissing = false)
@EnableConfigurationProperties(TasklistClientConfigurationProperties.class)
public class TasklistClientConfiguration {

  @Autowired Authentication authentication;

  // TODO: Declare bean for Tasklist
}
