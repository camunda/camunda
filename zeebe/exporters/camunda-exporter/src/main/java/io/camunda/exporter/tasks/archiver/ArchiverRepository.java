/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.tasks.archiver;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

/** Placeholder interface for future abstracted access to the underlying storage (e.g. ES/OS). */
public interface ArchiverRepository extends AutoCloseable {
  CompletableFuture<ArchiveBatch> getProcessInstancesNextBatch();

  CompletableFuture<ArchiveBatch> getBatchOperationsNextBatch();

  CompletableFuture<Void> setIndexLifeCycle(final String destinationIndexName);

  CompletableFuture<Void> setLifeCycleToAllIndexes();

  CompletableFuture<Void> deleteDocuments(
      final String sourceIndexName,
      final String idFieldName,
      final List<String> processInstanceKeys);

  CompletableFuture<Void> reindexDocuments(
      final String sourceIndexName,
      final String destinationIndexName,
      final String idFieldName,
      final List<String> processInstanceKeys);

  default CompletableFuture<Void> moveDocuments(
      final String sourceIndexName,
      final String destinationIndexName,
      final String idFieldName,
      final List<String> ids,
      final Executor executor) {
    return reindexDocuments(sourceIndexName, destinationIndexName, idFieldName, ids)
        .thenComposeAsync(ok -> setIndexLifeCycle(destinationIndexName), executor)
        .thenComposeAsync(ok -> deleteDocuments(sourceIndexName, idFieldName, ids), executor);
  }

  class NoopArchiverRepository implements ArchiverRepository {

    @Override
    public CompletableFuture<ArchiveBatch> getProcessInstancesNextBatch() {
      return CompletableFuture.completedFuture(new ArchiveBatch("2024-01-01", List.of()));
    }

    @Override
    public CompletableFuture<ArchiveBatch> getBatchOperationsNextBatch() {
      return CompletableFuture.completedFuture(new ArchiveBatch("2024-01-01", List.of()));
    }

    @Override
    public CompletableFuture<Void> setIndexLifeCycle(final String destinationIndexName) {
      return CompletableFuture.completedFuture(null);
    }

    @Override
    public CompletableFuture<Void> setLifeCycleToAllIndexes() {
      return CompletableFuture.completedFuture(null);
    }

    @Override
    public CompletableFuture<Void> deleteDocuments(
        final String sourceIndexName,
        final String idFieldName,
        final List<String> processInstanceKeys) {
      return CompletableFuture.completedFuture(null);
    }

    @Override
    public CompletableFuture<Void> reindexDocuments(
        final String sourceIndexName,
        final String destinationIndexName,
        final String idFieldName,
        final List<String> processInstanceKeys) {
      return CompletableFuture.completedFuture(null);
    }

    @Override
    public void close() throws Exception {}
  }
}
