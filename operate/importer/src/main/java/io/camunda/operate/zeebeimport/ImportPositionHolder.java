/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.zeebeimport;

import io.camunda.operate.Metrics;
import io.camunda.operate.entities.meta.ImportPositionEntity;
import io.camunda.operate.property.OperateProperties;
import io.camunda.operate.store.ImportStore;
import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.DependsOn;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.stereotype.Component;

@Component
@DependsOn("schemaStartup")
public class ImportPositionHolder {

  private static final Logger LOGGER = LoggerFactory.getLogger(ImportPositionHolder.class);

  // this is the in-memory only storage
  private final Map<String, ImportPositionEntity> lastScheduledPositions =
      new ConcurrentHashMap<>();

  private final Map<String, ImportPositionEntity> pendingImportPositionUpdates = new HashMap<>();
  private final Map<String, ImportPositionEntity> pendingPostImportPositionUpdates =
      new HashMap<>();
  private final Map<String, ImportPositionEntity> inflightImportPositions = new HashMap<>();
  private final Map<String, ImportPositionEntity> inflightPostImportPositions = new HashMap<>();

  // highest Zeebe log position imported per partition, over all record types (metric only)
  private final Map<Integer, AtomicLong> importedPositionByPartition = new ConcurrentHashMap<>();

  private ScheduledFuture<?> scheduledImportPositionUpdateTask;
  private final ReentrantLock inflightImportPositionLock = new ReentrantLock();

  @Autowired private OperateProperties operateProperties;

  @Autowired private ImportStore importStore;

  @Autowired private Metrics metrics;

  @Autowired
  @Qualifier("importPositionUpdateThreadPoolExecutor")
  private ThreadPoolTaskScheduler importPositionUpdateExecutor;

  @PostConstruct
  private void init() {
    LOGGER.info("INIT: Start import position updater...");
    scheduleImportPositionUpdateTask();
  }

  public void scheduleImportPositionUpdateTask() {
    final var interval = operateProperties.getImporter().getImportPositionUpdateInterval();
    scheduledImportPositionUpdateTask =
        importPositionUpdateExecutor.schedule(
            this::updateImportPositions,
            OffsetDateTime.now().plus(interval, ChronoUnit.MILLIS).toInstant());
  }

  public CompletableFuture<Void> cancelScheduledImportPositionUpdateTask() {
    final var future = new CompletableFuture<Void>();
    importPositionUpdateExecutor.submit(
        () -> {
          if (scheduledImportPositionUpdateTask != null) {
            scheduledImportPositionUpdateTask.cancel(false);
            scheduledImportPositionUpdateTask = null;
          }

          future.complete(null);
        });
    return future;
  }

  public ImportPositionEntity getLatestScheduledPosition(
      final String aliasTemplate, final int partitionId) throws IOException {
    final String key = getKey(aliasTemplate, partitionId);
    if (lastScheduledPositions.containsKey(key)) {
      return lastScheduledPositions.get(key);
    } else {
      final ImportPositionEntity latestLoadedPosition =
          getLatestLoadedPosition(aliasTemplate, partitionId);
      lastScheduledPositions.put(key, latestLoadedPosition);
      // the persisted position was imported before a restart
      updateImportedPositionMetric(latestLoadedPosition);
      return latestLoadedPosition;
    }
  }

  public void recordLatestScheduledPosition(
      final String aliasName,
      final int partitionId,
      final ImportPositionEntity importPositionEntity) {
    lastScheduledPositions.put(getKey(aliasName, partitionId), importPositionEntity);
  }

  public ImportPositionEntity getLatestLoadedPosition(
      final String aliasTemplate, final int partitionId) throws IOException {
    return importStore.getImportPositionByAliasAndPartitionId(aliasTemplate, partitionId);
  }

