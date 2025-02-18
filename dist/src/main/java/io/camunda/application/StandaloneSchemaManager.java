/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.application;

import io.camunda.application.listeners.ApplicationErrorListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FullyQualifiedAnnotationBeanNameGenerator;

/**
 * Create or update Schemas for ElasticSearch by running this standalone application.
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
@SpringBootConfiguration
@EnableConfigurationProperties
@ComponentScan(
    basePackages = {
      "io.camunda.operate.property",
      "io.camunda.operate.schema",
      "io.camunda.tasklist.property",
      "io.camunda.tasklist.schema"
    },
    nameGenerator = FullyQualifiedAnnotationBeanNameGenerator.class)
public class StandaloneSchemaManager {

  private static final Logger LOG = LoggerFactory.getLogger(StandaloneSchemaManager.class);

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

    final SpringApplication standaloneSchemaManagerApplication =
        new SpringApplicationBuilder()
            .web(WebApplicationType.NONE)
            .logStartupInfo(true)
            .sources(StandaloneSchemaManager.class) // SchemaManagerConnectConfiguration.class)
            .addCommandLineProperties(true)
            .listeners(new ApplicationErrorListener())
            .build(args);

    final ConfigurableApplicationContext applicationContext =
        standaloneSchemaManagerApplication.run(args);

    final io.camunda.operate.schema.SchemaStartup schemaStartup =
        applicationContext.getBean(io.camunda.operate.schema.SchemaStartup.class);
    final io.camunda.tasklist.schema.SchemaStartup tasklistSchemaStartup =
        applicationContext.getBean(io.camunda.tasklist.schema.SchemaStartup.class);

    LOG.info("... finished creating/updating Elasticsearch schema for Camunda");
    System.exit(0);
  }

  /*
  io.camunda.operate.schema.elasticsearch.ElasticsearchSchemaManager operateSchemaManager =
      new io.camunda.operate.schema.elasticsearch.ElasticsearchSchemaManager();
  io.camunda.tasklist.schema.manager.ElasticsearchSchemaManager tasklistSchemaManager =
      new io.camunda.tasklist.schema.manager.ElasticsearchSchemaManager();
  io.camunda.exporter.schema.ElasticsearchSchemaManager exporterSchemaManager =
      new io.camunda.exporter.schema.ElasticsearchSchemaManager(null, null, null, null);
  io.camunda.operate.connect.ElasticsearchConnector operateEsConnector;
  io.camunda.tasklist.es.ElasticsearchConnector tasklistEsConnector;
  */

  /*
  operateSchemaManager.createSchema();
  tasklistSchemaManager.createSchema();
  exporterSchemaManager.initialiseResources();
  */
}
