/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.rdbms;

import io.camunda.db.rdbms.RdbmsSchemaManager;
import io.camunda.db.rdbms.write.RdbmsWriterMetrics.FlushTrigger;
import io.camunda.db.rdbms.write.RdbmsWriters;
import io.camunda.db.rdbms.write.domain.ExporterPositionModel;
import io.camunda.db.rdbms.write.service.HistoryCleanupService;
import io.camunda.db.rdbms.write.service.HistoryDeletionService;
import io.camunda.zeebe.exporter.api.ExporterException;
import io.camunda.zeebe.exporter.api.context.Controller;
import io.camunda.zeebe.exporter.api.context.ScheduledTask;
import io.camunda.zeebe.protocol.Protocol;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.util.VisibleForTesting;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** https://docs.camunda.io/docs/next/components/zeebe/technical-concepts/process-lifecycles/ */
public final class RdbmsExporter {

  private static final Logger LOG = LoggerFactory.getLogger(RdbmsExporter.class);

  private final Map<ValueType, List<RdbmsExportHandler>> registeredHandlers;
  private Controller controller;

  private final int partitionId;
  private final RdbmsWriters rdbmsWriters;
  private final RdbmsSchemaManager rdbmsSchemaManager;

  // services
  private final HistoryCleanupService historyCleanupService;
  private final HistoryDeletionService historyDeletionService;

  // configuration
  private final Duration flushInterval;
  private final int queueSize;

  // volatile runtime properties
  private ExporterPositionModel exporterRdbmsPosition;
  private long lastPosition = -1;
  private long lastFlushedPosition = -1;
  private ScheduledTask currentFlushTask = null;
  private ScheduledTask currentCleanupTask = null;
  private ScheduledTask currentUsageMetricsCleanupTask = null;
  private ScheduledTask currentJobBatchMetricsCleanupTask = null;
  private ScheduledTask currentHistoryDeletionTask = null;

  // Track the oldest record timestamp in the current batch for exporting latency calculation
  private long oldestRecordTimestampInBatch = -1;

  private RdbmsExporter(
      final int partitionId,
      final Duration flushInterval,
      final int queueSize,
      final RdbmsWriters rdbmsWriters,
      final Map<ValueType, List<RdbmsExportHandler>> handlers,
      final RdbmsSchemaManager rdbmsSchemaManager,
      final HistoryCleanupService historyCleanupService,
      final HistoryDeletionService historyDeletionService) {
    this.historyCleanupService = historyCleanupService;
    this.rdbmsWriters = rdbmsWriters;
    registeredHandlers = handlers;

    this.partitionId = partitionId;
    this.flushInterval = flushInterval;
    this.queueSize = queueSize;
    this.rdbmsSchemaManager = rdbmsSchemaManager;
    this.historyDeletionService = historyDeletionService;

    LOG.info(
        "[RDBMS Exporter P{}] RdbmsExporter created with Configuration: flushInterval={}, queueSize={}",
        partitionId,
        flushInterval,
        queueSize);
  }

  public void open(final Controller controller) {
    this.controller = controller;
    LOG.info(
        "[RDBMS Exporter P{}] Opening exporter with broker position {}",
        partitionId,
        controller.getLastExportedRecordPosition());

    if (!rdbmsSchemaManager.isInitialized()) {
      LOG.warn("[RDBMS Exporter P{}] Schema is not yet ready for use", partitionId);
      throw new ExporterException("Schema is not ready for use");
    }

    if (!flushAfterEachRecord()) {
      currentFlushTask =
          controller.scheduleCancellableTask(flushInterval, this::flushAndReschedule);
    }

    initializeRdbmsPosition();
    lastPosition = controller.getLastExportedRecordPosition();
    if (exporterRdbmsPosition.lastExportedPosition() > -1) {
      if (lastPosition < exporterRdbmsPosition.lastExportedPosition()) {
        // This is needed since the brokers last exported position is from its last snapshot and can
        // be different from ours.
        LOG.info(
            "[RDBMS Exporter P{}] Updating broker position {} to last exported position in rdbms {}",
            partitionId,
            lastPosition,
            exporterRdbmsPosition.lastExportedPosition());
        lastPosition = exporterRdbmsPosition.lastExportedPosition();
        updatePositionInBroker();
      } else if (lastPosition > exporterRdbmsPosition.lastExportedPosition()) {
        LOG.info(
            "[RDBMS Exporter P{}] Position in Broker {} is more advanced than in rdbms {}",
            partitionId,
            exporterRdbmsPosition.lastExportedPosition(),
            lastPosition);
      }
    }
    lastFlushedPosition = lastPosition;

    rdbmsWriters.getExecutionQueue().registerPreFlushListener(this::updatePositionInRdbms);
    rdbmsWriters.getExecutionQueue().registerPostFlushListener(this::updatePositionInBroker);
    rdbmsWriters.getExecutionQueue().registerPostFlushListener(this::recordExportingLatency);

    // schedule first cleanup in 1 second. Future intervals are given by the history cleanup service
    // itself
    currentCleanupTask =
        controller.scheduleCancellableTask(Duration.ofSeconds(1), this::cleanupHistory);
    currentUsageMetricsCleanupTask =
        controller.scheduleCancellableTask(Duration.ofSeconds(1), this::cleanupUsageMetricsHistory);
    currentJobBatchMetricsCleanupTask =
        controller.scheduleCancellableTask(
            Duration.ofSeconds(1), this::cleanupJobBatchMetricsHistory);
    currentHistoryDeletionTask =
        controller.scheduleCancellableTask(Duration.ofSeconds(1), this::deleteHistory);

    LOG.info(
        "[RDBMS Exporter P{}] Exporter opened with last exported position {}",
        partitionId,
        lastPosition);
  }

