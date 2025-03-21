/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.application.commons.search;

import io.camunda.application.commons.search.SearchEngineDatabaseConfiguration.SearchEngineConnectProperties;
import io.camunda.application.commons.search.SearchEngineDatabaseConfiguration.SearchEngineIndexProperties;
import io.camunda.application.commons.search.SearchEngineDatabaseConfiguration.SearchEngineRetentionProperties;
import io.camunda.application.commons.search.SearchEngineDatabaseConfiguration.SearchEngineSchemaManagerProperties;
import io.camunda.exporter.config.ExporterConfiguration.IndexSettings;
import io.camunda.exporter.config.ExporterConfiguration.RetentionConfiguration;
import io.camunda.exporter.schema.config.SchemaManagerConfiguration;
import io.camunda.exporter.schema.config.SearchEngineConfiguration;
import io.camunda.search.connect.configuration.ConnectConfiguration;
import io.camunda.zeebe.util.VisibleForTesting;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@Conditional(SearchEngineEnabledCondition.class)
@EnableConfigurationProperties({
  SearchEngineConnectProperties.class,
  SearchEngineIndexProperties.class,
  SearchEngineRetentionProperties.class,
  SearchEngineSchemaManagerProperties.class,
})
public class SearchEngineDatabaseConfiguration {

  @Bean
  public SearchEngineSchemaInitializer searchEngineSchemaInitializer(
      final SearchEngineConfiguration searchEngineConfiguration) {
    return new SearchEngineSchemaInitializer(searchEngineConfiguration);
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
  public static final class SearchEngineIndexProperties extends IndexSettings {}

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
