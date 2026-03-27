/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.it.cluster.backup;

import static io.camunda.configuration.beans.LegacySearchEngineSchemaManagerProperties.CREATE_SCHEMA_PROPERTY;

import io.camunda.configuration.PrimaryStorageBackup.BackupStoreType;
import io.camunda.container.CamundaContainer.BrokerContainer;
import io.camunda.zeebe.qa.util.testcontainers.ZeebeTestContainerDefaults;
import io.camunda.zeebe.test.testcontainers.MinioContainer;
import io.camunda.zeebe.test.util.junit.RegressionTest;
import org.apache.commons.lang3.RandomStringUtils;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.Network;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
final class S3BackupAuthenticationIT {
  private static final Network NETWORK = Network.newNetwork();
  private static final String BUCKET_NAME = RandomStringUtils.randomAlphabetic(10).toLowerCase();

  @Container
  private static final MinioContainer MINIO =
      new MinioContainer().withNetwork(NETWORK).withDomain("minio.local", BUCKET_NAME);

  @Test
  @RegressionTest("https://github.com/camunda/camunda/issues/12433")
  void shouldConnectWithoutConfiguredCredentials() {
    // given
    final var zeebe =
        new BrokerContainer(ZeebeTestContainerDefaults.defaultTestImage())
            .withNetwork(NETWORK)
            .dependsOn(MINIO)
            .withoutTopologyCheck()
            .withUnifiedConfig(
                cfg -> {
                  final var s3Config = cfg.getData().getPrimaryStorage().getBackup().getS3();
                  cfg.getData().getPrimaryStorage().getBackup().setStore(BackupStoreType.S3);
                  s3Config.setBucketName(BUCKET_NAME);
                  s3Config.setEndpoint(MINIO.internalEndpoint());
                  s3Config.setRegion(MINIO.region());
                  s3Config.setAccessKey(MINIO.accessKey());
                  s3Config.setSecretKey(MINIO.secretKey());
                })
            .withProperty(CREATE_SCHEMA_PROPERTY, false)
            .withEnv("MANAGEMENT_ENDPOINTS_WEB_EXPOSURE_INCLUDE", "*");

    // when
    zeebe.start();

    // then
    Assertions.assertThat(zeebe.isStarted()).isTrue();

    // cleanup
    zeebe.close();
  }
}
