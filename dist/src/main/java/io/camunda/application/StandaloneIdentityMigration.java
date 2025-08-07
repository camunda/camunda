/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.application;

import io.camunda.application.commons.migration.BlockingMigrationsRunner;
import io.camunda.configuration.UnifiedConfiguration;
import io.camunda.configuration.UnifiedConfigurationHelper;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.ExitCodeGenerator;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;

@SpringBootConfiguration(proxyBeanMethods = false)
public class StandaloneIdentityMigration implements CommandLineRunner, ExitCodeGenerator {
  private final BlockingMigrationsRunner asyncMigrationsRunner;

  private int exitCode;

  public StandaloneIdentityMigration(final BlockingMigrationsRunner blockingMigrationsRunner) {
    asyncMigrationsRunner = blockingMigrationsRunner;
  }

  public static void main(final String[] args) {
    MainSupport.putSystemPropertyIfAbsent(
        "java.util.logging.manager", "org.apache.logging.log4j.jul.LogManager");
    MainSupport.putSystemPropertyIfAbsent(
        "spring.banner.location", "classpath:/assets/camunda_banner.txt");
    MainSupport.putSystemPropertyIfAbsent(
        "spring.config.location",
        "optional:classpath:/,optional:classpath:/config/,optional:file:./,optional:file:./config/");

    final SpringApplication application =
        new SpringApplicationBuilder()
            .logStartupInfo(true)
            .web(WebApplicationType.NONE)
            .sources(
                StandaloneIdentityMigration.class,
                BlockingMigrationsRunner.class,
                UnifiedConfigurationHelper.class,
                UnifiedConfiguration.class)
            .profiles(Profile.IDENTITY_MIGRATION.getId())
            .addCommandLineProperties(true)
            .build(args);

    System.exit(SpringApplication.exit(application.run(args)));
  }

  @Override
  public void run(final String... args) throws Exception {
    try {
      asyncMigrationsRunner.run();
    } catch (final Throwable e) {
      exitCode = 1;
    }
  }

  @Override
  public int getExitCode() {
    return exitCode;
  }
}
