/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.tasks.historydeletion;

import io.camunda.webapps.schema.entities.HistoryDeletionEntity;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

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
   * @return a {@link CompletableFuture} containing the number of entities that were deleted
   */
  CompletableFuture<Integer> deleteDocumentsById(
      final String sourceIndexName, final List<String> ids);

  /**
   * Creates audit log cleanup entries in the audit log cleanup index. These entries are used by the
   * audit log cleanup job to determine which audit logs to delete after the retention period has
   * expired for the deleted resources.
   *
   * @param historyDeletionEntities The history deletion entities that were processed
   * @param deletedResources The deleted resource IDs for which to create cleanup entries
   * @return a {@link CompletionStage} that completes when all entries have been indexed
   */
  CompletionStage<Void> createAuditLogCleanupEntries(
      final List<HistoryDeletionEntity> historyDeletionEntities,
      final Set<String> deletedResources);

  /**
   * Marks the individual operations of the batch operation as completed. The list of ids here
   * should adhere to the pattern defined in {@link
   * io.camunda.exporter.handlers.batchoperation.AbstractOperationHandler#ID_PATTERN}.
   *
   * @param ids the list of ids to mark as completed
   * @return a {@link CompletableFuture} containing the list of ids that were marked as completed
   */
  CompletableFuture<List<String>> completeOperations(final List<String> ids);

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
    public CompletionStage<Void> createAuditLogCleanupEntries(
        final List<HistoryDeletionEntity> historyDeletionEntities,
        final Set<String> deletedResources) {
      return CompletableFuture.completedFuture(null);
    }

    @Override
    public CompletableFuture<List<String>> completeOperations(final List<String> ids) {
      return CompletableFuture.completedFuture(List.of());
    }

    @Override
    public void close() throws Exception {}
  }
}
