/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.tasks.historydeletion;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/** Repository for querying and managing history deletion requests. */
public interface HistoryDeletionRepository extends AutoCloseable {

  /**
   * Retrieves the next batch of resource IDs to be deleted.
   *
   * <p>The batch should be sorted by id and filtered by partition id. The sorting ensures older
   * deletion requests are handled first. The filter ensures that the deletion load is distributed
   * fairly amongst partitions.
   *
   * @return a {@link CompletableFuture} containing a {@link HistoryDeletionBatch} of resource IDs,
   *     or an empty batch if none are available
   */
  CompletableFuture<HistoryDeletionBatch> getNextBatch();

  /**
   * Deletes documents from the specified index where the given field matches any of the provided
   * values.
   *
   * @param sourceIndexName The index to delete the documents from
   * @param idFieldName The field name to match against
   * @param fieldValues The values to match for deletion
   * @return a {@link CompletableFuture} containing the field values
   */
  CompletableFuture<List<Long>> deleteDocumentsByField(
      final String sourceIndexName, final String idFieldName, final List<Long> fieldValues);

  /**
   * Deletes documents from the specified index by their IDs.
   *
   * @param sourceIndexName The index to delete the documents from
   * @param ids The list of document IDs to delete
   * @return a {@link CompletableFuture} containing th number of entities that were deleted
   */
  CompletableFuture<Integer> deleteDocumentsById(
      final String sourceIndexName, final List<String> ids);

  class NoopHistoryDeletionRepository implements HistoryDeletionRepository {
    @Override
    public CompletableFuture<HistoryDeletionBatch> getNextBatch() {
      return CompletableFuture.completedFuture(new HistoryDeletionBatch(List.of()));
    }

    @Override
    public CompletableFuture<List<Long>> deleteDocumentsByField(
        final String sourceIndexName, final String idFieldName, final List<Long> fieldValues) {
      return CompletableFuture.completedFuture(fieldValues);
    }

    @Override
    public CompletableFuture<Integer> deleteDocumentsById(
        final String sourceIndexName, final List<String> ids) {
      return CompletableFuture.completedFuture(0);
    }

    @Override
    public void close() throws Exception {}
  }
}
