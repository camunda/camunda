/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.it.backup;

import io.camunda.zeebe.qa.util.testcontainers.ZeebeTestContainerDefaults;
import io.camunda.zeebe.test.testcontainers.S3MockTestContainer;
import io.camunda.zeebe.test.util.junit.RegressionTest;
import io.zeebe.containers.ZeebeContainer;
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
  private static final String S3_NETWORK_ALIAS = "s3mock";

  @Container private static final S3MockTestContainer S3 = new S3MockTestContainer();

  static {
    S3.withNetwork(NETWORK).withNetworkAliases(S3_NETWORK_ALIAS);
  }

  @Test
  @RegressionTest("https://github.com/camunda/camunda/issues/12433")
  void shouldConnectWithoutConfiguredCredentials() {
    // given
    final var zeebe =
        new ZeebeContainer(ZeebeTestContainerDefaults.defaultTestImage())
            .withNetwork(NETWORK)
            .dependsOn(S3)
            .withoutTopologyCheck()
            .withEnv("MANAGEMENT_ENDPOINTS_WEB_EXPOSURE_INCLUDE", "*")
            .withEnv("ZEEBE_BROKER_DATA_BACKUP_STORE", "S3")
            .withEnv("ZEEBE_BROKER_DATA_BACKUP_S3_BUCKETNAME", BUCKET_NAME)
            .withEnv("ZEEBE_BROKER_DATA_BACKUP_S3_ENDPOINT", S3.internalEndpoint(S3_NETWORK_ALIAS))
            .withEnv("ZEEBE_BROKER_DATA_BACKUP_S3_REGION", S3.region())
            .withEnv("ZEEBE_BROKER_DATA_BACKUP_S3_FORCEPATHSTYLEACCESS", "true")
            // Set env variables discovered by the AWS SDK, not Zeebe
            .withEnv("AWS_ACCESS_KEY_ID", S3.accessKey())
            .withEnv("AWS_SECRET_ACCESS_KEY", S3.secretKey());

    // when
    zeebe.start();

    // then
    Assertions.assertThat(zeebe.isStarted()).isTrue();

    // cleanup
    zeebe.close();
  }
}
