/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.application.commons.search;

import static org.springframework.context.annotation.Bean.Bootstrap.BACKGROUND;

import io.camunda.application.commons.search.SearchEngineDatabaseConfiguration.SearchEngineConnectProperties;
import io.camunda.application.commons.search.SearchEngineDatabaseConfiguration.SearchEngineIndexProperties;
import io.camunda.application.commons.search.SearchEngineDatabaseConfiguration.SearchEngineRetentionProperties;
import io.camunda.application.commons.search.SearchEngineDatabaseConfiguration.SearchEngineSchemaManagerProperties;
import io.camunda.search.connect.configuration.ConnectConfiguration;
import io.camunda.search.schema.config.IndexConfiguration;
import io.camunda.search.schema.config.RetentionConfiguration;
import io.camunda.search.schema.config.SchemaManagerConfiguration;
import io.camunda.search.schema.config.SearchEngineConfiguration;
import io.camunda.zeebe.util.VisibleForTesting;
import io.micrometer.core.instrument.MeterRegistry;
import java.io.IOException;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.DependsOn;

@Configuration(proxyBeanMethods = false)
@Conditional(SearchEngineEnabledCondition.class)
@EnableConfigurationProperties({
  SearchEngineConnectProperties.class,
  SearchEngineIndexProperties.class,
  SearchEngineRetentionProperties.class,
  SearchEngineSchemaManagerProperties.class,
})
public class SearchEngineDatabaseConfiguration {

  @Bean(bootstrap = BACKGROUND)
  @DependsOn({"searchEngineConfiguration", "prometheusMeterRegistry"})
  public SearchEngineSchemaInitializer searchEngineSchemaInitializer(
      final SearchEngineConfiguration searchEngineConfiguration, final MeterRegistry meterRegistry)
      throws IOException {
    final var searchEngineSchemaInitializer =
        new SearchEngineSchemaInitializer(searchEngineConfiguration, meterRegistry);
    searchEngineSchemaInitializer.afterPropertiesSet();
    return searchEngineSchemaInitializer;
  }

  @Bean("bootstrapExecutor")
  public Executor bootstrapExecutor() {
    return Executors.newSingleThreadExecutor();
  }

  @Bean
  public SearchEngineConfiguration searchEngineConfiguration(
      final SearchEngineConnectProperties searchEngineConnectProperties,
      final SearchEngineIndexProperties searchEngineIndexProperties,
      final SearchEngineRetentionProperties searchEngineRetentionProperties,
      final SearchEngineSchemaManagerProperties searchEngineSchemaManagerProperties) {
    return SearchEngineConfiguration.of(
        b ->
            b.connect(searchEngineConnectProperties)
                .index(searchEngineIndexProperties)
                .retention(searchEngineRetentionProperties)
                .schemaManager(searchEngineSchemaManagerProperties));
  }

  @ConfigurationProperties("camunda.database")
  public static final class SearchEngineConnectProperties extends ConnectConfiguration {}

  @ConfigurationProperties("camunda.database.index")
  public static final class SearchEngineIndexProperties extends IndexConfiguration {}

  @ConfigurationProperties("camunda.database.retention")
  public static final class SearchEngineRetentionProperties extends RetentionConfiguration {}

  @ConfigurationProperties("camunda.database.schema-manager")
  public static final class SearchEngineSchemaManagerProperties extends SchemaManagerConfiguration {
    @VisibleForTesting
    public static final String CREATE_SCHEMA_PROPERTY =
        "camunda.database.schema-manager.createSchema";

    @VisibleForTesting
    public static final String CREATE_SCHEMA_ENV_VAR =
        "CAMUNDA_DATABASE_SCHEMA_MANAGER_CREATE_SCHEMA";
  }
}
