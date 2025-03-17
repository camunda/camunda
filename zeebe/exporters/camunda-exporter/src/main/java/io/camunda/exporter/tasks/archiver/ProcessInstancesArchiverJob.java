/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.tasks.archiver;

import io.camunda.exporter.metrics.CamundaExporterMetrics;
import io.camunda.webapps.schema.descriptors.operate.ProcessInstanceDependant;
import io.camunda.webapps.schema.descriptors.operate.template.ListViewTemplate;
import io.camunda.zeebe.util.FunctionUtil;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executor;
import org.slf4j.Logger;

public class ProcessInstancesArchiverJob implements ArchiverJob {

  private final ArchiverRepository repository;
  private final ListViewTemplate template;
  private final List<ProcessInstanceDependant> dependants;
  private final CamundaExporterMetrics metrics;
  private final Logger logger;
  private final Executor executor;

  public ProcessInstancesArchiverJob(
      final ArchiverRepository repository,
      final ListViewTemplate template,
      final List<ProcessInstanceDependant> dependants,
      final CamundaExporterMetrics metrics,
      final Logger logger,
      final Executor executor) {
    this.repository = repository;
    this.template = template;
    this.dependants = dependants;
    this.metrics = metrics;
    this.logger = logger;
    this.executor = executor;
  }

  @Override
  public CompletionStage<Integer> archiveNextBatch() {
    return repository.getProcessInstancesNextBatch().thenComposeAsync(this::archiveBatch, executor);
  }

  private CompletionStage<Integer> archiveBatch(final ArchiveBatch batch) {
    if (batch != null && !(batch.ids() == null || batch.ids().isEmpty())) {
      logger.trace("Following process instances are found for archiving: {}", batch);

      return moveDependants(batch.finishDate(), batch.ids())
          .thenComposeAsync(
              count -> moveProcessInstances(batch.finishDate(), batch.ids()), executor)
          // we want to make sure the rescheduling happens after we update the metrics, so we peek
          // instead of creating an additional pipeline on the interim future
          .thenApplyAsync(FunctionUtil.peek(metrics::recordProcessInstancesArchived), executor);
    }

    logger.trace("Nothing to archive");
    return CompletableFuture.completedFuture(0);
  }

  private CompletableFuture<Void> moveDependants(
      final String finishDate, final List<String> processInstanceKeys) {
    final var movedDocuments =
        dependants.stream()
            .map(
                dependant ->
                    repository.moveDocuments(
                        dependant.getFullQualifiedName(),
                        dependant.getFullQualifiedName() + finishDate,
                        dependant.getProcessInstanceDependantField(),
                        processInstanceKeys,
                        executor))
            .toArray(CompletableFuture[]::new);
    return CompletableFuture.allOf(movedDocuments);
  }

  private CompletableFuture<Integer> moveProcessInstances(
      final String finishDate, final List<String> processInstanceKeys) {
    return repository
        .moveDocuments(
            template.getFullQualifiedName(),
            template.getFullQualifiedName() + finishDate,
            ListViewTemplate.PROCESS_INSTANCE_KEY,
            processInstanceKeys,
            executor)
        .thenApplyAsync(ok -> processInstanceKeys.size(), executor);
  }

  @Override
  public String getCaption() {
    return "Process instances archiver job";
  }
}
