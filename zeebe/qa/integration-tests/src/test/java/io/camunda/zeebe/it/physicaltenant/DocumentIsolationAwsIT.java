/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.it.physicaltenant;

import io.camunda.zeebe.backup.s3.S3BackupConfig.Builder;
import io.camunda.zeebe.backup.s3.S3BackupStore;
import io.camunda.zeebe.qa.util.cluster.TestStandaloneBroker;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration.TestZeebe;
import io.camunda.zeebe.test.testcontainers.MinioContainer;
import java.time.Duration;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.testcontainers.containers.Network;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
@ZeebeIntegration
final class DocumentIsolationAwsIT extends AbstractDocumentIsolationIT {

  private static final String BUCKET_A = "bucket-a";
  private static final String BUCKET_B = "bucket-b";
  private static final String USER_DEFAULT = "store-default-user";
  private static final String USER_A = "store-a-user";
  private static final String USER_B = "store-b-user";
  private static final String USER_C = "store-c-user";
  private static final Network NETWORK = Network.newNetwork();

  @Container
  private static final MinioContainer MINIO =
      new MinioContainer().withNetwork(NETWORK).withDomain("minio.local", BUCKET_A, BUCKET_B);

  @SuppressWarnings("resource") // lifecycle managed by @TestZeebe
  @TestZeebe(autoStart = false, purgeAfterEach = false)
  private static final TestStandaloneBroker BROKER =
      TENANTS.configure(new TestStandaloneBroker().withUnauthenticatedAccess());

  @BeforeAll
  static void setUp() {
    final var config =
        new Builder()
            .withBucketName(BUCKET_A)
            .withEndpoint(MINIO.externalEndpoint())
            .withRegion(MINIO.region())
            .withCredentials(MINIO.accessKey(), MINIO.secretKey())
            .withApiCallTimeout(Duration.ofSeconds(25))
            .forcePathStyleAccess(true)
            .build();
    try (final var client = S3BackupStore.buildClient(config)) {
      client.createBucket(cfg -> cfg.bucket(BUCKET_A)).join();
      client.createBucket(cfg -> cfg.bucket(BUCKET_B)).join();
    }

    // The root key pair is never handed to the broker, which makes the per-store credentials
    // load-bearing: dropping them falls back to the AWS SDK default chain, which has nothing to
    // offer here, and a store built with a sibling's pair is refused with AccessDenied. It cannot
    // separate store-a from store-c, which share bucket-a — those stay isolated by prefix alone,
    // as the class javadoc of AbstractDocumentIsolationIT describes.
    MINIO.createScopedUser(USER_DEFAULT, USER_DEFAULT + "-secret", BUCKET_B);
    MINIO.createScopedUser(USER_A, USER_A + "-secret", BUCKET_A);
    MINIO.createScopedUser(USER_B, USER_B + "-secret", BUCKET_B);
    MINIO.createScopedUser(USER_C, USER_C + "-secret", BUCKET_A);

    // every store is configured with its own endpoint, region and credentials, so nothing is read
    // from the process environment. An IP-based endpoint forces path-style access in the AWS SDK
    // v2.
    final String minioEndpoint = "http://127.0.0.1:" + MINIO.getMappedPort(9000);

    BROKER
        .withProperty("camunda.document.aws.store-default.bucket-name", BUCKET_B)
        .withProperty("camunda.document.aws.store-default.bucket-path", "default/")
        .withProperty("camunda.document.default-store-id", STORE_DEFAULT)
        .withProperty("camunda.physical-tenants.tenanta.document.aws.store-a.bucket-name", BUCKET_A)
        .withProperty(
            "camunda.physical-tenants.tenanta.document.aws.store-a.bucket-path", "tenanta/")
        .withProperty(
            "camunda.physical-tenants.tenanta.document.aws.store-a.region", MINIO.region())
        .withProperty("camunda.physical-tenants.tenanta.document.default-store-id", STORE_A)
        .withProperty("camunda.physical-tenants.tenanta.document.assigned[0]", STORE_A)
        .withProperty("camunda.physical-tenants.tenantb.document.aws.store-b.bucket-name", BUCKET_B)
        .withProperty(
            "camunda.physical-tenants.tenantb.document.aws.store-b.bucket-path", "tenantb/")
        .withProperty(
            "camunda.physical-tenants.tenantb.document.aws.store-b.region", MINIO.region())
        .withProperty("camunda.physical-tenants.tenantb.document.default-store-id", STORE_B)
        .withProperty("camunda.physical-tenants.tenantb.document.assigned[0]", STORE_B)
        .withProperty("camunda.physical-tenants.tenantc.document.aws.store-c.bucket-name", BUCKET_A)
        .withProperty(
            "camunda.physical-tenants.tenantc.document.aws.store-c.bucket-path", "tenantc/")
        .withProperty("camunda.physical-tenants.tenantc.document.default-store-id", STORE_C)
        .withProperty("camunda.physical-tenants.tenantc.document.assigned[0]", STORE_C);

    withStoreConnection(BROKER, "camunda.document.aws.store-default", minioEndpoint, USER_DEFAULT);
    withStoreConnection(
        BROKER, "camunda.physical-tenants.tenanta.document.aws.store-a", minioEndpoint, USER_A);
    withStoreConnection(
        BROKER, "camunda.physical-tenants.tenantb.document.aws.store-b", minioEndpoint, USER_B);
    withStoreConnection(
        BROKER, "camunda.physical-tenants.tenantc.document.aws.store-c", minioEndpoint, USER_C);

    BROKER.start();

    startClients(BROKER);
  }

  /**
   * Points a single document store at Minio with the key pair of the user created for it.
   * Configuring credentials per store — rather than through the AWS SDK's process-wide environment
   * — is what lets each physical tenant reach its storage under its own identity.
   */
  private static void withStoreConnection(
      final TestStandaloneBroker broker,
      final String storePrefix,
      final String endpoint,
      final String user) {
    broker
        .withProperty(storePrefix + ".endpoint", endpoint)
        .withProperty(storePrefix + ".region", MINIO.region())
        .withProperty(storePrefix + ".access-key", user)
        .withProperty(storePrefix + ".secret-key", user + "-secret");
  }

  @AfterAll
  static void tearDown() {
    closeClients();
    NETWORK.close();
  }
}
