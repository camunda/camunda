/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.archiver;

import io.camunda.operate.Metrics;
import io.camunda.operate.schema.templates.ListViewTemplate;
import io.camunda.operate.schema.templates.ProcessInstanceDependant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;

public abstract class AbstractProcessInstancesArchiverJob extends AbstractArchiverJob {

  private final List<Integer> partitionIds;
  private final Archiver archiver;
  private final ListViewTemplate processInstanceTemplate;
  private final List<ProcessInstanceDependant> processInstanceDependantTemplates;
  private final Metrics metrics;
  private final ArchiverRepository archiverRepository;
  private final Logger logger;

  private final Map<Integer, Long> totalPendingByPartition = new ConcurrentHashMap<>();

  protected AbstractProcessInstancesArchiverJob(
      final Archiver archiver,
      final List<Integer> partitionIds,
      final ListViewTemplate processInstanceTemplate,
      final List<ProcessInstanceDependant> processInstanceDependantTemplates,
      final Metrics metrics,
      final ArchiverRepository archiverRepository,
      final Logger logger) {
    this.archiver = archiver;
    this.partitionIds = partitionIds;
    this.processInstanceTemplate = processInstanceTemplate;
    this.processInstanceDependantTemplates = processInstanceDependantTemplates;
    this.metrics = metrics;
    this.archiverRepository = archiverRepository;
    this.logger = logger;

    partitionIds.forEach(
        partitionId -> {
          totalPendingByPartition.put(partitionId, 0L);
          metrics.registerGauge(
              Metrics.GAUGE_NAME_TOTAL_PENDING_ARCHIVE_INSTANCES,
              totalPendingByPartition,
              (pendingTotals) -> pendingTotals.getOrDefault(partitionId, 0L),
              Metrics.TAG_KEY_PARTITION,
              Integer.toString(partitionId));
        });
  }

  @Override
  public CompletableFuture<Integer> archiveBatch(final ArchiveBatch archiveBatch) {
    if (archiveBatch == null) {
      logger.debug("Nothing to archive");
      updateTotalPendingByPartition(Map.of());
      return CompletableFuture.completedFuture(0);
    }

    logger.debug("Following process instances are found for archiving: {}", archiveBatch);
    updateTotalPendingByPartition(archiveBatch.getTotalPendingByPartition());

    return archiveProcessInstances(archiveBatch)
        .thenApply(
            count -> {
              metrics.recordCounts(Metrics.COUNTER_NAME_PROCESS_INSTANCES_ARCHIVED, count);
              return count;
            });
  }

  @Override
  public CompletableFuture<ArchiveBatch> getNextBatch() {
    return archiverRepository.getProcessInstancesNextBatch(partitionIds);
  }

  public Archiver getArchiver() {
    return archiver;
  }

  public List<ProcessInstanceDependant> getProcessInstanceDependantTemplates() {
    return processInstanceDependantTemplates;
  }

  public ListViewTemplate getProcessInstanceTemplate() {
    return processInstanceTemplate;
  }

  public ArchiverRepository getArchiverRepository() {
    return archiverRepository;
  }

  private void updateTotalPendingByPartition(final Map<Integer, Long> pendingByPartition) {
    // if a partition has no entry, archiving has caught up; set pending to 0L.
    partitionIds.forEach(
        partitionId ->
            totalPendingByPartition.put(
                partitionId, pendingByPartition.getOrDefault(partitionId, 0L)));
  }

  /**
   * Archives the instances of the given non-null batch. The returned count is recorded under {@link
   * Metrics#COUNTER_NAME_PROCESS_INSTANCES_ARCHIVED} and returned to the scheduler.
   */
  protected abstract CompletableFuture<Integer> archiveProcessInstances(ArchiveBatch archiveBatch);
}
