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
import io.camunda.exporter.config.ExporterConfiguration.IndexSettings;
import io.camunda.exporter.config.ExporterConfiguration.RetentionConfiguration;
import io.camunda.search.connect.configuration.ConnectConfiguration;
import org.springframework.beans.factory.annotation.Value;
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
})
public class SearchEngineDatabaseConfiguration {

  @Bean
  public SearchEngineSchemaInitializer searchEngineSchemaInitializer(
      final SearchEngineConfiguration searchEngineConfiguration,
      @Value("${camunda.database.create-schema:true}") final boolean createSchema) {
    return new SearchEngineSchemaInitializer(searchEngineConfiguration, createSchema);
  }

  @Bean
  public SearchEngineConfiguration searchEngineConfiguration(
      final SearchEngineConnectProperties searchEngineConnectProperties,
      final SearchEngineIndexProperties searchEngineIndexProperties,
      final SearchEngineRetentionProperties searchEngineRetentionProperties) {
    return SearchEngineConfiguration.of(
        b ->
            b.connect(searchEngineConnectProperties)
                .index(searchEngineIndexProperties)
                .retention(searchEngineRetentionProperties));
  }

  @ConfigurationProperties("camunda.database")
  public static final class SearchEngineConnectProperties extends ConnectConfiguration {}

  @ConfigurationProperties("camunda.database.index")
  public static final class SearchEngineIndexProperties extends IndexSettings {}

  @ConfigurationProperties("camunda.database.retention")
  public static final class SearchEngineRetentionProperties extends RetentionConfiguration {}
}
