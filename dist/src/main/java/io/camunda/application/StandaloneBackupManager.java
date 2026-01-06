/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.application;

import io.camunda.application.commons.search.NativeSearchClientsConfiguration;
import io.camunda.configuration.UnifiedConfiguration;
import io.camunda.configuration.UnifiedConfigurationHelper;
import io.camunda.configuration.beanoverrides.SearchEngineConnectPropertiesOverride;
import io.camunda.configuration.beanoverrides.SearchEngineIndexPropertiesOverride;
import io.camunda.configuration.beanoverrides.SearchEngineRetentionPropertiesOverride;
import io.camunda.webapps.backup.BackupService;
import io.camunda.webapps.backup.BackupStateDto;
import io.camunda.webapps.backup.TakeBackupRequestDto;
import java.util.Arrays;
import java.util.EnumSet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.ExitCodeExceptionMapper;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.WebApplicationType;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FullyQualifiedAnnotationBeanNameGenerator;

/**
 * Standalone backup manager to create backups for Camunda, Operate and Tasklist indices in the
 * configured search engine (e.g. Elasticsearch or OpenSearch).
 *
 * <p>Example properties:
 *
 * <pre>
 * camunda.database.index-prefix=operate
 * camunda.database.cluster-name=search-cluster
 * camunda.database.url=https://localhost:9200
 * camunda.database.security.self-signed=true
 * camunda.database.security.verify-hostname=false
 * camunda.database.security.certificate-path=C:/.../config/certs/http_ca.crt
 * camunda.database.username=camunda-admin
 * camunda.database.password=camunda123
 *
 * camunda.data.backup.repository-name=search-backup-repo
 * </pre>
 *
 * All of those properties can also be handed over via environment variables, e.g.
 * `CAMUNDA_DATABASE_INDEXPREFIX`.
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

    LOG.info("Creating a backup for Camunda, Operate and Tasklist search engine indices ...");

    MainSupport.createDefaultApplicationBuilder()
        .web(WebApplicationType.NONE)
        .logStartupInfo(true)
        .sources(
            // Unified Configuration classes
            UnifiedConfigurationHelper.class,
            UnifiedConfiguration.class,
            SearchEngineConnectPropertiesOverride.class,
            SearchEngineIndexPropertiesOverride.class,
            SearchEngineRetentionPropertiesOverride.class,
            // ---
            BackupManagerConfiguration.class,
            StandaloneBackupManager.class,
            NativeSearchClientsConfiguration.class)
        .addCommandLineProperties(true)
        .run(args);

    // Explicit exit needed because there are daemon threads (at least from the search engine
    // client)
    // that are blocking shutdown.
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
      LOG.info("Triggered search engine snapshots: {}", backupResponse.getScheduledSnapshots());
    } catch (final Exception e) {
      LOG.error(
          "Expected to trigger search engine snapshots for backupId {}, but failed", backupId, e);
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

  @ComponentScan(
      basePackages = "io.camunda.application.commons.backup",
      nameGenerator = FullyQualifiedAnnotationBeanNameGenerator.class)
  public static class BackupManagerConfiguration {

    @Bean
    public ExitCodeExceptionMapper exitCodeExceptionMapper() {
      return ex -> 1;
    }
  }
}
