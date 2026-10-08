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
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executor;
import org.slf4j.Logger;

/**
 * Archiver job for process instance data. This job handles the archiving of the process instance
 * records itself and also delegates to the repository to move dependent records (decisions, flow
 * node instances, variable updates, etc).
 */
public class ProcessInstanceByIdArchiverJob implements ArchiverJob {
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

  public ProcessInstanceByIdArchiverJob(
      final HistoryConfiguration config,
      final ArchiverRepository repository,
      final ListViewTemplate processInstanceTemplate,
      final List<ProcessInstanceDependant> processInstanceDependants,
      final CamundaExporterMetrics metrics,
      final Logger logger,
      final Executor executor) {
    this.config = config;
    this.repository = repository;
    template = processInstanceTemplate;
    dependants = processInstanceDependants;
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
        .whenCompleteAsync((val, err) -> metrics.measureArchivingDuration(timer), executor);
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
          .thenApplyAsync(
              FunctionUtil.peek(
                  (count) -> recentlyArchivedProcessInstances.markRecentlyArchived(batch)),
              executor);
    }

    logger.trace("Nothing to archive");
    return CompletableFuture.completedFuture(0);
  }

  public CompletableFuture<Void> moveDependants(
      final String finishDate, final List<String> processInstanceKeys) {
    // get the usual process instance dependent archive tasks
    final List<CompletableFuture<?>> dependentFutures =
        getProcessDependentArchiveFutures(finishDate, processInstanceKeys);

    // add archiving tasks to archive data from list-view in parallel with the dependent indexes.

    // Note: We can archive all data except documents with `joinRelation: processInstance`.
    // These must be moved last to avoid dangling data (i.e., child/dependent records that cannot
    // be moved independently of their parent instance). We use fields from the parent
    // document (e.g., `endDate`, `status`) to decide whether to archive documents from the
    // main index.

    // add archiving variables from the list view index as a parallel task
    dependentFutures.add(
        archive(
            template.getFullQualifiedName(),
            ListViewTemplate.PROCESS_INSTANCE_KEY,
            finishDate,
            processInstanceKeys,
            Map.of(ListViewTemplate.JOIN_RELATION, ListViewTemplate.VARIABLES_JOIN_RELATION),
            Map.of()));

    // add archiving flownodes/activities from the list view index as a parallel task
    dependentFutures.add(
        archive(
            template.getFullQualifiedName(),
            ListViewTemplate.PROCESS_INSTANCE_KEY,
            finishDate,
            processInstanceKeys,
            Map.of(ListViewTemplate.JOIN_RELATION, ListViewTemplate.ACTIVITIES_JOIN_RELATION),
            Map.of()));

    return CompletableFuture.allOf(dependentFutures.toArray(CompletableFuture[]::new));
  }

  protected CompletableFuture<Integer> moveProcessInstances(
      final String finishDate, final List<String> processInstanceKeys) {
    // 1. First archive docs from dependent indices and `joinRelation={variable OR activity}`
    // from operate-list-view index (already done)
    // 2. Then archive all docs except `joinRelation=processInstance` from operate-list-view index;
    // we use this as a catch-all clause to move all related documents.
    // 3. Then archive all `joinRelation=processInstance` docs from operate-list-view index
    return archive(
            template.getFullQualifiedName(),
            ListViewTemplate.PROCESS_INSTANCE_KEY,
            finishDate,
            processInstanceKeys,
            Map.of(),
            Map.of(ListViewTemplate.JOIN_RELATION, ListViewTemplate.PROCESS_INSTANCE_JOIN_RELATION))
        .thenComposeAsync(
            ignored ->
                archive(
                    template.getFullQualifiedName(),
                    ListViewTemplate.PROCESS_INSTANCE_KEY,
                    finishDate,
                    processInstanceKeys,
                    Map.of(
                        ListViewTemplate.JOIN_RELATION,
                        ListViewTemplate.PROCESS_INSTANCE_JOIN_RELATION),
                    Map.of()),
            executor);
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
    return archive(sourceIdxName, idField, finishDate, processInstanceKeys, Map.of(), Map.of());
  }

  protected CompletableFuture<Integer> archive(
      final String sourceIdxName,
      final String idField,
      final String finishDate,
      final List<String> processInstanceKeys,
      final Map<String, String> inclusionFilters,
      final Map<String, String> exclusionFilters) {
    return repository
        .moveDocumentsById(
            sourceIdxName,
            sourceIdxName + finishDate,
            idField,
            processInstanceKeys,
            inclusionFilters,
            exclusionFilters,
            executor)
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
    return "Process instances by ID archiver job";
  }
}
