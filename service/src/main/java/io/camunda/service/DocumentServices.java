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
import io.camunda.document.api.DocumentError.UnknownDocumentError;
import io.camunda.document.api.DocumentLink;
import io.camunda.document.api.DocumentMetadataModel;
import io.camunda.document.api.DocumentReference;
import io.camunda.document.api.DocumentStore;
import io.camunda.document.api.DocumentStoreRecord;
import io.camunda.document.store.SimpleDocumentStoreRegistry;
import io.camunda.security.auth.Authentication;
import io.camunda.service.security.SecurityContextProvider;
import io.camunda.zeebe.broker.client.api.BrokerClient;
import io.camunda.zeebe.util.Either;
import java.io.InputStream;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DocumentServices extends ApiServices<DocumentServices> {

  private static final Logger LOG = LoggerFactory.getLogger(DocumentServices.class);

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

  /** Will return a failed future for any error returned by the store */
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
                    .thenApply(this::requireRightOrThrow)
                    .thenApply(
                        result ->
                            new DocumentReferenceResponse(
                                result.documentId(),
                                storeRecord.storeId(),
                                result.contentHash(),
                                result.metadata())));
  }

  /** Will never return a failed future; an Either type is returned instead */
  public CompletableFuture<List<Either<DocumentErrorResponse, DocumentReferenceResponse>>>
      createDocumentBatch(final List<DocumentCreateRequest> requests) {

    final List<Either<DocumentErrorResponse, DocumentReferenceResponse>> results =
        new ArrayList<>();

    final List<CompletableFuture<Void>> futures =
        requests.stream()
            .map(
                request -> {
                  final var storeRequest =
                      new DocumentCreationRequest(
                          request.documentId, request.contentInputStream, request.metadata);
                  return getDocumentStore(request.storeId)
                      .thenCompose(
                          storeRecord ->
                              storeRecord
                                  .instance()
                                  .createDocument(storeRequest)
                                  .thenApply(
                                      result ->
                                          transformResponse(
                                              request, result, storeRecord.storeId())))
                      .thenAccept(results::add);
                })
            .toList();

    return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
        .thenApply((ignoredRes) -> results);
  }

  public DocumentContentResponse getDocumentContent(
      final String documentId, final String storeId, final String contentHash) {

    return getDocumentStore(storeId)
        .thenCompose(
            storeRecord -> {
              final DocumentStore storeRecordInstance = storeRecord.instance();

              return storeRecordInstance
                  .verifyContentHash(documentId, contentHash)
                  .thenCompose(
                      verification -> {
                        if (verification.isLeft()) {
                          return CompletableFuture.completedFuture(
                              Either.left(verification.getLeft()));
                        }
                        return storeRecordInstance.getDocument(documentId);
                      });
            })
        .thenApply(this::requireRightOrThrow)
        .thenApply(
            documentContent ->
                new DocumentContentResponse(
                    documentContent.inputStream(), documentContent.contentType()))
        .join();
  }

  public CompletableFuture<Void> deleteDocument(final String documentId, final String storeId) {

    return getDocumentStore(storeId)
        .thenCompose(
            storeRecord ->
                storeRecord
                    .instance()
                    .deleteDocument(documentId)
                    .thenAccept(this::requireRightOrThrow));
  }

  public CompletableFuture<DocumentLink> createLink(
      final String documentId,
      final String storeId,
      final String contentHash,
      final DocumentLinkParams params) {

    final long ttl = params.timeToLive().toMillis();

    return getDocumentStore(storeId)
        .thenCompose(
            storeRecord -> {
              final DocumentStore storeRecordInstance = storeRecord.instance();

              return storeRecordInstance
                  .verifyContentHash(documentId, contentHash)
                  .thenCompose(
                      verification ->
                          verification.isLeft()
                              ? CompletableFuture.completedFuture(
                                  Either.left(verification.getLeft()))
                              : storeRecordInstance.createLink(documentId, ttl))
                  .thenApply(this::requireRightOrThrow);
            });
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

  private Either<DocumentErrorResponse, DocumentReferenceResponse> transformResponse(
      final DocumentCreateRequest request,
      final Either<DocumentError, DocumentReference> rawResult,
      final String storeId) {
    if (rawResult.isLeft()) {
      return Either.left(new DocumentErrorResponse(request, rawResult.getLeft()));
    }
    final var reference = rawResult.get();
    return Either.right(
        new DocumentReferenceResponse(
            reference.documentId(), storeId, reference.contentHash(), reference.metadata()));
  }

  private void logIfUnknownError(final DocumentError error) {
    if (error instanceof final UnknownDocumentError docError) {
      LOG.error("An unexpected error occurred", docError.cause());
    }
  }

  private <T> T requireRightOrThrow(final Either<DocumentError, T> response) {
    if (response.isLeft()) {
      logIfUnknownError(response.getLeft());
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
      String documentId, String storeId, String contentHash, DocumentMetadataModel metadata) {}

  public record DocumentContentResponse(InputStream content, String contentType) {}

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

  public record DocumentErrorResponse(DocumentCreateRequest request, DocumentError error) {}
}
