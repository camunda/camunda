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
import io.camunda.zeebe.client.ZeebeClient;
import io.camunda.zeebe.qa.util.actuator.BackupActuator;
import io.camunda.zeebe.qa.util.cluster.TestRestoreApp;
import io.camunda.zeebe.qa.util.testcontainers.MinioContainer;
import io.camunda.zeebe.qa.util.testcontainers.ZeebeTestContainerDefaults;
import io.camunda.zeebe.restore.BackupNotFoundException;
import io.camunda.zeebe.shared.management.openapi.models.BackupInfo;
import io.camunda.zeebe.shared.management.openapi.models.StateCode;
import io.camunda.zeebe.shared.management.openapi.models.TakeBackupResponse;
import io.zeebe.containers.ZeebeContainer;
import java.time.Duration;
import org.apache.commons.lang3.RandomStringUtils;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.Network;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
final class S3RestoreAcceptanceIT {
  private static final Network NETWORK = Network.newNetwork();
  private static final String BUCKET_NAME = RandomStringUtils.randomAlphabetic(10).toLowerCase();

  private static final long BACKUP_ID = 1;

  @Container
  private static final MinioContainer MINIO =
      new MinioContainer().withNetwork(NETWORK).withDomain("minio.local", BUCKET_NAME);

  @Container
  private final ZeebeContainer zeebe =
      new ZeebeContainer(ZeebeTestContainerDefaults.defaultTestImage())
          .withNetwork(NETWORK)
          .dependsOn(MINIO)
          .withoutTopologyCheck()
          .withEnv("MANAGEMENT_ENDPOINTS_WEB_EXPOSURE_INCLUDE", "*")
          .withEnv("ZEEBE_BROKER_DATA_BACKUP_STORE", "S3")
          .withEnv("ZEEBE_BROKER_DATA_BACKUP_S3_BUCKETNAME", BUCKET_NAME)
          .withEnv("ZEEBE_BROKER_DATA_BACKUP_S3_ENDPOINT", MINIO.internalEndpoint())
          .withEnv("ZEEBE_BROKER_DATA_BACKUP_S3_REGION", MINIO.region())
          .withEnv("ZEEBE_BROKER_DATA_BACKUP_S3_ACCESSKEY", MINIO.accessKey())
          .withEnv("ZEEBE_BROKER_DATA_BACKUP_S3_SECRETKEY", MINIO.secretKey());

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
    final var actuator = BackupActuator.of(zeebe);
    try (final var client =
        ZeebeClient.newClientBuilder()
            .gatewayAddress(zeebe.getExternalGatewayAddress())
            .usePlaintext()
            .build()) {
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
    final var actuator = BackupActuator.of(zeebe);
    try (final var client =
        ZeebeClient.newClientBuilder()
            .gatewayAddress(zeebe.getExternalGatewayAddress())
            .usePlaintext()
            .build()) {
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
      assertThatExceptionOfType(IllegalStateException.class)
          .isThrownBy(() -> restore.start())
          .havingRootCause()
          .isInstanceOf(BackupNotFoundException.class);
    }
  }
}
