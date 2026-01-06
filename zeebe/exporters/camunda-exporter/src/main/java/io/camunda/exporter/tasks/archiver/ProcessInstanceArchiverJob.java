/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.tasks.archiver;

import io.camunda.exporter.metrics.CamundaExporterMetrics;
import io.camunda.webapps.schema.descriptors.ProcessInstanceDependant;
import io.camunda.webapps.schema.descriptors.template.ListViewTemplate;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executor;
import org.slf4j.Logger;

public class ProcessInstanceArchiverJob extends ArchiverJob {

  private final ListViewTemplate processInstanceTemplate;
  private final List<ProcessInstanceDependant> processInstanceDependants;
  private final Executor executor;
  private final CamundaExporterMetrics metrics;
  private final Logger logger;

  public ProcessInstanceArchiverJob(
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
    this.processInstanceTemplate = processInstanceTemplate;
    this.processInstanceDependants =
        processInstanceDependants.stream()
            .sorted(Comparator.comparing(ProcessInstanceDependant::getFullQualifiedName))
            .toList(); // sort to ensure the execution order is stable
    this.executor = executor;
    this.metrics = metrics;
    this.logger = logger;
  }

  @Override
  String getJobName() {
    return "process-instance";
  }

  @Override
  CompletableFuture<ArchiveBatch> getNextBatch() {
    return getArchiverRepository().getProcessInstancesNextBatch();
  }

  @Override
  String getSourceIndexName() {
    return processInstanceTemplate.getFullQualifiedName();
  }

  @Override
  protected CompletionStage<Integer> archiveBatch(final ArchiveBatch batch) {
    if (batch == null) {
      return CompletableFuture.completedFuture(0);
    }

    final var idsMap = batch.ids();
    final int count = idsMap.values().stream().mapToInt(List::size).sum();

    if (count == 0) {
      return CompletableFuture.completedFuture(0);
    }

    logger.trace("Following {}s are found for archiving: {}", getJobName(), batch);
    metrics.recordProcessInstancesArchiving(count);

    return archive(batch.finishDate(), idsMap)
        .thenApplyAsync(
            ok -> {
              metrics.recordProcessInstancesArchived(count);
              return count;
            },
            executor);
  }

  /**
   * Overridden to archive dependants first and then move the process instances themselves.
   *
   * @param sourceIdx process instance index
   * @param finishDate move to the dated index
   * @param ids map of process instance keys to archive
   * @return future
   */
  @Override
  protected CompletableFuture<Void> archive(
      final String sourceIdx, final String finishDate, final Map<String, List<String>> ids) {
    return archiveProcessDependants(finishDate, ids)
        .thenComposeAsync(v -> super.archive(sourceIdx, finishDate, ids), executor);
  }

  private CompletableFuture<Void> archive(
      final String finishDate, final Map<String, List<String>> ids) {
    final var sourceIdx = getSourceIndexName();

    return archiveProcessDependants(finishDate, ids)
        .thenComposeAsync(
            v ->
                getArchiverRepository()
                    .moveDocuments(sourceIdx, sourceIdx + finishDate, ids, executor),
            executor);
  }

  private CompletableFuture<Void> archiveProcessDependants(
      final String finishDate, final List<String> processInstanceKeys) {
    // This is for the single list case (kept for compatibility if super calls it)
    return archiveProcessDependants(
        finishDate, Map.of(ListViewTemplate.PROCESS_INSTANCE_KEY, processInstanceKeys));
  }

  private CompletableFuture<Void> archiveProcessDependants(
      final String finishDate, final Map<String, List<String>> ids) {
    final var futures = new java.util.ArrayList<CompletableFuture<?>>();

    for (final var dependant : processInstanceDependants) {
      final var dependentSourceIdx = dependant.getFullQualifiedName();
      final var dependentIds = new HashMap<String, List<String>>();

      if (ids.containsKey(ListViewTemplate.ROOT_PROCESS_INSTANCE_KEY)) {
        dependentIds.put(
            dependant.getRootProcessInstanceKeyField(),
            ids.get(ListViewTemplate.ROOT_PROCESS_INSTANCE_KEY));
      }
      if (ids.containsKey(ListViewTemplate.PROCESS_INSTANCE_KEY)) {
        dependentIds.put(
            dependant.getProcessInstanceDependantField(),
            ids.get(ListViewTemplate.PROCESS_INSTANCE_KEY));
      }

      if (!dependentIds.isEmpty()) {
        futures.add(super.archive(dependentSourceIdx, finishDate, dependentIds));
      }
    }

    return CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new));
  }
}
