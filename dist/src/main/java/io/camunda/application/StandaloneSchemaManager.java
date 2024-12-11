/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.application;

import io.camunda.application.listeners.ApplicationErrorListener;
import io.camunda.exporter.adapters.ClientAdapter;
import io.camunda.exporter.config.ExporterConfiguration;
import io.camunda.exporter.schema.SchemaManager;
import io.camunda.exporter.schema.SearchEngineClient;
import io.camunda.search.connect.configuration.ConnectConfiguration;
import io.camunda.webapps.schema.descriptors.IndexDescriptors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ConfigurableApplicationContext;

@SpringBootConfiguration
@EnableConfigurationProperties
public class StandaloneSchemaManager {

  private static final Logger LOG = LoggerFactory.getLogger(SchemaManager.class);

  private static final String GLOBAL_PREFIX = "";
  private static final boolean IS_ELASTICSEARCH = true;

  public static void main(final String[] args) {

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

    LOG.info("Creating/updating Elasticsearch schema for Camunda ...");

    final ExporterConfiguration exporterConfig = new ExporterConfiguration();
    exporterConfig.setConnect(connectConfiguration);

    final IndexDescriptors indexDescriptors = new IndexDescriptors(GLOBAL_PREFIX, IS_ELASTICSEARCH);

    final SearchEngineClient client = ClientAdapter.of(exporterConfig).getSearchEngineClient();
    final SchemaManager schemaManager =
        new SchemaManager(
            client, indexDescriptors.indices(), indexDescriptors.templates(), exporterConfig);

    schemaManager.startup();

    LOG.info("... finished creating/updating Elasticsearch schema for Camunda");
    System.exit(0);
  }

  @ConfigurationProperties("camunda.elasticsearch")
  public static final class SchemaManagerConnectConfiguration extends ConnectConfiguration {}
}
