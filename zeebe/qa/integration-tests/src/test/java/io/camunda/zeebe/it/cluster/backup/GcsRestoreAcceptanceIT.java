/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.it.cluster.backup;

import com.google.cloud.storage.BucketInfo;
import io.camunda.configuration.Camunda;
import io.camunda.configuration.Gcs;
import io.camunda.zeebe.backup.gcs.GcsBackupConfig;
import io.camunda.zeebe.backup.gcs.GcsBackupStore;
import io.camunda.zeebe.broker.system.configuration.BrokerCfg;
import io.camunda.zeebe.broker.system.configuration.backup.BackupStoreCfg.BackupStoreType;
import io.camunda.zeebe.broker.system.configuration.backup.GcsBackupStoreConfig;
import io.camunda.zeebe.broker.system.configuration.backup.GcsBackupStoreConfig.GcsBackupStoreAuth;
import io.camunda.zeebe.test.testcontainers.GcsContainer;
import org.apache.commons.lang3.RandomStringUtils;
import org.junit.jupiter.api.BeforeAll;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
final class GcsRestoreAcceptanceIT implements RestoreAcceptance {
  private static final String BUCKET_NAME = RandomStringUtils.randomAlphabetic(10).toLowerCase();

  @Container private static final GcsContainer GCS = new GcsContainer();

  @BeforeAll
  static void setupBucket() throws Exception {
    final var config =
        new GcsBackupConfig.Builder()
            .withoutAuthentication()
            .withHost(GCS.externalEndpoint())
            .withBucketName(BUCKET_NAME)
            .build();

    try (final var client = GcsBackupStore.buildClient(config)) {
      client.create(BucketInfo.of(BUCKET_NAME));
    }
  }

  @Override
  public void configureBackupStore(final BrokerCfg cfg) {
    final var backup = cfg.getData().getBackup();
    backup.setStore(BackupStoreType.GCS);

    final var config = new GcsBackupStoreConfig();
    config.setAuth(GcsBackupStoreAuth.NONE);
    config.setBucketName(BUCKET_NAME);
    config.setHost(GCS.externalEndpoint());
    backup.setGcs(config);
  }

  @Override
  public void configureBackupStore(final Camunda cfg) {
    final var backup = cfg.getData().getBackup();
    backup.setStore(io.camunda.configuration.Backup.BackupStoreType.GCS);

    final var config = backup.getGcs();
    config.setAuth(Gcs.GcsBackupStoreAuth.NONE);
    config.setBucketName(BUCKET_NAME);
    config.setHost(GCS.externalEndpoint());
  }
}
