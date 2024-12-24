/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.document.api;

import io.camunda.zeebe.util.Either;
import java.util.concurrent.CompletableFuture;

public interface DocumentStore {

  CompletableFuture<Either<DocumentError, DocumentReference>> createDocument(
      DocumentCreationRequest request);

  CompletableFuture<Either<DocumentError, DocumentContent>> getDocument(String documentId);

  CompletableFuture<Either<DocumentError, Void>> deleteDocument(String documentId);

  CompletableFuture<Either<DocumentError, DocumentLink>> createLink(
      String documentId, long durationInMillis);

  CompletableFuture<Either<DocumentError, Void>> verifyContentHash(
      String documentId, String contentHash);
}
