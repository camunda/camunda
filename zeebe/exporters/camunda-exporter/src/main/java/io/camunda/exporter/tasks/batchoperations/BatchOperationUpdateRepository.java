/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.tasks.batchoperations;

import io.camunda.webapps.schema.entities.operation.BatchOperationEntity.BatchOperationState;
import io.camunda.webapps.schema.entities.operation.OperationState;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

public interface BatchOperationUpdateRepository extends AutoCloseable {

  /**
   * Returns the not finished batch operations. We can use endDate field to distinguish finished
   * from running.
   *
   * <p>The state and the total operations count are returned alongside the key because a batch
   * operation with no single operations at all is indistinguishable, from the aggregation alone,
   * from one whose single operations are simply not in the operation index anymore. See {@link
   * BatchOperationUpdateTask}.
   */
  CompletionStage<Collection<NotFinishedBatchOperation>> getNotFinishedBatchOperations();

  /**
   * Counts amount of single operations by state that are included in given batch operations.
   *
   * @param batchOperationKeys list of batch operation keys
   */
  CompletionStage<List<OperationsAggData>> getOperationsCount(
      Collection<String> batchOperationKeys);

  /**
   * Updates the batch operations with the different amounts operations by state. Update method
   * additionally includes the script to set endDate field to the current time for those batch
   * operations that have all operations finished (operationsTotalCount <= operationsFinishedCount).
   *
   * @param documentUpdates the update records
   * @return the number of updated documents
   */
  CompletionStage<Integer> bulkUpdate(List<DocumentUpdate> documentUpdates);

  /**
   * A batch operation that has no endDate yet, with the two stored fields the update task needs to
   * decide whether an endDate can be derived for it.
   *
   * @param id the batch operation key
   * @param state the stored state, {@code null} if the document had no readable source
   * @param operationsTotalCount the number of items the engine selected for this batch operation
   */
  record NotFinishedBatchOperation(
      String id, BatchOperationState state, long operationsTotalCount) {}

  /**
   * Represents a specific document store agnostic update to execute.
   *
   * <p>All fields are expected to be non-null.
   */
  record DocumentUpdate(
      String id,
      long finishedOperationsCount,
      long failedOperationsCount,
      long completedOperationsCount,
      long totalOperationsCount) {}

  record OperationsAggData(String batchOperationKey, Map<String, Long> stateCounts) {
    public long getFinishedOperationsCount() {
      return getCompletedOperationsCount() + getFailedOperationsCount();
    }

    public long getCompletedOperationsCount() {
      return stateCounts.getOrDefault(OperationState.COMPLETED.name(), 0L)
          + stateCounts.getOrDefault(OperationState.SKIPPED.name(), 0L);
    }

    public long getFailedOperationsCount() {
      return stateCounts.getOrDefault(OperationState.FAILED.name(), 0L);
    }

    public long getTotalOperationsCount() {
      return stateCounts.values().stream().mapToLong(Long::longValue).sum();
    }
  }

  class NoopBatchOperationUpdateRepository implements BatchOperationUpdateRepository {

    @Override
    public CompletionStage<Collection<NotFinishedBatchOperation>> getNotFinishedBatchOperations() {
      return CompletableFuture.completedFuture(List.of());
    }

    @Override
    public CompletionStage<List<OperationsAggData>> getOperationsCount(
        final Collection<String> batchOperationKeys) {
      return CompletableFuture.completedFuture(List.of());
    }

    @Override
    public CompletionStage<Integer> bulkUpdate(final List<DocumentUpdate> documentUpdates) {
      return CompletableFuture.completedFuture(0);
    }

    @Override
    public void close() throws Exception {}
  }
}
