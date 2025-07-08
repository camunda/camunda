/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.application.commons.search;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.search.connect.configuration.DatabaseConfig;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;

public class SearchEngineEnabledConditionTest {

  private final ApplicationContextRunner runner = new ApplicationContextRunner();

  @Test
  public void shouldEnableSearchEngineForElasticsearch() {
    runner
        .withUserConfiguration(TestConfiguration.class)
        .withPropertyValues("camunda.database.type=" + DatabaseConfig.ELASTICSEARCH)
        .run(
            context -> {
              assertThat(context).hasBean("testBean");
            });
  }

  @Test
  public void shouldEnableSearchEngineForOpensearch() {
    runner
        .withUserConfiguration(TestConfiguration.class)
        .withPropertyValues("camunda.database.type=" + DatabaseConfig.OPENSEARCH)
        .run(
            context -> {
              assertThat(context).hasBean("testBean");
            });
  }

  @Test
  public void shouldEnableSearchEngineWhenDatabaseTypeNotSpecified() {
    // Default should be enabled (elasticsearch)
    runner
        .withUserConfiguration(TestConfiguration.class)
        .run(
            context -> {
              assertThat(context).hasBean("testBean");
            });
  }

  @Test
  public void shouldDisableSearchEngineForNone() {
    runner
        .withUserConfiguration(TestConfiguration.class)
        .withPropertyValues("camunda.database.type=" + DatabaseConfig.NONE)
        .run(
            context -> {
              assertThat(context).doesNotHaveBean("testBean");
            });
  }

  @Configuration
  static class TestConfiguration {
    @Bean
    @Conditional(SearchEngineEnabledCondition.class)
    public String testBean() {
      return "test";
    }
  }
}
