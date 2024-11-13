/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migration.app;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Profile;

@Profile("!test")
@SpringBootApplication(scanBasePackages = {"io.camunda.migration"})
public class MigrationApp implements ApplicationRunner {

  private static final Logger LOG = LoggerFactory.getLogger(MigrationApp.class);
  @Autowired private RuntimeContainer runtimeContainer;

  public static void main(final String[] args) {
    envConfig("java.util.logging.manager", "org.apache.logging.log4j.jul.LogManager");
    final SpringApplication springApplication = new SpringApplication(MigrationApp.class);
    springApplication.setWebApplicationType(WebApplicationType.NONE);
    springApplication.setAddCommandLineProperties(true);
    springApplication.setHeadless(true);

    springApplication.run(args);
  }

  @Override
  public void run(final ApplicationArguments args) {
    try {
      runtimeContainer.start(args);
    } catch (final InterruptedException e) {
      LOG.error("Migration failed", e);
      System.exit(1);
    }
    LOG.info("Migration finished, shutting down");
    System.exit(0);
  }

  private static void envConfig(final String key, final String value) {
    if (System.getProperty(key) == null) {
      System.setProperty(key, value);
    }
  }
}
