/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.it.cluster.backup;

import io.camunda.configuration.Camunda;
import io.camunda.zeebe.backup.s3.S3BackupConfig.Builder;
import io.camunda.zeebe.backup.s3.S3BackupStore;
import io.camunda.zeebe.broker.system.configuration.BrokerCfg;
import io.camunda.zeebe.broker.system.configuration.backup.BackupStoreCfg.BackupStoreType;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration;
import io.camunda.zeebe.test.testcontainers.MinioContainer;
import java.time.Duration;
import org.apache.commons.lang3.RandomStringUtils;
import org.junit.jupiter.api.BeforeAll;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
@ZeebeIntegration
final class S3RestoreAcceptanceIT implements RestoreAcceptance {
  private static final String BUCKET_NAME = RandomStringUtils.randomAlphabetic(10).toLowerCase();

  @Container
  private static final MinioContainer MINIO =
      new MinioContainer().withDomain("minio.local", BUCKET_NAME);

  @BeforeAll
  static void setupBucket() {
    final var config =
        new Builder()
            .withBucketName(BUCKET_NAME)
            .withEndpoint(MINIO.externalEndpoint())
            .withRegion(MINIO.region())
            .withCredentials(MINIO.accessKey(), MINIO.secretKey())
            .withApiCallTimeout(Duration.ofSeconds(25))
            .forcePathStyleAccess(true)
            .build();
    try (final var client = S3BackupStore.buildClient(config)) {
      client.createBucket(cfg -> cfg.bucket(BUCKET_NAME)).join();
    }
  }

  @Override
  public void configureBackupStore(final BrokerCfg cfg) {
    final var backup = cfg.getData().getBackup();
    backup.setStore(BackupStoreType.S3);

    final var s3 = backup.getS3();
    s3.setRegion(MINIO.region());
    s3.setSecretKey(MINIO.secretKey());
    s3.setBucketName(BUCKET_NAME);
    s3.setEndpoint(MINIO.externalEndpoint());
    s3.setAccessKey(MINIO.accessKey());
    s3.setForcePathStyleAccess(true);
  }

  @Override
  public void configureBackupStore(final Camunda cfg) {
    final var backup = cfg.getData().getBackup();
    backup.setStore(io.camunda.configuration.Backup.BackupStoreType.S3);

    final var s3 = backup.getS3();
    s3.setRegion(MINIO.region());
    s3.setSecretKey(MINIO.secretKey());
    s3.setBucketName(BUCKET_NAME);
    s3.setEndpoint(MINIO.externalEndpoint());
    s3.setAccessKey(MINIO.accessKey());
    s3.setForcePathStyleAccess(true);
  }
}
