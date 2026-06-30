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
import io.camunda.zeebe.qa.util.cluster.PhysicalTenantsITHelper;
import io.camunda.zeebe.qa.util.cluster.PhysicalTenantsITHelper.Storage;
import io.camunda.zeebe.qa.util.cluster.TestStandaloneBroker;
import java.io.IOException;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Contract tests for physical-tenant document store isolation. Subclasses supply the store-specific
 * broker setup via {@code @BeforeAll} / {@code @AfterAll} and call {@link
 * #startClients(TestStandaloneBroker)} / {@link #closeClients()} from those hooks.
 */
abstract class AbstractDocumentIsolationIT {

  protected static final String TENANT_A = "tenanta";
  protected static final String TENANT_B = "tenantb";
  protected static final String STORE_A = "store-a";
  protected static final String STORE_B = "store-b";

  protected static final PhysicalTenantsITHelper TENANTS =
      PhysicalTenantsITHelper.builder()
          .withTenant(PhysicalTenantsITHelper.DEFAULT_TENANT_ID, Storage.none())
          .withTenant(TENANT_A, Storage.none())
          .withTenant(TENANT_B, Storage.none())
          .build();

  protected static CamundaClient clientA;
  protected static CamundaClient clientB;

  protected static void startClients(final TestStandaloneBroker broker) {
    clientA = TENANTS.newClientBuilder(broker, TENANT_A).build();
    clientB = TENANTS.newClientBuilder(broker, TENANT_B).build();
  }

  protected static void closeClients() {
    if (clientA != null) {
      clientA.close();
    }
    if (clientB != null) {
      clientB.close();
    }
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

    // when - B targets B's own store; document absent there, A's copy unaffected.
    // Cloud stores (S3/GCS/Azure) surface a 404 when the blob is missing; local store silently
    // succeeds. Either outcome is acceptable — the important assertion is that A's document
    // survives.
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
