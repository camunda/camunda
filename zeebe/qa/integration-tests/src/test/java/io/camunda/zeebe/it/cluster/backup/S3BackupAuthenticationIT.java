/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.it.cluster.backup;

import io.camunda.configuration.PrimaryStorageBackup.BackupStoreType;
import io.camunda.configuration.SecondaryStorage.SecondaryStorageType;
import io.camunda.container.CamundaContainer.BrokerContainer;
import io.camunda.management.backups.StateCode;
import io.camunda.zeebe.backup.s3.S3BackupConfig.Builder;
import io.camunda.zeebe.backup.s3.S3BackupStore;
import io.camunda.zeebe.qa.util.actuator.BackupActuator;
import io.camunda.zeebe.qa.util.testcontainers.ZeebeTestContainerDefaults;
import io.camunda.zeebe.test.testcontainers.S3MockTestContainer;
import io.camunda.zeebe.test.util.junit.RegressionTest;
import java.time.Duration;
import org.apache.commons.lang3.RandomStringUtils;
import org.assertj.core.api.Assertions;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.Network;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
final class S3BackupAuthenticationIT {
  private static final Network NETWORK = Network.newNetwork();
  private static final String BUCKET_NAME = RandomStringUtils.randomAlphabetic(10).toLowerCase();
  private static final String S3_NETWORK_ALIAS = "s3mock";

  @Container private static final S3MockTestContainer S3 = new S3MockTestContainer();

  static {
    S3.withNetwork(NETWORK).withNetworkAliases(S3_NETWORK_ALIAS);
  }

  @BeforeAll
  static void setupBucket() {
    final var config =
        new Builder()
            .withBucketName(BUCKET_NAME)
            .withEndpoint(S3.externalEndpoint())
            .withRegion(S3.region())
            .withCredentials(S3.accessKey(), S3.secretKey())
            .forcePathStyleAccess(true)
            .build();
    try (final var client = S3BackupStore.buildClient(config)) {
      Awaitility.await("until bucket is created")
          .untilAsserted(() -> client.createBucket(cfg -> cfg.bucket(BUCKET_NAME)).join());
    }
  }

  @Test
  @RegressionTest("https://github.com/camunda/camunda/issues/12433")
  void shouldConnectWithoutConfiguredCredentials() {
    // given
    final var zeebe =
        new BrokerContainer(ZeebeTestContainerDefaults.defaultTestImage())
            .withNetwork(NETWORK)
            .dependsOn(S3)
            .withoutTopologyCheck()
            .withUnifiedConfig(
                cfg -> {
                  cfg.getData().getSecondaryStorage().setType(SecondaryStorageType.none);
                  final var s3Config = cfg.getData().getPrimaryStorage().getBackup().getS3();
                  cfg.getData().getPrimaryStorage().getBackup().setStore(BackupStoreType.S3);
                  s3Config.setBucketName(BUCKET_NAME);
                  s3Config.setEndpoint(S3.internalEndpoint(S3_NETWORK_ALIAS));
                  s3Config.setRegion(S3.region());
                  s3Config.setForcePathStyleAccess(true);
                })
            .withEnv("AWS_ACCESS_KEY_ID", S3.accessKey())
            .withEnv("AWS_SECRET_ACCESS_KEY", S3.secretKey())
            .withEnv("MANAGEMENT_ENDPOINTS_WEB_EXPOSURE_INCLUDE", "*");

    // when
    zeebe.start();

    // then
    Assertions.assertThat(zeebe.isStarted()).isTrue();

    final var backupActuator = BackupActuator.of(zeebe);
    final var backupId = 1L;
    backupActuator.take(backupId);
    Awaitility.await("backup taken with the discovered credentials must complete")
        .atMost(Duration.ofSeconds(30))
        .ignoreExceptions()
        .untilAsserted(
            () ->
                Assertions.assertThat(backupActuator.status(backupId).getState())
                    .isEqualTo(StateCode.COMPLETED));

    // cleanup
    zeebe.close();
  }
}
