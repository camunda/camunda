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
import java.io.IOException;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationListener;
import org.springframework.lang.NonNull;

@SpringBootConfiguration(proxyBeanMethods = false)
public class StandaloneUsageMetricMigration implements ApplicationListener<MigrationFinishedEvent> {

  private static ApplicationContext applicationContext;

  public static void main(final String[] args) throws IOException {
    // To ensure that debug logging performed using java.util.logging is routed into Log4j 2
    MainSupport.putSystemPropertyIfAbsent(
        "java.util.logging.manager", "org.apache.logging.log4j.jul.LogManager");
    MainSupport.putSystemPropertyIfAbsent(
        "spring.banner.location", "classpath:/assets/camunda_banner.txt");

    final SpringApplication application =
        new SpringApplicationBuilder()
            .logStartupInfo(true)
            .web(WebApplicationType.NONE)
            .sources(StandaloneUsageMetricMigration.class, AsyncMigrationsRunner.class)
            .profiles(Profile.USAGE_METRIC_MIGRATION.getId())
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
