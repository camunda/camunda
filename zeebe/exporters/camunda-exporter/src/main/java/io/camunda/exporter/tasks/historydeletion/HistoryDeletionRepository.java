/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.tasks.historydeletion;

import java.util.Map;
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

  class NoopHistoryDeletionRepository implements HistoryDeletionRepository {
    @Override
    public CompletableFuture<HistoryDeletionBatch> getNextBatch() {
      return CompletableFuture.completedFuture(new HistoryDeletionBatch(Map.of()));
    }

    @Override
    public void close() throws Exception {}
  }
}
