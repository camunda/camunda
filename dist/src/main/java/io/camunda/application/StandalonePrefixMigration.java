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
import io.camunda.operate.property.OperateProperties;
import io.camunda.search.schema.configuration.SearchEngineConfiguration;
import io.camunda.tasklist.property.TasklistProperties;
import java.io.IOException;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;

@SpringBootConfiguration(proxyBeanMethods = false)
public class StandalonePrefixMigration {
  public static void main(final String[] args) throws IOException {
    // To ensure that debug logging performed using java.util.logging is routed into Log4j 2
    MainSupport.putSystemPropertyIfAbsent(
        "java.util.logging.manager", "org.apache.logging.log4j.jul.LogManager");

    final SpringApplication application =
        new SpringApplicationBuilder()
            .logStartupInfo(true)
            .web(WebApplicationType.NONE)
            .sources(
                StandaloneSchemaManager.class,
                SearchEngineDatabaseConfiguration.class,
                TasklistProperties.class,
                OperateProperties.class)
            .addCommandLineProperties(true)
            .build(args);

    final ConfigurableApplicationContext applicationContext = application.run(args);

    final var operateProperties = applicationContext.getBean(OperateProperties.class);
    final var tasklistProperties = applicationContext.getBean(TasklistProperties.class);
    final var searchEngineConfiguration =
        applicationContext.getBean(SearchEngineConfiguration.class);

    PrefixMigrationHelper.runPrefixMigration(
        operateProperties, tasklistProperties, searchEngineConfiguration);

    System.exit(0);
  }
}
