/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.it.physicaltenant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.camunda.client.CamundaClient;
import io.camunda.client.api.command.ProblemException;
import io.camunda.zeebe.backup.s3.S3BackupConfig.Builder;
import io.camunda.zeebe.backup.s3.S3BackupStore;
import io.camunda.zeebe.qa.util.cluster.PhysicalTenantsITHelper;
import io.camunda.zeebe.qa.util.cluster.PhysicalTenantsITHelper.Storage;
import io.camunda.zeebe.qa.util.cluster.TestStandaloneBroker;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration.TestZeebe;
import io.camunda.zeebe.test.testcontainers.MinioContainer;
import java.io.IOException;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.Network;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
@ZeebeIntegration
final class DocumentIsolationAwsIT {

  private static final String TENANT_A = "tenanta";
  private static final String TENANT_B = "tenantb";
  private static final String STORE_A = "store-a";
  private static final String STORE_B = "store-b";

  private static final String BUCKET_A = "bucket-a";
  private static final String BUCKET_B = "bucket-b";
  private static final Network NETWORK = Network.newNetwork();

  @Container
  private static final MinioContainer MINIO =
      new MinioContainer().withNetwork(NETWORK).withDomain("minio.local", BUCKET_A, BUCKET_B);

  private static final PhysicalTenantsITHelper TENANTS =
      PhysicalTenantsITHelper.builder()
          .withTenant(PhysicalTenantsITHelper.DEFAULT_TENANT_ID, Storage.none())
          .withTenant(TENANT_A, Storage.none())
          .withTenant(TENANT_B, Storage.none())
          .build();

  @SuppressWarnings("resource") // lifecycle managed by @TestZeebe
  @TestZeebe(autoStart = false, purgeAfterEach = false)
  private static final TestStandaloneBroker BROKER =
      TENANTS.configure(new TestStandaloneBroker().withUnauthenticatedAccess());

  private static CamundaClient clientA;
  private static CamundaClient clientB;

  @BeforeAll
  static void setupBucket() {
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

    // AwsDocumentStore uses S3Client.create() which reads from system properties.
    // Using an IP-based endpoint forces path-style access in the AWS SDK v2.
    final String minioEndpoint = "http://127.0.0.1:" + MINIO.getMappedPort(9000);
    System.setProperty("aws.endpointUrl", minioEndpoint);
    System.setProperty("aws.accessKeyId", MINIO.accessKey());
    System.setProperty("aws.secretAccessKey", MINIO.secretKey());
    System.setProperty("aws.region", MINIO.region());

    BROKER
        .withProperty("camunda.document.aws.store-a.bucket-name", BUCKET_A)
        .withProperty("camunda.document.aws.store-a.region", MINIO.region())
        .withProperty("camunda.document.aws.store-b.bucket-name", BUCKET_B)
        .withProperty("camunda.document.aws.store-b.region", MINIO.region())
        .withProperty("camunda.document.default-store-id", STORE_A)
        .withProperty("camunda.physical-tenants.tenanta.document.assigned[0]", STORE_A)
        .withProperty("camunda.physical-tenants.tenantb.document.assigned[0]", STORE_B)
        .withProperty("camunda.physical-tenants.tenantb.document.default-store-id", STORE_B)
        .start();

    clientA = TENANTS.newClientBuilder(BROKER, TENANT_A).build();
    clientB = TENANTS.newClientBuilder(BROKER, TENANT_B).build();
  }

  @AfterAll
  static void tearDown() {
    if (clientA != null) {
      clientA.close();
    }
    if (clientB != null) {
      clientB.close();
    }
    System.clearProperty("aws.endpointUrl");
    System.clearProperty("aws.accessKeyId");
    System.clearProperty("aws.secretAccessKey");
    System.clearProperty("aws.region");
  }

  @SuppressWarnings("resource")
  @Test
  void shouldDenyReadOfDocumentOwnedByAnotherTenant() {
    // given
    final var ref = clientA.newCreateDocumentCommand().content("hello-from-a").send().join();

    // when / then
    assertThatThrownBy(
            () ->
                clientB
                    .newDocumentContentGetRequest(ref.getDocumentId())
                    .storeId(ref.getStoreId())
                    .send()
                    .join())
        .isInstanceOf(ProblemException.class)
        .matches(e -> ((ProblemException) e).details().getStatus() == 400);
  }

  @SuppressWarnings("resource")
  @Test
  void shouldNotFindDocumentOwnedByAnotherTenantInOwnStore() {
    // given
    final var ref = clientA.newCreateDocumentCommand().content("hello-from-a").send().join();

    // when / then
    assertThatThrownBy(
            () -> clientB.newDocumentContentGetRequest(ref.getDocumentId()).send().join())
        .isInstanceOf(ProblemException.class)
        .matches(e -> ((ProblemException) e).details().getStatus() == 404);
  }

