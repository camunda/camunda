/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.document.store;

import io.camunda.document.api.DocumentContent;
import io.camunda.document.api.DocumentCreationRequest;
import io.camunda.document.api.DocumentError;
import io.camunda.document.api.DocumentLink;
import io.camunda.document.api.DocumentReference;
import io.camunda.document.api.DocumentStore;
import io.camunda.document.api.DocumentStoreConfiguration.DocumentStoreConfigurationRecord;
import io.camunda.document.api.DocumentStoreProvider;
import io.camunda.zeebe.util.Either;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;

/**
 * Dummy implementation of {@link DocumentStoreProvider} for testing purposes. Allows to see which
 * configuration is passed to the provider.
 */
public class TestDocumentStoreProvider implements DocumentStoreProvider {

  @Override
  public DocumentStore createDocumentStore(
      final DocumentStoreConfigurationRecord configuration, final ExecutorService executor) {
    return new DummyDocumentStore(executor, configuration);
  }

  public record DummyDocumentStore(
      ExecutorService executorService, DocumentStoreConfigurationRecord configuration)
      implements DocumentStore {

    @Override
    public CompletableFuture<Either<DocumentError, DocumentReference>> createDocument(
        final DocumentCreationRequest request) {
      throw new UnsupportedOperationException("Not implemented");
    }

    @Override
    public CompletableFuture<Either<DocumentError, DocumentContent>> getDocument(
        final String documentId) {
      throw new UnsupportedOperationException("Not implemented");
    }

    @Override
    public CompletableFuture<Either<DocumentError, Void>> deleteDocument(final String documentId) {
      throw new UnsupportedOperationException("Not implemented");
    }

    @Override
    public CompletableFuture<Either<DocumentError, DocumentLink>> createLink(
        final String documentId, final long durationInMillis) {
      throw new UnsupportedOperationException("Not implemented");
    }

    @Override
    public CompletableFuture<Either<DocumentError, Void>> verifyContentHash(
        final String documentId, final String contentHash) {
      throw new UnsupportedOperationException("Not implemented");
    }
  }
}
