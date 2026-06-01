/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.tasks.deleter;

import io.camunda.exporter.config.ExporterConfiguration.HistoryConfiguration;
import io.camunda.exporter.metrics.CamundaExporterMetrics;
import io.camunda.exporter.store.FakeOrdinalIndexLocatorProvider;
import io.camunda.exporter.tasks.archiver.ArchiverRepository;
import io.camunda.webapps.schema.descriptors.IndexTemplateDescriptor;
import io.camunda.webapps.schema.descriptors.ProcessInstanceDependant;
import io.camunda.webapps.schema.descriptors.template.ListViewTemplate;
import io.camunda.zeebe.exporter.common.tasks.BackgroundTask;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executor;
import java.util.stream.Collectors;
import org.slf4j.Logger;

public class ProcessInstanceDeleterJob implements BackgroundTask {
  private static final int MAX_LARGE_BATCH_SIZE = 5_000;
  private static final int SUB_BATCHES_PER_LARGE_BATCH = 10;

  private final HistoryConfiguration config;
  private final ArchiverRepository repository;
  private final ListViewTemplate processInstanceTemplate;
  private final List<ProcessInstanceDependant> processInstanceDependants;
  private final CamundaExporterMetrics exporterMetrics;
  private final Logger logger;
  private final Executor executor;

  private final Queue<ProcessInstanceDeleterBatch> pendingBatches =
      new java.util.concurrent.ConcurrentLinkedQueue<>();

  public ProcessInstanceDeleterJob(
      final HistoryConfiguration config,
      final ArchiverRepository repository,
      final ListViewTemplate processInstanceTemplate,
      final List<ProcessInstanceDependant> processInstanceDependants,
      final CamundaExporterMetrics metrics,
      final Logger logger,
      final Executor executor) {
    this.config = config;
    this.repository = repository;
    this.processInstanceTemplate = processInstanceTemplate;
    this.processInstanceDependants =
        processInstanceDependants.stream()
            .sorted(Comparator.comparing(ProcessInstanceDependant::getFullQualifiedName))
            .toList(); // sort to ensure the execution order is stable
    exporterMetrics = metrics;
    this.logger = logger;
    this.executor = executor;
  }

  CompletableFuture<ProcessInstanceDeleterBatch> getNextBatch() {
    if (!pendingBatches.isEmpty()) {
      return CompletableFuture.completedFuture(pendingBatches.poll());
    }
    return repository
        .getProcessInstancesToDeleteNextBatch(largeBatchSize())
        .thenApply(
            batch -> {
              if (batch == null || batch.isEmpty()) {
                return new ProcessInstanceDeleterBatch(-1, List.of());
              }
              final var processInstancesByOrdinal =
                  batch.stream()
                      .collect(
                          Collectors.groupingBy(
                              ProcessInstanceOrdinal::ordinal,
                              Collectors.mapping(
                                  ProcessInstanceOrdinal::rootProcessInstanceKey,
                                  Collectors.toList())));

              final var chunks =
                  processInstancesByOrdinal.entrySet().stream()
                      .map(
                          entry ->
                              new ProcessInstanceDeleterBatch(entry.getKey(), entry.getValue()))
                      .flatMap(
                          deleterBatch ->
                              deleterBatch.chunk(config.getRolloverBatchSize()).stream())
                      .toList();
              final var first = chunks.getFirst();
              pendingBatches.addAll(chunks.subList(1, chunks.size()));
              return first;
            });
  }

  @Override
  public CompletionStage<Integer> execute() {
    return getNextBatch().thenComposeAsync(this::deleteBatch, executor);
  }