  @Test
  void shouldNotOverwriteDocumentOwnedByAnotherTenant() throws IOException {
    // given
    final String docId = "doc-" + UUID.randomUUID();
    final var refB =
        clientB
            .newCreateDocumentCommand()
            .content("original-content-b")
            .documentId(docId)
            .send()
            .join();

    // when
    clientA
        .newCreateDocumentCommand()
        .content("tampered-content-a")
        .documentId(docId)
        .send()
        .join();

    // then
    try (final var stream = clientB.newDocumentContentGetRequest(refB).send().join()) {
      assertThat(stream.readAllBytes()).isEqualTo("original-content-b".getBytes());
    }
  }

  @Test
  void shouldDenyDeleteOfDocumentOwnedByAnotherTenantFromUnassignedStore() {
    // given
    final var ref = clientA.newCreateDocumentCommand().content("protected").send().join();

    // when / then
    assertThatThrownBy(
            () ->
                clientB
                    .newDeleteDocumentCommand(ref.getDocumentId())
                    .storeId(ref.getStoreId())
                    .send()
                    .join())
        .isInstanceOf(ProblemException.class)
        .matches(e -> ((ProblemException) e).details().getStatus() == 400);
  }

  @Test
  void shouldNotDeleteDocumentOwnedByAnotherTenant() {
    // given
    final var ref = clientA.newCreateDocumentCommand().content("protected").send().join();

    // when - B's delete targets B's own bucket; blob absent there → 404, document A unaffected
    try {
      clientB.newDeleteDocumentCommand(ref.getDocumentId()).send().join();
    } catch (final Exception ignored) {
      // do nothing
    }

    // then
    assertThatCode(
            () -> {
              try (final var stream = clientA.newDocumentContentGetRequest(ref).send().join()) {
                assertThat(stream).isNotNull();
              }
            })
        .doesNotThrowAnyException();
  }

  @SuppressWarnings("resource")
  @Test
  void shouldNotExposeDocumentIdAcrossTenants() {
    // given
    final var ref = clientB.newCreateDocumentCommand().content("private-b").send().join();

    // when / then
    assertThatThrownBy(
            () -> clientA.newDocumentContentGetRequest(ref.getDocumentId()).send().join())
        .isInstanceOf(ProblemException.class)
        .matches(e -> ((ProblemException) e).details().getStatus() == 404);
  }

  @SuppressWarnings("resource")
  @Test
  void shouldNotExposeDocumentIdAcrossTenantsViaUnassignedStore() {
    // given
    final var ref = clientB.newCreateDocumentCommand().content("private-b").send().join();

    // when / then
    assertThatThrownBy(
            () ->
                clientA
                    .newDocumentContentGetRequest(ref.getDocumentId())
                    .storeId(ref.getStoreId())
                    .send()
                    .join())
        .isInstanceOf(ProblemException.class)
        .matches(e -> ((ProblemException) e).details().getStatus() == 400);
  }

  @Test
  void shouldAllowTenantToUseItsAssignedStore() throws IOException {
    // given / when
    final var ref =
        clientA.newCreateDocumentCommand().content("hello-from-a").storeId(STORE_A).send().join();

    // then
    assertThat(ref.getStoreId()).isEqualTo(STORE_A);

    try (final var stream = clientA.newDocumentContentGetRequest(ref).send().join()) {
      assertThat(stream).isNotNull();
    }
  }

  @Test
  void shouldRejectAccessToUnassignedStore() {
    assertThatThrownBy(
            () ->
                clientA
                    .newCreateDocumentCommand()
                    .content("attempt")
                    .storeId(STORE_B)
                    .send()
                    .join())
        .isInstanceOf(ProblemException.class)
        .matches(e -> ((ProblemException) e).details().getStatus() == 400);
  }

  @SuppressWarnings("resource")
  @Test
  void shouldIsolateDocumentsBetweenTenantStores() {
    // given
    final var ref =
        clientA.newCreateDocumentCommand().content("secret-a").storeId(STORE_A).send().join();

    // when / then
    assertThatThrownBy(
            () ->
                clientB
                    .newDocumentContentGetRequest(ref.getDocumentId())
                    .storeId(STORE_A)
                    .send()
                    .join())
        .isInstanceOf(ProblemException.class)
        .matches(e -> ((ProblemException) e).details().getStatus() == 400);
  }

  @Test
  void shouldPreserveContentIndependenceForSameDocumentIdAcrossStores() throws IOException {
    // given
    final String docId = "shared-doc-" + UUID.randomUUID();
    final var refA =
        clientA
            .newCreateDocumentCommand()
            .content("content-from-a")
            .documentId(docId)
            .storeId(STORE_A)
            .send()
            .join();
    final var refB =
        clientB
            .newCreateDocumentCommand()
            .content("content-from-b")
            .documentId(docId)
            .storeId(STORE_B)
            .send()
            .join();

    // when / then
    try (final var streamA = clientA.newDocumentContentGetRequest(refA).send().join()) {
      assertThat(streamA.readAllBytes()).isEqualTo("content-from-a".getBytes());
    }
    try (final var streamB = clientB.newDocumentContentGetRequest(refB).send().join()) {
      assertThat(streamB.readAllBytes()).isEqualTo("content-from-b".getBytes());
    }
  }
}
