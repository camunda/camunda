/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import io.camunda.document.api.DocumentContent;
import io.camunda.document.api.DocumentCreationRequest;
import io.camunda.document.api.DocumentMetadataModel;
import io.camunda.document.api.DocumentReference;
import io.camunda.document.api.DocumentStore;
import io.camunda.document.api.DocumentStoreRecord;
import io.camunda.document.store.SimpleDocumentStoreRegistry;
import io.camunda.security.api.model.CamundaAuthentication;
import io.camunda.security.api.model.authz.PermissionType;
import io.camunda.security.api.model.config.AuthorizationsConfiguration;
import io.camunda.security.core.authz.AuthorizationChecker;
import io.camunda.service.DocumentServices.DocumentCreateRequest;
import io.camunda.service.security.SecurityContextProvider;
import io.camunda.zeebe.broker.client.api.BrokerClient;
import io.camunda.zeebe.util.Either;
import java.io.ByteArrayInputStream;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Characterizes {@link DocumentServices}'s own contract: every permission check is delegated to
 * whichever {@link AuthorizationChecker} instance it was constructed with, with no shared or static
 * state leaking a check to a sibling instance's checker.
 */
class DocumentServicesAuthorizationCheckerDelegationTest {

  private final CamundaAuthentication authentication = mock(CamundaAuthentication.class);
  private final AuthorizationChecker checkerA = mock(AuthorizationChecker.class);
  private final AuthorizationChecker checkerB = mock(AuthorizationChecker.class);
  private final SimpleDocumentStoreRegistry registryA = mock(SimpleDocumentStoreRegistry.class);
  private final SimpleDocumentStoreRegistry registryB = mock(SimpleDocumentStoreRegistry.class);
  private DocumentServices servicesA;
  private DocumentServices servicesB;

  @BeforeEach
  void beforeEach() {
    final var enabledConfig = new AuthorizationsConfiguration();
    enabledConfig.setEnabled(true);
    servicesA =
        new DocumentServices(
            "tenanta",
            mock(BrokerClient.class),
            mock(SecurityContextProvider.class),
            registryA,
            checkerA,
            enabledConfig,
            mock(ApiServicesExecutorProvider.class),
            null);
    servicesB =
        new DocumentServices(
            "tenantb",
            mock(BrokerClient.class),
            mock(SecurityContextProvider.class),
            registryB,
            checkerB,
            enabledConfig,
            mock(ApiServicesExecutorProvider.class),
            null);
  }

  @Test
  void shouldOnlyQueryOwnPhysicalTenantsCheckerOnCreate() {
    // given
    when(checkerA.collectPermissionTypes(any(), any(), any()))
        .thenReturn(Set.of(PermissionType.CREATE));
    final var storeRecord = mock(DocumentStoreRecord.class);
    final var storeInstance = mock(DocumentStore.class);
    when(storeRecord.instance()).thenReturn(storeInstance);
    when(storeRecord.storeId()).thenReturn("store-a");
    when(registryA.getDefaultDocumentStore()).thenReturn(storeRecord);
    final var file = createDocRequest("tenant a");
    final var expected = createDocumentReference(file);
    when(storeInstance.createDocument(
            new DocumentCreationRequest(
                file.documentId(), file.contentInputStream(), file.metadata())))
        .thenReturn(CompletableFuture.completedFuture(Either.right(expected)));

    // when
    final var future = servicesA.createDocument(file, authentication);

    // then
    assertThat(future.join()).isNotNull();
    verify(checkerA).collectPermissionTypes(any(), any(), any());
    verifyNoInteractions(checkerB);
  }

  @Test
  void shouldOnlyQueryOwnPhysicalTenantsCheckerOnDelete() {
    // given
    when(checkerA.collectPermissionTypes(any(), any(), any()))
        .thenReturn(Set.of(PermissionType.DELETE));
    final var storeRecord = mock(DocumentStoreRecord.class);
    final var storeInstance = mock(DocumentStore.class);
    final var documentId = "tenant-a-document";
    when(registryA.getDocumentStore("store-a")).thenReturn(storeRecord);
    when(storeRecord.instance()).thenReturn(storeInstance);
    when(storeInstance.deleteDocument(documentId))
        .thenReturn(CompletableFuture.completedFuture(Either.right(null)));

    // when
    final var future = servicesA.deleteDocument(documentId, "store-a", authentication);

    // then
    assertThat(future.isCompletedExceptionally()).isFalse();
    verify(checkerA).collectPermissionTypes(any(), any(), any());
    verifyNoInteractions(checkerB);
  }

  @Test
  void shouldReadDocumentContentUsingOwnPhysicalTenantsChecker() {
    // given
    when(checkerB.collectPermissionTypes(any(), any(), any()))
        .thenReturn(Set.of(PermissionType.READ));
    final var storeRecord = mock(DocumentStoreRecord.class);
    final var storeInstance = mock(DocumentStore.class);
    final var documentId = "tenant-b-document";
    final var contentHash = "tenant-b-hash";
    when(registryB.getDocumentStore("store-b")).thenReturn(storeRecord);
    when(storeRecord.instance()).thenReturn(storeInstance);
    when(storeInstance.verifyContentHash(documentId, contentHash))
        .thenReturn(CompletableFuture.completedFuture(Either.right(null)));
    final var content = "hello from tenant b";
    final var documentContent =
        new DocumentContent(new ByteArrayInputStream(content.getBytes()), "text/plain");
    when(storeInstance.getDocument(documentId))
        .thenReturn(CompletableFuture.completedFuture(Either.right(documentContent)));

    // when
    final var response =
        servicesB.getDocumentContent(documentId, "store-b", contentHash, authentication).join();

    // then
    assertThat(response).isNotNull();
    verify(checkerB).collectPermissionTypes(any(), any(), any());
    verifyNoInteractions(checkerA);
  }

  @Test
  void shouldDenyDeleteFromWrongTenantGrant() {
    // given: tenant A's checker would grant DELETE, but this request is scoped to tenant B, whose
    // own checker grants nothing -- proves DocumentServices always defers to the checker it was
    // constructed with, never a sibling instance's.
    when(checkerA.collectPermissionTypes(any(), any(), any()))
        .thenReturn(Set.of(PermissionType.DELETE));
    when(checkerB.collectPermissionTypes(any(), any(), any())).thenReturn(Collections.emptySet());

    // when
    final var future = servicesB.deleteDocument("tenant-a-document", "store-a", authentication);

    // then
    assertThat(future.isCompletedExceptionally()).isTrue();
    verify(checkerB).collectPermissionTypes(any(), any(), any());
    verifyNoInteractions(checkerA);
  }

  private DocumentCreateRequest createDocRequest(final String content) {
    return new DocumentCreateRequest(
        null,
        null,
        new ByteArrayInputStream(content.getBytes()),
        new DocumentMetadataModel(
            "text/plain",
            UUID.randomUUID() + ".txt",
            null,
            (long) content.length(),
            null,
            null,
            Map.of()));
  }

  private DocumentReference createDocumentReference(final DocumentCreateRequest request) {
    return new DocumentReference(
        request.documentId() != null ? request.documentId() : UUID.randomUUID().toString(),
        UUID.randomUUID().toString(),
        new DocumentMetadataModel(
            request.metadata().contentType(),
            request.metadata().fileName(),
            null,
            request.metadata().size(),
            request.metadata().processDefinitionId(),
            request.metadata().processInstanceKey(),
            request.metadata().customProperties()));
  }
}
