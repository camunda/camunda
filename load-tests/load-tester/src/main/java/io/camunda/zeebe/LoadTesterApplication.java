/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe;

import io.camunda.client.CamundaClient;
import io.camunda.client.CamundaClientConfiguration;
import io.camunda.zeebe.config.LoadTesterProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Lazy;

@SpringBootApplication
@EnableConfigurationProperties(LoadTesterProperties.class)
public class LoadTesterApplication {

  public static void main(final String[] args) {
    SpringApplication.run(LoadTesterApplication.class, args);
  }

  // Has its own connections, but shares the executor service of the configuration and shuts it
  // down on close, so the starter must not run in the same JVM as job workers.
  @Bean(defaultCandidate = false)
  @Lazy
  CamundaClient queryCamundaClient(final CamundaClientConfiguration configuration) {
    return CamundaClient.newClient(configuration);
  }
}
