/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.archiver;

import static org.springframework.beans.factory.config.BeanDefinition.SCOPE_PROTOTYPE;

import io.camunda.operate.Metrics;
import io.camunda.operate.schema.templates.ListViewTemplate;
import io.camunda.operate.schema.templates.ProcessInstanceDependant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

@Component
@Scope(SCOPE_PROTOTYPE)
public class ProcessInstancesArchiverJob extends AbstractArchiverJob {
  private static final Logger LOGGER = LoggerFactory.getLogger(ProcessInstancesArchiverJob.class);

  private final List<Integer> partitionIds;
  private final Archiver archiver;
  private final ListViewTemplate processInstanceTemplate;
  private final List<ProcessInstanceDependant> processInstanceDependantTemplates;
  private final Metrics metrics;
  private final ArchiverRepository archiverRepository;

  private final Map<Integer, Long> totalPendingByPartition = new ConcurrentHashMap<>();

  @Autowired
  public ProcessInstancesArchiverJob(
      final Archiver archiver,
      final List<Integer> partitionIds,
      final ListViewTemplate processInstanceTemplate,
      final List<ProcessInstanceDependant> processInstanceDependantTemplates,
      final Metrics metrics,
      final ArchiverRepository archiverRepository) {
    this.archiver = archiver;
    this.partitionIds = partitionIds;
    this.processInstanceTemplate = processInstanceTemplate;
    this.processInstanceDependantTemplates = processInstanceDependantTemplates;
    this.metrics = metrics;
    this.archiverRepository = archiverRepository;

    partitionIds.forEach(
        partitionId -> {
          totalPendingByPartition.put(partitionId, 0L);
          metrics.registerGauge(
              Metrics.GAUGE_NAME_TOTAL_PENDING_ARCHIVE_INSTANCES,
              totalPendingByPartition,
              (pendingTotals) -> totalPendingByPartition.getOrDefault(partitionId, 0L),
              Metrics.TAG_KEY_PARTITION,
              Integer.toString(partitionId));
        });
  }

  @Override
  public CompletableFuture<Integer> archiveBatch(final ArchiveBatch archiveBatch) {
    final CompletableFuture<Integer> archiveBatchFuture;

    if (archiveBatch != null) {
      LOGGER.debug("Following process instances are found for archiving: {}", archiveBatch);
      // if a partition has no entry, archiving has caught up; set pending to 0L.
      final var pendingByPartition = archiveBatch.getTotalPendingByPartition();
      partitionIds.forEach(
          partitionId ->
              totalPendingByPartition.put(
                  partitionId, pendingByPartition.getOrDefault(partitionId, 0L)));

      archiveBatchFuture = new CompletableFuture<Integer>();
      final var finishDate = archiveBatch.getFinishDate();
      final var processInstanceKeys = archiveBatch.getIds();

      moveDependableDocuments(finishDate, processInstanceKeys)
          .thenCompose(
              (v) -> {
                return moveProcessInstanceDocuments(finishDate, processInstanceKeys);
              })
          .thenAccept(
              (i) -> {
                metrics.recordCounts(Metrics.COUNTER_NAME_PROCESS_INSTANCES_ARCHIVED, i);
                archiveBatchFuture.complete(i);
              })
          .exceptionally(
              (t) -> {
                archiveBatchFuture.completeExceptionally(t);
                return null;
              });

    } else {
      LOGGER.debug("Nothing to archive");
      archiveBatchFuture = CompletableFuture.completedFuture(0);
      partitionIds.forEach(
          (partitionId) -> {
            totalPendingByPartition.put(partitionId, 0L);
          });
    }

    return archiveBatchFuture;
  }

  @Override
  public CompletableFuture<ArchiveBatch> getNextBatch() {
    return archiverRepository.getProcessInstancesNextBatch(partitionIds);
  }

  private CompletableFuture<Void> moveDependableDocuments(
      final String finishDate, final List<Object> processInstanceKeys) {
    final var dependableFutures = new ArrayList<CompletableFuture<Void>>();

    for (final ProcessInstanceDependant template : processInstanceDependantTemplates) {
      final var moveDocumentsFuture =
          archiver.moveDocuments(
              template.getFullQualifiedName(),
              ProcessInstanceDependant.PROCESS_INSTANCE_KEY,
              finishDate,
              processInstanceKeys);
      dependableFutures.add(moveDocumentsFuture);
    }

    return CompletableFuture.allOf(
        dependableFutures.toArray(new CompletableFuture[dependableFutures.size()]));
  }

  private CompletableFuture<Integer> moveProcessInstanceDocuments(
      final String finishDate, final List<Object> processInstanceKeys) {
    final var future = new CompletableFuture<Integer>();

    archiver
        .moveDocuments(
            processInstanceTemplate.getFullQualifiedName(),
            ListViewTemplate.PROCESS_INSTANCE_KEY,
            finishDate,
            processInstanceKeys)
        .thenAccept((ignore) -> future.complete(processInstanceKeys.size()))
        .exceptionally(
            (t) -> {
              future.completeExceptionally(t);
              return null;
            });

    return future;
  }
}
