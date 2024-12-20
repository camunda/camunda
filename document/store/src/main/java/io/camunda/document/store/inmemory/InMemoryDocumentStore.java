/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.document.store.inmemory;

import io.camunda.document.api.DocumentCreationRequest;
import io.camunda.document.api.DocumentError;
import io.camunda.document.api.DocumentError.OperationNotSupported;
import io.camunda.document.api.DocumentLink;
import io.camunda.document.api.DocumentReference;
import io.camunda.document.api.DocumentStore;
import io.camunda.zeebe.util.Either;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * An in-memory implementation of the {@link DocumentStore} interface. This implementation is
 * intended for testing purposes only. It is not multi-instance safe and does not persist documents
 * across restarts.
 */
public class InMemoryDocumentStore implements DocumentStore {

  private final Map<String, byte[]> documents;

  public InMemoryDocumentStore() {
    documents = new ConcurrentHashMap<>();
  }

  @Override
  public CompletableFuture<Either<DocumentError, DocumentReference>> createDocument(
      final DocumentCreationRequest request) {

    final String id =
        Optional.ofNullable(request.documentId()).orElse(UUID.randomUUID().toString());
    if (documents.containsKey(id)) {
      return CompletableFuture.completedFuture(
          Either.left(new DocumentError.DocumentAlreadyExists(id)));
    }
    final MessageDigest md;
    try {
      md = MessageDigest.getInstance("SHA-256");
    } catch (final Exception e) {
      // should never happen
      return CompletableFuture.completedFuture(
          Either.left(new DocumentError.UnknownDocumentError("Failed to create document")));
    }
    final DigestInputStream contentInputStream =
        new DigestInputStream(request.contentInputStream(), md);
    final byte[] content;
    try {
      content = contentInputStream.readAllBytes();
      contentInputStream.close();
    } catch (final IOException e) {
      return CompletableFuture.completedFuture(
          Either.left(new DocumentError.InvalidInput("Failed to read content")));
    }
    documents.put(id, content);
    final String contentHash = HexFormat.of().formatHex(md.digest());
    return CompletableFuture.completedFuture(
        Either.right(new DocumentReference(id, contentHash, request.metadata())));
  }

  @Override
  public CompletableFuture<Either<DocumentError, InputStream>> getDocument(
      final String documentId) {
    final var content = documents.get(documentId);
    if (content == null) {
      return CompletableFuture.completedFuture(
          Either.left(new DocumentError.DocumentNotFound(documentId)));
    }
    return CompletableFuture.completedFuture(Either.right(new ByteArrayInputStream(content)));
  }

  @Override
  public CompletableFuture<Either<DocumentError, Void>> deleteDocument(final String documentId) {
    final var content = documents.remove(documentId);
    if (content == null) {
      return CompletableFuture.completedFuture(
          Either.left(new DocumentError.DocumentNotFound(documentId)));
    }
    return CompletableFuture.completedFuture(Either.right(null));
  }

  @Override
  public CompletableFuture<Either<DocumentError, DocumentLink>> createLink(
      final String documentId, final long durationInMillis) {
    return CompletableFuture.completedFuture(
        Either.left(
            new OperationNotSupported(
                "The in-memory document store does not support creating links")));
  }

  @Override
  public CompletableFuture<Either<DocumentError, Void>> verifyContentHash(
      final String documentId, final String contentHash) {
    final MessageDigest md;
    try {
      md = MessageDigest.getInstance("SHA-256");
    } catch (final Exception e) {
      return CompletableFuture.completedFuture(
          Either.left(new DocumentError.UnknownDocumentError(e)));
    }

    final var content = documents.get(documentId);

    if (content == null) {
      return CompletableFuture.completedFuture(
          Either.left(new DocumentError.DocumentNotFound(documentId)));
    }

    final var actualHash = HexFormat.of().formatHex(md.digest(content));

    if (!actualHash.equals(contentHash)) {
      return CompletableFuture.completedFuture(
          Either.left(new DocumentError.DocumentHashMismatch(documentId, contentHash)));
    }
    return CompletableFuture.completedFuture(Either.right(null));
  }
}
