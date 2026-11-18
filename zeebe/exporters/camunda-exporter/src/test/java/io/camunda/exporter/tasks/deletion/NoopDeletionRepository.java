/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.tasks.deletion;

import io.camunda.exporter.tasks.util.AsyncDocumentPipeline.DocumentBatch;
import io.camunda.exporter.tasks.util.FilterByKeysQuery;
import io.camunda.exporter.tasks.util.SearchAfter;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

public class NoopDeletionRepository implements DeletionRepository {

  @Override
  public CompletableFuture<Void> deleteDocumentsById(
      final String indexName, final FilterByKeysQuery<String> filter, final Executor executor) {
    return CompletableFuture.completedFuture(null);
  }

  @Override
  public CompletableFuture<ProcessInstanceDeletionBatch> getProcessInstancesNextBatch() {
    return CompletableFuture.completedFuture(ProcessInstanceDeletionBatch.empty());
  }

  @Override
  public CompletableFuture<DocumentBatch<DocToDelete, SearchAfter>> searchDocumentsToDelete(
      final String indexName,
      final FilterByKeysQuery<?> filter,
      final SearchAfter searchAfter,
      final int batchSize) {
    throw new UnsupportedOperationException("Not implemented");
  }

  @Override
  public CompletableFuture<Integer> deleteDocBatch(
      final DocumentBatch<DocToDelete, SearchAfter> batch) {
    throw new UnsupportedOperationException("Not implemented");
  }

  @Override
  public void close() throws Exception {}
}
