/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.tasks.migrationvariablebackfill;

import io.camunda.webapps.schema.entities.VariableEntity;
import io.camunda.webapps.schema.entities.usertask.TaskJoinRelationship;
import io.camunda.webapps.schema.entities.usertask.TaskJoinRelationship.TaskJoinRelationshipType;
import io.camunda.webapps.schema.entities.usertask.TaskVariableEntity;
import io.camunda.zeebe.exporter.common.tasks.BackgroundTask;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import org.slf4j.Logger;

/**
 * Background task that backfills process-instance variable documents into the {@code tasklist-task}
 * index for process instances that were migrated from a process definition with no user tasks to
 * one that does have user tasks.
 *
 * <p>When {@code skipVariableWriteWithoutUserTasks} is enabled, an instance started on a
 * no-user-task definition never has its variables exported to {@code tasklist-task}. After
 * migration to a definition with user tasks, the {@code processInstanceVariables} filter on {@code
 * POST /user-tasks/search} would return no results because the required {@code PROCESS_VARIABLE}
 * join documents are absent.
 *
 * <p>This task reads pending backfill entries written by {@link
 * io.camunda.exporter.handlers.PostImporterQueueFromProcessInstanceMigrationHandler}, fetches the
 * current variable values from {@code operate-variable}, upserts them as {@code PROCESS_VARIABLE}
 * join documents into {@code tasklist-task}, then deletes the consumed queue entries.
 */
public final class MigrationVariableBackfillTask implements BackgroundTask {

  private static final String ID_PATTERN = "%s-%s";

  private final MigrationVariableBackfillRepository repository;
  private final int batchSize;
  private final int variableSizeThreshold;
  private final Logger logger;

  public MigrationVariableBackfillTask(
      final MigrationVariableBackfillRepository repository,
      final int batchSize,
      final int variableSizeThreshold,
      final Logger logger) {
    this.repository = repository;
    this.batchSize = batchSize;
    this.variableSizeThreshold = variableSizeThreshold;
    this.logger = logger;
  }

  @Override
  public CompletionStage<Integer> execute() {
    try {
      return CompletableFuture.completedFuture(processNextBatch());
    } catch (final Exception e) {
      return CompletableFuture.failedFuture(e);
    }
  }

  @Override
  public String getCaption() {
    return "Migration variable backfill task";
  }

  private int processNextBatch() {
    final List<Long> processInstanceKeys =
        repository.getPendingBackfillBatch(batchSize).toCompletableFuture().join();

    if (processInstanceKeys.isEmpty()) {
      return 0;
    }

    logger.trace(
        "Backfilling task variables for {} migrated process instances", processInstanceKeys.size());

    int totalVariablesWritten = 0;
    for (final long processInstanceKey : processInstanceKeys) {
      totalVariablesWritten += backfillForInstance(processInstanceKey);
    }

    repository.deletePendingBackfillEntries(processInstanceKeys).toCompletableFuture().join();

    logger.debug(
        "Backfilled {} task variable documents for {} migrated process instances",
        totalVariablesWritten,
        processInstanceKeys.size());

    return totalVariablesWritten;
  }

  private int backfillForInstance(final long processInstanceKey) {
    final List<VariableEntity> variables =
        repository
            .getVariablesByProcessInstanceKey(processInstanceKey)
            .toCompletableFuture()
            .join();

    if (variables.isEmpty()) {
      logger.trace("No variables found for process instance {}", processInstanceKey);
      return 0;
    }

    final List<TaskVariableEntity> taskVariables =
        variables.stream().map(this::buildTaskVariable).toList();

    repository.bulkUpsertTaskVariables(taskVariables).toCompletableFuture().join();
    return taskVariables.size();
  }

  private TaskVariableEntity buildTaskVariable(final VariableEntity source) {
    final var entity = new TaskVariableEntity();

    entity
        .setId(ID_PATTERN.formatted(source.getScopeKey(), source.getName()))
        .setKey(source.getKey())
        .setPartitionId(source.getPartitionId())
        .setTenantId(source.getTenantId())
        .setName(source.getName())
        .setScopeKey(source.getScopeKey())
        .setProcessInstanceId(source.getProcessInstanceKey());

    // Replicate the truncation state from operate-variable: isPreview=true means the value was
    // already truncated and fullValue held the original; we mirror that as isTruncated.
    entity.setValue(source.getValue()).setIsTruncated(source.getIsPreview());

    if (source.getRootProcessInstanceKey() != null && source.getRootProcessInstanceKey() > 0) {
      entity.setRootProcessInstanceKey(source.getRootProcessInstanceKey());
    }

    final var join = new TaskJoinRelationship();
    join.setName(TaskJoinRelationshipType.PROCESS_VARIABLE.getType());
    join.setParent(source.getProcessInstanceKey());
    entity.setJoin(join);

    return entity;
  }
}
