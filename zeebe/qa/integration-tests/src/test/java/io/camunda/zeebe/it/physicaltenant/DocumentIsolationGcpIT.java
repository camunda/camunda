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

import com.google.cloud.NoCredentials;
import com.google.cloud.storage.BucketInfo;
import com.google.cloud.storage.StorageOptions;
import io.camunda.client.CamundaClient;
import io.camunda.client.api.command.ProblemException;
import io.camunda.document.store.gcp.GcpDocumentStoreProvider;
import io.camunda.zeebe.backup.gcs.GcsBackupConfig;
import io.camunda.zeebe.backup.gcs.GcsBackupStore;
import io.camunda.zeebe.qa.util.cluster.PhysicalTenantsITHelper;
import io.camunda.zeebe.qa.util.cluster.PhysicalTenantsITHelper.Storage;
import io.camunda.zeebe.qa.util.cluster.TestStandaloneBroker;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration.TestZeebe;
import io.camunda.zeebe.test.testcontainers.GcsContainer;
import java.io.IOException;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
@ZeebeIntegration
final class DocumentIsolationGcpIT {

  private static final String TENANT_A = "tenanta";
  private static final String TENANT_B = "tenantb";
  private static final String STORE_A = "store-a";
  private static final String STORE_B = "store-b";

  private static final String BUCKET_A = "bucket-a";
  private static final String BUCKET_B = "bucket-b";

  @Container private static final GcsContainer GCS = new GcsContainer();

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
  static void setupBucket() throws Exception {
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

    // TestGcpDocumentStoreProvider installs a storage override on GcpDocumentStoreProvider so
    // that the broker connects to the local fake-gcs-server instead of storage.googleapis.com.
    // No HOST key is needed in document store configuration.
    configure(GCS);

    BROKER
        .withProperty("camunda.document.gcp.store-a.bucket-name", BUCKET_A)
        .withProperty("camunda.document.gcp.store-b.bucket-name", BUCKET_B)
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
    reset();
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

  public static void configure(final GcsContainer gcs) {
    final String endpoint = gcs.externalEndpoint();
    GcpDocumentStoreProvider.storageOverride =
        () ->
            StorageOptions.newBuilder()
                .setHost(endpoint)
                .setCredentials(NoCredentials.getInstance())
                .build()
                .getService();
  }

  public static void reset() {
    GcpDocumentStoreProvider.storageOverride = null;
  }
}
