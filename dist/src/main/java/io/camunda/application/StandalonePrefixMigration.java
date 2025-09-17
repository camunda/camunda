/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.application;

import static io.camunda.application.commons.search.SearchEngineDatabaseConfiguration.SearchEngineSchemaManagerProperties.CREATE_SCHEMA_PROPERTY;

import io.camunda.application.StandalonePrefixMigration.OperateIndexPrefixPropertiesOverride;
import io.camunda.application.StandalonePrefixMigration.TasklistIndexPrefixPropertiesOverride;
import io.camunda.application.commons.migration.PrefixMigrationHelper;
import io.camunda.application.commons.search.SearchEngineDatabaseConfiguration;
import io.camunda.configuration.UnifiedConfiguration;
import io.camunda.configuration.UnifiedConfigurationHelper;
import io.camunda.configuration.beanoverrides.SearchEngineConnectPropertiesOverride;
import io.camunda.search.connect.configuration.ConnectConfiguration;
import java.io.IOException;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootConfiguration(proxyBeanMethods = false)
@EnableAutoConfiguration
@EnableConfigurationProperties(
    value = {
      TasklistIndexPrefixPropertiesOverride.class,
      OperateIndexPrefixPropertiesOverride.class
    })
public class StandalonePrefixMigration implements CommandLineRunner {

  private final ConnectConfiguration connectConfiguration;
  private final TasklistIndexPrefixPropertiesOverride tasklistProperties;
  private final OperateIndexPrefixPropertiesOverride operateProperties;

  public StandalonePrefixMigration(
      final ConnectConfiguration connectConfiguration,
      final TasklistIndexPrefixPropertiesOverride tasklistProperties,
      final OperateIndexPrefixPropertiesOverride operateProperties) {
    this.connectConfiguration = connectConfiguration;
    this.tasklistProperties = tasklistProperties;
    this.operateProperties = operateProperties;
  }

  public static void main(final String[] args) throws IOException {
    // To ensure that debug logging performed using java.util.logging is routed into Log4j 2
    MainSupport.putSystemPropertyIfAbsent(
        "java.util.logging.manager", "org.apache.logging.log4j.jul.LogManager");

    // Disable 8.8 Schema Manager during the prefix migration
    MainSupport.putSystemPropertyIfAbsent(CREATE_SCHEMA_PROPERTY, "false");

    final SpringApplication application =
        new SpringApplicationBuilder()
            .logStartupInfo(true)
            .web(WebApplicationType.NONE)
            .sources(
                // Unified Configuration classes
                UnifiedConfiguration.class,
                UnifiedConfigurationHelper.class,
                SearchEngineConnectPropertiesOverride.class,
                // ---
                StandalonePrefixMigration.class,
                SearchEngineDatabaseConfiguration.class)
            .addCommandLineProperties(true)
            .build(args);

    application.run(args);

    System.exit(0);
  }

  @Override
  public void run(final String... args) throws Exception {
    PrefixMigrationHelper.runPrefixMigration(
        operateProperties, tasklistProperties, connectConfiguration);
  }

  ///  Override {@link io.camunda.tasklist.property.TasklistProperties} bean which is validated
  /// by the Unified Configuration and gets overriden by the {@link
  /// io.camunda.configuration.SecondaryStorage} new prefix configuration
  @ConfigurationProperties("camunda.tasklist")
  public record TasklistIndexPrefixPropertiesOverride(
      String elasticsearchIndexPrefix, String opensearchIndexPrefix) {}

  ///  Override {@link io.camunda.operate.property.OperateProperties} bean which is validated
  /// by the Unified Configuration and gets overriden by the {@link
  /// io.camunda.configuration.SecondaryStorage} new prefix configuration
  @ConfigurationProperties("camunda.operate")
  public record OperateIndexPrefixPropertiesOverride(
      String elasticsearchIndexPrefix, String opensearchIndexPrefix) {}
}
