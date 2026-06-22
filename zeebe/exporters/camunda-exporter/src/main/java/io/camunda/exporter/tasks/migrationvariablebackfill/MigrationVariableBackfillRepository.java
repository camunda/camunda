/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.tasks.migrationvariablebackfill;

import io.camunda.webapps.schema.entities.VariableEntity;
import io.camunda.webapps.schema.entities.usertask.TaskVariableEntity;
import java.util.List;
import java.util.concurrent.CompletionStage;

/**
 * Storage access layer for {@link MigrationVariableBackfillTask}. Encapsulates all reads from the
 * post-importer queue and the variable store, and all writes to the task-variable index, so the
 * task logic stays search-engine-agnostic.
 */
public interface MigrationVariableBackfillRepository extends AutoCloseable {

  /**
   * Returns the next batch of process instance keys that need variable backfilling. Only entries
   * written by {@link
   * io.camunda.exporter.handlers.PostImporterQueueFromProcessInstanceMigrationHandler} (action type
   * {@code PROCESS_INSTANCE_MIGRATION}) are returned, and only those with a position strictly
   * greater than {@code fromPosition}.
   *
   * @param fromPosition the exclusive lower bound on the position field
   * @param size the maximum number of entries to return
   * @return a batch; {@link PendingBackfillBatch#isEmpty()} is true when there is no work to do
   */
  CompletionStage<PendingBackfillBatch> getPendingBackfillBatch(long fromPosition, int size);

  /**
   * Fetches all variables for a process instance from the variable store.
   *
   * @param processInstanceKey the process instance to fetch variables for
   * @return a list of variable entities; may be empty if none exist yet
   */
  CompletionStage<List<VariableEntity>> getVariablesByProcessInstanceKey(long processInstanceKey);

  /**
   * Writes the given task variable documents to the task-variable index as upserts (creating
   * entries that are missing, updating existing entries). Each document is written with routing set
   * to its {@code processInstanceId}.
   *
   * @param variables the task-variable documents to write
   */
  CompletionStage<Void> bulkUpsertTaskVariables(List<TaskVariableEntity> variables);

  /** A batch of pending migration variable backfill work. */
  record PendingBackfillBatch(long highestPosition, List<Long> processInstanceKeys) {
    public boolean isEmpty() {
      return processInstanceKeys.isEmpty();
    }
  }

  /** No-op implementation for use in environments where the feature is disabled. */
  class Noop implements MigrationVariableBackfillRepository {

    @Override
    public CompletionStage<PendingBackfillBatch> getPendingBackfillBatch(
        final long fromPosition, final int size) {
      return java.util.concurrent.CompletableFuture.completedFuture(
          new PendingBackfillBatch(-1, List.of()));
    }

    @Override
    public CompletionStage<List<VariableEntity>> getVariablesByProcessInstanceKey(
        final long processInstanceKey) {
      return java.util.concurrent.CompletableFuture.completedFuture(List.of());
    }

    @Override
    public CompletionStage<Void> bulkUpsertTaskVariables(final List<TaskVariableEntity> variables) {
      return java.util.concurrent.CompletableFuture.completedFuture(null);
    }

    @Override
    public void close() {}
  }
}
