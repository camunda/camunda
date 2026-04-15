/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.tasks.archiver;

import io.camunda.exporter.config.ExporterConfiguration.HistoryConfiguration;
import io.camunda.exporter.metrics.CamundaExporterMetrics;
import io.camunda.webapps.schema.descriptors.ProcessInstanceDependant;
import io.camunda.webapps.schema.descriptors.template.ListViewTemplate;
import io.camunda.zeebe.util.FunctionUtil;
import io.camunda.zeebe.util.VisibleForTesting;
import io.micrometer.core.instrument.Timer;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executor;
import org.slf4j.Logger;

public class ProcessInstanceArchiverJob implements ArchiverJob {
  private static final int MAX_LARGE_BATCH_SIZE = 5_000;
  private static final int SUB_BATCHES_PER_LARGE_BATCH = 10;

  private final HistoryConfiguration config;
  private final ArchiverRepository repository;
  private final ListViewTemplate template;
  private final List<ProcessInstanceDependant> dependants;
  private final CamundaExporterMetrics metrics;
  private final Logger logger;
  private final Executor executor;
  private final RecentlyArchivedProcessInstances recentlyArchivedProcessInstances;
  private final Queue<ArchiveBatch> pendingBatches =
      new java.util.concurrent.ConcurrentLinkedQueue<>();

  public ProcessInstanceArchiverJob(
      final HistoryConfiguration config,
      final ArchiverRepository repository,
      final ListViewTemplate template,
      final List<ProcessInstanceDependant> dependants,
      final CamundaExporterMetrics metrics,
      final Logger logger,
      final Executor executor) {
    this.config = config;
    this.repository = repository;
    this.template = template;
    this.dependants = dependants;
    this.metrics = metrics;
    this.logger = logger;
    this.executor = executor;
    recentlyArchivedProcessInstances = new RecentlyArchivedProcessInstances(largeBatchSize());
  }

  @Override
  public CompletionStage<Integer> archiveNextBatch() {
    final var timer = Timer.start();
    return getNextBatch()
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

  @VisibleForTesting
  CompletableFuture<ArchiveBatch> getNextBatch() {
    if (!pendingBatches.isEmpty()) {
      return CompletableFuture.completedFuture(pendingBatches.poll());
    }
    return repository
        .getProcessInstancesNextBatch(largeBatchSize())
        .thenApply(
            batch -> {
              if (batch != null) {
                final var deduped = recentlyArchivedProcessInstances.deduplicate(batch);
                final var duplication = batch.ids().size() - deduped.ids().size();
                metrics.recordProcessInstancesArchivingDeduplicated(duplication);
                final var chunks = deduped.chunk(config.getRolloverBatchSize());
                final var first = chunks.removeFirst();
                pendingBatches.addAll(chunks);
                return first;
              }
              return batch;
            });
  }

  private CompletionStage<Integer> archiveBatch(final ArchiveBatch batch) {
    if (batch != null && !(batch.ids() == null || batch.ids().isEmpty())) {
      logger.trace("Following process instances are found for archiving: {}", batch);
      metrics.recordProcessInstancesArchiving(batch.ids().size());

      return moveDependants(batch.finishDate(), batch.ids())
          .thenComposeAsync(
              count -> moveProcessInstances(batch.finishDate(), batch.ids()), executor)
          // we want to make sure the rescheduling happens after we update the metrics, so we peek
          // instead of creating an additional pipeline on the interim future
          .thenApplyAsync(FunctionUtil.peek(metrics::recordProcessInstancesArchived), executor)
          .thenApply(
              archived -> {
                recentlyArchivedProcessInstances.markRecentlyArchived(batch);
                return archived;
              });
    }

    logger.trace("Nothing to archive");
    return CompletableFuture.completedFuture(0);
  }

  private CompletableFuture<Void> moveDependants(
      final String finishDate, final List<String> processInstanceKeys) {
    final List<CompletableFuture<?>> dependentFutures =
        getProcessDependentArchiveFutures(finishDate, processInstanceKeys);
    return CompletableFuture.allOf(dependentFutures.toArray(CompletableFuture[]::new));
  }

  protected CompletableFuture<Integer> moveProcessInstances(
      final String finishDate, final List<String> processInstanceKeys) {
    return archive(
        template.getFullQualifiedName(),
        ListViewTemplate.PROCESS_INSTANCE_KEY,
        finishDate,
        processInstanceKeys);
  }

  protected List<CompletableFuture<?>> getProcessDependentArchiveFutures(
      final String finishDate, final List<String> processInstanceKeys) {
    final var futures = new ArrayList<CompletableFuture<?>>();

    for (final var dependant : dependants) {
      futures.add(
          archive(
              dependant.getFullQualifiedName(),
              dependant.getProcessInstanceDependantField(),
              finishDate,
              processInstanceKeys));
    }
    return futures;
  }

  protected CompletableFuture<Integer> archive(
      final String sourceIdxName,
      final String idField,
      final String finishDate,
      final List<String> processInstanceKeys) {
    return repository
        .moveDocuments(
            sourceIdxName, sourceIdxName + finishDate, idField, processInstanceKeys, executor)
        .thenApplyAsync(ok -> processInstanceKeys.size(), executor);
  }

  private int largeBatchSize() {
    final int rolloverBatchSize = config.getRolloverBatchSize();
    final int largeBatchSize =
        Math.min(MAX_LARGE_BATCH_SIZE, SUB_BATCHES_PER_LARGE_BATCH * rolloverBatchSize);
    // just in case rollover batch size is configured very high
    return Math.max(largeBatchSize, rolloverBatchSize);
  }

  @Override
  public String getCaption() {
    return "Process instances archiver job";
  }

  public ArchiverRepository getRepository() {
    return repository;
  }

  public Executor getExecutor() {
    return executor;
  }
}
