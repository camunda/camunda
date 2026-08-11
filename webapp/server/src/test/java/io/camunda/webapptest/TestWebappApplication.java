/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.webapptest;

import io.camunda.webapp.WebappModuleConfiguration;
import io.camunda.zeebe.gateway.rest.config.PhysicalTenantWebMvcConfig;
import io.camunda.zeebe.gateway.rest.controller.PhysicalTenantFilter;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;

/**
 * Minimal Spring Boot bootstrap used by integration tests in this module (e.g., {@code
 * WebappCacheHeadersIT}).
 */
@SpringBootConfiguration
@EnableAutoConfiguration
@Import({WebappModuleConfiguration.class, PhysicalTenantWebMvcConfig.class})
public class TestWebappApplication {

  @Bean
  FilterRegistrationBean<PhysicalTenantFilter> physicalTenantFilter() {
    final var registration = new FilterRegistrationBean<>(new PhysicalTenantFilter());
    registration.setOrder(Integer.MIN_VALUE);
    return registration;
  }
}
