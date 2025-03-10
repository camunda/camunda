/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.application;

import io.camunda.application.StandaloneSchemaManager.SchemaManagerConfiguration.BrokerBasedProperties;
import io.camunda.operate.webapp.backup.BackupService;
import io.camunda.tasklist.webapp.es.backup.BackupManager;
import io.camunda.tasklist.webapp.management.dto.BackupStateDto;
import io.camunda.tasklist.webapp.management.dto.TakeBackupRequestDto;
import java.util.Arrays;
import java.util.EnumSet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.ExitCodeExceptionMapper;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FullyQualifiedAnnotationBeanNameGenerator;

/**
 * Backup Operate and Tasklist indices for ElasticSearch by running this standalone application.
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
 * camunda.operate.backup.repositoryName=els-test
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
 * camunda.tasklist.backup.repositoryName=els-test
 *
 * </pre>
 *
 * All of those properties can also be handed over via environment variables, e.g.
 * `CAMUNDA_OPERATE_ELASTICSEARCH_INDEXPREFIX`
 */
public class StandaloneBackupManager implements CommandLineRunner {

  private static final Logger LOG = LoggerFactory.getLogger(StandaloneBackupManager.class);
  private final BackupManager tasklistBackupManager;
  private final BackupService operateBackupManager;

  public StandaloneBackupManager(
      final BackupManager tasklistBackupManager, final BackupService operateBackupManager) {
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

    LOG.info("Creating a backup for Operate and Tasklist elasticsearch indices ...");

    MainSupport.createDefaultApplicationBuilder()
        .web(WebApplicationType.NONE)
        .logStartupInfo(true)
        .sources(
            BackupManagerConfiguration.class,
            StandaloneBackupManager.class,
            io.camunda.tasklist.JacksonConfig.class,
            io.camunda.operate.JacksonConfig.class)
        .addCommandLineProperties(true)
        .run(args);

    // Explicit exit needed because there are daemon threads (at least from the ES client) that are
    // blocking shutdown.
    System.exit(0);
  }

  @Override
  public void run(final String... args) throws Exception {
    if (args.length != 1) {
      throw new IllegalArgumentException(
          String.format("Expected one argument, the backup ID, but got: %s", Arrays.asList(args)));
    }

    final long backupId;
    try {
      backupId = Long.parseLong(args[0]);
    } catch (final NumberFormatException nfe) {
      throw new IllegalArgumentException(
          String.format("Expected as argument the backup ID as long, but got %s", args[0]));
    }

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

      // Tasklist
      final var tasklistTakeBackupRequestDto = new TakeBackupRequestDto();
      tasklistTakeBackupRequestDto.setBackupId(backupId);
      final var tasklistBackupResponse =
          tasklistBackupManager.takeBackup(tasklistTakeBackupRequestDto);

      LOG.info(
          "Triggered ES snapshots for Tasklist indices: {}",
          tasklistBackupResponse.getScheduledSnapshots());
    } catch (final Exception e) {
      LOG.error("Expected to trigger ES snapshots for backupId {}, but failed", backupId, e);
      throw e;
    }

    LOG.info("Will observe snapshot creation...");
    boolean isBackupInProgress = true;
    while (isBackupInProgress) {
      // we need to sleep here already otherwise the getBackupState fails, as it can't
      // handle empty snapshot responses...
      Thread.sleep(5 * 1_000);
      final var operateBackup = operateBackupManager.getBackupState(backupId);
      final var operateBackupState = operateBackup.getState();
      final var tasklistBackup = tasklistBackupManager.getBackupState(backupId);
      final var tasklistBackupState = tasklistBackup.getState();

      LOG.info("Snapshot observation:");
      LOG.info("Operate indices snapshot is {}. Details: [{}]", operateBackupState, operateBackup);
      LOG.info(
          "Tasklist indices snapshot is {}. Details: [{}]", tasklistBackupState, tasklistBackup);

      if (isCompletedBackup(operateBackupState, tasklistBackupState)) {
        isBackupInProgress = false;
      } else if (isFailedBackup(operateBackupState, tasklistBackupState)) {
        final var failureReason = new StringBuilder();
        if (operateBackup.getFailureReason() != null
            && !operateBackup.getFailureReason().isEmpty()) {
          failureReason.append(
              " Operate indices backup failure: %s.".formatted(operateBackup.getFailureReason()));
        }
        if (tasklistBackup.getFailureReason() != null
            && !tasklistBackup.getFailureReason().isEmpty()) {
          failureReason.append(
              " Tasklist indices backup failure: %s.".formatted(tasklistBackup.getFailureReason()));
        }
        throw new IllegalStateException(
            "Backup with id:[%d] failed.%s".formatted(backupId, failureReason));
      }
    }

    LOG.info("Backup with id:[{}] is completed!", backupId);
  }

  private boolean isCompletedBackup(
      final io.camunda.operate.webapp.management.dto.BackupStateDto operateBackupState,
      final BackupStateDto tasklistBackupState) {
    return operateBackupState == io.camunda.operate.webapp.management.dto.BackupStateDto.COMPLETED
        && tasklistBackupState == BackupStateDto.COMPLETED;
  }

  private boolean isFailedBackup(
      final io.camunda.operate.webapp.management.dto.BackupStateDto operateBackupState,
      final BackupStateDto tasklistBackupState) {
    return EnumSet.of(
                io.camunda.operate.webapp.management.dto.BackupStateDto.FAILED,
                io.camunda.operate.webapp.management.dto.BackupStateDto.INCOMPATIBLE)
            .contains(operateBackupState)
        || EnumSet.of(BackupStateDto.FAILED, BackupStateDto.INCOMPATIBLE)
            .contains(tasklistBackupState);
  }

  @SpringBootConfiguration
  @EnableConfigurationProperties(BrokerBasedProperties.class)
  @ConfigurationPropertiesScan
  @ComponentScan(
      // CAUTION: basePackageClasses makes Spring scan the entire package of the specified
      // classes, including sub-packages.
      basePackageClasses = {
        io.camunda.tasklist.schema.indices.IndexDescriptor.class,
        io.camunda.tasklist.schema.templates.TemplateDescriptor.class,
        io.camunda.operate.schema.indices.IndexDescriptor.class,
        io.camunda.operate.schema.templates.TemplateDescriptor.class,
        // Backup services from Operate / Tasklist - to be used to create backups
        io.camunda.operate.webapp.backup.BackupService.class,
        io.camunda.operate.webapp.elasticsearch.backup.ElasticsearchBackupRepository.class,
        io.camunda.tasklist.webapp.es.backup.BackupManager.class,
        // Containing the properties/configurations for Operate/Tasklist
        io.camunda.operate.property.OperateProperties.class,
        io.camunda.tasklist.property.TasklistProperties.class,
        io.camunda.operate.conditions.DatabaseInfo.class,
        // To set up the right clients, that have to be used by other components
        io.camunda.operate.connect.ElasticsearchConnector
            .class, // we need this to find the right clients for Operate
        io.camunda.tasklist.connect.ElasticsearchConnector.class,
        // Object mapper used by other components
        io.camunda.application.commons.sources.DefaultObjectMapperConfiguration.class,
      },
      nameGenerator = FullyQualifiedAnnotationBeanNameGenerator.class)
  public static class BackupManagerConfiguration {
    @Bean
    public ExitCodeExceptionMapper exitCodeExceptionMapper() {
      return ex -> 1;
    }
  }
}
