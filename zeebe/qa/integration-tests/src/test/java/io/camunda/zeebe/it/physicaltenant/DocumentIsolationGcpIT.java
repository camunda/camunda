/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.it.physicaltenant;

import com.google.cloud.NoCredentials;
import com.google.cloud.storage.BucketInfo;
import com.google.cloud.storage.StorageOptions;
import io.camunda.document.store.gcp.GcpDocumentStoreProvider;
import io.camunda.zeebe.backup.gcs.GcsBackupConfig;
import io.camunda.zeebe.backup.gcs.GcsBackupStore;
import io.camunda.zeebe.qa.util.cluster.TestStandaloneBroker;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration.TestZeebe;
import io.camunda.zeebe.test.testcontainers.GcsContainer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
@ZeebeIntegration
final class DocumentIsolationGcpIT extends AbstractDocumentIsolationIT {

  private static final String BUCKET_A = "bucket-a";
  private static final String BUCKET_B = "bucket-b";

  @Container private static final GcsContainer GCS = new GcsContainer();

  @SuppressWarnings("resource") // lifecycle managed by @TestZeebe
  @TestZeebe(autoStart = false, purgeAfterEach = false)
  private static final TestStandaloneBroker BROKER =
      TENANTS.configure(new TestStandaloneBroker().withUnauthenticatedAccess());

  @BeforeAll
  static void setUp() throws Exception {
    final var config =
        new GcsBackupConfig.Builder()
            .withBucketName(BUCKET_A)
            .withHost(GCS.externalEndpoint())
            .withoutAuthentication()
            .build();
    try (final var client = GcsBackupStore.buildClient(config)) {
      client.create(BucketInfo.of(BUCKET_A));
      client.create(BucketInfo.of(BUCKET_B));
    }

    // Redirect GcpDocumentStoreProvider to the local fake-gcs-server.
    GcpDocumentStoreProvider.storageOverride =
        () ->
            StorageOptions.newBuilder()
                .setHost(GCS.externalEndpoint())
                .setCredentials(NoCredentials.getInstance())
                .build()
                .getService();

    BROKER
        .withProperty("camunda.document.gcp.store-a.bucket-name", BUCKET_A)
        .withProperty("camunda.document.gcp.store-b.bucket-name", BUCKET_B)
        .withProperty("camunda.document.default-store-id", STORE_A)
        .withProperty("camunda.physical-tenants.tenanta.document.assigned[0]", STORE_A)
        .withProperty("camunda.physical-tenants.tenantb.document.assigned[0]", STORE_B)
        .withProperty("camunda.physical-tenants.tenantb.document.default-store-id", STORE_B)
        .start();

    startClients(BROKER);
  }

  @AfterAll
  static void tearDown() {
    closeClients();
    GcpDocumentStoreProvider.storageOverride = null;
  }
}
