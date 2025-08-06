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
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.camunda.document.api.DocumentContent;
import io.camunda.document.api.DocumentCreationRequest;
import io.camunda.document.api.DocumentMetadataModel;
import io.camunda.document.api.DocumentReference;
import io.camunda.document.api.DocumentStore;
import io.camunda.document.api.DocumentStoreRecord;
import io.camunda.document.store.SimpleDocumentStoreRegistry;
import io.camunda.security.auth.CamundaAuthentication;
import io.camunda.security.configuration.AuthorizationsConfiguration;
import io.camunda.security.configuration.SecurityConfiguration;
import io.camunda.security.impl.AuthorizationChecker;
import io.camunda.service.DocumentServices.DocumentCreateRequest;
import io.camunda.service.DocumentServices.DocumentReferenceResponse;
import io.camunda.service.security.SecurityContextProvider;
import io.camunda.zeebe.broker.client.api.BrokerClient;
import io.camunda.zeebe.protocol.record.value.PermissionType;
import io.camunda.zeebe.util.Either;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class DocumentServicesTest {

  private DocumentServices services;
  private final SimpleDocumentStoreRegistry registry = mock(SimpleDocumentStoreRegistry.class);
  private final AuthorizationChecker authorizationChecker = mock(AuthorizationChecker.class);
  private final SecurityConfiguration securityConfiguration = mock(SecurityConfiguration.class);

  @BeforeEach
  public void before() {
    services =
        new DocumentServices(
            mock(BrokerClient.class),
            mock(SecurityContextProvider.class),
            mock(CamundaAuthentication.class),
            registry,
            authorizationChecker,
            securityConfiguration,
            mock(ApiServicesExecutorProvider.class));

    final var authorizationConfiguration = new AuthorizationsConfiguration();
    authorizationConfiguration.setEnabled(false);
    when(securityConfiguration.getAuthorizations()).thenReturn(authorizationConfiguration);
  }

  @Test
  public void createDocumentShouldCompleteExceptionallyWhenAUserHasNoAuthorizations() {
    // given
    // Authorizations are enabled by default
    final var authorizationConfiguration = new AuthorizationsConfiguration();
    when(securityConfiguration.getAuthorizations()).thenReturn(authorizationConfiguration);
    when(authorizationChecker.collectPermissionTypes(any(), any(), any()))
        .thenReturn(Collections.emptySet());
    final var fileMock = mock(DocumentCreateRequest.class);

    // when
    final var future = services.createDocument(fileMock);

    assertThat(future.isCompletedExceptionally()).isTrue();
  }

  @Test
  public void createDocumentShouldCompleteExceptionallyWhenAUserIsNotAuthorizedToCreate() {
    // given
    // Authorizations are enabled by default
    final var authorizationConfiguration = new AuthorizationsConfiguration();
    when(securityConfiguration.getAuthorizations()).thenReturn(authorizationConfiguration);
    when(authorizationChecker.collectPermissionTypes(any(), any(), any()))
        .thenReturn(Set.of(PermissionType.READ));
    final var fileMock = mock(DocumentCreateRequest.class);

    // when
    final var future = services.createDocument(fileMock);

    assertThat(future.isCompletedExceptionally()).isTrue();
  }

  @Test
  public void deleteDocumentShouldCompleteExceptionallyWhenAUserIsNotAuthorizedToDelete() {
    // given
    // Authorizations are enabled by default
    final var authorizationConfiguration = new AuthorizationsConfiguration();
    when(securityConfiguration.getAuthorizations()).thenReturn(authorizationConfiguration);
    when(authorizationChecker.collectPermissionTypes(any(), any(), any()))
        .thenReturn(Set.of(PermissionType.READ));

    // when
    final var future = services.deleteDocument("irrelevant-document-id", "irrelevant-store-id");

    assertThat(future.isCompletedExceptionally()).isTrue();
  }

  @Test
  public void getDocumentShouldCompleteExceptionallyWhenAUserIsNotAuthorizedToRead() {
    // given
    // Authorizations are enabled by default
    final var authorizationConfiguration = new AuthorizationsConfiguration();
    when(securityConfiguration.getAuthorizations()).thenReturn(authorizationConfiguration);
    when(authorizationChecker.collectPermissionTypes(any(), any(), any()))
        .thenReturn(Collections.emptySet());

    // when
    final var future =
        services.getDocumentContent(
            "irrelevant-document-id", "irrelevant-store-id", "irrelevant-hash");

    assertThat(future.isCompletedExceptionally()).isTrue();
  }

  @Test
  public void shouldSkipAuthorizationChecksWhenNotEnabled() {
    // given
    final var storeRecord = mock(DocumentStoreRecord.class);
    final var storeInstance = mock(DocumentStore.class);
    final var storeId = "in-memory-store";
    when(storeRecord.instance()).thenReturn(storeInstance);
    when(storeRecord.storeId()).thenReturn(storeId);
    when(registry.getDefaultDocumentStore()).thenReturn(storeRecord);

    final var content1 = "hello world";
    final var file1 = createDocRequest(content1);
    final var expectedResult1 = createDocumentReference(file1);
    when(storeInstance.createDocument(
            new DocumentCreationRequest(
                file1.documentId(), file1.contentInputStream(), file1.metadata())))
        .thenReturn(CompletableFuture.completedFuture(Either.right(expectedResult1)));

    // when
    final var response = services.createDocument(file1).join();

    // then
    assertThat(response)
        .isNotNull()
        .isEqualTo(
            new DocumentReferenceResponse(
                expectedResult1.documentId(),
                storeId,
                expectedResult1.contentHash(),
                expectedResult1.metadata()));

    verify(authorizationChecker, times(0)).collectPermissionTypes(any(), any(), any());
    verify(registry, times(1)).getDefaultDocumentStore();
    verify(storeRecord, times(1)).instance();
  }

  @Test
  public void shouldDeleteDocumentWhenUserIsAuthorized() {
    // given
    // Authorizations are enabled by default
    final var authorizationConfiguration = new AuthorizationsConfiguration();
    when(securityConfiguration.getAuthorizations()).thenReturn(authorizationConfiguration);
    when(authorizationChecker.collectPermissionTypes(any(), any(), any()))
        .thenReturn(Set.of(PermissionType.DELETE));

    final var storeRecord = mock(DocumentStoreRecord.class);
    final var storeInstance = mock(DocumentStore.class);
    final var storeId = "in-memory-store";
    final var documentId = "test-document-id-1";
    when(registry.getDocumentStore(storeId)).thenReturn(storeRecord);
    when(storeRecord.instance()).thenReturn(storeInstance);
    when(storeInstance.deleteDocument(documentId))
        .thenReturn(CompletableFuture.completedFuture(Either.right(null)));

    // when
    final var future = services.deleteDocument(documentId, storeId);

    // then
    assertThat(future).isNotNull();
  }

  @Test
  public void shouldCreateADocumentWhenUserIsAuthorized() {
    // given
    // Authorizations are enabled by default
    final var authorizationConfiguration = new AuthorizationsConfiguration();
    when(securityConfiguration.getAuthorizations()).thenReturn(authorizationConfiguration);
    when(authorizationChecker.collectPermissionTypes(any(), any(), any()))
        .thenReturn(Set.of(PermissionType.CREATE));

    final var storeRecord = mock(DocumentStoreRecord.class);
    final var storeInstance = mock(DocumentStore.class);
    final var storeId = "in-memory-store";
    when(storeRecord.instance()).thenReturn(storeInstance);
    when(storeRecord.storeId()).thenReturn(storeId);
    when(registry.getDefaultDocumentStore()).thenReturn(storeRecord);

    final var content1 = "hello world";
    final var file1 = createDocRequest(content1);
    final var expectedResult1 = createDocumentReference(file1);
    when(storeInstance.createDocument(
            new DocumentCreationRequest(
                file1.documentId(), file1.contentInputStream(), file1.metadata())))
        .thenReturn(CompletableFuture.completedFuture(Either.right(expectedResult1)));

    // when
    final var response = services.createDocument(file1).join();

    // then
    assertThat(response)
        .isNotNull()
        .isEqualTo(
            new DocumentReferenceResponse(
                expectedResult1.documentId(),
                storeId,
                expectedResult1.contentHash(),
                expectedResult1.metadata()));

    verify(registry, times(1)).getDefaultDocumentStore();
    verify(storeRecord, times(1)).instance();
  }

  @Test
  public void shouldUploadBatchWithoutStoreId() {
    // given
    final var storeRecord = mock(DocumentStoreRecord.class);
    final var storeInstance = mock(DocumentStore.class);
    final var storeId = "in-memory-store";
    when(storeRecord.instance()).thenReturn(storeInstance);
    when(storeRecord.storeId()).thenReturn(storeId);
    when(registry.getDefaultDocumentStore()).thenReturn(storeRecord);

    final var content1 = "hello world";
    final var file1 = createDocRequest(content1);
    final var expectedResult1 = createDocumentReference(file1);
    when(storeInstance.createDocument(
            new DocumentCreationRequest(
                file1.documentId(), file1.contentInputStream(), file1.metadata())))
        .thenReturn(CompletableFuture.completedFuture(Either.right(expectedResult1)));

    final var content2 = "Sup\nworld";
    final var file2 = createDocRequest(content2);
    final var expectedResult2 = createDocumentReference(file2);
    when(storeInstance.createDocument(
            new DocumentCreationRequest(
                file2.documentId(), file2.contentInputStream(), file2.metadata())))
        .thenReturn(CompletableFuture.completedFuture(Either.right(expectedResult2)));

    // when
    final var response = services.createDocumentBatch(List.of(file1, file2)).join();

    // then
    assertThat(response).isNotNull();
    assertThat(response).size().isEqualTo(2);

    assertThat(response.get(0).isRight()).isTrue();
    assertThat(response.get(0).get())
        .isEqualTo(
            new DocumentReferenceResponse(
                expectedResult1.documentId(),
                storeId,
                expectedResult1.contentHash(),
                expectedResult1.metadata()));

    assertThat(response.get(1).isRight()).isTrue();
    assertThat(response.get(1).get())
        .isEqualTo(
            new DocumentReferenceResponse(
                expectedResult2.documentId(),
                storeId,
                expectedResult2.contentHash(),
                expectedResult2.metadata()));

    verify(registry, times(2)).getDefaultDocumentStore();
    verify(storeRecord, times(2)).instance();
  }

  @Test
  public void shouldGetDocumentWhenUserIsAuthorized() {
    // given
    // Authorizations are enabled by default
    final var authorizationConfiguration = new AuthorizationsConfiguration();
    when(securityConfiguration.getAuthorizations()).thenReturn(authorizationConfiguration);
    when(authorizationChecker.collectPermissionTypes(any(), any(), any()))
        .thenReturn(Set.of(PermissionType.READ));

    final var storeRecord = mock(DocumentStoreRecord.class);
    final var storeInstance = mock(DocumentStore.class);
    final var storeId = "in-memory-store";
    final var documentId = "test-document-id-1";
    final var contentHash = "test-document-hash";
    when(registry.getDocumentStore(storeId)).thenReturn(storeRecord);
    when(storeRecord.instance()).thenReturn(storeInstance);
    when(storeInstance.verifyContentHash(documentId, contentHash))
        .thenReturn(CompletableFuture.completedFuture(Either.right(null)));

    final var content = "hello world";
    final var documentContent =
        new DocumentContent(new ByteArrayInputStream(content.getBytes()), "text/plain");
    when(storeInstance.getDocument(documentId))
        .thenReturn(CompletableFuture.completedFuture(Either.right(documentContent)));

    // when
    final var actualDocumentContent =
        services.getDocumentContent(documentId, storeId, contentHash).join();

    // then
    assertThat(actualDocumentContent).isNotNull();
    assertThat(actualDocumentContent.contentType()).isEqualTo("text/plain");
    assertThat(getDocumentContent(actualDocumentContent.content())).isEqualTo(content);
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

  private String getDocumentContent(final InputStream inputStream) {
    return new BufferedReader(new InputStreamReader(inputStream))
        .lines()
        .collect(Collectors.joining("\n"));
  }
}
