/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.tasks.deleter;

import io.camunda.exporter.tasks.BackgroundTask;
import io.camunda.webapps.schema.descriptors.ProcessInstanceDependant;
import io.camunda.webapps.schema.descriptors.template.OperationTemplate;
import io.micrometer.core.instrument.Timer;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executor;

public class DeleterJob implements BackgroundTask {
  private final List<ProcessInstanceDependant> processInstanceDependants;
  private final Executor executor;
  private final DeleterRepository deleterRepository;

  public DeleterJob(
      final List<ProcessInstanceDependant> processInstanceDependants,
      final Executor executor,
      final DeleterRepository deleterRepository) {
    this.processInstanceDependants =
        processInstanceDependants.stream()
            .sorted(Comparator.comparing(ProcessInstanceDependant::getFullQualifiedName))
            .toList(); // sort to ensure the execution order is stable
    this.executor = executor;
    this.deleterRepository = deleterRepository;
  }

  @Override
  public CompletionStage<Integer> execute() {
    final var timer = Timer.start();

    return deleterRepository
        .getNextBatch()
        .thenComposeAsync(this::deleteBatch, executor)
        // we schedule gathering timer metrics after the archiveBatch future - to correctly
        // measure the time it takes all in all, including searching, reindexing, deletion
        // There is some overhead with the scheduling at the executor, but this
        // should be negligible
        .thenComposeAsync((ok) -> CompletableFuture.completedFuture(1), executor);
  }

  @Override
  public String getCaption() {
    return BackgroundTask.super.getCaption();
  }

  @Override
  public void close() {
    BackgroundTask.super.close();
  }

  private CompletionStage<Void> deleteBatch(final DeleteBatch batch) {
    final var deleteDependentDocuments =
        processInstanceDependants.stream()
            .filter(t -> !(t instanceof OperationTemplate))
            .map(
                dependant -> {
                  final var dependentSourceIdx = dependant.getFullQualifiedName();
                  final var dependentIdFieldName = dependant.getProcessInstanceDependantField();
                  return deleterRepository.deleteDocuments(
                      dependentSourceIdx, dependentIdFieldName, batch.ids());
                })
            .toList();

    //    deleterRepository.deleteFromListView(batch.ids()); TODO delete from list view. Concurrency
    // issues happened
    deleterRepository.deleteFromDeleteIndex(batch.ids());

    // TODO fix the return here
    return CompletableFuture.allOf();
  }
}
