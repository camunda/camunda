/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.tasklist.zeebeimport;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.tasklist.Metrics;
import io.camunda.tasklist.property.TasklistProperties;
import io.camunda.tasklist.v86.entities.meta.ImportPositionEntity;
import io.camunda.tasklist.v86.schema.indices.TasklistImportPositionIndex;
import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.locks.ReentrantLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

public abstract class ImportPositionHolderAbstract implements ImportPositionHolder {
  private static final Logger LOGGER = LoggerFactory.getLogger(ImportPositionHolderAbstract.class);

  protected Map<String, ImportPositionEntity> lastScheduledPositions = new HashMap<>();

  protected Map<String, ImportPositionEntity> pendingProcessedPositions = new HashMap<>();
  protected Map<String, ImportPositionEntity> inflightProcessedPositions = new HashMap<>();

  protected ScheduledFuture<?> scheduledTask;
  protected ReentrantLock inflightImportPositionLock = new ReentrantLock();

  @Autowired protected TasklistImportPositionIndex importPositionType;

  @Autowired
  @Qualifier("tasklistObjectMapper")
  protected ObjectMapper objectMapper;

  @Autowired protected TasklistProperties tasklistProperties;

  @Autowired protected Metrics metrics;

  @Autowired
  @Qualifier("tasklistImportPositionUpdateThreadPoolExecutor")
  protected ThreadPoolTaskScheduler importPositionUpdateExecutor;

  @PostConstruct
  private void init() {
    LOGGER.info("INIT: Start import position updater...");
    scheduleImportPositionUpdateTask();
  }

  @Override
  public void scheduleImportPositionUpdateTask() {
    final var interval = tasklistProperties.getImporter().getImportPositionUpdateInterval();
    scheduledTask =
        importPositionUpdateExecutor.schedule(
            this::updateImportPositions,
            OffsetDateTime.now().plus(interval, ChronoUnit.MILLIS).toInstant());
  }

  @Override
  public CompletableFuture<Void> cancelScheduledImportPositionUpdateTask() {
    final var future = new CompletableFuture<Void>();
    importPositionUpdateExecutor.submit(
        () -> {
          if (scheduledTask != null) {
            scheduledTask.cancel(false);
            scheduledTask = null;
          }

          future.complete(null);
        });
    return future;
  }

  @Override
  public ImportPositionEntity getLatestScheduledPosition(
      final String aliasTemplate, final int partitionId) throws IOException {
    final String key = getKey(aliasTemplate, partitionId);
    if (lastScheduledPositions.containsKey(key)) {
      return lastScheduledPositions.get(key);
    } else {
      final ImportPositionEntity latestLoadedPosition =
          getLatestLoadedPosition(aliasTemplate, partitionId);
      lastScheduledPositions.put(key, latestLoadedPosition);
      return latestLoadedPosition;
    }
  }

  @Override
  public void recordLatestScheduledPosition(
      final String aliasName,
      final int partitionId,
      final ImportPositionEntity importPositionEntity) {
    lastScheduledPositions.put(getKey(aliasName, partitionId), importPositionEntity);
  }

  @Override
  public void recordLatestLoadedPosition(final ImportPositionEntity lastProcessedPosition) {
    withInflightImportPositionLock(
        () -> {
          final var aliasName = lastProcessedPosition.getAliasName();
          final var partition = lastProcessedPosition.getPartitionId();
          inflightProcessedPositions.put(getKey(aliasName, partition), lastProcessedPosition);
        });
  }

  @Override
  public void clearCache() {
    lastScheduledPositions.clear();
    pendingProcessedPositions.clear();
    withInflightImportPositionLock(() -> inflightProcessedPositions.clear());
  }

  @Override
  public void updateImportPositions() {
    withInflightImportPositionLock(
        () -> {
          pendingProcessedPositions.putAll(inflightProcessedPositions);
          inflightProcessedPositions.clear();
        });

    final var result = updateImportPositions(pendingProcessedPositions);

    if (result.getOrElse(false)) {
      // clear only map when updating the import positions
      // succeeded, otherwise, it may result in lost updates
      pendingProcessedPositions.clear();
    }

    // self scheduling just for the case the interval is set too short
    scheduleImportPositionUpdateTask();
  }

  private String getKey(final String aliasTemplate, final int partitionId) {
    return String.format("%s-%d", aliasTemplate, partitionId);
  }

  protected void withImportPositionTimer(final Callable<Void> action) throws Exception {
    metrics.getTimer(Metrics.TIMER_NAME_IMPORT_POSITION_UPDATE).recordCallable(action);
  }

  protected void withInflightImportPositionLock(final Runnable action) {
    try {
      inflightImportPositionLock.lock();
      action.run();
    } finally {
      inflightImportPositionLock.unlock();
    }
  }
}
