/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.it.backup;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatNoException;

import io.camunda.zeebe.backup.s3.S3BackupConfig.Builder;
import io.camunda.zeebe.backup.s3.S3BackupStore;
import io.camunda.zeebe.broker.system.configuration.BrokerCfg;
import io.camunda.zeebe.broker.system.configuration.backup.BackupStoreCfg.BackupStoreType;
import io.camunda.zeebe.management.backups.BackupInfo;
import io.camunda.zeebe.management.backups.StateCode;
import io.camunda.zeebe.management.backups.TakeBackupResponse;
import io.camunda.zeebe.qa.util.actuator.BackupActuator;
import io.camunda.zeebe.qa.util.cluster.TestRestoreApp;
import io.camunda.zeebe.qa.util.cluster.TestStandaloneBroker;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration.TestZeebe;
import io.camunda.zeebe.qa.util.testcontainers.MinioContainer;
import io.camunda.zeebe.restore.BackupNotFoundException;
import java.time.Duration;
import java.util.concurrent.CompletionException;
import org.apache.commons.lang3.RandomStringUtils;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
@ZeebeIntegration
final class S3RestoreAcceptanceIT {
  private static final String BUCKET_NAME = RandomStringUtils.randomAlphabetic(10).toLowerCase();

  private static final long BACKUP_ID = 1;

  @Container
  private static final MinioContainer MINIO =
      new MinioContainer().withDomain("minio.local", BUCKET_NAME);

  @TestZeebe(awaitCompleteTopology = false)
  private final TestStandaloneBroker zeebe =
      new TestStandaloneBroker()
          .withProperty("management.endpoints.web.exposure.include", "*")
          .withBrokerConfig(
              cfg -> {
                final var backup = cfg.getData().getBackup();
                final var s3 = backup.getS3();

                backup.setStore(BackupStoreType.S3);
                s3.setBucketName(BUCKET_NAME);
                s3.setEndpoint(MINIO.externalEndpoint());
                s3.setRegion(MINIO.region());
                s3.setAccessKey(MINIO.accessKey());
                s3.setSecretKey(MINIO.secretKey());
                s3.setForcePathStyleAccess(true);
              });

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

  @Test
  void shouldRunRestore() {
    // given
    final var actuator = BackupActuator.ofAddress(zeebe.monitoringAddress());
    try (final var client = zeebe.newClientBuilder().build()) {
      client.newPublishMessageCommand().messageName("name").correlationKey("key").send().join();
    }
    final var response = actuator.take(BACKUP_ID);
    assertThat(response).isInstanceOf(TakeBackupResponse.class);
    Awaitility.await("until a backup exists with the given ID")
        .atMost(Duration.ofSeconds(30))
        .ignoreExceptions() // 404 NOT_FOUND throws exception
        .untilAsserted(
            () -> {
              final var status = actuator.status(BACKUP_ID);
              assertThat(status)
                  .extracting(BackupInfo::getBackupId, BackupInfo::getState)
                  .containsExactly(1L, StateCode.COMPLETED);
            });

    // when
    try (final var restore = new TestRestoreApp()) {
      restore.withBackupId(BACKUP_ID).withBrokerConfig(this::configureBackupStore);

      // then
      assertThatNoException().isThrownBy(() -> restore.start());
    }
  }

  private void configureBackupStore(final BrokerCfg config) {
    final var backup = config.getData().getBackup();
    backup.setStore(BackupStoreType.S3);

    final var s3 = backup.getS3();
    s3.setRegion(MINIO.region());
    s3.setSecretKey(MINIO.secretKey());
    s3.setBucketName(BUCKET_NAME);
    s3.setEndpoint(MINIO.externalEndpoint());
    s3.setAccessKey(MINIO.accessKey());
    s3.setForcePathStyleAccess(true);
  }

  @Test
  void shouldFailForNonExistingBackup() {
    // given
    final var actuator = BackupActuator.ofAddress(zeebe.monitoringAddress());
    try (final var client = zeebe.newClientBuilder().build()) {
      client.newPublishMessageCommand().messageName("name").correlationKey("key").send().join();
    }
    final var response = actuator.take(BACKUP_ID);
    assertThat(response).isInstanceOf(TakeBackupResponse.class);
    Awaitility.await("until a backup exists with the given ID")
        .atMost(Duration.ofSeconds(30))
        .ignoreExceptions() // 404 NOT_FOUND throws exception
        .untilAsserted(
            () -> {
              final var status = actuator.status(BACKUP_ID);
              assertThat(status)
                  .extracting(BackupInfo::getBackupId, BackupInfo::getState)
                  .containsExactly(1L, StateCode.COMPLETED);
            });

    // when
    try (final var restore = new TestRestoreApp()) {
      restore.withBackupId(1234).withBrokerConfig(this::configureBackupStore);

      // then
      assertThatExceptionOfType(CompletionException.class)
          .isThrownBy(() -> restore.start())
          .havingRootCause()
          .isInstanceOf(BackupNotFoundException.class);
    }
  }
}
