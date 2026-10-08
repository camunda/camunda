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
import io.camunda.exporter.tasks.archiver.ArchiveBatch.ProcessInstanceArchiveBatch;
import io.camunda.webapps.schema.descriptors.IndexTemplateDescriptor;
import io.camunda.webapps.schema.descriptors.ProcessInstanceDependant;
import io.camunda.webapps.schema.descriptors.template.ListViewTemplate;
import io.camunda.zeebe.util.FunctionUtil;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import org.slf4j.Logger;

/**
 * Archiver job for process instance data. This job handles the archiving of the process instance
 * records itself and also delegates to the repository to move dependent records (decisions, flow
 * node instances, variable updates, etc).
 */
public class ProcessInstanceArchiverJob extends ArchiverJob<ProcessInstanceArchiveBatch> {

  private static final int MAX_LARGE_BATCH_SIZE = 5_000;
  private static final int SUB_BATCHES_PER_LARGE_BATCH = 10;
  private final HistoryConfiguration config;
  private final ListViewTemplate processInstanceTemplate;
  private final List<ProcessInstanceDependant> processInstanceDependants;
  private final RecentlyArchivedProcessInstances recentlyArchivedProcessInstances;
  private final Queue<ProcessInstanceArchiveBatch> pendingBatches =
      new java.util.concurrent.ConcurrentLinkedQueue<>();

  public ProcessInstanceArchiverJob(
      final HistoryConfiguration config,
      final ArchiverRepository repository,
      final ListViewTemplate processInstanceTemplate,
      final List<ProcessInstanceDependant> processInstanceDependants,
      final CamundaExporterMetrics metrics,
      final Logger logger,
      final Executor executor) {
    super(
        repository,
        metrics,
        logger,
        executor,
        metrics::recordProcessInstancesArchiving,
        metrics::recordProcessInstancesArchived);
    this.config = config;
    this.processInstanceTemplate = processInstanceTemplate;
    this.processInstanceDependants =
        processInstanceDependants.stream()
            .sorted(Comparator.comparing(ProcessInstanceDependant::getFullQualifiedName))
            .toList(); // sort to ensure the execution order is stable
    recentlyArchivedProcessInstances = new RecentlyArchivedProcessInstances(largeBatchSize());
  }

  @Override
  String getJobName() {
    return "process-instance-by-id";
  }

  @Override
  CompletableFuture<ProcessInstanceArchiveBatch> getNextBatch() {
    if (!pendingBatches.isEmpty()) {
      return CompletableFuture.completedFuture(pendingBatches.poll());
    }
    return getArchiverRepository()
        .getProcessInstancesNextBatch(largeBatchSize())
        .thenApply(
            batch -> {
              if (batch == null) {
                return null;
              }
              final var deduped = recentlyArchivedProcessInstances.deduplicate(batch);
              final var duplication = batch.size() - deduped.size();
              getExporterMetrics().recordProcessInstancesArchivingDeduplicated(duplication);
              final var chunks = deduped.chunk(config.getRolloverBatchSize());
              final var first = chunks.removeFirst();
              pendingBatches.addAll(chunks);
              return first;
            });
  }

  @Override
  ListViewTemplate getTemplateDescriptor() {
    return processInstanceTemplate;
  }

  @Override
  protected CompletableFuture<Integer> archive(
      final IndexTemplateDescriptor templateDescriptor, final ProcessInstanceArchiveBatch batch) {
    // 1. First archive docs from dependent indices and `joinRelation={variable OR activity}`
    // from operate-list-view index
    // 2. Then archive all docs except `joinRelation=processInstance` from operate-list-view index;
    // we use this as a catch-all clause to move all related documents.
    // 3. Then archive all `joinRelation=processInstance` docs from operate-list-view index
    return archiveProcessDependants(batch)
        .thenComposeAsync(
            v ->
                archive(
                    templateDescriptor,
                    batch,
                    Map.of(),
                    Map.of(
                        ListViewTemplate.JOIN_RELATION,
                        ListViewTemplate.PROCESS_INSTANCE_JOIN_RELATION)),
            getExecutor())
        .thenComposeAsync(
            ignored ->
                archive(
                    templateDescriptor,
                    batch,
                    Map.of(
                        ListViewTemplate.JOIN_RELATION,
                        ListViewTemplate.PROCESS_INSTANCE_JOIN_RELATION),
                    Map.of()),
            getExecutor())
        .thenApply(FunctionUtil.peek(archived -> markBatchRecentlyArchived(batch)));
  }

