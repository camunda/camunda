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
import io.camunda.document.api.DocumentMetadataModel;
import io.camunda.document.api.DocumentStoreRecord;
import io.camunda.document.store.SimpleDocumentStoreRegistry;
import io.camunda.search.clients.CamundaSearchClient;
import io.camunda.service.security.auth.Authentication;
import io.camunda.service.transformers.ServiceTransformers;
import io.camunda.zeebe.broker.client.api.BrokerClient;
import java.io.InputStream;
import java.util.concurrent.CompletableFuture;

public class DocumentServices extends ApiServices<DocumentServices> {

  private final SimpleDocumentStoreRegistry registry = new SimpleDocumentStoreRegistry();

  public DocumentServices(final BrokerClient brokerClient, final CamundaSearchClient searchClient) {
    this(brokerClient, searchClient, null, null);
  }

  public DocumentServices(
      final BrokerClient brokerClient,
      final CamundaSearchClient searchClient,
      final ServiceTransformers transformers,
      final Authentication authentication) {
    super(brokerClient, searchClient, transformers, authentication);
  }

  @Override
  public DocumentServices withAuthentication(final Authentication authentication) {
    return new DocumentServices(brokerClient, searchClient, transformers, authentication);
  }

  public CompletableFuture<DocumentReferenceResponse> createDocument(
      final DocumentCreateRequest request) {

    final DocumentCreationRequest storeRequest =
        new DocumentCreationRequest(
            request.documentId, request.contentInputStream, request.metadata);

    final DocumentStoreRecord storeRecord = registry.getDocumentStore(request.storeId);
    return storeRecord
        .instance()
        .createDocument(storeRequest)
        .thenApply(
            result -> {
              if (result.isLeft()) {
                throw new DocumentException("Failed to create document", result.getLeft());
              } else {
                return new DocumentReferenceResponse(
                    result.get().documentId(), request.storeId, result.get().metadata());
              }
            });
  }

  public InputStream getDocumentContent(final String documentId, final String storeId) {

    final DocumentStoreRecord storeRecord = registry.getDocumentStore(storeId);
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

    final DocumentStoreRecord storeRecord = registry.getDocumentStore(storeId);
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

  public record DocumentCreateRequest(
      String documentId,
      String storeId,
      InputStream contentInputStream,
      DocumentMetadataModel metadata) {}

  public record DocumentReferenceResponse(
      String documentId, String storeId, DocumentMetadataModel metadata) {}

  public static class DocumentException extends CamundaServiceException {

    private final DocumentError error;

    public DocumentException(final String message, final DocumentError error) {
      super(message);
      this.error = error;
    }

    public DocumentException(
        final String message, final DocumentError error, final Throwable cause) {
      super(message, cause);
      this.error = error;
    }
  }
}
