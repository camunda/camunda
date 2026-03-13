/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.gatekeeper.spring.unit.condition;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.gatekeeper.spring.condition.ConditionalOnProtectedApi;
import io.camunda.gatekeeper.spring.condition.ConditionalOnUnprotectedApi;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

final class ConditionalOnProtectedApiTest {

  private final ApplicationContextRunner contextRunner = new ApplicationContextRunner();

  @Test
  void shouldActivateWhenPropertyNotSet() {
    contextRunner
        .withUserConfiguration(ProtectedConfig.class)
        .run(
            context -> {
              assertThat(context).hasBean("protectedBean");
            });
  }

  @Test
  void shouldActivateWhenPropertyIsFalse() {
    contextRunner
        .withUserConfiguration(ProtectedConfig.class)
        .withPropertyValues("camunda.security.authentication.unprotected-api=false")
        .run(
            context -> {
              assertThat(context).hasBean("protectedBean");
            });
  }

  @Test
  void shouldNotActivateWhenPropertyIsTrue() {
    contextRunner
        .withUserConfiguration(ProtectedConfig.class)
        .withPropertyValues("camunda.security.authentication.unprotected-api=true")
        .run(
            context -> {
              assertThat(context).doesNotHaveBean("protectedBean");
            });
  }

  @Test
  void shouldActivateUnprotectedBeanWhenPropertyIsTrue() {
    contextRunner
        .withUserConfiguration(UnprotectedConfig.class)
        .withPropertyValues("camunda.security.authentication.unprotected-api=true")
        .run(
            context -> {
              assertThat(context).hasBean("unprotectedBean");
            });
  }

  @Test
  void shouldNotActivateUnprotectedBeanWhenPropertyNotSet() {
    contextRunner
        .withUserConfiguration(UnprotectedConfig.class)
        .run(
            context -> {
              assertThat(context).doesNotHaveBean("unprotectedBean");
            });
  }

  @Configuration(proxyBeanMethods = false)
  static class ProtectedConfig {
    @Bean
    @ConditionalOnProtectedApi
    String protectedBean() {
      return "protected";
    }
  }

  @Configuration(proxyBeanMethods = false)
  static class UnprotectedConfig {
    @Bean
    @ConditionalOnUnprotectedApi
    String unprotectedBean() {
      return "unprotected";
    }
  }
}
