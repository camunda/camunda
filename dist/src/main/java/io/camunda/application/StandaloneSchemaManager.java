/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.application;

import io.camunda.application.commons.search.SearchEngineDatabaseConfiguration;
import io.camunda.application.initializers.StandaloneSchemaManagerInitializer;
import io.camunda.application.listeners.ApplicationErrorListener;
import io.camunda.search.connect.configuration.ConnectConfiguration;
import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

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

  private static final Logger LOG = LoggerFactory.getLogger(StandaloneSchemaManager.class);

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
            .sources(StandaloneSchemaManager.class, SearchEngineDatabaseConfiguration.class)
            .initializers(new StandaloneSchemaManagerInitializer())
            .addCommandLineProperties(true)
            .listeners(new ApplicationErrorListener())
            .build(args);

    LOG.info("Creating/updating Elasticsearch schema for Camunda ...");

    standaloneSchemaManagerApplication.run(args);

    LOG.info("... finished creating/updating schema for Camunda");
    System.exit(0);
  }
}
