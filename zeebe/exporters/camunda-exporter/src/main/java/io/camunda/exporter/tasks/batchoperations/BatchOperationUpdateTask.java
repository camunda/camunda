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
import io.camunda.webapps.schema.descriptors.operate.template.BatchOperationTemplate;
import io.camunda.webapps.schema.entities.operation.BatchOperationEntity;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.stream.Collectors;
import org.slf4j.Logger;

public class BatchOperationUpdateTask implements BackgroundTask {

  private final BatchOperationUpdateRepository batchOperationUpdateRepository;

  private final Logger logger;

  public BatchOperationUpdateTask(
      final BatchOperationUpdateRepository batchOperationUpdateRepository, final Logger logger) {
    this.batchOperationUpdateRepository = batchOperationUpdateRepository;
    this.logger = logger;
  }

  @Override
  public CompletionStage<Integer> execute() {

    final var batchOperations = batchOperationUpdateRepository.getNotFinishedBatchOperations();

    if (batchOperations.size() > 0) {

      final var finishedOperationsCount =
          batchOperationUpdateRepository.getFinishedOperationsCount(
              batchOperations.stream()
                  .map(BatchOperationEntity::getId)
                  .collect(Collectors.toList()));

      final var documentUpdates =
          finishedOperationsCount.stream()
              .map(
                  d ->
                      new DocumentUpdate(
                          d.batchOperationId(),
                          Map.of(
                              BatchOperationTemplate.COMPLETED_OPERATIONS_COUNT,
                              d.finishedOperationsCount())))
              .toList();

      if (documentUpdates.size() > 0) {
        return CompletableFuture.completedFuture(
            batchOperationUpdateRepository.bulkUpdate(documentUpdates));
      } // else return 0
    }

    return CompletableFuture.completedFuture(0);
  }
}
