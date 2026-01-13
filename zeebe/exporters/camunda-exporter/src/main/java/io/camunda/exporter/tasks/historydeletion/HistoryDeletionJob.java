/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.tasks.historydeletion;

import io.camunda.exporter.tasks.BackgroundTask;
import io.camunda.webapps.schema.descriptors.ProcessInstanceDependant;
import io.camunda.webapps.schema.descriptors.index.HistoryDeletionIndex;
import io.camunda.webapps.schema.descriptors.template.ListViewTemplate;
import io.camunda.webapps.schema.descriptors.template.OperationTemplate;
import io.camunda.zeebe.protocol.record.value.HistoryDeletionType;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executor;
import org.slf4j.Logger;

/**
 * Background task that queries the history-deletion index for resources marked for deletion and
 * deletes the corresponding data from secondary storage. Work is distributed across partitions to
 * balance the deletion load.
 *
 * <p>The job interacts with the history-deletion index to identify resources scheduled for
 * deletion, and executes deletion requests in batches, partitioned by partition ID to ensure
 * balanced processing.
 */
public class HistoryDeletionJob implements BackgroundTask {
  private final List<ProcessInstanceDependant> processInstanceDependants;
  private final Executor executor;
  private final HistoryDeletionRepository deleterRepository;
  private final Logger logger;
  private final HistoryDeletionIndex historyDeletionIndex;
  private final ListViewTemplate listViewTemplate;

  public HistoryDeletionJob(
      final List<ProcessInstanceDependant> processInstanceDependants,
      final Executor executor,
      final HistoryDeletionRepository historyDeletionRepository,
      final Logger logger,
      final HistoryDeletionIndex historyDeletionIndex,
      final ListViewTemplate listViewTemplate) {
    this.processInstanceDependants =
        processInstanceDependants.stream()
            .sorted(Comparator.comparing(ProcessInstanceDependant::getFullQualifiedName))
            .toList(); // sort to ensure the execution order is stable
    this.executor = executor;
    deleterRepository = historyDeletionRepository;
    this.logger = logger;
    this.historyDeletionIndex = historyDeletionIndex;
    this.listViewTemplate = listViewTemplate;
  }

  @Override
  public CompletionStage<Integer> execute() {
    return deleterRepository.getNextBatch().thenComposeAsync(this::deleteBatch, executor);
  }

  /**
   * This deletes all entities in a given batch. A batch could contain different types of entities,
   * such as process instances, or process definitions.
   *
   * @param batch The batch of entities to delete
   * @return A future containing the amount of entities that were deleted
   */
  private CompletionStage<Integer> deleteBatch(final HistoryDeletionBatch batch) {
    logger.trace("Deleting historic data for entities: {}", batch.historyDeletionEntities());

    if (batch.historyDeletionEntities().isEmpty()) {
      logger.trace("No historic data to delete");
      return CompletableFuture.completedFuture(0);
    }

    return deleteProcessInstances(batch).thenCompose(this::deleteFromHistoryDeletionIndex);
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
  private CompletionStage<List<String>> deleteProcessInstances(final HistoryDeletionBatch batch) {
    final var processInstanceKeys = batch.getResourceKeys(HistoryDeletionType.PROCESS_INSTANCE);
    if (processInstanceKeys.isEmpty()) {
      return CompletableFuture.completedFuture(List.of());
    }

    final var deletionFutures =
        processInstanceDependants.stream()
            .filter(t -> !(t instanceof OperationTemplate))
            .map(
                dependant -> {
                  final var dependentSourceIdx = dependant.getFullQualifiedName() + "*";
                  final var dependentIdFieldName = dependant.getProcessInstanceDependantField();
                  return deleterRepository.deleteDocumentsByField(
                      dependentSourceIdx, dependentIdFieldName, processInstanceKeys);
                })
            .toList();

    return CompletableFuture.allOf(deletionFutures.toArray(CompletableFuture[]::new))
        .thenCompose(
            ignored -> {
              final var dependentSourceIdx = listViewTemplate.getIndexPattern();
              final var dependentIdFieldName = ListViewTemplate.PROCESS_INSTANCE_KEY;
              return deleterRepository.deleteDocumentsByField(
                  dependentSourceIdx, dependentIdFieldName, processInstanceKeys);
            })
        .thenApply(ignored -> batch.getHistoryDeletionIds(HistoryDeletionType.PROCESS_INSTANCE));
  }

  private CompletionStage<Integer> deleteFromHistoryDeletionIndex(final List<String> ids) {
    if (ids.isEmpty()) {
      return CompletableFuture.completedFuture(0);
    }
    return deleterRepository.deleteDocumentsById(historyDeletionIndex.getFullQualifiedName(), ids);
  }
}
