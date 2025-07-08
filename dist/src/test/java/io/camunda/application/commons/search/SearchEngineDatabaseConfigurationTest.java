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
import io.camunda.search.schema.config.SearchEngineConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

public class SearchEngineDatabaseConfigurationTest {

  private final ApplicationContextRunner runner = new ApplicationContextRunner();

  @Test
  public void shouldDisableSchemaCreationWhenDatabaseTypeIsNone() {
    runner
        .withUserConfiguration(TestConfiguration.class)
        .withPropertyValues(
            "camunda.database.type=" + DatabaseConfig.NONE,
            "camunda.database.schema-manager.createSchema=true" // explicit true should be overridden
            )
        .run(context -> {
          assertThat(context).hasSingleBean(SearchEngineConfiguration.class);
          final var config = context.getBean(SearchEngineConfiguration.class);
          assertThat(config.schemaManager().isCreateSchema()).isFalse();
        });
  }

  @Test
  public void shouldPreserveSchemaCreationWhenDatabaseTypeIsElasticsearch() {
    runner
        .withUserConfiguration(TestConfiguration.class)
        .withPropertyValues(
            "camunda.database.type=" + DatabaseConfig.ELASTICSEARCH,
            "camunda.database.schema-manager.createSchema=true"
            )
        .run(context -> {
          assertThat(context).hasSingleBean(SearchEngineConfiguration.class);
          final var config = context.getBean(SearchEngineConfiguration.class);
          assertThat(config.schemaManager().isCreateSchema()).isTrue();
        });
  }

  @Test
  public void shouldPreserveSchemaCreationWhenDatabaseTypeIsOpensearch() {
    runner
        .withUserConfiguration(TestConfiguration.class)
        .withPropertyValues(
            "camunda.database.type=" + DatabaseConfig.OPENSEARCH,
            "camunda.database.schema-manager.createSchema=false"
            )
        .run(context -> {
          assertThat(context).hasSingleBean(SearchEngineConfiguration.class);
          final var config = context.getBean(SearchEngineConfiguration.class);
          assertThat(config.schemaManager().isCreateSchema()).isFalse();
        });
  }

  @Configuration
  static class TestConfiguration extends SearchEngineDatabaseConfiguration {
    
    @Bean
    @Override
    public SearchEngineConfiguration searchEngineConfiguration(
        final SearchEngineConnectProperties searchEngineConnectProperties,
        final SearchEngineIndexProperties searchEngineIndexProperties,
        final SearchEngineRetentionProperties searchEngineRetentionProperties,
        final SearchEngineSchemaManagerProperties searchEngineSchemaManagerProperties,
        final Environment environment) {
      return super.searchEngineConfiguration(
          searchEngineConnectProperties,
          searchEngineIndexProperties,
          searchEngineRetentionProperties,
          searchEngineSchemaManagerProperties,
          environment);
    }
  }
}