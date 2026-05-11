/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.rest.controller.system;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.core.env.Environment;
import org.springframework.mock.env.MockEnvironment;

/**
 * Test configuration that provides proper Environment bean for SystemController tests. This is
 * needed because WebMvcTest doesn't load the full application context including application.yml
 * properties.
 */
@TestConfiguration
public class SystemControllerTestConfiguration {

  @Bean
  @Primary
  public Environment testEnvironment() {
    final MockEnvironment env = new MockEnvironment();

    // Default values for SystemController constructor @Value parameters
    env.setProperty("camunda.webapp.enterprise", "false");
    env.setProperty("camunda.webapps.login-delegated", "false");
    env.setProperty("spring.servlet.multipart.max-request-size", "4MB");
    // Don't set cloud properties - let them default to null

    // Default component configuration
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
