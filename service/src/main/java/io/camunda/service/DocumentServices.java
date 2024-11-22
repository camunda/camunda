/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.service;

import io.camunda.document.api.DocumentCreationRequest;
import io.camunda.document.api.DocumentError;
import io.camunda.document.api.DocumentError.StoreDoesNotExist;
import io.camunda.document.api.DocumentLink;
import io.camunda.document.api.DocumentMetadataModel;
import io.camunda.document.api.DocumentStoreRecord;
import io.camunda.document.store.SimpleDocumentStoreRegistry;
import io.camunda.security.auth.Authentication;
import io.camunda.service.security.SecurityContextProvider;
import io.camunda.zeebe.broker.client.api.BrokerClient;
import io.camunda.zeebe.util.Either;
import java.io.InputStream;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;

public class DocumentServices extends ApiServices<DocumentServices> {

  private final SimpleDocumentStoreRegistry registry;

  public DocumentServices(
      final BrokerClient brokerClient,
      final SecurityContextProvider securityContextProvider,
      final Authentication authentication,
      final SimpleDocumentStoreRegistry registry) {
    super(brokerClient, securityContextProvider, authentication);
    this.registry = registry;
  }

  @Override
  public DocumentServices withAuthentication(final Authentication authentication) {
    return new DocumentServices(brokerClient, securityContextProvider, authentication, registry);
  }

  public CompletableFuture<DocumentReferenceResponse> createDocument(
      final DocumentCreateRequest request) {

    final DocumentCreationRequest storeRequest =
        new DocumentCreationRequest(
            request.documentId, request.contentInputStream, request.metadata);

    return getDocumentStore(request.storeId)
        .thenCompose(
            storeRecord ->
                storeRecord
                    .instance()
                    .createDocument(storeRequest)
                    .thenApply(this::handleResponse)
                    .thenApply(
                        result ->
                            new DocumentReferenceResponse(
                                result.documentId(), storeRecord.storeId(), result.metadata())));
  }

  public InputStream getDocumentContent(final String documentId, final String storeId) {

    return getDocumentStore(storeId)
        .thenCompose(storeRecord -> storeRecord.instance().getDocument(documentId))
        .thenApply(this::handleResponse)
        .join();
  }

  public CompletableFuture<Void> deleteDocument(final String documentId, final String storeId) {

    return getDocumentStore(storeId)
        .thenCompose(
            storeRecord ->
                storeRecord.instance().deleteDocument(documentId).thenAccept(this::handleResponse));
  }

  public CompletableFuture<DocumentLink> createLink(
      final String documentId, final String storeId, final DocumentLinkParams params) {

    final long ttl = params.timeToLive().toMillis();

    return getDocumentStore(storeId)
        .thenCompose(
            documentStoreRecord ->
                documentStoreRecord
                    .instance()
                    .createLink(documentId, ttl)
                    .thenApply(this::handleResponse));
  }

  private CompletableFuture<DocumentStoreRecord> getDocumentStore(final String id) {
    final DocumentStoreRecord storeRecord;
    try {
      if (id == null) {
        storeRecord = registry.getDefaultDocumentStore();
      } else {
        storeRecord = registry.getDocumentStore(id);
      }
      return CompletableFuture.completedStage(storeRecord).toCompletableFuture();
    } catch (final IllegalArgumentException e) {
      return CompletableFuture.failedFuture(new DocumentException(new StoreDoesNotExist(id)));
    }
  }

  private <T> T handleResponse(final Either<DocumentError, T> response) {
    if (response.isLeft()) {
      throw new DocumentException(response.getLeft());
    } else {
      return response.get();
    }
  }

  public record DocumentCreateRequest(
      String documentId,
      String storeId,
      InputStream contentInputStream,
      DocumentMetadataModel metadata) {}

  public record DocumentReferenceResponse(
      String documentId, String storeId, DocumentMetadataModel metadata) {}

  public record DocumentLinkParams(Duration timeToLive) {}

  public static class DocumentException extends RuntimeException {

    private final DocumentError documentError;

    public DocumentException(final DocumentError error) {
      documentError = error;
    }

    public DocumentError getDocumentError() {
      return documentError;
    }
  }
}
