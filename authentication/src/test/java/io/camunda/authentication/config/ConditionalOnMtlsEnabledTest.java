/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.authentication.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

class ConditionalOnMtlsEnabledTest {

  private final ApplicationContextRunner contextRunner =
      new ApplicationContextRunner().withUserConfiguration(TestConfiguration.class);

  @Test
  void shouldCreateBeanWhenMtlsEnabled() {
    contextRunner
        .withPropertyValues("camunda.security.authentication.mtls.enabled=true")
        .run(
            context -> {
              assertThat(context).hasSingleBean(TestService.class);
              assertThat(context.getBean(TestService.class)).isNotNull();
            });
  }

  @Test
  void shouldNotCreateBeanWhenMtlsDisabled() {
    contextRunner
        .withPropertyValues("camunda.security.authentication.mtls.enabled=false")
        .run(
            context -> {
              assertThat(context).doesNotHaveBean(TestService.class);
            });
  }

  @Test
  void shouldNotCreateBeanWhenPropertyMissing() {
    contextRunner.run(
        context -> {
          assertThat(context).doesNotHaveBean(TestService.class);
        });
  }

  @Test
  void shouldNotCreateBeanWhenPropertyEmpty() {
    contextRunner
        .withPropertyValues("camunda.security.authentication.mtls.enabled=")
        .run(
            context -> {
              assertThat(context).doesNotHaveBean(TestService.class);
            });
  }

  @Configuration
  static class TestConfiguration {

    @Bean
    @ConditionalOnMtlsEnabled
    public TestService testService() {
      return new TestService();
    }
  }

  static class TestService {
    // Test service for conditional bean creation
  }
}
