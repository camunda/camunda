/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.service;

import io.camunda.document.api.DocumentCreationRequest;
import io.camunda.document.api.DocumentMetadataModel;
import io.camunda.document.api.DocumentOperationResponse;
import io.camunda.document.api.DocumentOperationResponse.DocumentErrorCode;
import io.camunda.document.api.DocumentReference;
import io.camunda.document.api.DocumentStoreClient;
import io.camunda.document.store.DocumentStoreClientImpl;
import io.camunda.search.clients.CamundaSearchClient;
import io.camunda.service.security.auth.Authentication;
import io.camunda.service.transformers.ServiceTransformers;
import io.camunda.zeebe.broker.client.api.BrokerClient;
import java.io.InputStream;
import java.util.concurrent.CompletableFuture;

public class DocumentServices extends ApiServices<DocumentServices> {

  private final DocumentStoreClient documentStoreClient = new DocumentStoreClientImpl();

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

    return documentStoreClient
        .withStore(request.storeId)
        .executeAsync(store -> store.createDocument(storeRequest))
        .thenApply(
            result -> {
              if (result
                  instanceof final DocumentOperationResponse.Failure<DocumentReference> failure) {
                throw new DocumentException(
                    "Failed to create document", failure.errorCode(), failure.cause());
              }
              final var success = (DocumentOperationResponse.Success<DocumentReference>) result;
              return new DocumentReferenceResponse(
                  success.result().documentId(),
                  documentStoreClient.resolveStoreId(request.storeId),
                  success.result().metadata());
            });
  }

  public InputStream getDocumentContent(final String documentId, final String storeId) {

    final var result =
        documentStoreClient.withStore(storeId).execute(store -> store.getDocument(documentId));

    if (result instanceof final DocumentOperationResponse.Failure<InputStream> failure) {
      throw new DocumentException("Failed to get document", failure.errorCode(), failure.cause());
    }
    return ((DocumentOperationResponse.Success<InputStream>) result).result();
  }

  public CompletableFuture<Void> deleteDocument(final String documentId, final String storeId) {
    return documentStoreClient
        .withStore(storeId)
        .executeAsync(store -> store.deleteDocument(documentId))
        .thenAccept(
            result -> {
              if (result instanceof final DocumentOperationResponse.Failure<Void> failure) {
                throw new DocumentException(
                    "Failed to delete document", failure.errorCode(), failure.cause());
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

    private final DocumentErrorCode errorCode;

    public DocumentException(final String message, final DocumentErrorCode errorCode) {
      super(message);
      this.errorCode = errorCode;
    }

    public DocumentException(
        final String message, final DocumentErrorCode errorCode, final Throwable cause) {
      super(message, cause);
      this.errorCode = errorCode;
    }
  }
}
