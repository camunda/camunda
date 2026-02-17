/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.test.backup;

import com.google.cloud.storage.BucketInfo;
import io.camunda.configuration.Camunda;
import io.camunda.configuration.Gcs;
import io.camunda.configuration.PrimaryStorageBackup;
import io.camunda.zeebe.backup.gcs.GcsBackupConfig;
import io.camunda.zeebe.backup.gcs.GcsBackupStore;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration;
import io.camunda.zeebe.test.testcontainers.GcsContainer;
import java.util.Map;
import org.apache.commons.lang3.RandomStringUtils;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.extension.AfterAllCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.testcontainers.containers.Network;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Cross-version backup compatibility test for GCS (using fake-gcs-server).
 *
 * <p>The fake-gcs-server advertises its internal URL ({@code http://gcs:8000}) so that the old
 * broker container can perform uploads correctly. The restore app running on the host accesses the
 * server via the mapped external port — reads work regardless of the advertised URL.
 */
@Testcontainers
@ZeebeIntegration
final class GcsBackupCompatibilityIT implements BackupCompatibilityAcceptance, AfterAllCallback {
  private static final String BUCKET_NAME =
      RandomStringUtils.insecure().nextAlphabetic(10).toLowerCase();
  private static final Network NETWORK = Network.newNetwork();

  @Container
  private static final GcsContainer GCS =
      new GcsContainer().withNetwork(NETWORK).withNetworkAlias("gcs");

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

  @Override
  public Network getNetwork() {
    return NETWORK;
  }

  @Override
  public Map<String, String> backupStoreEnvVars() {
    return Map.of(
        "ZEEBE_BROKER_DATA_BACKUP_STORE",
        "GCS",
        "ZEEBE_BROKER_DATA_BACKUP_GCS_BUCKETNAME",
        BUCKET_NAME,
        "ZEEBE_BROKER_DATA_BACKUP_GCS_HOST",
        GCS.internalEndpoint(),
        "ZEEBE_BROKER_DATA_BACKUP_GCS_AUTH",
        "NONE");
  }

  @Override
  public void configureBackupStore(final Camunda cfg) {
    final var backup = cfg.getData().getPrimaryStorage().getBackup();
    backup.setStore(PrimaryStorageBackup.BackupStoreType.GCS);

    final var gcs = backup.getGcs();
    gcs.setAuth(Gcs.GcsBackupStoreAuth.NONE);
    gcs.setBucketName(BUCKET_NAME);
    gcs.setHost(GCS.externalEndpoint());
  }

  @Override
  public void afterAll(final ExtensionContext context) {
    NETWORK.close();
  }
}