  public void close() {
    try {
      if (currentFlushTask != null) {
        currentFlushTask.cancel();
      }
      if (currentCleanupTask != null) {
        currentCleanupTask.cancel();
      }
      if (currentUsageMetricsCleanupTask != null) {
        currentUsageMetricsCleanupTask.cancel();
      }
      if (currentJobBatchMetricsCleanupTask != null) {
        currentJobBatchMetricsCleanupTask.cancel();
      }
      if (currentHistoryDeletionTask != null) {
        currentHistoryDeletionTask.cancel();
      }

      try {
        rdbmsWriters.flush(true);
      } catch (final Exception e) {
        LOG.warn(
            "[RDBMS Exporter P{}] Failed to execute final flush on close for partition {}",
            partitionId,
            partitionId);
        throw e;
      }
    } catch (final Exception e) {
      LOG.warn(
          "[RDBMS Exporter P{}] Failed to flush records before closing exporter.", partitionId, e);
    }

    LOG.info(
        "[RDBMS Exporter P{}] Exporter closed at positions Broker {}, RDBMS {}",
        partitionId,
        lastPosition,
        exporterRdbmsPosition == null ? null : exporterRdbmsPosition.lastExportedPosition());
  }

  public void export(final Record<?> record) {
    LOG.trace(
        "[RDBMS Exporter P{}] Process record {}-{} - {}:{}",
        partitionId,
        record.getPartitionId(),
        record.getPosition(),
        record.getValueType(),
        record.getIntent());

    boolean exported = false;
    if (registeredHandlers.containsKey(record.getValueType())) {
      for (final var handler : registeredHandlers.get(record.getValueType())) {
        if (handler.canExport(record)) {
          LOG.trace(
              "[RDBMS Exporter P{}] Exporting record {} with handler {}",
              partitionId,
              record.getValue(),
              handler.getClass());
          handler.export(record);
          exported = true;
        } else {
          LOG.trace(
              "[RDBMS Exporter P{}] Handler {} can not export record {}",
              partitionId,
              handler.getClass(),
              record.getValueType());
        }
      }
      // Update lastPosition once per record, after all handlers have processed it
      lastPosition = record.getPosition();
    } else {
      LOG.trace(
          "[RDBMS Exporter P{}] No registered handler found for {}",
          partitionId,
          record.getValueType());
    }

    if (exported) {
      // Track the oldest record timestamp in the current batch
      final long recordTimestamp = record.getTimestamp();
      if (oldestRecordTimestampInBatch < 0 || recordTimestamp < oldestRecordTimestampInBatch) {
        oldestRecordTimestampInBatch = recordTimestamp;
      }
      // causes a flush check after each processed record. Depending on the queue size and
      // configuration, the writers ExecutionQueue may or may not flush here.
      try {
        final boolean flushed = rdbmsWriters.flush(flushAfterEachRecord());
        if (flushed) {
          resetIntervalFlush();
        }
      } catch (final Exception e) {
        LOG.warn(
            "[RDBMS Exporter P{}] Failed to flush record for positions {} to {} to the database.",
            partitionId,
            lastFlushedPosition + 1,
            lastPosition);
        throw e;
      }
    } else {
      LOG.trace(
          "[RDBMS Exporter P{}] Record with key {} and original partitionId {} could not be exported {}.",
          partitionId,
          record.getKey(),
          Protocol.decodePartitionId(record.getKey()),
          record);
    }
  }

