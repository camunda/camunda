/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.restore;

import io.camunda.zeebe.backup.api.BackupStore;
import io.camunda.zeebe.backup.azure.AzureBackupStore;
import io.camunda.zeebe.backup.filesystem.FilesystemBackupStore;
import io.camunda.zeebe.backup.gcs.GcsBackupStore;
import io.camunda.zeebe.backup.s3.S3BackupStore;
import io.camunda.zeebe.broker.exporter.repo.ExporterDescriptor;
import io.camunda.zeebe.broker.exporter.repo.ExporterRepository;
import io.camunda.zeebe.broker.system.configuration.BrokerCfg;
import io.camunda.zeebe.broker.system.configuration.backup.AzureBackupStoreConfig;
import io.camunda.zeebe.broker.system.configuration.backup.BackupStoreCfg;
import io.camunda.zeebe.broker.system.configuration.backup.FilesystemBackupStoreConfig;
import io.camunda.zeebe.broker.system.configuration.backup.GcsBackupStoreConfig;
import io.camunda.zeebe.broker.system.configuration.backup.S3BackupStoreConfig;
import java.util.List;
import java.util.concurrent.Executors;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Component;

@Component
final class BackupStoreComponent {

  private final BrokerCfg brokerCfg;

  @Autowired
  BackupStoreComponent(final BrokerCfg brokerCfg) {
    this.brokerCfg = brokerCfg;
  }

  @Bean(destroyMethod = "closeAsync")
  BackupStore backupStore() {
    return buildBackupStore(brokerCfg.getData().getBackup());
  }

  @Bean
  public ExporterRepository exporterRepository(
      @Autowired(required = true) final List<ExporterDescriptor> exporterDescriptors) {
    if (exporterDescriptors != null && !exporterDescriptors.isEmpty()) {
      return new ExporterRepository(exporterDescriptors);
    } else {
      return new ExporterRepository();
    }
  }

  private BackupStore buildBackupStore(final BackupStoreCfg backupCfg) {
    final var store = backupCfg.getStore();
    return switch (store) {
      case S3 -> buildS3BackupStore(backupCfg);
      case GCS -> buildGcsBackupStore(backupCfg);
      case AZURE -> buildAzureBackupStore(backupCfg);
      case FILESYSTEM -> buildFilesystemBackupStore(backupCfg);
      case NONE ->
          throw new IllegalArgumentException(
              "No backup store configured, cannot restore from backup.");
    };
  }

  private static S3BackupStore buildS3BackupStore(final BackupStoreCfg backupStoreCfg) {
    final var storeConfig = S3BackupStoreConfig.toStoreConfig(backupStoreCfg.getS3());
    return new S3BackupStore(storeConfig);
  }

  private static GcsBackupStore buildGcsBackupStore(final BackupStoreCfg backupStoreCfg) {
    final var storeConfig = GcsBackupStoreConfig.toStoreConfig(backupStoreCfg.getGcs());
    return new GcsBackupStore(storeConfig);
  }

  private static AzureBackupStore buildAzureBackupStore(final BackupStoreCfg backupStoreCfg) {
    final var storeConfig = AzureBackupStoreConfig.toStoreConfig(backupStoreCfg.getAzure());
    return new AzureBackupStore(storeConfig);
  }

  private static FilesystemBackupStore buildFilesystemBackupStore(
      final BackupStoreCfg backupStoreCfg) {
    final var storeConfig =
        FilesystemBackupStoreConfig.toStoreConfig(backupStoreCfg.getFilesystem());
    return new FilesystemBackupStore(storeConfig, Executors.newVirtualThreadPerTaskExecutor());
  }
}