  private CompletionStage<Integer> deleteBatch(final ProcessInstanceDeleterBatch batch) {
    if (batch.isEmpty()) {
      return CompletableFuture.completedFuture(0);
    }
    logger.info(
        "Deleting batch of {} root process instances for ordinal {}",
        batch.rootProcessInstances().size(),
        batch.ordinal());
    return deleteProcessDependants(batch)
        .thenComposeAsync(
            v ->
                delete(
                    processInstanceTemplate,
                    batch,
                    Map.of(),
                    Map.of(
                        ListViewTemplate.JOIN_RELATION,
                        ListViewTemplate.PROCESS_INSTANCE_JOIN_RELATION)),
            executor)
        .thenComposeAsync(
            ignored ->
                delete(
                    processInstanceTemplate,
                    batch,
                    Map.of(
                        ListViewTemplate.JOIN_RELATION,
                        ListViewTemplate.PROCESS_INSTANCE_JOIN_RELATION),
                    Map.of()),
            executor);
  }

  protected CompletableFuture<Integer> delete(
      final IndexTemplateDescriptor templateDescriptor,
      final ProcessInstanceDeleterBatch batch,
      final Map<String, String> inclusionFilters) {
    return delete(templateDescriptor, batch, inclusionFilters, Map.of());
  }

  protected CompletableFuture<Integer> delete(
      final IndexTemplateDescriptor templateDescriptor,
      final ProcessInstanceDeleterBatch batch,
      final Map<String, String> inclusionFilters,
      final Map<String, String> exclusionFilters) {
    final var sourceIdxName = templateDescriptor.getFullQualifiedName();
    final var idsMap = createIdsByFieldMap(templateDescriptor, batch);
    return repository
        .deleteDocumentsById(
            sourceIdxName
                + FakeOrdinalIndexLocatorProvider.createOrdinalSuffix("ord", batch.ordinal()),
            idsMap,
            inclusionFilters,
            exclusionFilters,
            executor)
        .thenApplyAsync(ok -> batch.rootProcessInstances().size(), executor);
  }

  protected Map<String, List<String>> createIdsByFieldMap(
      final IndexTemplateDescriptor templateDescriptor, final ProcessInstanceDeleterBatch batch) {
    final Map<String, List<String>> idsMap = new HashMap<>();
    final String rootProcessInstanceKeyField;
    switch (templateDescriptor) {
      case final ListViewTemplate ignored -> {
        rootProcessInstanceKeyField = ListViewTemplate.ROOT_PROCESS_INSTANCE_KEY;
      }
      case final ProcessInstanceDependant pid -> {
        rootProcessInstanceKeyField = pid.getRootProcessInstanceKeyField();
      }
      default ->
          throw new IllegalArgumentException(
              "Unsupported template descriptor: " + templateDescriptor.getClass().getName());
    }
    idsMap.put(
        rootProcessInstanceKeyField,
        batch.rootProcessInstances().stream().map(String::valueOf).toList());
    return idsMap;
  }

  protected CompletableFuture<Void> deleteProcessDependants(
      final ProcessInstanceDeleterBatch batch) {
    final var futures = new ArrayList<CompletableFuture<?>>();

    for (final var dependant : processInstanceDependants) {
      futures.add(delete(dependant, batch, Map.of()));
    }

    // add deleting variables from the list view index as a parallel task
    futures.add(
        delete(
            processInstanceTemplate,
            batch,
            Map.of(ListViewTemplate.JOIN_RELATION, ListViewTemplate.VARIABLES_JOIN_RELATION)));

    // add deleting flownodes/activities from the list view index as a parallel task
    futures.add(
        delete(
            processInstanceTemplate,
            batch,
            Map.of(ListViewTemplate.JOIN_RELATION, ListViewTemplate.ACTIVITIES_JOIN_RELATION)));

    return CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new));
  }

  private int largeBatchSize() {
    final int rolloverBatchSize = config.getRolloverBatchSize();
    final int largeBatchSize =
        Math.min(MAX_LARGE_BATCH_SIZE, SUB_BATCHES_PER_LARGE_BATCH * rolloverBatchSize);
    // just in case rollover batch size is configured very high
    return Math.max(largeBatchSize, rolloverBatchSize);
  }
}