  /**
   * After a flush triggered not by an interval, we need to reset the interval flush task to avoid
   * too many flushes.
   */
  private void resetIntervalFlush() {
    if (!flushAfterEachRecord() && currentFlushTask != null) {
      currentFlushTask.cancel();
      currentFlushTask =
          controller.scheduleCancellableTask(flushInterval, this::flushAndReschedule);
    }
  }

  public void purge() {
    if (currentFlushTask != null) {
      currentFlushTask.cancel();
    }
    if (currentCleanupTask != null) {
      currentCleanupTask.cancel();
    }
    if (currentUsageMetricsCleanupTask != null) {
      currentUsageMetricsCleanupTask.cancel();
    }
    if (currentJobBatchMetricsCleanupTask != null) {
      currentJobBatchMetricsCleanupTask.cancel();
    }
    if (currentHistoryDeletionTask != null) {
      currentHistoryDeletionTask.cancel();
    }

    rdbmsWriters.getRdbmsPurger().purgeRdbms();
  }

  private void updatePositionInBroker() {
    LOG.trace("[RDBMS Exporter P{}] Updating position to {} in broker", partitionId, lastPosition);
    lastFlushedPosition = lastPosition;
    controller.updateLastExportedRecordPosition(lastPosition);
  }

  private void updatePositionInRdbms() {
    if (lastPosition > exporterRdbmsPosition.lastExportedPosition()) {
      LOG.trace("[RDBMS Exporter P{}] Updating position to {} in rdbms", partitionId, lastPosition);
      exporterRdbmsPosition =
          new ExporterPositionModel(
              exporterRdbmsPosition.partitionId(),
              exporterRdbmsPosition.exporter(),
              lastPosition,
              exporterRdbmsPosition.created(),
              LocalDateTime.now());
      rdbmsWriters.getExporterPositionService().update(exporterRdbmsPosition);
    }
  }

  private void recordExportingLatency() {
    if (oldestRecordTimestampInBatch >= 0) {
      final long latencyMs = System.currentTimeMillis() - oldestRecordTimestampInBatch;
      rdbmsWriters.getMetrics().recordExportingLatency(latencyMs);
      // Reset for the next batch
      oldestRecordTimestampInBatch = -1;
    }
  }

  private void initializeRdbmsPosition() {
    try {
      exporterRdbmsPosition = rdbmsWriters.getExporterPositionService().findOne(partitionId);
    } catch (final Exception e) {
      LOG.warn(
          "[RDBMS Exporter P{}] Failed to initialize exporter position because Database is not ready, retrying ... {}",
          partitionId,
          e.getMessage());
      throw e;
    }

    if (exporterRdbmsPosition == null) {
      exporterRdbmsPosition =
          new ExporterPositionModel(
              partitionId,
              getClass().getSimpleName(),
              lastPosition,
              LocalDateTime.now(),
              LocalDateTime.now());
      rdbmsWriters.getExporterPositionService().createWithoutQueue(exporterRdbmsPosition);
      LOG.debug("[RDBMS Exporter P{}] Initialize position in rdbms", partitionId);
    } else {
      LOG.debug(
          "[RDBMS Exporter P{}] Found position in rdbms for this exporter: {}",
          partitionId,
          exporterRdbmsPosition);
    }
  }

  private boolean flushAfterEachRecord() {
    return flushInterval.isZero() || queueSize <= 0;
  }

  @VisibleForTesting
  void flushAndReschedule() {
    try {
      flushExecutionQueue();
    } catch (final Exception e) {
      LOG.warn(
          "[RDBMS Exporter P{}] Failed to flush records for positions {} to {} to the database",
          partitionId,
          lastFlushedPosition + 1,
          lastPosition);
    } finally {
      currentFlushTask =
          controller.scheduleCancellableTask(flushInterval, this::flushAndReschedule);
    }
  }

  @VisibleForTesting
  void cleanupHistory() {
    try {
      final var newDuration =
          historyCleanupService.cleanupHistory(partitionId, OffsetDateTime.now());
      currentCleanupTask = controller.scheduleCancellableTask(newDuration, this::cleanupHistory);
    } catch (final Exception e) {
      LOG.warn(
          "[RDBMS Exporter P{}] Failed to cleanup history, retrying ... {}",
          partitionId,
          e.getMessage());
      currentCleanupTask =
          controller.scheduleCancellableTask(
              historyCleanupService.getCurrentCleanupInterval(partitionId), this::cleanupHistory);
    }
  }

