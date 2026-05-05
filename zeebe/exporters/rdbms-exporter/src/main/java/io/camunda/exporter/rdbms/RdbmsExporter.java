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
import io.camunda.db.rdbms.write.service.ExporterPositionMismatchException;
import io.camunda.db.rdbms.write.service.HistoryCleanupService;
import io.camunda.db.rdbms.write.service.HistoryDeletionService;
import io.camunda.exporter.rdbms.replication.ReplicationController;
import io.camunda.exporter.rdbms.replication.ReplicationControllerFactory;
import io.camunda.exporter.rdbms.tasks.RdbmsBackgroundTaskManager;
import io.camunda.zeebe.exporter.api.ExporterException;
import io.camunda.zeebe.exporter.api.context.Controller;
import io.camunda.zeebe.exporter.api.context.ScheduledTask;
import io.camunda.zeebe.protocol.Protocol;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.util.VisibleForTesting;
import java.time.Duration;
import java.time.LocalDateTime;
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
  private RdbmsBackgroundTaskManager backgroundTaskManager = null;

  // Track the oldest record timestamp in the current batch for exporting latency calculation
  private long oldestRecordTimestampInBatch = -1;

  // Async replication support — null when disabled
  private final ReplicationControllerFactory replicationControllerFactory;
  private ReplicationController replicationController;

  private RdbmsExporter(
      final int partitionId,
      final Duration flushInterval,
      final int queueSize,
      final RdbmsWriters rdbmsWriters,
      final Map<ValueType, List<RdbmsExportHandler>> handlers,
      final RdbmsSchemaManager rdbmsSchemaManager,
      final HistoryCleanupService historyCleanupService,
      final HistoryDeletionService historyDeletionService,
      final ReplicationControllerFactory replicationControllerFactory) {
    this.historyCleanupService = historyCleanupService;
    this.rdbmsWriters = rdbmsWriters;
    registeredHandlers = handlers;

    this.partitionId = partitionId;
    this.flushInterval = flushInterval;
    this.queueSize = queueSize;
    this.rdbmsSchemaManager = rdbmsSchemaManager;
    this.historyDeletionService = historyDeletionService;
    this.replicationControllerFactory = replicationControllerFactory;

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
            "[RDBMS Exporter P{}] Broker position {} is more advanced than rdbms position {}. Requesting replay from {}",
            partitionId,
            lastPosition,
            exporterRdbmsPosition.lastExportedPosition(),
            exporterRdbmsPosition.lastExportedPosition());
        lastPosition = exporterRdbmsPosition.lastExportedPosition();
        final boolean replayInitiated =
            controller.requestReplay(exporterRdbmsPosition.lastExportedPosition());
        if (!replayInitiated) {
          throw new ExporterException(
              String.format(
                  "[RDBMS Exporter P%d] Cannot replay records from position %d: log segments are no longer available. "
                      + "The RDBMS secondary storage cannot be recovered automatically.",
                  partitionId, exporterRdbmsPosition.lastExportedPosition() + 1));
        }
      }
    }
    lastFlushedPosition = lastPosition;

    replicationController = replicationControllerFactory.createReplicationController(controller);
    rdbmsWriters
        .getExporterPositionService()
        .registerLockPositionHook(partitionId, () -> lastFlushedPosition);

    rdbmsWriters.getExecutionQueue().registerPreFlushListener(this::updatePositionInRdbms);
    rdbmsWriters.getExecutionQueue().registerPostFlushListener(this::recordExportingLatency);
    rdbmsWriters
        .getExecutionQueue()
        .registerPostFlushListener(() -> lastFlushedPosition = lastPosition);
    rdbmsWriters
        .getExecutionQueue()
        .registerPostFlushListener(() -> replicationController.onFlush(lastPosition));

    if (!flushAfterEachRecord()) {
      currentFlushTask =
          controller.scheduleCancellableTask(flushInterval, this::flushAndReschedule);
    }

    // Start background tasks (history cleanup and deletion) in a separate thread pool,
    // decoupled from the main export thread
    backgroundTaskManager =
        new RdbmsBackgroundTaskManager(
            partitionId, historyCleanupService, historyDeletionService, LOG);
    backgroundTaskManager.start();

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
      if (backgroundTaskManager != null) {
        backgroundTaskManager.close();
        backgroundTaskManager = null;
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

      if (replicationController != null) {
        replicationController.close();
        replicationController = null;
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
    if (!replicationController.isReplicationInSync()) {
      throw new ExporterException(
          String.format(
              "[RDBMS Exporter P%d] Exporting paused: DB-reported lag exceeded maxLag. Retry later.",
              partitionId));
    }

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
    } else {
      LOG.trace(
          "[RDBMS Exporter P{}] No registered handler found for {}",
          partitionId,
          record.getValueType());
    }

    // Always update lastPosition for every record, including ignored ones, so that the exporter
    // position is advanced even when no handler processes the record.
    lastPosition = record.getPosition();

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
      } catch (final ExporterPositionMismatchException e) {
        LOG.warn(
            "[RDBMS Exporter P{}] Exporter position mismatch detected while flushing positions {} to {}. "
                + "The exporter will be closed and reopened to recover.",
            partitionId,
            lastFlushedPosition + 1,
            lastPosition);
        throw new ExporterException(
            String.format(
                "[RDBMS Exporter P%d] Exporter position mismatch detected. "
                    + "The exporter needs to be reopened to recover.",
                partitionId),
            e,
            false);
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
    if (backgroundTaskManager != null) {
      backgroundTaskManager.close();
      backgroundTaskManager = null;
    }

    rdbmsWriters.getRdbmsPurger().purgeRdbms();
  }

  private void updatePositionInBroker() {
    LOG.trace("[RDBMS Exporter P{}] Updating position to {} in broker", partitionId, lastPosition);
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
    private ReplicationControllerFactory replicationControllerFactory;

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

    public Builder replicationControllerFactory(
        final ReplicationControllerFactory replicationControllerFactory) {
      this.replicationControllerFactory = replicationControllerFactory;
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
          historyDeletionService,
          replicationControllerFactory);
    }
  }
}
