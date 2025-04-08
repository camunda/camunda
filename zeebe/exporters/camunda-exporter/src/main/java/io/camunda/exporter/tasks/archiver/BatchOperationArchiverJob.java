/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.tasks.archiver;

import io.camunda.exporter.metrics.CamundaExporterMetrics;
import io.camunda.webapps.schema.descriptors.template.BatchOperationTemplate;
import io.camunda.zeebe.util.FunctionUtil;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executor;
import org.slf4j.Logger;

public class BatchOperationArchiverJob implements ArchiverJob {

  private final ArchiverRepository repository;
  private final BatchOperationTemplate batchOperationTemplate;
  private final CamundaExporterMetrics metrics;
  private final Logger logger;
  private final Executor executor;

  public BatchOperationArchiverJob(
      final ArchiverRepository repository,
      final BatchOperationTemplate batchOperationTemplate,
      final CamundaExporterMetrics metrics,
      final Logger logger,
      final Executor executor) {
    this.repository = repository;
    this.metrics = metrics;
    this.batchOperationTemplate = batchOperationTemplate;
    this.logger = logger;
    this.executor = executor;
  }

  @Override
  public CompletionStage<Integer> archiveNextBatch() {
    return repository.getBatchOperationsNextBatch().thenComposeAsync(this::archiveBatch, executor);
  }

  private CompletableFuture<Integer> archiveBatch(final ArchiveBatch archiveBatch) {

    if (archiveBatch != null) {
      logger.trace("Following batch operations are found for archiving: {}", archiveBatch);

      return moveBatch(archiveBatch.finishDate(), archiveBatch.ids())
          .thenApplyAsync(FunctionUtil.peek(metrics::recordBatchOperationsArchived), executor);
    }

    logger.trace("Nothing to archive");
    return CompletableFuture.completedFuture(0);
  }

  private CompletableFuture<Integer> moveBatch(
      final String finishDate, final List<String> processInstanceKeys) {
    return repository
        .moveDocuments(
            batchOperationTemplate.getFullQualifiedName(),
            batchOperationTemplate.getFullQualifiedName() + finishDate,
            finishDate,
            processInstanceKeys,
            executor)
        .thenApplyAsync(ok -> processInstanceKeys.size(), executor);
  }

  @Override
  public String toString() {
    return "Batch operation archiver job";
  }
}
