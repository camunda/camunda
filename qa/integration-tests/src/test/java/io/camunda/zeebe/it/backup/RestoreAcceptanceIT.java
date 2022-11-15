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

import io.camunda.zeebe.backup.s3.S3BackupConfig;
import io.camunda.zeebe.backup.s3.S3BackupStore;
import io.camunda.zeebe.client.ZeebeClient;
import io.camunda.zeebe.qa.util.actuator.BackupActuator;
import io.camunda.zeebe.qa.util.actuator.BackupActuator.TakeBackupResponse;
import io.camunda.zeebe.qa.util.testcontainers.ContainerLogsDumper;
import io.camunda.zeebe.qa.util.testcontainers.MinioContainer;
import io.camunda.zeebe.qa.util.testcontainers.ZeebeTestContainerDefaults;
import io.camunda.zeebe.shared.management.openapi.models.BackupInfo;
import io.camunda.zeebe.shared.management.openapi.models.StateCode;
import io.zeebe.containers.ZeebeContainer;
import java.time.Duration;
import java.util.Map;
import org.apache.commons.lang3.RandomStringUtils;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.testcontainers.containers.ContainerLaunchException;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.startupcheck.OneShotStartupCheckStrategy;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
final class RestoreAcceptanceIT {
  private static final Network NETWORK = Network.newNetwork();
  private static final String BUCKET_NAME = RandomStringUtils.randomAlphabetic(10).toLowerCase();

  private static final int BACKUP_ID = 1;

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
          .withEnv("MANAGEMENT_ENDPOINTS_BACKUPS_ENABLED", "true")
          .withEnv("ZEEBE_BROKER_EXPERIMENTAL_FEATURES_ENABLEBACKUP", "true")
          .withEnv("ZEEBE_BROKER_DATA_BACKUP_STORE", "S3")
          .withEnv("ZEEBE_BROKER_DATA_BACKUP_S3_BUCKETNAME", BUCKET_NAME)
          .withEnv("ZEEBE_BROKER_DATA_BACKUP_S3_ENDPOINT", MINIO.internalEndpoint())
          .withEnv("ZEEBE_BROKER_DATA_BACKUP_S3_REGION", MINIO.region())
          .withEnv("ZEEBE_BROKER_DATA_BACKUP_S3_ACCESSKEY", MINIO.accessKey())
          .withEnv("ZEEBE_BROKER_DATA_BACKUP_S3_SECRETKEY", MINIO.secretKey());

  // No `@Container` annotation because we want to start this on demand
  @SuppressWarnings("resource")
  private final GenericContainer<?> restore =
      new GenericContainer<>(ZeebeTestContainerDefaults.defaultTestImage())
          .withNetwork(NETWORK)
          .dependsOn(MINIO)
          .withStartupCheckStrategy(
              new OneShotStartupCheckStrategy().withTimeout(Duration.ofMinutes(1)))
          .withEnv("ZEEBE_RESTORE", "true")
          .withEnv("ZEEBE_RESTORE_FROM_BACKUP_ID", Integer.toString(BACKUP_ID))
          .withEnv("ZEEBE_BROKER_EXPERIMENTAL_FEATURES_ENABLEBACKUP", "true")
          .withEnv("ZEEBE_BROKER_DATA_BACKUP_STORE", "S3")
          .withEnv("ZEEBE_BROKER_DATA_BACKUP_S3_BUCKETNAME", BUCKET_NAME)
          .withEnv("ZEEBE_BROKER_DATA_BACKUP_S3_ENDPOINT", MINIO.internalEndpoint())
          .withEnv("ZEEBE_BROKER_DATA_BACKUP_S3_REGION", MINIO.region())
          .withEnv("ZEEBE_BROKER_DATA_BACKUP_S3_ACCESSKEY", MINIO.accessKey())
          .withEnv("ZEEBE_BROKER_DATA_BACKUP_S3_SECRETKEY", MINIO.secretKey());

  @RegisterExtension
  @SuppressWarnings("unused")
  final ContainerLogsDumper logsWatcher = new ContainerLogsDumper(() -> Map.of("restore", restore));

  @BeforeAll
  static void setupBucket() {
    final var config =
        S3BackupConfig.from(
            BUCKET_NAME,
            MINIO.externalEndpoint(),
            MINIO.region(),
            MINIO.accessKey(),
            MINIO.secretKey(),
            Duration.ofSeconds(25),
            true);
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
    assertThat(response).isEqualTo(new TakeBackupResponse(BACKUP_ID));
    Awaitility.await("until a backup exists with the given ID")
        .atMost(Duration.ofSeconds(30))
        .untilAsserted(
            () -> {
              final var status = actuator.status(response.id());
              assertThat(status)
                  .extracting(BackupInfo::getBackupId, BackupInfo::getState)
                  .containsExactly(1L, StateCode.COMPLETED);
            });

    // then
    assertThatNoException().isThrownBy(restore::start);
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
    assertThat(response).isEqualTo(new TakeBackupResponse(BACKUP_ID));
    Awaitility.await("until a backup exists with the given ID")
        .atMost(Duration.ofSeconds(30))
        .untilAsserted(
            () -> {
              final var status = actuator.status(response.id());
              assertThat(status)
                  .extracting(BackupInfo::getBackupId, BackupInfo::getState)
                  .containsExactly(1L, StateCode.COMPLETED);
            });

    // then -- restore container exits with an error code
    // we can't check the exit code directly, but we can observe that testcontainers was unable
    // to start the container.
    assertThatExceptionOfType(ContainerLaunchException.class)
        .isThrownBy(
            () ->
                restore
                    .withStartupAttempts(1)
                    .withEnv("ZEEBE_RESTORE_FROM_BACKUP_ID", "1234")
                    .withStartupCheckStrategy(
                        new OneShotStartupCheckStrategy().withTimeout(Duration.ofSeconds(10)))
                    .start());
  }
}
