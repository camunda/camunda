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
import io.camunda.zeebe.backup.s3.S3BackupConfig;
import io.camunda.zeebe.backup.s3.S3BackupConfig.Builder;
import io.camunda.zeebe.backup.s3.S3BackupStore;
import io.camunda.zeebe.broker.system.configuration.BrokerCfg;
import io.camunda.zeebe.broker.system.configuration.backup.BackupStoreCfg;
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
      case GCS -> new GcsBackupStore();
      case NONE -> throw new IllegalArgumentException(
          "No backup store configured, cannot restore from backup.");
    };
  }

  private static S3BackupStore buildS3BackupStore(final BackupStoreCfg backupStoreCfg) {
    final var s3Config = backupStoreCfg.getS3();
    final S3BackupConfig storeConfig =
        new Builder()
            .withBucketName(s3Config.getBucketName())
            .withEndpoint(s3Config.getEndpoint())
            .withRegion(s3Config.getRegion())
            .withCredentials(s3Config.getAccessKey(), s3Config.getSecretKey())
            .withApiCallTimeout(s3Config.getApiCallTimeout())
            .forcePathStyleAccess(s3Config.isForcePathStyleAccess())
            .withCompressionAlgorithm(s3Config.getCompression())
            .withBasePath(s3Config.getBasePath())
            .build();
    return new S3BackupStore(storeConfig);
  }

  private static GcsBackupStore buildGcsBackupStore(final BackupStoreCfg backupStoreCfg) {
    return new GcsBackupStore();
  }
}
