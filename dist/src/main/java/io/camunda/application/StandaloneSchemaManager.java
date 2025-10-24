/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.application;

import static io.camunda.zeebe.protocol.impl.record.RecordMetadata.CURRENT_BROKER_VERSION;

import io.camunda.application.initializers.StandaloneSchemaManagerInitializer;
import io.camunda.application.listeners.ApplicationErrorListener;
import io.camunda.configuration.UnifiedConfiguration;
import io.camunda.configuration.UnifiedConfigurationHelper;
import io.camunda.configuration.beanoverrides.SearchEngineConnectPropertiesOverride;
import io.camunda.configuration.beanoverrides.SearchEngineIndexPropertiesOverride;
import io.camunda.configuration.beans.LegacyBrokerBasedProperties;
import io.camunda.search.connect.configuration.ConnectConfiguration;
import io.camunda.zeebe.broker.exporter.context.ExporterConfiguration;
import io.camunda.zeebe.exporter.ElasticsearchExporterConfiguration;
import io.camunda.zeebe.exporter.ElasticsearchExporterSchemaManager;
import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FullyQualifiedAnnotationBeanNameGenerator;

/**
 * Soley create or update Schema for ElasticSearch by running this standalone application.
 *
 * <p>Configure with {@link ConnectConfiguration} properties, prefixed by `camunda.database`, for
 * example:
 *
 * <pre>
 * camunda.database.type=elasticsearch
 * camunda.database.url=
 * camunda.database.security.self-signed=
 * camunda.database.security.enabled=
 * camunda.database.security.certificate-path=
 * camunda.database.username=
 * camunda.database.password=
 * camunda.database.index-prefix=
 * </pre>
 *
 * All of those porperties can also be handed over via environment variables, e.g.
 * `CAMUNDA_DATABASE_URL`
 */
@SpringBootConfiguration(proxyBeanMethods = false)
public class StandaloneSchemaManager implements CommandLineRunner {

  private static final Logger LOG = LoggerFactory.getLogger(StandaloneSchemaManager.class);
  private final LegacyBrokerBasedProperties brokerProperties;

  public StandaloneSchemaManager(final LegacyBrokerBasedProperties brokerProperties) {
    this.brokerProperties = brokerProperties;
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

    LOG.info("Creating/updating Elasticsearch schema for Camunda ...");

    MainSupport.createDefaultApplicationBuilder()
        .web(WebApplicationType.NONE)
        .logStartupInfo(true)
        .sources(
            // Unified Configuration classes
            UnifiedConfigurationHelper.class,
            UnifiedConfiguration.class,
            SearchEngineConnectPropertiesOverride.class,
            SearchEngineIndexPropertiesOverride.class,
            // ---
            StandaloneSchemaManagerConfiguration.class)
        .initializers(new StandaloneSchemaManagerInitializer())
        .addCommandLineProperties(true)
        .listeners(new ApplicationErrorListener())
        .run(args);

    // Explicit exit needed because there are daemon threads (at least from the ES client) that are
    // blocking shutdown.
    System.exit(0);
  }

  @Override
  public void run(final String... args) throws Exception {
    if (brokerProperties.getExporters().containsKey("elasticsearch")) {
      final var elasticsearchConfig =
          new ExporterConfiguration(
                  "elasticsearch", brokerProperties.getExporters().get("elasticsearch").getArgs())
              .instantiate(ElasticsearchExporterConfiguration.class);
      new ElasticsearchExporterSchemaManager(elasticsearchConfig)
          .createSchema(CURRENT_BROKER_VERSION.toString());
    }
    LOG.info("... finished creating/updating Elasticsearch schema for Camunda");
  }

  @EnableAutoConfiguration
  // TODO: Use unified configuration when it is available
  @EnableConfigurationProperties(LegacyBrokerBasedProperties.class)
  @ComponentScan(
      basePackages = {"io.camunda.application.commons.search", "io.camunda.configuration"},
      nameGenerator = FullyQualifiedAnnotationBeanNameGenerator.class)
  public static class StandaloneSchemaManagerConfiguration {}
}
