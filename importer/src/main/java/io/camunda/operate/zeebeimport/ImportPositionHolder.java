/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.operate.zeebeimport;

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
import java.util.concurrent.ScheduledFuture;
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

  private static final Logger logger = LoggerFactory.getLogger(ImportPositionHolder.class);

  // this is the in-memory only storage
  private Map<String, ImportPositionEntity> lastScheduledPositions = new HashMap<>();

  private Map<String, ImportPositionEntity> pendingImportPositionUpdates = new HashMap<>();
  private Map<String, ImportPositionEntity> pendingPostImportPositionUpdates = new HashMap<>();
  private Map<String, ImportPositionEntity> inflightImportPositions = new HashMap<>();
  private Map<String, ImportPositionEntity> inflightPostImportPositions = new HashMap<>();

  private ScheduledFuture<?> scheduledImportPositionUpdateTask;
  private ReentrantLock inflightImportPositionLock = new ReentrantLock();

  @Autowired private OperateProperties operateProperties;

  @Autowired private ImportStore importStore;

  @Autowired
  @Qualifier("importPositionUpdateThreadPoolExecutor")
  private ThreadPoolTaskScheduler importPositionUpdateExecutor;

  @PostConstruct
  private void init() {
    logger.info("INIT: Start import position updater...");
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

  public ImportPositionEntity getLatestScheduledPosition(String aliasTemplate, int partitionId)
      throws IOException {
    String key = getKey(aliasTemplate, partitionId);
    if (lastScheduledPositions.containsKey(key)) {
      return lastScheduledPositions.get(key);
    } else {
      ImportPositionEntity latestLoadedPosition =
          getLatestLoadedPosition(aliasTemplate, partitionId);
      lastScheduledPositions.put(key, latestLoadedPosition);
      return latestLoadedPosition;
    }
  }

  public void recordLatestScheduledPosition(
      String aliasName, int partitionId, ImportPositionEntity importPositionEntity) {
    lastScheduledPositions.put(getKey(aliasName, partitionId), importPositionEntity);
  }

  public ImportPositionEntity getLatestLoadedPosition(String aliasTemplate, int partitionId)
      throws IOException {
    return importStore.getImportPositionByAliasAndPartitionId(aliasTemplate, partitionId);
  }

  public void recordLatestLoadedPosition(ImportPositionEntity lastProcessedPosition) {
    withInflightImportPositionLock(
        () -> {
          final var aliasName = lastProcessedPosition.getAliasName();
          final var partition = lastProcessedPosition.getPartitionId();
          // update only import fields (not post import)
          String key = getKey(aliasName, partition);
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
  }

  public void recordLatestPostImportedPosition(ImportPositionEntity lastPostImportedPosition) {
    withInflightImportPositionLock(
        () -> {
          final var aliasName = lastPostImportedPosition.getAliasName();
          final var partition = lastPostImportedPosition.getPartitionId();
          // update only post import fields (not import)
          String key = getKey(aliasName, partition);
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
    lastScheduledPositions.clear();
    pendingImportPositionUpdates.clear();
    pendingPostImportPositionUpdates.clear();

    withInflightImportPositionLock(
        () -> {
          inflightImportPositions.clear();
          inflightPostImportPositions.clear();
        });
  }

  private String getKey(String aliasTemplate, int partitionId) {
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
