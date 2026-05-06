/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.webapptest;

import io.camunda.security.configuration.SecurityConfiguration;
import io.camunda.webapp.WebappModuleConfiguration;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;

/**
 * Minimal Spring Boot bootstrap used by integration tests in this module (e.g., {@code
 * WebappCacheHeadersIT}, {@code ClusterConfigurationControllerIT}).
 */
@SpringBootConfiguration
@EnableAutoConfiguration
@Import(WebappModuleConfiguration.class)
public class TestWebappApplication {

  /**
   * Provides a default {@link SecurityConfiguration} bean with all-default (self-managed) values so
   * that {@link io.camunda.webapp.rest.ClusterConfigurationController} can be wired up in the test
   * context without a full {@code CamundaSecurityConfiguration} setup.
   */
  @Bean
  SecurityConfiguration securityConfiguration() {
    return new SecurityConfiguration();
  }
}
