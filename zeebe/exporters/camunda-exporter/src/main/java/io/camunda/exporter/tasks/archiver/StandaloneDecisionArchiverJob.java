/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.tasks.archiver;

import io.camunda.exporter.metrics.CamundaExporterMetrics;
import io.camunda.webapps.schema.descriptors.template.DecisionInstanceTemplate;
import io.camunda.zeebe.util.FunctionUtil;
import io.micrometer.core.instrument.Timer;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executor;
import org.slf4j.Logger;

public class StandaloneDecisionArchiverJob implements ArchiverJob {

  private final ArchiverRepository repository;
  private final DecisionInstanceTemplate decisionInstanceTemplate;
  private final CamundaExporterMetrics metrics;
  private final Logger logger;
  private final Executor executor;

  public StandaloneDecisionArchiverJob(
      final ArchiverRepository repository,
      final DecisionInstanceTemplate decisionInstanceTemplate,
      final CamundaExporterMetrics metrics,
      final Logger logger,
      final Executor executor) {
    this.repository = repository;
    this.metrics = metrics;
    this.decisionInstanceTemplate = decisionInstanceTemplate;
    this.logger = logger;
    this.executor = executor;
  }

  @Override
  public CompletionStage<Integer> archiveNextBatch() {
    final var timer = Timer.start();
    return repository
        .getStandaloneDecisionNextBatch()
        .thenComposeAsync(this::archiveBatch, executor)
        // we schedule us after the archiveBatch future - to correctly measure
        // the time it takes all in all, including searching, reindexing, deletion
        // There is some overhead with the scheduling at the executor, but this should be
        // negligible
        .thenComposeAsync(
            count -> {
              metrics.measureArchivingDuration(timer);
              return CompletableFuture.completedFuture(count);
            },
            executor);
  }

  private CompletableFuture<Integer> archiveBatch(final ArchiveBatch archiveBatch) {
    if (archiveBatch != null && !(archiveBatch.ids() == null || archiveBatch.ids().isEmpty())) {
      logger.trace(
          "Following standalone decision instances are found for archiving: {}", archiveBatch);
      metrics.recordStandaloneDecisionsArchiving(archiveBatch.ids().size());

      return moveBatch(archiveBatch.finishDate(), archiveBatch.ids())
          .thenApplyAsync(FunctionUtil.peek(metrics::recordStandaloneDecisionsArchived), executor);
    }

    logger.trace("Nothing to archive");
    return CompletableFuture.completedFuture(0);
  }

  private CompletableFuture<Integer> moveBatch(final String finishDate, final List<String> ids) {
    return repository
        .moveDocuments(
            decisionInstanceTemplate.getFullQualifiedName(),
            decisionInstanceTemplate.getFullQualifiedName() + finishDate,
            DecisionInstanceTemplate.ID,
            ids,
            executor)
        .thenApplyAsync(ok -> ids.size(), executor);
  }

  @Override
  public String toString() {
    return "Standalone decision instances archiver job";
  }
}
