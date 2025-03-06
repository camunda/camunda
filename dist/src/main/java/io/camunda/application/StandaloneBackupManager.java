/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.application;

import static org.springframework.core.env.AbstractEnvironment.ACTIVE_PROFILES_PROPERTY_NAME;

import io.camunda.application.StandaloneSchemaManager.SchemaManagerConfiguration.BrokerBasedProperties;
import io.camunda.application.commons.sources.DefaultObjectMapperConfiguration;
import io.camunda.application.listeners.ApplicationErrorListener;
import io.camunda.operate.property.OperateProperties;
import io.camunda.operate.webapp.backup.BackupService;
import io.camunda.tasklist.connect.ElasticsearchConnector;
import io.camunda.tasklist.property.TasklistProperties;
import io.camunda.tasklist.webapp.es.backup.BackupManager;
import io.camunda.tasklist.webapp.management.dto.TakeBackupRequestDto;
import io.camunda.zeebe.broker.system.configuration.BrokerCfg;
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
  private static final String SPRING_PROFILES_ACTIVE_PROPERTY = ACTIVE_PROFILES_PROPERTY_NAME;
  private static final String DEFAULT_CAMUNDA_PROFILES =
      String.join(",", Profile.OPERATE.getId(), Profile.TASKLIST.getId());
  private final BrokerBasedProperties brokerProperties;
  private final BackupManager tasklistBackupManager;
  private final BackupService operateBackupManager;

  public StandaloneBackupManager(
      final BrokerBasedProperties brokerProperties,
      final BackupManager tasklistBackupManager,
      final BackupService operateBackupManager) {
    this.brokerProperties = brokerProperties;
    this.tasklistBackupManager = tasklistBackupManager;
    this.operateBackupManager = operateBackupManager;
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

    //    final var defaultActiveProfiles = getDefaultActiveProfiles();
    MainSupport.createDefaultApplicationBuilder()
        .web(WebApplicationType.NONE)
        .logStartupInfo(true)
        .sources(
            BackupManagerConfiguration.class,
            //            CommonsModuleConfiguration.class,
            //            OperateModuleConfiguration.class,
            //            TasklistModuleConfiguration.class,
            //            WebappModuleConfiguration.class,
            StandaloneBackupManager.class,
            io.camunda.tasklist.JacksonConfig.class,
            io.camunda.operate.JacksonConfig.class)
        .addCommandLineProperties(true)
        //        .properties(defaultActiveProfiles)
        .listeners(new ApplicationErrorListener())
        .run(args);

    // Explicit exit needed because there are daemon threads (at least from the ES client) that are
    // blocking shutdown.
    System.exit(0);
  }

  //
  //  public static Map<String, Object> getDefaultActiveProfiles() {
  //    final var defaultProperties = new HashMap<String, Object>();
  //    defaultProperties.put(SPRING_PROFILES_ACTIVE_PROPERTY, DEFAULT_CAMUNDA_PROFILES);
  //    return defaultProperties;
  //  }

  @Override
  public void run(final String... args) throws Exception {
    final var backupId = 0xCAFEL;
    try {
      // Operate
      final var operateTakeBackupRequestDto =
          new io.camunda.operate.webapp.management.dto.TakeBackupRequestDto();
      operateTakeBackupRequestDto.setBackupId(backupId);
      final var operateBackupResponse =
          operateBackupManager.takeBackup(operateTakeBackupRequestDto);

      LOG.info(
          "Triggered ES snapshots for Operate indices: {}",
          operateBackupResponse.getScheduledSnapshots());

      // TASKLIST
      final var tasklistTakeBackupRequestDto = new TakeBackupRequestDto();
      tasklistTakeBackupRequestDto.setBackupId(backupId);
      final var tasklistBackupResponse =
          tasklistBackupManager.takeBackup(tasklistTakeBackupRequestDto);

      LOG.info(
          "Triggered ES snapshots for Tasklist indices: {}",
          tasklistBackupResponse.getScheduledSnapshots());
      //      final var elasticsearchConfig =
      //          new ExporterConfiguration(
      //                  "elasticsearch",
      // brokerProperties.getExporters().get("elasticsearch").getArgs())
      //              .instantiate(ElasticsearchExporterConfiguration.class);

      // Not needed
      //      new io.camunda.zeebe.exporter.SchemaManager(elasticsearchConfig).createSchema();
      //      operateUserDetailsService.initializeUsers();
    } catch (final Exception e) {
      LOG.error("Expected to trigger ES snapshots for backupId {}, but failed", backupId, e);
      throw e;
    }

    LOG.info("... triggered Elasticsearch snapshot based on Camunda's backup procedure");
  }

  // LEARNING - basePackageClasses - defining a class causes to scan the whole package with * doing
  // a traversal to include all childs
  // This approach doesnt work pretty good as we include a lot of other things

  @SpringBootConfiguration
  @EnableConfigurationProperties(BrokerBasedProperties.class)
  @ConfigurationPropertiesScan
  @ComponentScan(
      //      basePackages = {"io.camunda.operate.*"}
      basePackageClasses = {
        //        OperateUserDetailsService.class, - Not needed
        // Schema startup - to have access on index descriptors
        // TODO: Find a better way
        //        io.camunda.tasklist.schema.SchemaStartup.class, // - Not needed
        io.camunda.tasklist.schema.indices.IndexDescriptor.class,
        io.camunda.tasklist.schema.templates.TemplateDescriptor.class,
        io.camunda.operate.schema.indices.IndexDescriptor.class,
        io.camunda.operate.schema.templates.TemplateDescriptor.class,
        // we need this metrics for the ElasticsearchImportStore; todo: why do we need it?
        // WE DONT WANT THIS
        //        io.camunda.operate.Metrics.class,
        //        io.camunda.tasklist.Metrics.class,

        //        io.camunda.operate.schema.SchemaStartup.class, // - Not needed
        // Backup services from Operate / Tasklist - to be used to create backups
        io.camunda.operate.webapp.backup.BackupService.class,
        io.camunda.operate.webapp.elasticsearch.backup.ElasticsearchBackupRepository.class,
        io.camunda.tasklist.webapp.es.backup.BackupManager.class,
        // Containing the properties/configurations for Operate/Tasklist
        OperateProperties.class,
        TasklistProperties.class,
        io.camunda.operate.conditions.DatabaseInfo.class,
        // To set up the right clients, that have to be used by other components
        io.camunda.operate.connect.ElasticsearchConnector
            .class, // we need this to find the right clients for Operate
        ElasticsearchConnector.class,
        // WE DONT WANT THIS
        //        io.camunda.operate.tenant.TenantAwareElasticsearchClient
        //            .class, // needed for decision store - that is pulled in from somwhere TODO;
        // clarify
        //        io.camunda.operate.store.elasticsearch.RetryElasticsearchClient.class,
        //        io.camunda.tasklist.es.RetryElasticsearchClient.class,
        // Object mapper used by other components
        DefaultObjectMapperConfiguration.class,
      },
      nameGenerator = FullyQualifiedAnnotationBeanNameGenerator.class)
  public static class BackupManagerConfiguration {
    @ConfigurationProperties("zeebe.broker")
    public static final class BrokerBasedProperties extends BrokerCfg {}
  }
}