  @Override
  protected CompletableFuture<Integer> archive(
      final IndexTemplateDescriptor templateDescriptor,
      final ProcessInstanceArchiveBatch batch,
      final Map<String, String> inclusionFilters) {
    return archive(templateDescriptor, batch, inclusionFilters, Map.of());
  }

  @Override
  protected Map<String, List<String>> createIdsByFieldMap(
      final IndexTemplateDescriptor templateDescriptor, final ProcessInstanceArchiveBatch batch) {
    final Map<String, List<String>> idsMap = new HashMap<>();
    final String processInstanceKeyField;
    final String rootProcessInstanceKeyField;
    switch (templateDescriptor) {
      case final ListViewTemplate ignored -> {
        processInstanceKeyField = ListViewTemplate.PROCESS_INSTANCE_KEY;
        rootProcessInstanceKeyField = ListViewTemplate.ROOT_PROCESS_INSTANCE_KEY;
      }
      case final ProcessInstanceDependant pid -> {
        processInstanceKeyField = pid.getProcessInstanceDependantField();
        rootProcessInstanceKeyField = pid.getRootProcessInstanceKeyField();
      }
      default ->
          throw new IllegalArgumentException(
              "Unsupported template descriptor: " + templateDescriptor.getClass().getName());
    }
    if (!batch.processInstanceKeys().isEmpty()) {
      idsMap.put(
          processInstanceKeyField,
          batch.processInstanceKeys().stream().map(String::valueOf).toList());
    }
    if (!batch.rootProcessInstanceKeys().isEmpty()) {
      idsMap.put(
          rootProcessInstanceKeyField,
          batch.rootProcessInstanceKeys().stream().map(String::valueOf).toList());
    }
    return idsMap;
  }

  protected CompletableFuture<Void> archiveProcessDependants(
      final ProcessInstanceArchiveBatch batch) {
    // get the usual process instance dependent archive tasks
    final List<CompletableFuture<?>> dependentFutures = getProcessDependentArchiveFutures(batch);

    // add archiving tasks to archive data from list-view in parallel with the dependent indexes.

    // Note: We can archive all data except documents with `joinRelation: processInstance`.
    // These must be moved last to avoid dangling data (i.e., child/dependent records that cannot
    // be moved independently of their parent instance). We use fields from the parent
    // document (e.g., `endDate`, `status`) to decide whether to archive documents from the
    // main index.

    // add archiving variables from the list view index as a parallel task
    dependentFutures.add(
        archive(
            getTemplateDescriptor(),
            batch,
            Map.of(ListViewTemplate.JOIN_RELATION, ListViewTemplate.VARIABLES_JOIN_RELATION)));

    // add archiving flownodes/activities from the list view index as a parallel task
    dependentFutures.add(
        archive(
            getTemplateDescriptor(),
            batch,
            Map.of(ListViewTemplate.JOIN_RELATION, ListViewTemplate.ACTIVITIES_JOIN_RELATION)));

    return CompletableFuture.allOf(dependentFutures.toArray(CompletableFuture[]::new));
  }

  protected CompletableFuture<Integer> archive(
      final IndexTemplateDescriptor templateDescriptor,
      final ProcessInstanceArchiveBatch batch,
      final Map<String, String> inclusionFilters,
      final Map<String, String> exclusionFilters) {
    final var sourceIdxName = templateDescriptor.getFullQualifiedName();
    final var idsMap = createIdsByFieldMap(templateDescriptor, batch);
    final var finishDate = batch.finishDate();
    return getArchiverRepository()
        .moveDocumentsById(
            sourceIdxName,
            sourceIdxName + finishDate,
            idsMap,
            inclusionFilters,
            exclusionFilters,
            getExecutor())
        .thenApplyAsync(ok -> batch.size(), getExecutor());
  }

  protected void markBatchRecentlyArchived(final ProcessInstanceArchiveBatch batch) {
    recentlyArchivedProcessInstances.markRecentlyArchived(batch);
  }

  protected List<CompletableFuture<?>> getProcessDependentArchiveFutures(
      final ProcessInstanceArchiveBatch batch) {
    final var futures = new ArrayList<CompletableFuture<?>>();

    for (final var dependant : processInstanceDependants) {
      futures.add(archive(dependant, batch, Map.of()));
    }

    return futures;
  }

  private int largeBatchSize() {
    final int rolloverBatchSize = config.getRolloverBatchSize();
    final int largeBatchSize =
        Math.min(MAX_LARGE_BATCH_SIZE, SUB_BATCHES_PER_LARGE_BATCH * rolloverBatchSize);
    // just in case rollover batch size is configured very high
    return Math.max(largeBatchSize, rolloverBatchSize);
  }
}
