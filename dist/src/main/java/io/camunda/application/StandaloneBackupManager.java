/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.application;

import static io.camunda.application.commons.backup.ConditionalOnBackupWebappsEnabled.BACKUP_WEBAPPS_ENABLED;
import static java.util.Optional.ofNullable;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import io.camunda.application.StandaloneBackupManager.BackupManagerConfiguration.BackupWebappsProperties;
import io.camunda.application.commons.backup.BackupPriorityConfiguration;
import io.camunda.configuration.beans.SearchEngineConnectProperties;
import io.camunda.search.connect.configuration.ConnectConfiguration;
import io.camunda.search.connect.es.ElasticsearchConnector;
import io.camunda.webapps.backup.BackupRepository;
import io.camunda.webapps.backup.BackupService;
import io.camunda.webapps.backup.BackupStateDto;
import io.camunda.webapps.backup.TakeBackupRequestDto;
import io.camunda.webapps.backup.repository.BackupRepositoryProps;
import io.camunda.webapps.backup.repository.WebappsSnapshotNameProvider;
import io.camunda.webapps.backup.repository.elasticsearch.ElasticsearchBackupRepository;
import io.camunda.webapps.schema.descriptors.backup.BackupPriorities;
import io.camunda.zeebe.util.VersionUtil;
import java.util.Arrays;
import java.util.EnumSet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.ExitCodeExceptionMapper;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FullyQualifiedAnnotationBeanNameGenerator;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * Backup Camunda, Operate and Tasklist indices for ElasticSearch by running this standalone
 * application.
 *
 * <p>Example properties:
 *
 * <pre>
 * camunda.database.indexPrefix=operate
 * camunda.database.clusterName=elasticsearch
 * camunda.database.url=https://localhost:9200
 * camunda.database.security.selfSigned=true
 * camunda.database.security.verifyHostname=false
 * camunda.database.security.certificatePath=C:/.../config/certs/http_ca.crt
 * camunda.database.username=camunda-admin
 * camunda.database.password=camunda123
 *
 * camunda.backup.webapps.repositoryName=els-test
 * *
 * </pre>
 *
 * All of those properties can also be handed over via environment variables, e.g.
 * `CAMUNDA_DATABASE_INDEXPREFIX`
 */
@SpringBootConfiguration(proxyBeanMethods = false)
public class StandaloneBackupManager implements CommandLineRunner {

  private static final Logger LOG = LoggerFactory.getLogger(StandaloneBackupManager.class);
  private final BackupService backupService;

  public StandaloneBackupManager(final BackupService backupService) {
    this.backupService = backupService;
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

    LOG.info("Creating a backup for Camunda, Operate and Tasklist elasticsearch indices ...");

    MainSupport.createDefaultApplicationBuilder()
        .web(WebApplicationType.NONE)
        .logStartupInfo(true)
        .sources(BackupManagerConfiguration.class, StandaloneBackupManager.class)
        .properties(BACKUP_WEBAPPS_ENABLED + "=true")
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
      final var takeBackupRequestDto = new TakeBackupRequestDto();
      takeBackupRequestDto.setBackupId(backupId);
      final var backupResponse = backupService.takeBackup(takeBackupRequestDto);
      LOG.info("Triggered ES snapshots: {}", backupResponse.getScheduledSnapshots());
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
      final var backup = backupService.getBackupState(backupId);
      final var backupState = backup.getState();

      LOG.info("Snapshot observation:");
      LOG.info("Indices snapshot is {}. Details: [{}]", backupState, backup);

      if (isCompletedBackup(backupState)) {
        isBackupInProgress = false;
      } else if (isFailedBackup(backupState)) {
        final var failureReason =
            backup.getFailureReason() != null && !backup.getFailureReason().isEmpty()
                ? " Indices backup failure: %s.".formatted(backup.getFailureReason())
                : "";
        throw new IllegalStateException(
            "Backup with id:[%d] failed.%s".formatted(backupId, failureReason));
      }
    }

    LOG.info("Backup with id:[{}] is completed!", backupId);
  }

  private boolean isCompletedBackup(final BackupStateDto backupStateDto) {
    return backupStateDto == BackupStateDto.COMPLETED;
  }

  private boolean isFailedBackup(final BackupStateDto backupStateDto) {
    return EnumSet.of(BackupStateDto.FAILED, BackupStateDto.INCOMPATIBLE).contains(backupStateDto);
  }

  @EnableConfigurationProperties({
    SearchEngineConnectProperties.class,
    BackupWebappsProperties.class,
  })
  @ComponentScan(
      basePackages = "io.camunda.application.commons.backup",
      nameGenerator = FullyQualifiedAnnotationBeanNameGenerator.class)
  public static class BackupManagerConfiguration {

    @Bean
    public ExitCodeExceptionMapper exitCodeExceptionMapper() {
      return ex -> 1;
    }

    @Bean
    public ElasticsearchClient elasticsearchClient(
        final ConnectConfiguration connectConfiguration) {
      return new ElasticsearchConnector(connectConfiguration).createClient();
    }

    @Bean
    public BackupRepository backupRepository(
        final ElasticsearchClient elasticsearchClient,
        final BackupRepositoryProps backupRepositoryProps,
        @Qualifier("backupThreadPoolExecutor")
            final ThreadPoolTaskExecutor threadPoolTaskExecutor) {
      return new ElasticsearchBackupRepository(
          elasticsearchClient,
          backupRepositoryProps,
          new WebappsSnapshotNameProvider(),
          threadPoolTaskExecutor);
    }

    @Bean
    public BackupPriorities backupPrioritiesForStandaloneBackup(
        final ConnectConfiguration connectConfiguration) {
      return BackupPriorityConfiguration.getBackupPriorities(
          connectConfiguration.getIndexPrefix(),
          connectConfiguration.getTypeEnum().isElasticSearch());
    }

    @ConfigurationProperties("camunda.backup.webapps")
    public static final class BackupWebappsProperties implements BackupRepositoryProps {

      private String repositoryName;

      private int snapshotTimeout;

      private Long incompleteCheckTimeoutInSeconds;

      @Override
      public String version() {
        return VersionUtil.getVersion();
      }

      @Override
      public String repositoryName() {
        return repositoryName;
      }

      @Override
      public int snapshotTimeout() {
        return snapshotTimeout;
      }

      @Override
      public Long incompleteCheckTimeoutInSeconds() {
        return ofNullable(incompleteCheckTimeoutInSeconds)
            .orElseGet(BackupRepositoryProps.EMPTY::incompleteCheckTimeoutInSeconds);
      }

      public void setRepositoryName(final String repositoryName) {
        this.repositoryName = repositoryName;
      }

      public void setSnapshotTimeout(final int snapshotTimeout) {
        this.snapshotTimeout = snapshotTimeout;
      }

      public void setIncompleteCheckTimeoutInSeconds(final Long incompleteCheckTimeoutInSeconds) {
        this.incompleteCheckTimeoutInSeconds = incompleteCheckTimeoutInSeconds;
      }
    }
  }
}
