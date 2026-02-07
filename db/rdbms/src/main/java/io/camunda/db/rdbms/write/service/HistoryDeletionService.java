/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.db.rdbms.write.service;

import io.camunda.db.rdbms.read.service.HistoryDeletionDbReader;
import io.camunda.db.rdbms.read.service.ProcessInstanceDbReader;
import io.camunda.db.rdbms.write.RdbmsWriterConfig.HistoryDeletionConfig;
import io.camunda.db.rdbms.write.RdbmsWriters;
import io.camunda.db.rdbms.write.domain.HistoryDeletionBatch;
import io.camunda.db.rdbms.write.domain.HistoryDeletionDbModel.HistoryDeletionTypeDbModel;
import io.camunda.zeebe.util.ExponentialBackoff;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This service is for deleting history on user request. For data retention see {@link
 * HistoryCleanupService}.
 */
public class HistoryDeletionService {
  private static final Logger LOG = LoggerFactory.getLogger(HistoryDeletionService.class);

  private final RdbmsWriters rdbmsWriters;
  private final HistoryDeletionDbReader historyDeletionDbReader;
  private final ProcessInstanceDbReader processInstanceDbReader;
  private final HistoryDeletionConfig config;
  private final ExponentialBackoff exponentialBackoff;
  private Duration currentDelayBetweenRuns;

  public HistoryDeletionService(
      final RdbmsWriters rdbmsWriters,
      final HistoryDeletionDbReader historyDeletionDbReader,
      final ProcessInstanceDbReader processInstanceDbReader,
      final HistoryDeletionConfig config) {
    this.rdbmsWriters = rdbmsWriters;
    this.historyDeletionDbReader = historyDeletionDbReader;
    this.processInstanceDbReader = processInstanceDbReader;
    this.config = config;
    exponentialBackoff =
        new ExponentialBackoff(
            config.maxDelayBetweenRuns().toMillis(),
            config.delayBetweenRuns().toMillis(),
            2,
            0.0); // Use 0.0 jitter for deterministic backoff and clamp to min delay to avoid
    // sub-min values
    currentDelayBetweenRuns = config.delayBetweenRuns();
  }

  public Duration deleteHistory(final int partitionId) {
    final var batch = historyDeletionDbReader.getNextBatch(partitionId, config.queueBatchSize());

    final var deletedResourceCount = deleteBatch(batch).toCompletableFuture().join();

    return nextDelay(deletedResourceCount);
  }

  /**
   * This deletes all entities in a given batch. A batch could contain different types of entities,
   * such as process instances, process definitions, or decision instances.
   *
   * @param batch The batch of entities to delete
   * @return A future containing the amount of entities that were deleted
   */
  private CompletionStage<Integer> deleteBatch(final HistoryDeletionBatch batch) {
    LOG.trace("Deleting historic data for entities: {}", batch.historyDeletionModels());

    if (batch.historyDeletionModels().isEmpty()) {
      LOG.trace("No historic data to delete");
      return CompletableFuture.completedFuture(0);
    }

    final var deleteProcessInstancesAndDefinitionsFuture =
        deleteProcessInstances(batch)
            .thenCompose(
                ids ->
                    deleteProcessDefinitions(batch, ids)
                        .exceptionally(
                            ex -> {
                              LOG.warn(
                                  "Failed to delete process definitions for batch: {}", batch, ex);
                              return ids;
                            }))
            .exceptionally(
                ex -> {
                  LOG.warn("Failed to delete process instances for batch: {}", batch, ex);
                  return List.of();
                })
            .toCompletableFuture();

    final var deleteDecisionInstancesFuture =
        deleteDecisionInstances(batch)
            .exceptionally(
                ex -> {
                  LOG.warn("Failed to delete decision instances for batch: {}", batch, ex);
                  return List.of();
                })
            .toCompletableFuture();

    return CompletableFuture.allOf(
            deleteProcessInstancesAndDefinitionsFuture, deleteDecisionInstancesFuture)
        .thenCompose(
            ignored -> {
              final var deletedResources = new ArrayList<Long>();
              deletedResources.addAll(deleteProcessInstancesAndDefinitionsFuture.join());
              deletedResources.addAll(deleteDecisionInstancesFuture.join());
              return CompletableFuture.completedFuture(
                  deleteFromHistoryDeletionTable(deletedResources));
            });
  }

