/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.tasks.batchoperations;

import java.util.Collection;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

public interface BatchOperationUpdateRepository extends AutoCloseable {

  /**
   * Returns the list of not finished batch operations. We can use endDate field to distinguish
   * finished from running.
   */
  CompletionStage<Collection<String>> getNotFinishedBatchOperations();

  /**
   * Counts amount of single operations that are finished (COMPLETED or FAILED state) that are
   * included in given batch operations. Those batch operations that does not have any finished
   * operations will not be included in the result. Therefore, the result list size may be less than
   * the input list size.
   *
   * @param batchOperationIds list of batch operation ids
   */
  CompletionStage<List<OperationsAggData>> getFinishedOperationsCount(
      Collection<String> batchOperationIds);

  /**
   * Updates the batch operations with the amount of finished operations. Update method additionally
   * includes the script to set endDate field to the current time for those batch operations that
   * have all operations finished (operationsTotalCount <= operationsFinishedCount).
   *
   * @param documentUpdates
   * @return
   */
  CompletionStage<Integer> bulkUpdate(List<DocumentUpdate> documentUpdates);

  /**
   * Represents a specific document store agnostic update to execute.
   *
   * <p>All fields are expected to be non-null.
   */
  record DocumentUpdate(String id, long finishedOperationsCount) {}

  record OperationsAggData(String batchOperationId, long finishedOperationsCount) {}

  class NoopBatchOperationUpdateRepository implements BatchOperationUpdateRepository {

    @Override
    public CompletionStage<Collection<String>> getNotFinishedBatchOperations() {
      return CompletableFuture.completedFuture(List.of());
    }

    @Override
    public CompletionStage<List<OperationsAggData>> getFinishedOperationsCount(
        final Collection<String> batchOperationIds) {
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
