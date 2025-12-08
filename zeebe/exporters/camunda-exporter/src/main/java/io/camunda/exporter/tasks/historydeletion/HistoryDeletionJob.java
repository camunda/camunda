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

  public HistoryDeletionJob(
      final List<ProcessInstanceDependant> processInstanceDependants,
      final Executor executor,
      final HistoryDeletionRepository historyDeletionRepository,
      final Logger logger) {
    this.processInstanceDependants =
        processInstanceDependants.stream()
            .sorted(Comparator.comparing(ProcessInstanceDependant::getFullQualifiedName))
            .toList(); // sort to ensure the execution order is stable
    this.executor = executor;
    deleterRepository = historyDeletionRepository;
    this.logger = logger;
  }

  @Override
  public CompletionStage<Integer> execute() {
    return deleterRepository.getNextBatch().thenComposeAsync(this::deleteBatch, executor);
  }

  private CompletionStage<Integer> deleteBatch(final HistoryDeletionBatch batch) {
    logger.trace("Deleting history for process instances with ids: {}", batch.ids());
    // TODO perform actual deletion
    //  ES: https://github.com/camunda/camunda/issues/41105
    //  OS: https://github.com/camunda/camunda/issues/41109
    return CompletableFuture.completedFuture(0);
  }
}
