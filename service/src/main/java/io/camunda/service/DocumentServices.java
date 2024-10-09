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
import io.camunda.document.api.DocumentLink;
import io.camunda.document.api.DocumentMetadataModel;
import io.camunda.document.api.DocumentStoreRecord;
import io.camunda.document.store.SimpleDocumentStoreRegistry;
import io.camunda.search.security.auth.Authentication;
import io.camunda.zeebe.broker.client.api.BrokerClient;
import java.io.InputStream;
import java.time.ZonedDateTime;
import java.util.concurrent.CompletableFuture;

public class DocumentServices extends ApiServices<DocumentServices> {

  private final SimpleDocumentStoreRegistry registry = new SimpleDocumentStoreRegistry();

  public DocumentServices(final BrokerClient brokerClient) {
    this(brokerClient, null);
  }

  public DocumentServices(final BrokerClient brokerClient, final Authentication authentication) {
    super(brokerClient, authentication);
  }

  @Override
  public DocumentServices withAuthentication(final Authentication authentication) {
    return new DocumentServices(brokerClient, authentication);
  }

  public CompletableFuture<DocumentReferenceResponse> createDocument(
      final DocumentCreateRequest request) {

    final DocumentCreationRequest storeRequest =
        new DocumentCreationRequest(
            request.documentId, request.contentInputStream, request.metadata);

    final DocumentStoreRecord storeRecord = getDocumentStore(request.storeId);
    return storeRecord
        .instance()
        .createDocument(storeRequest)
        .thenApply(
            result -> {
              if (result.isLeft()) {
                throw new DocumentException("Failed to create document", result.getLeft());
              } else {
                return new DocumentReferenceResponse(
                    result.get().documentId(), storeRecord.storeId(), result.get().metadata());
              }
            });
  }

  public InputStream getDocumentContent(final String documentId, final String storeId) {

    final DocumentStoreRecord storeRecord = getDocumentStore(storeId);
    return storeRecord
        .instance()
        .getDocument(documentId)
        .thenApply(
            result -> {
              if (result.isLeft()) {
                throw new DocumentException("Failed to get document", result.getLeft());
              } else {
                return result.get();
              }
            })
        .join();
  }

  public CompletableFuture<Void> deleteDocument(final String documentId, final String storeId) {

    final DocumentStoreRecord storeRecord = getDocumentStore(storeId);
    return storeRecord
        .instance()
        .deleteDocument(documentId)
        .thenAccept(
            result -> {
              if (result.isLeft()) {
                throw new DocumentException("Failed to delete document", result.getLeft());
              }
            });
  }

  public CompletableFuture<DocumentLink> createLink(
      final String documentId, final String storeId, final DocumentLinkParams params) {

    final DocumentStoreRecord storeRecord = getDocumentStore(storeId);
    final long ttl =
        params.expiresAt().toInstant().getEpochSecond()
            - ZonedDateTime.now().toInstant().getEpochSecond();
    return storeRecord
        .instance()
        .createLink(documentId, ttl)
        .thenApply(
            result -> {
              if (result.isLeft()) {
                throw new DocumentException("Failed to create link", result.getLeft());
              } else {
                return result.get();
              }
            });
  }

  private DocumentStoreRecord getDocumentStore(final String id) {
    if (id == null) {
      return registry.getDefaultDocumentStore();
    } else {
      return registry.getDocumentStore(id);
    }
  }

  public record DocumentCreateRequest(
      String documentId,
      String storeId,
      InputStream contentInputStream,
      DocumentMetadataModel metadata) {}

  public record DocumentReferenceResponse(
      String documentId, String storeId, DocumentMetadataModel metadata) {}

  public record DocumentLinkParams(ZonedDateTime expiresAt) {}

  public static class DocumentException extends RuntimeException {

    private final DocumentError documentError;

    public DocumentException(final String message, final DocumentError error) {
      documentError = error;
    }

    public DocumentException(
        final String message, final DocumentError error, final Throwable cause) {
      documentError = error;
    }

    public DocumentError getDocumentError() {
      return documentError;
    }
  }
}
