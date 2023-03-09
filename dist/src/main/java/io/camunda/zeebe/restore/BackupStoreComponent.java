/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.restore;

import io.camunda.zeebe.backup.api.BackupStore;
import io.camunda.zeebe.backup.gcs.GcsBackupStore;
import io.camunda.zeebe.backup.s3.S3BackupStore;
import io.camunda.zeebe.broker.system.configuration.BrokerCfg;
import io.camunda.zeebe.broker.system.configuration.backup.BackupStoreCfg;
import io.camunda.zeebe.broker.system.configuration.backup.GCSBackupStoreConfig;
import io.camunda.zeebe.broker.system.configuration.backup.S3BackupStoreConfig;
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

  private BackupStore buildBackupStore(final BackupStoreCfg backupCfg) {
    final var store = backupCfg.getStore();
    return switch (store) {
      case S3 -> buildS3BackupStore(backupCfg);
      case GCS -> buildGcsBackupStore(backupCfg);
      case NONE -> throw new IllegalArgumentException(
          "No backup store configured, cannot restore from backup.");
    };
  }

  private static S3BackupStore buildS3BackupStore(final BackupStoreCfg backupStoreCfg) {
    final var storeConfig = S3BackupStoreConfig.toStoreConfig(backupStoreCfg.getS3());
    return new S3BackupStore(storeConfig);
  }

  private static GcsBackupStore buildGcsBackupStore(final BackupStoreCfg backupStoreCfg) {
    final var storeConfig = GCSBackupStoreConfig.toStoreConfig(backupStoreCfg.getGcs());
    return new GcsBackupStore(storeConfig);
  }
}