  /**
   * This method will delete a batch of process instances in two steps:
   *
   * <ol>
   *   <li>It deletes the PI related data from all process instance dependants (eg, variable, jobs)
   *   <li>It deletes the PIs from the list-view
   * </ol>
   *
   * @param batch The batch of entities to delete
   * @return A future containing the list of history-deletion IDs that were processed
   */
  private CompletionStage<List<Long>> deleteProcessInstances(final HistoryDeletionBatch batch) {
    final var processInstanceKeys =
        batch.getResourceKeys(HistoryDeletionTypeDbModel.PROCESS_INSTANCE);
    if (processInstanceKeys.isEmpty()) {
      return CompletableFuture.completedFuture(List.of());
    }

    final var deletionFutures =
        rdbmsWriters.getProcessInstanceDependantWriters().stream()
            .filter(dependant -> !(dependant instanceof AuditLogWriter))
            .map(
                dependant -> {
                  final var limit = config.dependentRowLimit();
                  return CompletableFuture.supplyAsync(
                      () ->
                          dependant.deleteRootProcessInstanceRelatedData(
                              processInstanceKeys, limit));
                })
            .toList();

    return CompletableFuture.allOf(deletionFutures.toArray(CompletableFuture[]::new))
        .thenCompose(
            ignored -> {
              rdbmsWriters.getProcessInstanceWriter().deleteByKeys(processInstanceKeys);
              return CompletableFuture.completedFuture(processInstanceKeys);
            });
  }

  /**
   * This method will delete a batch of process definitions by deleting the process definitions from
   * the process index
   *
   * @param batch The batch of entities to delete
   * @param deletedResourceKeys the list of the deleted process instance keys
   * @return A future containing the list of history-deletion IDs that were processed
   */
  private CompletionStage<List<Long>> deleteProcessDefinitions(
      final HistoryDeletionBatch batch, final List<Long> deletedResourceKeys) {
    final var processDefinitionKeys =
        batch.getResourceKeys(HistoryDeletionTypeDbModel.PROCESS_DEFINITION);

    if (processDefinitionKeys.isEmpty()) {
      return CompletableFuture.completedFuture(deletedResourceKeys);
    }

    return CompletableFuture.supplyAsync(
        () -> {
          rdbmsWriters.getProcessDefinitionWriter().deleteByKeys(processDefinitionKeys);

          final var deletedResources = new ArrayList<>(deletedResourceKeys);
          deletedResources.addAll(processDefinitionKeys);
          return deletedResources;
        });
  }

  /**
   * This method will delete a batch of decision instances.
   *
   * @param batch The batch of entities to delete
   * @return A future containing the list of history-deletion IDs that were processed
   */
  private CompletionStage<List<Long>> deleteDecisionInstances(final HistoryDeletionBatch batch) {
    final var decisionInstanceKeys =
        batch.getResourceKeys(HistoryDeletionTypeDbModel.DECISION_INSTANCE);
    if (decisionInstanceKeys.isEmpty()) {
      return CompletableFuture.completedFuture(List.of());
    }

    return CompletableFuture.supplyAsync(
        () -> {
          rdbmsWriters.getDecisionInstanceWriter().deleteByKeys(decisionInstanceKeys);
          return decisionInstanceKeys;
        });
  }

  private int deleteFromHistoryDeletionTable(final List<Long> deletedResourceKeys) {
    if (deletedResourceKeys.isEmpty()) {
      return 0;
    }

    return rdbmsWriters.getHistoryDeletionWriter().deleteByResourceKeys(deletedResourceKeys);
  }

  private Duration nextDelay(final int deletedResourceCount) {
    if (deletedResourceCount > 0) {
      currentDelayBetweenRuns = config.delayBetweenRuns();
    } else {
      final long nextMs = exponentialBackoff.supplyRetryDelay(currentDelayBetweenRuns.toMillis());
      currentDelayBetweenRuns = Duration.ofMillis(nextMs);
    }
    return currentDelayBetweenRuns;
  }

  public Duration getCurrentDelayBetweenRuns() {
    return currentDelayBetweenRuns;
  }
}
