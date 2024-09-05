/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.document.store;

import io.camunda.document.api.DocumentCreationRequest;
import io.camunda.document.api.DocumentLink;
import io.camunda.document.api.DocumentOperationResponse;
import io.camunda.document.api.DocumentOperationResponse.DocumentErrorCode;
import io.camunda.document.api.DocumentOperationResponse.Failure;
import io.camunda.document.api.DocumentOperationResponse.Success;
import io.camunda.document.api.DocumentReference;
import io.camunda.document.api.DocumentStore;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public class InMemoryDocumentStore implements DocumentStore {

  private final Map<String, byte[]> documents;

  public InMemoryDocumentStore() {
    documents = new HashMap<>();
  }

  @Override
  public DocumentOperationResponse<DocumentReference> createDocument(
      final DocumentCreationRequest request) {

    final String id =
        Optional.ofNullable(request.documentId()).orElse(UUID.randomUUID().toString());
    if (documents.containsKey(id)) {
      return new Failure<>(DocumentOperationResponse.DocumentErrorCode.DOCUMENT_ALREADY_EXISTS);
    }
    final var contentInputStream = request.contentInputStream();
    final byte[] content;
    try {
      content = contentInputStream.readAllBytes();
      contentInputStream.close();
    } catch (final IOException e) {
      return new Failure<>(DocumentErrorCode.UNKNOWN_ERROR, e);
    }
    documents.put(id, content);
    return new Success<>(new DocumentReference(id, request.metadata()));
  }

  @Override
  public DocumentOperationResponse<InputStream> getDocument(final String documentId) {
    final var content = documents.get(documentId);
    if (content == null) {
      return new Failure<>(DocumentErrorCode.DOCUMENT_NOT_FOUND);
    }
    return new Success<>(new ByteArrayInputStream(content));
  }

  @Override
  public DocumentOperationResponse<Void> deleteDocument(final String documentId) {
    final var content = documents.remove(documentId);
    if (content == null) {
      return new Failure<>(DocumentErrorCode.DOCUMENT_NOT_FOUND);
    }
    return new Success<>(null);
  }

  @Override
  public DocumentOperationResponse<DocumentLink> createLink(
      final String documentId, final long durationInSeconds) {
    return new Failure<>(DocumentErrorCode.OPERATION_NOT_SUPPORTED);
  }
}
