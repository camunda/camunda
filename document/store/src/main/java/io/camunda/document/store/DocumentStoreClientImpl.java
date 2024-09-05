/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.document.store;

import io.camunda.document.api.DocumentOperationResponse;
import io.camunda.document.api.DocumentStore;
import io.camunda.document.api.DocumentStoreClient;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Function;
import javax.annotation.Nullable;

public class DocumentStoreClientImpl implements DocumentStoreClient {

  private final DocumentStoreRegistry documentStoreRegistry;
  private final ExecutorService executorService;

  private final DocumentStore documentStore;

  public DocumentStoreClientImpl() {
    this(new DocumentStoreRegistry(), Executors.newSingleThreadExecutor());
  }

  public DocumentStoreClientImpl(
      final DocumentStoreRegistry documentStoreRegistry, final ExecutorService executorService) {
    this.documentStoreRegistry = documentStoreRegistry;
    this.executorService = executorService;
    final var documentStoreInstance = documentStoreRegistry.getDefaultDocumentStore();
    documentStore = documentStoreInstance.store();
  }

  public DocumentStoreClientImpl(
      final DocumentStoreRegistry documentStoreRegistry,
      final ExecutorService executorService,
      final String storeId) {
    this.documentStoreRegistry = documentStoreRegistry;
    this.executorService = executorService;
    final var documentStoreInstance = documentStoreRegistry.getDocumentStore(storeId);
    documentStore = documentStoreInstance.store();
  }

  @Override
  public String resolveStoreId(@Nullable final String storeId) {
    return documentStoreRegistry.getDocumentStore(storeId).storeId();
  }

  @Override
  public DocumentStoreClient withStore(@Nullable final String storeId) {
    if (storeId == null) {
      return new DocumentStoreClientImpl(documentStoreRegistry, executorService);
    }
    return new DocumentStoreClientImpl(documentStoreRegistry, executorService, storeId);
  }

  @Override
  public <T> DocumentOperationResponse<T> execute(
      final Function<DocumentStore, DocumentOperationResponse<T>> operation) {
    return operation.apply(documentStore);
  }

  @Override
  public <T> CompletableFuture<DocumentOperationResponse<T>> executeAsync(
      final Function<DocumentStore, DocumentOperationResponse<T>> operation) {
    return CompletableFuture.supplyAsync(() -> operation.apply(documentStore), executorService);
  }
}
