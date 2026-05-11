/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.rest.controller.system;

import io.camunda.zeebe.gateway.rest.config.WebappConfiguration;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.core.env.Environment;
import org.springframework.mock.env.MockEnvironment;

@TestConfiguration
public class SystemControllerTestConfiguration {

  @Bean
  @Primary
  public WebappConfiguration testWebappConfiguration() {
    return new WebappConfiguration();
  }

  @Bean
  @Primary
  public Environment testEnvironment() {
    final MockEnvironment env = new MockEnvironment();

    env.setProperty("spring.servlet.multipart.max-request-size", "4MB");

    env.setProperty("camunda.admin.webapp-enabled", "true");
    env.setProperty("camunda.webapps.admin.enabled", "true");
    env.setProperty("camunda.webapps.admin.ui-enabled", "true");
    env.setProperty("camunda.operate.webapp-enabled", "true");
    env.setProperty("camunda.webapps.operate.enabled", "true");
    env.setProperty("camunda.webapps.operate.ui-enabled", "true");
    env.setProperty("camunda.tasklist.webapp-enabled", "true");
    env.setProperty("camunda.webapps.tasklist.enabled", "true");
    env.setProperty("camunda.webapps.tasklist.ui-enabled", "true");

    return env;
  }
}
