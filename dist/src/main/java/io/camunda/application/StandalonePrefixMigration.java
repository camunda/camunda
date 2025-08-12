/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.application;

import io.camunda.application.commons.migration.PrefixMigrationHelper;
import io.camunda.application.commons.search.SearchEngineDatabaseConfiguration;
import io.camunda.configuration.UnifiedConfiguration;
import io.camunda.configuration.UnifiedConfigurationHelper;
import io.camunda.configuration.beanoverrides.OperatePropertiesOverride;
import io.camunda.configuration.beanoverrides.SearchEngineConnectPropertiesOverride;
import io.camunda.configuration.beanoverrides.TasklistPropertiesOverride;
import io.camunda.operate.property.OperateProperties;
import io.camunda.search.connect.configuration.ConnectConfiguration;
import io.camunda.tasklist.property.TasklistProperties;
import java.io.IOException;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.builder.SpringApplicationBuilder;

@SpringBootConfiguration(proxyBeanMethods = false)
@EnableAutoConfiguration
public class StandalonePrefixMigration implements CommandLineRunner {

  private final ConnectConfiguration connectConfiguration;
  private final TasklistProperties tasklistProperties;
  private final OperateProperties operateProperties;

  public StandalonePrefixMigration(
      final ConnectConfiguration connectConfiguration,
      final TasklistProperties tasklistProperties,
      final OperateProperties operateProperties) {
    this.connectConfiguration = connectConfiguration;
    this.tasklistProperties = tasklistProperties;
    this.operateProperties = operateProperties;
  }

  public static void main(final String[] args) throws IOException {
    // To ensure that debug logging performed using java.util.logging is routed into Log4j 2
    MainSupport.putSystemPropertyIfAbsent(
        "java.util.logging.manager", "org.apache.logging.log4j.jul.LogManager");

    final SpringApplication application =
        new SpringApplicationBuilder()
            .logStartupInfo(true)
            .web(WebApplicationType.NONE)
            .sources(
                StandalonePrefixMigration.class,
                SearchEngineDatabaseConfiguration.class,
                UnifiedConfiguration.class,
                UnifiedConfigurationHelper.class,
                TasklistPropertiesOverride.class,
                OperatePropertiesOverride.class,
                SearchEngineConnectPropertiesOverride.class)
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
}
