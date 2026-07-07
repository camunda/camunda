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

  // All three stores share one GCS bucket but use distinct prefixes, so the isolation tests
  // exercise prefix-level separation rather than trivially separate buckets.
  private static final String SHARED_BUCKET = "shared-bucket";

  @Container private static final GcsContainer GCS = new GcsContainer();

  @SuppressWarnings("resource") // lifecycle managed by @TestZeebe
  @TestZeebe(autoStart = false, purgeAfterEach = false)
  private static final TestStandaloneBroker BROKER =
      TENANTS.configure(new TestStandaloneBroker().withUnauthenticatedAccess());

  @BeforeAll
  static void setUp() throws Exception {
    final var config =
        new GcsBackupConfig.Builder()
            .withBucketName(SHARED_BUCKET)
            .withHost(GCS.externalEndpoint())
            .withoutAuthentication()
            .build();
    try (final var client = GcsBackupStore.buildClient(config)) {
      client.create(BucketInfo.of(SHARED_BUCKET));
    }

    // Redirect GcpDocumentStoreProvider to the local fake-gcs-server.
    GcpDocumentStoreProvider.setStorageOverrideForTests(
        () ->
            StorageOptions.newBuilder()
                .setHost(GCS.externalEndpoint())
                .setCredentials(NoCredentials.getInstance())
                .build()
                .getService());

    BROKER
        .withProperty("camunda.document.gcp.store-default.bucket-name", SHARED_BUCKET)
        .withProperty("camunda.document.gcp.store-default.prefix", "")
        .withProperty("camunda.document.default-store-id", STORE_DEFAULT)
        .withProperty(
            "camunda.physical-tenants.tenanta.document.gcp.store-a.bucket-name", SHARED_BUCKET)
        .withProperty("camunda.physical-tenants.tenanta.document.gcp.store-a.prefix", "tenanta/")
        .withProperty("camunda.physical-tenants.tenanta.document.default-store-id", STORE_A)
        .withProperty("camunda.physical-tenants.tenanta.document.assigned[0]", STORE_A)
        .withProperty(
            "camunda.physical-tenants.tenantb.document.gcp.store-b.bucket-name", SHARED_BUCKET)
        .withProperty("camunda.physical-tenants.tenantb.document.gcp.store-b.prefix", "tenantb/")
        .withProperty("camunda.physical-tenants.tenantb.document.default-store-id", STORE_B)
        .withProperty("camunda.physical-tenants.tenantb.document.assigned[0]", STORE_B)
        .withProperty(
            "camunda.physical-tenants.tenantc.document.gcp.store-c.bucket-name", SHARED_BUCKET)
        .withProperty("camunda.physical-tenants.tenantc.document.gcp.store-c.prefix", "tenantc/")
        .withProperty("camunda.physical-tenants.tenantc.document.default-store-id", STORE_C)
        .withProperty("camunda.physical-tenants.tenantc.document.assigned[0]", STORE_C)
        .start();

    startClients(BROKER);
  }

  @AfterAll
  static void tearDown() {
    closeClients();
    GcpDocumentStoreProvider.clearStorageOverrideForTests();
  }
}
