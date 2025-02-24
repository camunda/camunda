/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.tasks.batchoperations;

import io.camunda.exporter.tasks.BackgroundTask;
import io.camunda.exporter.tasks.batchoperations.BatchOperationUpdateRepository.DocumentUpdate;
import io.camunda.exporter.tasks.batchoperations.BatchOperationUpdateRepository.OperationsAggData;
import io.camunda.zeebe.util.FunctionUtil;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executor;
import org.slf4j.Logger;

public class BatchOperationUpdateTask implements BackgroundTask {

  private static final int NO_UPDATES = 0;
  private final BatchOperationUpdateRepository batchOperationUpdateRepository;

  private final Logger logger;
  private final Executor executor;

  public BatchOperationUpdateTask(
      final BatchOperationUpdateRepository batchOperationUpdateRepository,
      final Logger logger,
      final Executor executor) {
    this.batchOperationUpdateRepository = batchOperationUpdateRepository;
    this.logger = logger;
    this.executor = executor;
  }

  @Override
  public CompletionStage<Integer> execute() {
    return batchOperationUpdateRepository
        .getNotFinishedBatchOperations()
        .thenComposeAsync(this::updateBatchOperations, executor);
  }

  @Override
  public String getCaption() {
    return "Batch operation update task";
  }

  private CompletionStage<Integer> updateBatchOperations(
      final Collection<String> batchOperationIds) {
    if (batchOperationIds.isEmpty()) {
      return CompletableFuture.completedFuture(NO_UPDATES);
    }

    return batchOperationUpdateRepository
        .getFinishedOperationsCount(batchOperationIds)
        .thenApplyAsync(this::collectDocumentUpdates, executor)
        .thenComposeAsync(batchOperationUpdateRepository::bulkUpdate, executor)
        .thenApplyAsync(
            FunctionUtil.peek(
                (updatesCount) -> logger.trace("Updated {} batch operations", updatesCount)));
  }

  private List<DocumentUpdate> collectDocumentUpdates(
      final List<OperationsAggData> finishedSingleOperationsCount) {
    return finishedSingleOperationsCount.stream()
        .map(d -> new DocumentUpdate(d.batchOperationId(), d.finishedOperationsCount()))
        .toList();
  }
}
