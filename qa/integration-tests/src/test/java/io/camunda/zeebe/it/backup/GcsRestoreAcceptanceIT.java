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

import com.google.cloud.storage.BucketInfo;
import io.camunda.zeebe.backup.gcs.GcsBackupConfig;
import io.camunda.zeebe.backup.gcs.GcsBackupStore;
import io.camunda.zeebe.client.ZeebeClient;
import io.camunda.zeebe.qa.util.actuator.BackupActuator;
import io.camunda.zeebe.qa.util.testcontainers.GcsContainer;
import io.camunda.zeebe.qa.util.testcontainers.ZeebeTestContainerDefaults;
import io.camunda.zeebe.shared.management.openapi.models.BackupInfo;
import io.camunda.zeebe.shared.management.openapi.models.StateCode;
import io.camunda.zeebe.shared.management.openapi.models.TakeBackupResponse;
import io.zeebe.containers.ZeebeContainer;
import java.time.Duration;
import org.apache.commons.lang3.RandomStringUtils;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.ContainerLaunchException;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.output.Slf4jLogConsumer;
import org.testcontainers.containers.startupcheck.OneShotStartupCheckStrategy;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
final class GcsRestoreAcceptanceIT {
  private static final Logger LOG = LoggerFactory.getLogger(GcsRestoreAcceptanceIT.class);
  private static final Network NETWORK = Network.newNetwork();
  private static final String BUCKET_NAME = RandomStringUtils.randomAlphabetic(10).toLowerCase();

  @Container private static final GcsContainer GCS = new GcsContainer(NETWORK, "gcs.local");

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

  @Test
  void shouldRunRestore() {
    // given
    final var backupId = 17;

    // when
    takeBackup(backupId);

    // then
    assertThatNoException().isThrownBy(() -> restoreBackup(backupId));
  }

  @Test
  void shouldFailForNonExistingBackup() {
    // then -- restore container exits with an error code
    // we can't check the exit code directly, but we can observe that testcontainers was unable
    // to start the container.
    assertThatExceptionOfType(ContainerLaunchException.class).isThrownBy(() -> restoreBackup(1234));
  }

  private void takeBackup(final long backupId) {
    try (final var zeebe =
        new ZeebeContainer(ZeebeTestContainerDefaults.defaultTestImage())
            .withLogConsumer(new Slf4jLogConsumer(LOG))
            .withNetwork(NETWORK)
            .dependsOn(GCS)
            .withoutTopologyCheck()
            .withEnv("ZEEBE_BROKER_DATA_BACKUP_STORE", "GCS")
            .withEnv("ZEEBE_BROKER_DATA_BACKUP_GCS_BUCKETNAME", BUCKET_NAME)
            .withEnv("ZEEBE_BROKER_DATA_BACKUP_GCS_AUTH", "none")
            .withEnv("ZEEBE_BROKER_DATA_BACKUP_GCS_HOST", GCS.internalEndpoint())) {
      zeebe.start();

      final var actuator = BackupActuator.of(zeebe);

      try (final var client =
          ZeebeClient.newClientBuilder()
              .gatewayAddress(zeebe.getExternalGatewayAddress())
              .usePlaintext()
              .build()) {
        client.newPublishMessageCommand().messageName("name").correlationKey("key").send().join();
      }

      assertThat(actuator.take(backupId)).isInstanceOf(TakeBackupResponse.class);
      Awaitility.await("until a backup exists with the given ID")
          .atMost(Duration.ofSeconds(60))
          .ignoreExceptions() // 404 NOT_FOUND throws exception
          .untilAsserted(
              () -> {
                final var status = actuator.status(backupId);
                assertThat(status)
                    .extracting(BackupInfo::getBackupId, BackupInfo::getState)
                    .containsExactly(backupId, StateCode.COMPLETED);
              });
    }
  }

  private void restoreBackup(final long backupId) {
    try (final var restore =
        new GenericContainer<>(ZeebeTestContainerDefaults.defaultTestImage())
            .withLogConsumer(new Slf4jLogConsumer(LOG))
            .withNetwork(NETWORK)
            .dependsOn(GCS)
            .withStartupAttempts(1)
            .withStartupCheckStrategy(
                new OneShotStartupCheckStrategy().withTimeout(Duration.ofMinutes(1)))
            .withEnv("ZEEBE_RESTORE", "true")
            .withEnv("ZEEBE_BROKER_DATA_BACKUP_STORE", "GCS")
            .withEnv("ZEEBE_BROKER_DATA_BACKUP_GCS_BUCKETNAME", BUCKET_NAME)
            .withEnv("ZEEBE_BROKER_DATA_BACKUP_GCS_AUTH", "none")
            .withEnv("ZEEBE_BROKER_DATA_BACKUP_GCS_HOST", GCS.internalEndpoint())
            .withEnv("ZEEBE_RESTORE_FROM_BACKUP_ID", String.valueOf(backupId))) {
      restore.start();
    }
  }
}
