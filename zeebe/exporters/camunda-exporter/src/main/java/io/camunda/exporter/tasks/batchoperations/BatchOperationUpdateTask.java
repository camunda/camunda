/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.tasks.batchoperations;

import io.camunda.exporter.tasks.batchoperations.BatchOperationUpdateRepository.DocumentUpdate;
import io.camunda.exporter.tasks.batchoperations.BatchOperationUpdateRepository.NotFinishedBatchOperation;
import io.camunda.exporter.tasks.batchoperations.BatchOperationUpdateRepository.OperationsAggData;
import io.camunda.exporter.tasks.util.AdaptiveBatchSize;
import io.camunda.exporter.tasks.util.BulkRequestTooLargeException;
import io.camunda.webapps.schema.entities.operation.BatchOperationEntity.BatchOperationState;
import io.camunda.zeebe.exporter.common.tasks.BackgroundTask;
import io.camunda.zeebe.util.FunctionUtil;
import io.camunda.zeebe.util.concurrency.FuturesUtil;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executor;
import java.util.stream.Collectors;
import org.slf4j.Logger;

public class BatchOperationUpdateTask implements BackgroundTask {

  /**
   * Caps what one cycle reads, and through that the aggregation and the bulk update derived from
   * it. Each batch operation contributes exactly one document, so this is far larger than the
   * post-export batch size, which bounds a task whose every item fans out into a tree.
   */
  public static final int MAX_BATCH_OPERATIONS_PER_CYCLE = 1000;

  private static final int NO_UPDATES = 0;

  private final BatchOperationUpdateRepository batchOperationUpdateRepository;

  private final AdaptiveBatchSize batchSize;
  private final Logger logger;
  private final Executor executor;

  public BatchOperationUpdateTask(
      final BatchOperationUpdateRepository batchOperationUpdateRepository,
      final int batchSize,
      final Logger logger,
      final Executor executor) {
    this.batchOperationUpdateRepository = batchOperationUpdateRepository;
    this.batchSize = new AdaptiveBatchSize(batchSize);
    this.logger = logger;
    this.executor = executor;
  }

  @Override
  public CompletionStage<Integer> execute() {
    return batchOperationUpdateRepository
        .getNotFinishedBatchOperations(batchSize.current())
        .thenComposeAsync(this::updateBatchOperations, executor)
        .handleAsync(
            (updatesCount, error) -> {
              if (error != null) {
                throw new CompletionException(adjustBatchSize(error));
              }

              return updatesCount;
            },
            executor);
  }

  /** The read is the only lever on how large the write becomes, so it is all that is reduced. */
  private Throwable adjustBatchSize(final Throwable error) {
    final var cause = FuturesUtil.unwrapCompletionException(error);
    if (!(cause instanceof BulkRequestTooLargeException)) {
      return cause;
    }

    if (batchSize.reduce()) {
      logger.warn(
          """
            The store refused the batch operation update write for being too large; retrying with \
            at most {} batch operation(s) per cycle instead of {}.""",
          batchSize.current(),
          batchSize.configured(),
          cause);
    } else {
      logger.warn(
          """
            The store refused the batch operation update write for being too large at a single \
            batch operation; it accepts less than one update per request.""",
          cause);
    }

    return cause;
  }

  @Override
  public String getCaption() {
    return "Batch operation update task";
  }

  private CompletionStage<Integer> updateBatchOperations(
      final Collection<NotFinishedBatchOperation> batchOperations) {
    if (batchOperations.isEmpty()) {
      return CompletableFuture.completedFuture(NO_UPDATES);
    }

    final var batchOperationKeys =
        batchOperations.stream().map(NotFinishedBatchOperation::id).toList();
    final var response = batchOperationUpdateRepository.getOperationsCount(batchOperationKeys);

    return response
        .thenApplyAsync(counts -> collectDocumentUpdates(batchOperations, counts), executor)
        .thenComposeAsync(batchOperationUpdateRepository::bulkUpdate, executor)
        .thenApplyAsync(
            FunctionUtil.peek(
                (updatesCount) -> {
                  batchSize.reset();
                  logger.trace(
                      "BatchOperationUpdateTask - Updated {} batch operations", updatesCount);
                }));
  }

  private List<DocumentUpdate> collectDocumentUpdates(
      final Collection<NotFinishedBatchOperation> batchOperations,
      final List<OperationsAggData> finishedSingleOperationsCount) {
    final var countsByBatchOperationKey =
        finishedSingleOperationsCount.stream()
            .collect(Collectors.toMap(OperationsAggData::batchOperationKey, counts -> counts));

    final var updates = new ArrayList<DocumentUpdate>(batchOperations.size());
    for (final var batchOperation : batchOperations) {
      final var counts = countsByBatchOperationKey.get(batchOperation.id());
      if (counts != null) {
        updates.add(
            new DocumentUpdate(
                batchOperation.id(),
                counts.getFinishedOperationsCount(),
                counts.getFailedOperationsCount(),
                counts.getCompletedOperationsCount(),
                counts.getTotalOperationsCount()));
      } else if (canBeFinalizedWithoutOperations(batchOperation)) {
        updates.add(new DocumentUpdate(batchOperation.id(), 0, 0, 0, 0));
      }
    }

    return updates;
  }

  /**
   * A batch operation whose item query matched nothing - a retry on instances without an incident,
   * for example - has no single operations, so it never shows up in the aggregation and its endDate
   * would never be written. This finalizes it with zeroed counts.
   *
   * <p>Any other absent aggregation bucket is left alone. The counts are then merely unknown: the
   * single operations may not be exported yet, or they may have been archived out of the operation
   * index, and writing zeros would overwrite counts we never measured. The condition deliberately
   * mirrors the update script's own guard, so an emitted update always results in an endDate.
   * Emitting one that does not would keep the batch operation in the selection of every following
   * run, and a run that reports work never lets the task back off.
   */
  private static boolean canBeFinalizedWithoutOperations(
      final NotFinishedBatchOperation batchOperation) {
    return batchOperation.state() == BatchOperationState.COMPLETED
        && batchOperation.operationsTotalCount() == 0;
  }
}