  public void recordLatestLoadedPosition(final ImportPositionEntity lastProcessedPosition) {
    withInflightImportPositionLock(
        () -> {
          final var aliasName = lastProcessedPosition.getAliasName();
          final var partition = lastProcessedPosition.getPartitionId();
          // update only import fields (not post import)
          final String key = getKey(aliasName, partition);
          ImportPositionEntity importPosition = inflightImportPositions.get(key);
          if (importPosition == null) {
            importPosition = lastProcessedPosition;
          } else {
            importPosition
                .setPosition(lastProcessedPosition.getPosition())
                .setSequence(lastProcessedPosition.getSequence())
                .setIndexName(lastProcessedPosition.getIndexName());
          }
          inflightImportPositions.put(key, importPosition);
        });
    updateImportedPositionMetric(lastProcessedPosition);
  }

  public void recordLatestPostImportedPosition(
      final ImportPositionEntity lastPostImportedPosition) {
    withInflightImportPositionLock(
        () -> {
          final var aliasName = lastPostImportedPosition.getAliasName();
          final var partition = lastPostImportedPosition.getPartitionId();
          // update only post import fields (not import)
          final String key = getKey(aliasName, partition);
          ImportPositionEntity importPosition = inflightPostImportPositions.get(key);
          if (importPosition == null) {
            importPosition = lastPostImportedPosition;
          } else {
            importPosition.setPostImporterPosition(
                lastPostImportedPosition.getPostImporterPosition());
          }
          inflightPostImportPositions.put(key, importPosition);
        });
  }

  public void updateImportPositions() {
    withInflightImportPositionLock(
        () -> {
          pendingImportPositionUpdates.putAll(inflightImportPositions);
          inflightImportPositions.clear();
          pendingPostImportPositionUpdates.putAll(inflightPostImportPositions);
          inflightPostImportPositions.clear();
        });

    final var result =
        importStore.updateImportPositions(
            pendingImportPositionUpdates.values().stream().toList(),
            pendingPostImportPositionUpdates.values().stream().toList());

    if (result.getOrElse(false)) {
      // clear only map when updating the import positions
      // succeeded, otherwise, it may result in lost updates
      pendingImportPositionUpdates.clear();
      pendingPostImportPositionUpdates.clear();
    }

    // self scheduling just for the case the interval is set too short
    scheduleImportPositionUpdateTask();
  }

  public void clearCache() {
    importedPositionByPartition.values().forEach(position -> position.set(0));
    lastScheduledPositions.clear();
    pendingImportPositionUpdates.clear();
    pendingPostImportPositionUpdates.clear();

    withInflightImportPositionLock(
        () -> {
          inflightImportPositions.clear();
          inflightPostImportPositions.clear();
        });
  }

  /**
   * Publishes, per partition, the highest Zeebe log position of all records imported so far (over
   * all record types) as gauge {@code operate.import.position}. Same unit as Zeebe's {@code
   * zeebe.exporter.last.exported.position}; the difference is how far the importer is behind.
   */
  private void updateImportedPositionMetric(final ImportPositionEntity importedPosition) {
    if (importedPosition == null
        || !operateProperties.getImporter().isImportPositionMetricEnabled()) {
      return;
    }
    importedPositionByPartition
        .computeIfAbsent(importedPosition.getPartitionId(), this::registerImportedPositionGauge)
        .accumulateAndGet(importedPosition.getPosition(), Math::max);
  }

  private AtomicLong registerImportedPositionGauge(final int partitionId) {
    final AtomicLong position = new AtomicLong();
    metrics.registerGauge(
        Metrics.GAUGE_NAME_IMPORT_POSITION,
        position,
        AtomicLong::get,
        Metrics.TAG_KEY_PARTITION,
        String.valueOf(partitionId));
    return position;
  }

  private String getKey(final String aliasTemplate, final int partitionId) {
    return String.format("%s-%d", aliasTemplate, partitionId);
  }

  private void withInflightImportPositionLock(final Runnable action) {
    try {
      inflightImportPositionLock.lock();
      action.run();
    } finally {
      inflightImportPositionLock.unlock();
    }
  }
}
