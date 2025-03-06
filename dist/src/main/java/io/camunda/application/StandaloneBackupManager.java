/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.application;

import io.camunda.application.StandaloneSchemaManager.SchemaManagerConfiguration.BrokerBasedProperties;
import io.camunda.application.commons.sources.DefaultObjectMapperConfiguration;
import io.camunda.application.listeners.ApplicationErrorListener;
import io.camunda.operate.property.OperateProperties;
import io.camunda.operate.webapp.security.auth.OperateUserDetailsService;
import io.camunda.tasklist.property.TasklistProperties;
import io.camunda.zeebe.broker.exporter.context.ExporterConfiguration;
import io.camunda.zeebe.broker.system.configuration.BrokerCfg;
import io.camunda.zeebe.exporter.ElasticsearchExporterConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FullyQualifiedAnnotationBeanNameGenerator;

/**
 * Backup indices for ElasticSearch by running this standalone application.
 *
 * <p>Example properties:
 *
 * <pre>
 * camunda.operate.elasticsearch.indexPrefix=operate
 * camunda.operate.elasticsearch.clusterName=elasticsearch
 * camunda.operate.elasticsearch.url=https://localhost:9200
 * camunda.operate.elasticsearch.ssl.selfSigned=true
 * camunda.operate.elasticsearch.ssl.verifyHostname=false
 * camunda.operate.elasticsearch.ssl.certificatePath=C:/.../config/certs/http_ca.crt
 * camunda.operate.elasticsearch.username=camunda-admin
 * camunda.operate.elasticsearch.password=camunda123
 *
 * camunda.operate.zeebeElasticsearch.indexPrefix=zeebe-record
 * camunda.operate.zeebeElasticsearch.clusterName=elasticsearch
 * camunda.operate.zeebeElasticsearch.url=https://localhost:9200
 * camunda.operate.zeebeElasticsearch.ssl.selfSigned=true
 * camunda.operate.zeebeElasticsearch.ssl.verifyHostname=false
 * camunda.operate.zeebeElasticsearch.ssl.certificatePath=C:/.../config/certs/http_ca.crt
 * camunda.operate.zeebeElasticsearch.username=camunda-admin
 * camunda.operate.zeebeElasticsearch.password=camunda123
 *
 * camunda.tasklist.elasticsearch.indexPrefix=tasklist
 * camunda.tasklist.elasticsearch.clusterName=elasticsearch
 * camunda.tasklist.elasticsearch.url=https://localhost:9200
 * camunda.tasklist.elasticsearch.ssl.selfSigned=true
 * camunda.tasklist.elasticsearch.ssl.verifyHostname=false
 * camunda.tasklist.elasticsearch.ssl.certificatePath=C:/.../config/certs/http_ca.crt
 * camunda.tasklist.elasticsearch.username=camunda-admin
 * camunda.tasklist.elasticsearch.password=camunda123
 *
 * camunda.tasklist.zeebeElasticsearch.indexPrefix=zeebe-record
 * camunda.tasklist.zeebeElasticsearch.clusterName=elasticsearch
 * camunda.tasklist.zeebeElasticsearch.url=https://localhost:9200
 * camunda.tasklist.zeebeElasticsearch.ssl.selfSigned=true
 * camunda.tasklist.zeebeElasticsearch.ssl.verifyHostname=false
 * camunda.tasklist.zeebeElasticsearch.ssl.certificatePath=C:/.../config/certs/http_ca.crt
 * camunda.tasklist.zeebeElasticsearch.username=camunda-admin
 * camunda.tasklist.zeebeElasticsearch.password=camunda123
 * </pre>
 *
 * All of those properties can also be handed over via environment variables, e.g.
 * `CAMUNDA_OPERATE_ELASTICSEARCH_INDEXPREFIX`
 */
public class StandaloneBackupManager implements CommandLineRunner {

  private static final Logger LOG = LoggerFactory.getLogger(StandaloneBackupManager.class);
  private final BrokerBasedProperties brokerProperties;
  private final OperateUserDetailsService operateUserDetailsService;

  public StandaloneBackupManager(
      final BrokerBasedProperties brokerProperties,
      final OperateUserDetailsService operateUserDetailsService) {
    this.brokerProperties = brokerProperties;
    this.operateUserDetailsService = operateUserDetailsService;
  }

  public static void main(final String[] args) throws Exception {
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
            BackupManagerConfiguration.class,
            StandaloneBackupManager.class,
            io.camunda.tasklist.JacksonConfig.class,
            io.camunda.operate.JacksonConfig.class)
        .addCommandLineProperties(true)
        .listeners(new ApplicationErrorListener())
        .run(args);

    // Explicit exit needed because there are daemon threads (at least from the ES client) that are
    // blocking shutdown.
    System.exit(0);
  }

  @Override
  public void run(final String... args) throws Exception {
    try {
      final var elasticsearchConfig =
          new ExporterConfiguration(
                  "elasticsearch", brokerProperties.getExporters().get("elasticsearch").getArgs())
              .instantiate(ElasticsearchExporterConfiguration.class);

      // Not needed
      //      new io.camunda.zeebe.exporter.SchemaManager(elasticsearchConfig).createSchema();
      //      operateUserDetailsService.initializeUsers();
    } catch (final Exception e) {
      LOG.error("Failed to create/update schemas", e);
      throw e;
    }

    LOG.info("... finished creating/updating Elasticsearch schema for Camunda");
  }

  @SpringBootConfiguration
  @EnableConfigurationProperties(BrokerBasedProperties.class)
  @ConfigurationPropertiesScan
  @ComponentScan(
      basePackageClasses = {
        //        OperateUserDetailsService.class, - Not needed
        //        io.camunda.tasklist.schema.SchemaStartup.class, - Not needed
        //        io.camunda.operate.schema.SchemaStartup.class, - Not needed
        OperateProperties.class,
        TasklistProperties.class,
        io.camunda.tasklist.es.RetryElasticsearchClient.class,
        io.camunda.operate.store.elasticsearch.RetryElasticsearchClient.class,
        DefaultObjectMapperConfiguration.class,
      },
      nameGenerator = FullyQualifiedAnnotationBeanNameGenerator.class)
  public static class BackupManagerConfiguration {
    @ConfigurationProperties("zeebe.broker")
    public static final class BrokerBasedProperties extends BrokerCfg {}
  }
}
