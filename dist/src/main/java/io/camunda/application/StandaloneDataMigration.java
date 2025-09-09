/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.application;

import io.camunda.application.commons.migration.AsyncMigrationsRunner;
import io.camunda.application.commons.migration.MigrationFinishedEvent;
import io.camunda.configuration.UnifiedConfiguration;
import io.camunda.configuration.UnifiedConfigurationHelper;
import io.camunda.configuration.beanoverrides.SearchEngineConnectPropertiesOverride;
import java.io.IOException;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationListener;
import org.springframework.lang.NonNull;

@SpringBootConfiguration(proxyBeanMethods = false)
@EnableAutoConfiguration
public class StandaloneDataMigration implements ApplicationListener<MigrationFinishedEvent> {

  private static ApplicationContext applicationContext;

  public static void main(final String[] args) throws IOException {
    MainSupport.putSystemPropertyIfAbsent(
        "java.util.logging.manager", "org.apache.logging.log4j.jul.LogManager");
    MainSupport.putSystemPropertyIfAbsent(
        "spring.banner.location", "classpath:/assets/camunda_banner.txt");
    MainSupport.putSystemPropertyIfAbsent(
        "spring.config.location",
        "optional:classpath:/,optional:classpath:/config/,optional:file:./,optional:file:./config/");
    MainSupport.putSystemPropertyIfAbsent(
        "management.endpoints.web.exposure.include", "health, prometheus, loggers");
    MainSupport.putSystemPropertyIfAbsent("management.server.port", "9600");

    final SpringApplication application =
        new SpringApplicationBuilder()
            .logStartupInfo(true)
            .web(WebApplicationType.SERVLET)
            .sources(
                StandaloneDataMigration.class,
                AsyncMigrationsRunner.class,
                UnifiedConfigurationHelper.class,
                UnifiedConfiguration.class,
                SearchEngineConnectPropertiesOverride.class)
            .profiles(
                Profile.USAGE_METRIC_MIGRATION.getId(),
                Profile.PROCESS_MIGRATION.getId(),
                Profile.TASK_MIGRATION.getId())
            .addCommandLineProperties(true)
            .build(args);

    applicationContext = application.run(args);
  }

  @Override
  public void onApplicationEvent(final @NonNull MigrationFinishedEvent event) {
    final int exitCode =
        SpringApplication.exit(applicationContext, () -> event.isSuccess() ? 0 : 1);
    System.exit(exitCode);
  }
}
