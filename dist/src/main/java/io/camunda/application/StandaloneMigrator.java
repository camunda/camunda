/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.application;

import io.camunda.application.listeners.ApplicationErrorListener;
import io.camunda.configuration.Elasticsearch;
import io.camunda.configuration.Rdbms;
import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootConfiguration(proxyBeanMethods = false)
public class StandaloneMigrator implements CommandLineRunner {

  private static final Logger LOG = LoggerFactory.getLogger(StandaloneMigrator.class);
  private final Elasticsearch elasticsearch;
  private final Rdbms rdbms;

  public StandaloneMigrator(final Elasticsearch elasticsearch, final Rdbms rdbms) {
    this.elasticsearch = elasticsearch;
    this.rdbms = rdbms;
  }

  public static void main(final String[] args) throws IOException {

    // To ensure that debug logging performed using java.util.logging is routed into Log4j 2
    MainSupport.putSystemPropertyIfAbsent(
        "java.util.logging.manager", "org.apache.logging.log4j.jul.LogManager");
    // Workaround for https://github.com/spring-projects/spring-boot/issues/26627
    MainSupport.putSystemPropertyIfAbsent(
        "spring.config.location",
        "optional:classpath:/,optional:classpath:/config/,optional:file:./,optional:file:./config/");

    // show banner
    MainSupport.putSystemPropertyIfAbsent(
        "spring.banner.location", "classpath:/assets/camunda_banner.txt");

    LOG.info("Creating/updating schema for Camunda ...");

    MainSupport.createDefaultApplicationBuilder()
        .web(WebApplicationType.NONE)
        .logStartupInfo(true)
        .sources(Configuration.class)
        .addCommandLineProperties(true)
        .listeners(new ApplicationErrorListener())
        .run(args);

    LOG.info("... finished migrating from ES to RDBMS.");

    // Explicit exit needed because there are daemon threads (at least from the ES client) that are
    // blocking shutdown.
    System.exit(0);
  }

  @Override
  public void run(final String... args) throws Exception {
    try {
      LOG.info("Starting migration from ES to RDBMS ...");
    } catch (final Exception e) {
      LOG.error("Failed to migrate from ES to RDBMS", e);
      throw e;
    }
  }

  @EnableConfigurationProperties({ElasticsearchProperties.class, RdbmsProperties.class})
  public static class Configuration {}

  @ConfigurationProperties("camunda.migration.es")
  public static class ElasticsearchProperties extends Elasticsearch {}

  @ConfigurationProperties("camunda.migration.rdbms")
  public static class RdbmsProperties extends Rdbms {}
}
