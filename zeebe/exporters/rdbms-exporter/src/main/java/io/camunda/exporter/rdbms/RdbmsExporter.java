/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.rdbms;

import static org.slf4j.event.Level.DEBUG;
import static org.slf4j.event.Level.INFO;
import static org.slf4j.event.Level.TRACE;
import static org.slf4j.event.Level.WARN;

import io.camunda.db.rdbms.RdbmsSchemaManagerRegistry;
import io.camunda.db.rdbms.exception.ExporterPositionMismatchException;
import io.camunda.db.rdbms.write.RdbmsWriterMetrics.FlushTrigger;
import io.camunda.db.rdbms.write.RdbmsWriters;
import io.camunda.db.rdbms.write.domain.ExporterPositionModel;
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
import org.agrona.CloseHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.event.Level;

/** https://docs.camunda.io/docs/next/components/zeebe/technical-concepts/process-lifecycles/ */
public final class RdbmsExporter {

  private static final Logger LOG = LoggerFactory.getLogger(RdbmsExporter.class);

  private final Map<ValueType, List<RdbmsExportHandler>> registeredHandlers;
  private Controller controller;

  private final int partitionId;
  private final String physicalTenantId;
  private final RdbmsWriters rdbmsWriters;
  private final RdbmsSchemaManagerRegistry rdbmsSchemaManagerRegistry;
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
      final String physicalTenantId,
      final Duration flushInterval,
      final int queueSize,
      final RdbmsWriters rdbmsWriters,
      final Map<ValueType, List<RdbmsExportHandler>> handlers,
      final RdbmsSchemaManagerRegistry rdbmsSchemaManagerRegistry,
      final HistoryCleanupService historyCleanupService,
      final HistoryDeletionService historyDeletionService,
      final ReplicationControllerFactory replicationControllerFactory) {
    this.historyCleanupService = historyCleanupService;
    this.rdbmsWriters = rdbmsWriters;
    registeredHandlers = handlers;

    this.partitionId = partitionId;
    this.physicalTenantId = physicalTenantId;
    this.flushInterval = flushInterval;
    this.queueSize = queueSize;
    this.rdbmsSchemaManagerRegistry = rdbmsSchemaManagerRegistry;
    this.historyDeletionService = historyDeletionService;
    this.replicationControllerFactory = replicationControllerFactory;

    log(
        INFO,
        "RdbmsExporter created with Configuration: flushInterval={}, queueSize={}",
        flushInterval,
        queueSize);
  }

  public void open(final Controller controller) {
    try {
      doOpen(controller);
    } catch (final RuntimeException e) {
      // open() may fail after allocating resources (replication controller, scheduled flush task,
      // background task manager). Release them via close() before propagating so a retried open
      // starts from a clean state instead of leaking the partial allocation.
      close();
      throw e;
    }
  }

  private void doOpen(final Controller controller) {
    this.controller = controller;
    rdbmsWriters.getExecutionQueue().reset();
    log(
        INFO,
        "Opening exporter with broker position {}, last flushed position {}",
        controller.getLastExportedRecordPosition(),
        lastFlushedPosition);

    if (!rdbmsSchemaManagerRegistry.isInitialized(physicalTenantId)) {
      log(WARN, "Schema is not yet ready for use");
      throw new ExporterException("Schema is not ready for use");
    }

    initializeRdbmsPosition();
    // Use whichever of the broker-acked position and this instance's own last-flushed position
    // (preserved across a close+reopen on the same instance) is more advanced. On a cold start
    // lastFlushedPosition is still -1, so this is exactly the broker position, matching what a
    // fresh restart would use. On an in-process reopen, this instance may have already flushed
    // further than what's been acknowledged - e.g. under async LSN-based replication
    // (LsnReplicationSignalStrategy), acking is intentionally delayed until replication is
    // confirmed.
    lastPosition = Math.max(controller.getLastExportedRecordPosition(), lastFlushedPosition);
    replicationController = replicationControllerFactory.createReplicationController(controller);
    if (exporterRdbmsPosition.lastExportedPosition() > -1) {
      if (lastPosition < exporterRdbmsPosition.lastExportedPosition()) {
        // This is needed since the brokers last exported position is from its last snapshot and can
        // be different from ours.
        log(
            INFO,
            "Updating broker position {} to last exported position in rdbms {}",
            lastPosition,
            exporterRdbmsPosition.lastExportedPosition());
        lastPosition = exporterRdbmsPosition.lastExportedPosition();
        // Advance the broker through the replication controller instead of acking it directly: the
        // RDBMS position only reflects what was flushed to the primary DB, not what replicas have
        // confirmed. Under async replication acking the broker straight to this position would let
        // the journal drop records that were never replicated (see #51588). The controller acks the
        // broker according to its replication policy (immediately for NONE, once confirmed for
        // LSN).
        replicationController.onFlush(lastPosition);
      } else if (lastPosition > exporterRdbmsPosition.lastExportedPosition()) {
        log(
            INFO,
            "Broker position {} is more advanced than rdbms position {}. Requesting replay from {}",
            lastPosition,
            exporterRdbmsPosition.lastExportedPosition(),
            exporterRdbmsPosition.lastExportedPosition());
        lastPosition = exporterRdbmsPosition.lastExportedPosition();
        final boolean replayInitiated =
            controller.requestReplay(exporterRdbmsPosition.lastExportedPosition());
        if (!replayInitiated) {
          throw new ExporterException(
              String.format(
                  "[RDBMS Exporter P%d T-%s] Cannot replay records from position %d: log segments are no longer available. "
                      + "The RDBMS secondary storage cannot be recovered automatically.",
                  partitionId, physicalTenantId, exporterRdbmsPosition.lastExportedPosition() + 1));
        }
      }
    }
    lastFlushedPosition = lastPosition;

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

    log(INFO, "Exporter opened with last exported position {}", lastPosition);
  }

  public void close() {
    try {
      CloseHelper.closeAll(
          () -> {
            if (currentFlushTask != null) {
              currentFlushTask.cancel();
            }
          },
          backgroundTaskManager,
          rdbmsWriters,
          replicationController);
    } catch (final Exception e) {
      log(WARN, "Failed to flush records before closing exporter.", e);
    }

    log(
        INFO,
        "Exporter closed at positions Broker {}, RDBMS {}",
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

    log(
        TRACE,
        "Process record {}-{} - {}:{}",
        record.getPartitionId(),
        record.getPosition(),
        record.getValueType(),
        record.getIntent());

    // this may be a retry - do not re-process the record but still trigger a flush
    // when it is not a retry but a re-delivery because of other reasons (exporter guarantees
    // at-least-once), the queue will be empty and flush will be a no-op.
    final boolean alreadyProcessed = record.getPosition() <= lastPosition;

    boolean exported = false;
    boolean shouldFlushAfterRecordProcessed = false;
    if (!alreadyProcessed && registeredHandlers.containsKey(record.getValueType())) {
      for (final var handler : registeredHandlers.get(record.getValueType())) {
        if (handler.canExport(record)) {
          log(TRACE, "Exporting record {} with handler {}", record.getValue(), handler.getClass());
          handler.export(record);
          exported = true;
          shouldFlushAfterRecordProcessed |= handler.shouldFlushAfterRecordProcessed();
        } else {
          log(
              TRACE,
              "Handler {} can not export record {}",
              handler.getClass(),
              record.getValueType());
        }
      }
    } else if (!alreadyProcessed) {
      log(TRACE, "No registered handler found for {}", record.getValueType());
    }

    if (!alreadyProcessed) {
      lastPosition = record.getPosition();
    }

    if (exported || alreadyProcessed) {
      if (exported) {
        // Track the oldest record timestamp in the current batch
        final long recordTimestamp = record.getTimestamp();
        if (oldestRecordTimestampInBatch < 0 || recordTimestamp < oldestRecordTimestampInBatch) {
          oldestRecordTimestampInBatch = recordTimestamp;
        }
      }
      // causes a flush check after each processed record. Depending on the queue size and
      // configuration, the writers ExecutionQueue may or may not flush here. When a flush fails
      // transiently, lastPosition has already advanced, so the broker redelivers the same record as
      // a retry (alreadyProcessed). We force the flush to drain the batch that failed before,
      // instead of letting it linger until the next interval flush. For re-deliveries with an empty
      // queue this is a harmless no-op.
      try {
        final boolean shouldFlush =
            alreadyProcessed || flushAfterEachRecord() || shouldFlushAfterRecordProcessed;
        final boolean flushed = rdbmsWriters.flush(shouldFlush);
        if (flushed) {
          resetIntervalFlush();
        }
      } catch (final ExporterPositionMismatchException e) {
        log(
            WARN,
            "Exporter position conflict detected during flush — requesting reopen to re-sync from DB position.");
        throw new ExporterException(
            String.format(
                "[RDBMS Exporter P%d T-%s] Flush failed due to exporter position conflict;"
                    + " exporter will reopen to re-sync.",
                partitionId, physicalTenantId),
            e,
            ExporterException.Compensation.REOPEN);
      } catch (final Exception e) {
        log(
            WARN,
            "Failed to flush record for positions {} to {} to the database.",
            lastFlushedPosition + 1,
            lastPosition);
        throw e;
      }
    } else {
      log(
          TRACE,
          "Record with key {} and original partitionId {} could not be exported {}.",
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

  private void updatePositionInRdbms() {
    if (lastPosition > exporterRdbmsPosition.lastExportedPosition()) {
      log(TRACE, "Updating position to {} in rdbms", lastPosition);
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
      log(
          WARN,
          "Failed to initialize exporter position because Database is not ready, retrying ... {}",
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
      log(DEBUG, "Initialize position in rdbms");
    } else {
      log(DEBUG, "Found position in rdbms for this exporter: {}", exporterRdbmsPosition);
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
      log(
          WARN,
          "Failed to flush records for positions {} to {} to the database",
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
      log(WARN, "Unnecessary flush called, since flush interval is zero or max queue size is zero");
      return;
    }
    rdbmsWriters.getMetrics().recordQueueFlush(FlushTrigger.FLUSH_INTERVAL);
    rdbmsWriters.flush(true);
  }

  private void log(final Level level, final String message, final Object... args) {
    if (LOG.isEnabledForLevel(level)) {
      LOG.atLevel(level).log(withLogContext(message), withLogContextArgs(args));
    }
  }

  private String withLogContext(final String message) {
    return "[RDBMS Exporter P{} T-{}] " + message;
  }

  private Object[] withLogContextArgs(final Object... args) {
    final Object[] contextualArgs = new Object[args.length + 2];
    contextualArgs[0] = partitionId;
    contextualArgs[1] = physicalTenantId;
    System.arraycopy(args, 0, contextualArgs, 2, args.length);
    return contextualArgs;
  }

  @VisibleForTesting("Allows verification of registered handlers in tests")
  Map<ValueType, List<RdbmsExportHandler>> getRegisteredHandlers() {
    return registeredHandlers;
  }

  public static final class Builder {

    private int partitionId;
    private String physicalTenantId;
    private Duration flushInterval;
    private int queueSize;
    private RdbmsWriters rdbmsWriters;
    private RdbmsSchemaManagerRegistry rdbmsSchemaManagerRegistry;
    private Map<ValueType, List<RdbmsExportHandler>> handlers = new EnumMap<>(ValueType.class);
    private HistoryCleanupService historyCleanupService;
    private HistoryDeletionService historyDeletionService;
    private ReplicationControllerFactory replicationControllerFactory;

    public Builder partitionId(final int value) {
      partitionId = value;
      return this;
    }

    public Builder physicalTenantId(final String value) {
      physicalTenantId = value;
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

    public Builder rdbmsSchemaManagerRegistry(final RdbmsSchemaManagerRegistry value) {
      rdbmsSchemaManagerRegistry = value;
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
          physicalTenantId,
          flushInterval,
          queueSize,
          rdbmsWriters,
          handlers,
          rdbmsSchemaManagerRegistry,
          historyCleanupService,
          historyDeletionService,
          replicationControllerFactory);
    }
  }
}
