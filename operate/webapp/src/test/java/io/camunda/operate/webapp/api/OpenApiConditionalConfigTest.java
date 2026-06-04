/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.webapp.api;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

class OpenApiConditionalConfigTest {

  @Nested
  @SpringJUnitConfig
  @ContextConfiguration(classes = OpenApiConfig.class)
  @TestPropertySource(properties = "springdoc.api-docs.enabled=true")
  class WhenEnabled {
    @Autowired ApplicationContext context;

    @Test
    void shouldRegisterApiV1BeanWhenApiDocsEnabled() {
      assertThat(context.containsBean("apiV1")).isTrue();
    }
  }

  @Nested
  @SpringJUnitConfig
  @ContextConfiguration(classes = OpenApiConfig.class)
  @TestPropertySource(properties = "springdoc.api-docs.enabled=false")
  class WhenDisabled {
    @Autowired ApplicationContext context;

    @Test
    void shouldNotRegisterApiV1BeanWhenApiDocsDisabled() {
      assertThat(context.containsBean("apiV1")).isFalse();
    }
  }
}
