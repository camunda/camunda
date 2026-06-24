/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.configuration;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.configuration.conditions.ConditionalOnJfr;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

class OnJfrConditionTest {

  private final ApplicationContextRunner contextRunner = new ApplicationContextRunner();

  @ParameterizedTest(name = "camunda.monitoring.jfr={0} → bean should exist={1}")
  @CsvSource({"true,  true", "false, false"})
  void shouldLoadOrNotLoadBeanWhenOnlyNewPropertyIsSet(
      final String propertyValue, final boolean shouldLoadBean) {
    contextRunner
        .withPropertyValues("camunda.monitoring.jfr=" + propertyValue)
        .withUserConfiguration(TestConfig.class)
        .run(
            context -> {
              if (shouldLoadBean) {
                assertThat(context).hasSingleBean(TestBean.class);
              } else {
                assertThat(context).doesNotHaveBean(TestBean.class);
              }
            });
  }

  @ParameterizedTest(name = "camunda.flags.jfr.metrics={0} → bean should exist={1}")
  @CsvSource({"true,  true", "false, false"})
  void shouldLoadOrNotLoadBeanWhenOnlyLegacyPropertyIsSet(
      final String propertyValue, final boolean shouldLoadBean) {
    contextRunner
        .withPropertyValues("camunda.flags.jfr.metrics=" + propertyValue)
        .withUserConfiguration(TestConfig.class)
        .run(
            context -> {
              if (shouldLoadBean) {
                assertThat(context).hasSingleBean(TestBean.class);
              } else {
                assertThat(context).doesNotHaveBean(TestBean.class);
              }
            });
  }

  @Test
  void shouldUseNewPropertyInFavourOfLegacy() {
    contextRunner
        .withPropertyValues("camunda.monitoring.jfr=true")
        .withPropertyValues("camunda.flags.jfr.metrics=false")
        .withUserConfiguration(TestConfig.class)
        .run(
            context -> {
              assertThat(context).hasSingleBean(TestBean.class);
            });
  }

  @Test
  void shouldNotLoadBeanWhenNewAndLegacyAreNotSet() {
    contextRunner
        .withUserConfiguration(TestConfig.class)
        .run(
            context -> {
              assertThat(context).doesNotHaveBean(TestBean.class);
            });
  }

  @Configuration
  static class TestConfig {
    @Bean
    @ConditionalOnJfr
    TestBean testBean() {
      return new TestBean();
    }
  }

  static class TestBean {}
}
