/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.test.backup;

import io.camunda.configuration.Camunda;
import io.camunda.configuration.PrimaryStorageBackup;
import io.camunda.zeebe.backup.s3.S3BackupConfig.Builder;
import io.camunda.zeebe.backup.s3.S3BackupStore;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration;
import io.camunda.zeebe.test.testcontainers.S3MockTestContainer;
import java.time.Duration;
import java.util.Map;
import org.apache.commons.lang3.RandomStringUtils;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.extension.AfterAllCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.testcontainers.containers.Network;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
@ZeebeIntegration
final class S3BackupCompatibilityIT implements BackupCompatibilityAcceptance, AfterAllCallback {
  private static final String BUCKET_NAME =
      RandomStringUtils.insecure().nextAlphabetic(10).toLowerCase();
  private static final Network NETWORK = Network.newNetwork();
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
            .withApiCallTimeout(Duration.ofSeconds(25))
            .forcePathStyleAccess(true)
            .build();
    try (final var client = S3BackupStore.buildClient(config)) {
      client.createBucket(cfg -> cfg.bucket(BUCKET_NAME)).join();
    }
  }

  @Override
  public Network getNetwork() {
    return NETWORK;
  }

  @Override
  public Map<String, String> oldBrokerBackupStoreEnvVars(final String storeBasePath) {
    return Map.of(
        "ZEEBE_BROKER_DATA_BACKUP_STORE", "S3",
        "ZEEBE_BROKER_DATA_BACKUP_S3_BUCKETNAME", BUCKET_NAME,
        "ZEEBE_BROKER_DATA_BACKUP_S3_BASEPATH", storeBasePath,
        "ZEEBE_BROKER_DATA_BACKUP_S3_ENDPOINT", S3.internalEndpoint(S3_NETWORK_ALIAS),
        "ZEEBE_BROKER_DATA_BACKUP_S3_REGION", S3.region(),
        "ZEEBE_BROKER_DATA_BACKUP_S3_ACCESSKEY", S3.accessKey(),
        "ZEEBE_BROKER_DATA_BACKUP_S3_SECRETKEY", S3.secretKey(),
        "ZEEBE_BROKER_DATA_BACKUP_S3_FORCEPATHSTYLEACCESS", "true",
        // Also set AWS SDK env vars as fallback for credential discovery
        "AWS_ACCESS_KEY_ID", S3.accessKey(),
        "AWS_SECRET_ACCESS_KEY", S3.secretKey());
  }

  @Override
  public void configureCurrentBackupStore(final Camunda cfg, final String storeBasePath) {
    final var backup = cfg.getData().getPrimaryStorage().getBackup();
    backup.setStore(PrimaryStorageBackup.BackupStoreType.S3);

    final var s3 = backup.getS3();
    s3.setBasePath(storeBasePath);
    s3.setRegion(S3.region());
    s3.setSecretKey(S3.secretKey());
    s3.setBucketName(BUCKET_NAME);
    s3.setEndpoint(S3.externalEndpoint());
    s3.setAccessKey(S3.accessKey());
    s3.setForcePathStyleAccess(true);
  }

  @Override
  public void afterAll(final ExtensionContext context) {
    NETWORK.close();
  }
}
