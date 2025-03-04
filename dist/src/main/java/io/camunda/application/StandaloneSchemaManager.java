/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.application;

import io.camunda.application.commons.migration.SchemaManagerHelper;
import io.camunda.application.listeners.ApplicationErrorListener;
import io.camunda.db.se.config.ConnectConfiguration;
import io.camunda.exporter.schema.SchemaManager;
import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ConfigurableApplicationContext;

/**
 * Soley create or update Schema for ElasticSearch by running this standalone application.
 *
 * <p>Configure with {@link ConnectConfiguration} properties, prefixed by `camunda.database`, for
 * example:
 *
 * <pre>
 * camunda.database.type=elasticsearch
 * camunda.database.url=
 * camunda.database.security.selfSigned=
 * camunda.database.security.enabled=
 * camunda.database.security.certificatePath=
 * camunda.database.username=
 * camunda.database.password=
 * camunda.database.indexPrefix=
 * </pre>
 *
 * All of those porperties can also be handed over via environment variables, e.g.
 * `CAMUNDA_DATABASE_URL`
 */
@SpringBootConfiguration
@EnableConfigurationProperties
public class StandaloneSchemaManager {

  private static final Logger LOG = LoggerFactory.getLogger(SchemaManager.class);

  private static final boolean IS_ELASTICSEARCH = true;

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

    final SpringApplication standaloneSchemaManagerApplication =
        new SpringApplicationBuilder()
            .web(WebApplicationType.NONE)
            .logStartupInfo(true)
            .sources(StandaloneSchemaManager.class, SchemaManagerConnectConfiguration.class)
            .addCommandLineProperties(true)
            .listeners(new ApplicationErrorListener())
            .build(args);

    final ConfigurableApplicationContext applicationContext =
        standaloneSchemaManagerApplication.run(args);

    final SchemaManagerConnectConfiguration connectConfiguration =
        applicationContext.getBean(SchemaManagerConnectConfiguration.class);

    if (!"elasticsearch".equalsIgnoreCase(connectConfiguration.getType())) {
      LOG.error(
          "Cannot creating schema for anything other than Elasticsearch with this script for now...");
      System.exit(1);
    }

    LOG.info("Creating/updating Elasticsearch schema for Camunda ...");

    final var clientAdapter = SchemaManagerHelper.createClientAdapter(connectConfiguration);
    SchemaManagerHelper.createSchema(connectConfiguration, clientAdapter);
    LOG.info("... finished creating/updating schema for Camunda");
    clientAdapter.close();
    System.exit(0);
  }

  /** Helper class to use Spring defaults to read properties for database connection */
  @ConfigurationProperties("camunda.database")
  public static final class SchemaManagerConnectConfiguration extends ConnectConfiguration {}
}
