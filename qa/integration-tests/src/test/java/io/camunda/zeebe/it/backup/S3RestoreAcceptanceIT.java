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
import io.camunda.zeebe.broker.system.configuration.backup.S3BackupStoreConfig;
import io.camunda.zeebe.qa.util.actuator.BackupActuator;
import io.camunda.zeebe.qa.util.cluster.TestRestoreApp;
import io.camunda.zeebe.qa.util.cluster.TestStandaloneBroker;
import io.camunda.zeebe.qa.util.cluster.junit.ManageTestNodes;
import io.camunda.zeebe.qa.util.cluster.junit.ManageTestNodes.TestNode;
import io.camunda.zeebe.qa.util.testcontainers.MinioContainer;
import io.camunda.zeebe.restore.BackupNotFoundException;
import io.camunda.zeebe.shared.management.openapi.models.BackupInfo;
import io.camunda.zeebe.shared.management.openapi.models.StateCode;
import io.camunda.zeebe.shared.management.openapi.models.TakeBackupResponse;
import io.camunda.zeebe.test.util.junit.AutoCloseResources;
import io.camunda.zeebe.test.util.junit.AutoCloseResources.AutoCloseResource;
import java.time.Duration;
import org.apache.commons.lang3.RandomStringUtils;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
@ManageTestNodes
@AutoCloseResources
final class S3RestoreAcceptanceIT {
  private static final long BACKUP_ID = 1;

  @Container
  private static final MinioContainer MINIO = new MinioContainer().withDomain("minio.local");

  private static final String BUCKET_NAME = RandomStringUtils.randomAlphabetic(10).toLowerCase();

  @TestNode
  private final TestStandaloneBroker zeebe =
      new TestStandaloneBroker()
          .withEnv("management.endpoints.web.exposure.include", "*")
          .withBrokerConfig(this::configureBroker);

  @AutoCloseResource
  private final TestRestoreApp restore =
      new TestRestoreApp().withBackupId(BACKUP_ID).withBrokerConfig(this::configureBroker);

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

    // when
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

    // then
    assertThatNoException().isThrownBy(restore::start);
  }

  @Test
  void shouldFailForNonExistingBackup() {
    // given
    final var actuator = BackupActuator.ofAddress(zeebe.monitoringAddress());
    try (final var client = zeebe.newClientBuilder().build()) {
      client.newPublishMessageCommand().messageName("name").correlationKey("key").send().join();
    }

    // when
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

    // then -- restore container exits with an error code
    // we can't check the exit code directly, but we can observe that testcontainers was unable
    // to start the container.
    assertThatExceptionOfType(IllegalStateException.class)
        .isThrownBy(() -> restore.withBackupId(1234).start())
        .havingRootCause()
        .isInstanceOf(BackupNotFoundException.class);
  }

  private void configureBroker(final BrokerCfg cfg) {
    final var config = new S3BackupStoreConfig();
    config.setAccessKey(MINIO.accessKey());
    config.setEndpoint(MINIO.externalEndpoint());
    config.setBucketName(BUCKET_NAME);
    config.setSecretKey(MINIO.secretKey());
    config.setRegion(MINIO.region());
    config.setForcePathStyleAccess(true);

    cfg.getData().getBackup().setStore(BackupStoreType.S3);
    cfg.getData().getBackup().setS3(config);
  }
}