  @VisibleForTesting
  void cleanupUsageMetricsHistory() {

    try {
      historyCleanupService.cleanupUsageMetricsHistory(partitionId, OffsetDateTime.now());
    } catch (final Exception e) {
      LOG.warn(
          "[RDBMS Exporter P{}] Failed to cleanup usage metrics history, retrying ... {}",
          partitionId,
          e.getMessage());
    }
    currentUsageMetricsCleanupTask =
        controller.scheduleCancellableTask(
            historyCleanupService.getUsageMetricsHistoryCleanupInterval(),
            this::cleanupUsageMetricsHistory);
  }

  @VisibleForTesting
  void cleanupJobBatchMetricsHistory() {
    try {
      historyCleanupService.cleanupJobBatchMetricsHistory(partitionId, OffsetDateTime.now());
    } catch (final Exception e) {
      LOG.warn(
          "[RDBMS Exporter P{}] Failed to cleanup job batch metrics history, retrying ... {}",
          partitionId,
          e.getMessage());
    }
    currentJobBatchMetricsCleanupTask =
        controller.scheduleCancellableTask(
            historyCleanupService.getJobBatchMetricsHistoryCleanupInterval(),
            this::cleanupJobBatchMetricsHistory);
  }

  @VisibleForTesting
  void deleteHistory() {
    try {
      final var newDuration = historyDeletionService.deleteHistory(partitionId);
      currentHistoryDeletionTask =
          controller.scheduleCancellableTask(newDuration, this::deleteHistory);
    } catch (final Exception e) {
      LOG.warn(
          "[RDBMS Exporter P{}] Failed to delete history, retrying ... {}",
          partitionId,
          e.getMessage());
      currentHistoryDeletionTask =
          controller.scheduleCancellableTask(
              historyDeletionService.getCurrentDelayBetweenRuns(), this::deleteHistory);
    }
  }

  @VisibleForTesting(
      "Each exporter creates it's own executionQueue, so we need an accessible flush method for tests")
  public void flushExecutionQueue() {
    if (flushAfterEachRecord()) {
      LOG.warn("Unnecessary flush called, since flush interval is zero or max queue size is zero");
      return;
    }
    rdbmsWriters.getMetrics().recordQueueFlush(FlushTrigger.FLUSH_INTERVAL);
    rdbmsWriters.flush(true);
  }

  @VisibleForTesting("Allows verification of registered handlers in tests")
  Map<ValueType, List<RdbmsExportHandler>> getRegisteredHandlers() {
    return registeredHandlers;
  }

  public static final class Builder {

    private int partitionId;
    private Duration flushInterval;
    private int queueSize;
    private RdbmsWriters rdbmsWriters;
    private RdbmsSchemaManager rdbmsSchemaManager;
    private Map<ValueType, List<RdbmsExportHandler>> handlers = new EnumMap<>(ValueType.class);
    private HistoryCleanupService historyCleanupService;
    private HistoryDeletionService historyDeletionService;

    public Builder partitionId(final int value) {
      partitionId = value;
      return this;
    }

    public Builder flushInterval(final Duration value) {
      flushInterval = value;
      return this;
    }

    public Builder queueSize(final int value) {
      queueSize = value;
      return this;
    }

    public Builder rdbmsWriter(final RdbmsWriters value) {
      rdbmsWriters = value;
      return this;
    }

    public Builder handlers(final Map<ValueType, List<RdbmsExportHandler>> value) {
      handlers = value;
      return this;
    }

    public Builder rdbmsSchemaManager(final RdbmsSchemaManager value) {
      rdbmsSchemaManager = value;
      return this;
    }

    public Builder withHandler(final ValueType valueType, final RdbmsExportHandler handler) {
      if (!handlers.containsKey(valueType)) {
        handlers.put(valueType, new ArrayList<>());
      }
      handlers.get(valueType).add(handler);

      return this;
    }

    public Builder historyCleanupService(final HistoryCleanupService historyCleanupService) {
      this.historyCleanupService = historyCleanupService;
      return this;
    }

    public Builder historyDeletionService(final HistoryDeletionService historyDeletionService) {
      this.historyDeletionService = historyDeletionService;
      return this;
    }

    public RdbmsExporter build() {
      return new RdbmsExporter(
          partitionId,
          flushInterval,
          queueSize,
          rdbmsWriters,
          handlers,
          rdbmsSchemaManager,
          historyCleanupService,
          historyDeletionService);
    }
  }
}
