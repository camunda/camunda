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
import io.camunda.security.auth.Authorization;
import io.camunda.security.auth.CamundaAuthentication;
import io.camunda.security.configuration.SecurityConfiguration;
import io.camunda.security.impl.AuthorizationChecker;
import io.camunda.service.exception.ErrorMapper;
import io.camunda.service.exception.ServiceException;
import io.camunda.service.security.SecurityContextProvider;
import io.camunda.zeebe.broker.client.api.BrokerClient;
import io.camunda.zeebe.protocol.record.value.AuthorizationResourceType;
import io.camunda.zeebe.protocol.record.value.AuthorizationScope;
import io.camunda.zeebe.protocol.record.value.PermissionType;
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
  private final AuthorizationChecker authorizationChecker;
  private final SecurityConfiguration securityConfig;

  public DocumentServices(
      final BrokerClient brokerClient,
      final SecurityContextProvider securityContextProvider,
      final CamundaAuthentication authentication,
      final SimpleDocumentStoreRegistry registry,
      final AuthorizationChecker authorizationChecker,
      final SecurityConfiguration securityConfig) {
    super(brokerClient, securityContextProvider, authentication);
    this.registry = registry;
    this.authorizationChecker = authorizationChecker;
    this.securityConfig = securityConfig;
  }

  @Override
  public DocumentServices withAuthentication(final CamundaAuthentication authentication) {
    return new DocumentServices(
        brokerClient,
        securityContextProvider,
        authentication,
        registry,
        authorizationChecker,
        securityConfig);
  }

  /** Will return a failed future for any error returned by the store */
  public CompletableFuture<DocumentReferenceResponse> createDocument(
      final DocumentCreateRequest request) {

    if (!hasDocumentPermission(PermissionType.CREATE)) {
      return CompletableFuture.failedFuture(
          ErrorMapper.createForbiddenException(
              Authorization.of(a -> a.document().permissionType(PermissionType.CREATE))));
    }

    final DocumentCreationRequest storeRequest =
        new DocumentCreationRequest(
            request.documentId, request.contentInputStream, request.metadata);

    final DocumentStoreRecord documentStore = getDocumentStore(request.storeId);
    return documentStore
        .instance()
        .createDocument(storeRequest)
        .handleAsync(
            (response, error) -> {
              final var right = requireRightOrThrow(response, error);
              return new DocumentReferenceResponse(
                  right.documentId(),
                  documentStore.storeId(),
                  right.contentHash(),
                  right.metadata());
            });
  }

  /** Will never return a failed future; an Either type is returned instead */
  public CompletableFuture<List<Either<DocumentErrorResponse, DocumentReferenceResponse>>>
      createDocumentBatch(final List<DocumentCreateRequest> requests) {

    if (!hasDocumentPermission(PermissionType.CREATE)) {
      return CompletableFuture.failedFuture(
          ErrorMapper.createForbiddenException(
              Authorization.of(a -> a.document().permissionType(PermissionType.CREATE))));
    }

    final List<Either<DocumentErrorResponse, DocumentReferenceResponse>> results =
        new ArrayList<>();

    final List<CompletableFuture<Boolean>> futures =
        requests.stream()
            .map(
                request -> {
                  final var storeRequest =
                      new DocumentCreationRequest(
                          request.documentId, request.contentInputStream, request.metadata);
                  final DocumentStoreRecord documentStore = getDocumentStore(request.storeId);
                  return documentStore
                      .instance()
                      .createDocument(storeRequest)
                      .handleAsync(
                          (response, error) ->
                              results.add(
                                  transformResponse(
                                      request, response, error, documentStore.storeId())));
                })
            .toList();

    return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
        .handleAsync(
            (ignoredRes, error) -> {
              if (error != null) {
                throw ErrorMapper.mapError(error);
              }
              return results;
            });
  }

  public CompletableFuture<DocumentContentResponse> getDocumentContent(
      final String documentId, final String storeId, final String contentHash) {

    if (!hasDocumentPermission(PermissionType.READ)) {
      return CompletableFuture.failedFuture(
          ErrorMapper.createForbiddenException(
              Authorization.of(a -> a.document().permissionType(PermissionType.READ))));
    }

    final DocumentStore documentStore = getDocumentStore(storeId).instance();
    return documentStore
        .verifyContentHash(documentId, contentHash)
        .thenCompose(
            verification -> {
              if (verification.isLeft()) {
                return CompletableFuture.completedFuture(Either.left(verification.getLeft()));
              }
              return documentStore.getDocument(documentId);
            })
        .handleAsync(
            (response, error) -> {
              final var documentContent = requireRightOrThrow(response, error);
              return new DocumentContentResponse(
                  documentContent.inputStream(), documentContent.contentType());
            });
  }

  public CompletableFuture<Void> deleteDocument(final String documentId, final String storeId) {

    if (!hasDocumentPermission(PermissionType.DELETE)) {
      return CompletableFuture.failedFuture(
          ErrorMapper.createForbiddenException(
              Authorization.of(a -> a.document().permissionType(PermissionType.DELETE))));
    }

    return getDocumentStore(storeId)
        .instance()
        .deleteDocument(documentId)
        .handleAsync(this::requireRightOrThrow);
  }

  public CompletableFuture<DocumentLink> createLink(
      final String documentId,
      final String storeId,
      final String contentHash,
      final DocumentLinkParams params) {

    if (!hasDocumentPermission(PermissionType.CREATE)) {
      return CompletableFuture.failedFuture(
          ErrorMapper.createForbiddenException(
              Authorization.of(a -> a.document().permissionType(PermissionType.CREATE))));
    }

    final long ttl = params.timeToLive().toMillis();

    final DocumentStore documentStore = getDocumentStore(storeId).instance();
    return documentStore
        .verifyContentHash(documentId, contentHash)
        .thenCompose(
            verification ->
                verification.isLeft()
                    ? CompletableFuture.completedFuture(Either.left(verification.getLeft()))
                    : documentStore.createLink(documentId, ttl))
        .handleAsync(this::requireRightOrThrow);
  }

  private DocumentStoreRecord getDocumentStore(final String id) {
    try {
      if (id == null) {
        return registry.getDefaultDocumentStore();
      } else {
        return registry.getDocumentStore(id);
      }
    } catch (final IllegalArgumentException e) {
      throw ErrorMapper.mapDocumentError(new StoreDoesNotExist(id));
    } catch (final Exception e) {
      throw ErrorMapper.mapError(e);
    }
  }

  private Either<DocumentErrorResponse, DocumentReferenceResponse> transformResponse(
      final DocumentCreateRequest request,
      final Either<DocumentError, DocumentReference> rawResult,
      final Throwable error,
      final String storeId) {
    if (error != null) {
      return Either.left(new DocumentErrorResponse(request, ErrorMapper.mapError(error)));
    }
    if (rawResult.isLeft()) {
      return Either.left(
          new DocumentErrorResponse(request, ErrorMapper.mapDocumentError(rawResult.getLeft())));
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

  private <T> T requireRightOrThrow(
      final Either<DocumentError, T> response, final Throwable error) {
    if (error != null) {
      throw ErrorMapper.mapError(error);
    }
    if (response.isLeft()) {
      logIfUnknownError(response.getLeft());
      throw ErrorMapper.mapDocumentError(response.getLeft());
    } else {
      return response.get();
    }
  }

  private boolean hasDocumentPermission(final PermissionType permission) {
    if (!securityConfig.getAuthorizations().isEnabled()) {
      return true;
    }

    return authorizationChecker
        .collectPermissionTypes(
            AuthorizationScope.WILDCARD_CHAR, AuthorizationResourceType.DOCUMENT, authentication)
        .contains(permission);
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

  public record DocumentErrorResponse(DocumentCreateRequest request, ServiceException error) {}
}
