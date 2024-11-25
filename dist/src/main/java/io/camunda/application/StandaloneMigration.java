/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.application;

import io.camunda.application.commons.migration.MigrationsModuleConfiguration;
import io.camunda.application.listeners.ApplicationErrorListener;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ApplicationEvent;
import org.springframework.context.ApplicationListener;

@SpringBootConfiguration(proxyBeanMethods = false)
public class StandaloneMigration {

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
            .sources(MigrationsModuleConfiguration.class)
            .profiles(Profile.MIGRATION.getId())
            .addCommandLineProperties(true)
            .listeners(new ApplicationErrorListener(), new ApplicationTerminateListener())
            .build(args);

    application.run(args);
  }

  public static class ApplicationTerminateListener
      implements ApplicationListener<MigrationFinishedEvent> {

    private int exitCode = 0;
    private int arrivedEvents = 0;

    @Override
    public void onApplicationEvent(final MigrationFinishedEvent event) {
      // Since this will always be running with the full Migration profile we can safely assume that
      // we await for 2 events
      final int awaitEvents = 2;
      final int errorCode = (int) event.getSource();
      if (errorCode < exitCode) {
        exitCode = errorCode;
      }
      if (++arrivedEvents == awaitEvents) {
        System.exit(exitCode);
      }
    }
  }

  public static class MigrationFinishedEvent extends ApplicationEvent {

    public MigrationFinishedEvent(final int errorCode) {
      super(errorCode);
    }
  }
}
