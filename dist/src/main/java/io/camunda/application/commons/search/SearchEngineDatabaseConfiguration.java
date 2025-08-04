/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.application.commons.search;

import io.camunda.application.commons.condition.ConditionalOnSecondaryStorageType;
import io.camunda.application.commons.search.SearchEngineDatabaseConfiguration.SearchEngineConnectProperties;
import io.camunda.application.commons.search.SearchEngineDatabaseConfiguration.SearchEngineIndexProperties;
import io.camunda.application.commons.search.SearchEngineDatabaseConfiguration.SearchEngineRetentionProperties;
import io.camunda.application.commons.search.SearchEngineDatabaseConfiguration.SearchEngineSchemaManagerProperties;
import io.camunda.search.connect.configuration.ConnectConfiguration;
import io.camunda.search.connect.configuration.DatabaseConfig;
import io.camunda.search.connect.configuration.DatabaseType;
import io.camunda.search.schema.config.IndexConfiguration;
import io.camunda.search.schema.config.RetentionConfiguration;
import io.camunda.search.schema.config.SchemaManagerConfiguration;
import io.camunda.search.schema.config.SearchEngineConfiguration;
import io.camunda.zeebe.broker.Broker;
import io.camunda.zeebe.broker.system.configuration.BrokerCfg;
import io.camunda.zeebe.util.VisibleForTesting;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@ConditionalOnSecondaryStorageType({DatabaseConfig.ELASTICSEARCH, DatabaseConfig.OPENSEARCH})
@EnableConfigurationProperties({
  SearchEngineConnectProperties.class,
  SearchEngineIndexProperties.class,
  SearchEngineRetentionProperties.class,
  SearchEngineSchemaManagerProperties.class,
})
public class SearchEngineDatabaseConfiguration {

  @Bean
  public SearchEngineSchemaInitializer searchEngineSchemaInitializer(
      final SearchEngineConfiguration searchEngineConfiguration,
      final MeterRegistry meterRegistry,
      @Autowired(required = false)
          final Broker broker, // if present, then it will ensure that the broker is started first
      @Autowired(required = false) final BrokerCfg brokerCfg) {
    final boolean isGatewayEnabled = brokerCfg == null || brokerCfg.getGateway().isEnable();
    return new SearchEngineSchemaInitializer(
        searchEngineConfiguration, meterRegistry, isGatewayEnabled);
  }

  @Bean
  public SearchEngineConfiguration searchEngineConfiguration(
      final SearchEngineConnectProperties searchEngineConnectProperties,
      final SearchEngineIndexProperties searchEngineIndexProperties,
      final SearchEngineRetentionProperties searchEngineRetentionProperties,
      final SearchEngineSchemaManagerProperties searchEngineSchemaManagerProperties) {

    // Override schema creation if database type is "none"
    final DatabaseType databaseType = searchEngineConnectProperties.getTypeEnum();
    if (DatabaseConfig.NONE.equals(databaseType.name())) {
      searchEngineSchemaManagerProperties.setCreateSchema(false);
    }

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
